package dialer.dominik.pl.ui.screens

import android.Manifest
import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dialer.dominik.pl.ui.components.IkonyAplikacji
import dialer.dominik.pl.ui.components.KartaUprawnieniaAmoled
import dialer.dominik.pl.ui.components.TloAmoled
import dialer.dominik.pl.ui.theme.ZielonyMorski
import dialer.dominik.pl.ui.theme.ZielonyNeon
import kotlinx.coroutines.delay

// Sposob nadania uprawnienia
private enum class TypUprawnienia { Systemowe, Nakladka, PelnyEkran, DomyslnaApka }

private data class Uprawnienie(
    val id: String,
    val tytul: String,
    val ikonaTarcza: Boolean,
    val typ: TypUprawnienia,
    // System żąda uprawnień GRUPAMI. Żądanie pojedynczego uprawnienia z grupy,
    // której aplikacja nie ma, kończy się ciszą - dialog się nie pojawia.
    // Dlatego dla każdej pozycji podajemy wszystkie potrzebne uprawnienia naraz.
    val doZadania: List<String> = emptyList(),
    // Dla roli domyślnej aplikacji (np. SMS, Dialer) — nazwa roli z RoleManager.
    val rola: String? = null
)

// Lista uprawnien - tylko tytul i ikona, bez opisow
private fun zbudujListeUprawnien(): List<Uprawnienie> = buildList {
    // Grupa PHONE: READ_PHONE_STATE + READ_PHONE_NUMBERS muszą iść razem
    add(Uprawnienie(
        "stan_telefonu", "Stan telefonu", true, TypUprawnienia.Systemowe,
        listOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_PHONE_NUMBERS)
    ))
    // Grupa CALL_LOG
    add(Uprawnienie(
        "historia", "Historia połączeń", false, TypUprawnienia.Systemowe,
        listOf(Manifest.permission.READ_CALL_LOG)
    ))
    add(Uprawnienie(
        "polaczenie", "Telefon", true, TypUprawnienia.Systemowe,
        listOf(Manifest.permission.CALL_PHONE)
    ))
    add(Uprawnienie(
        "odbieranie", "Odbieranie połączeń", true, TypUprawnienia.Systemowe,
        listOf(Manifest.permission.ANSWER_PHONE_CALLS, Manifest.permission.READ_PHONE_STATE)
    ))
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Uprawnienie(
            "powiadomienia", "Powiadomienia", false, TypUprawnienia.Systemowe,
            listOf(Manifest.permission.POST_NOTIFICATIONS)
        ))
    }
    // Grupa CONTACTS — dialer wyszukuje kontakty po nazwie i numerze
    add(Uprawnienie(
        "kontakty", "Kontakty", false, TypUprawnienia.Systemowe,
        listOf(Manifest.permission.READ_CONTACTS)
    ))
    add(Uprawnienie(
        "nagrywanie", "Nagrywanie rozmów", false, TypUprawnienia.Systemowe,
        listOf(Manifest.permission.RECORD_AUDIO)
    ))
    // Grupa SMS: wszystkie trzy naraz - inaczej system odmawia po cichu
    add(Uprawnienie(
        "wysylanie_sms", "Wysyłanie SMS", false, TypUprawnienia.Systemowe,
        listOf(Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)
    ))
    add(Uprawnienie(
        "odbieranie_sms", "Odbieranie SMS", false, TypUprawnienia.Systemowe,
        listOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS)
    ))
    add(Uprawnienie(
        "czytanie_sms", "Czytanie SMS", false, TypUprawnienia.Systemowe,
        listOf(Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS)
    ))
    // Rola domyślnej aplikacji SMS — bez niej Android nie przekazuje
    // przychodzących wiadomości, a sam wysyłać można tylko przez intencję.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        add(Uprawnienie(
            "domyslna_sms", "Domyślna aplikacja SMS", false, TypUprawnienia.DomyslnaApka,
            rola = android.app.role.RoleManager.ROLE_SMS
        ))
    }
    // Rola domyślnego dialera — aby połączenia używały naszego ekranu rozmowy.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        add(Uprawnienie(
            "domyslny_dialer", "Domyślny dialer", true, TypUprawnienia.DomyslnaApka,
            rola = android.app.role.RoleManager.ROLE_DIALER
        ))
        // Bez tej roli UslugaIdentyfikacjiPolaczen nie dostaje połączeń do blokady spamu.
        add(Uprawnienie(
            "identyfikacja_spamu", "Identyfikacja rozmów i spamu", true, TypUprawnienia.DomyslnaApka,
            rola = android.app.role.RoleManager.ROLE_CALL_SCREENING
        ))
    }
    add(Uprawnienie("nakladka", "Wyświetlanie nad aplikacjami", false, TypUprawnienia.Nakladka))
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        add(Uprawnienie("pelny_ekran", "Pełny ekran", false, TypUprawnienia.PelnyEkran))
    }
}

// Sprawdza stan uprawnienia. Dla nakladki uzywamy AppOps, bo Settings.canDrawOverlays
// bywa opoznione o kilka sekund po powrocie z ustawien.
private fun Uprawnienie.czyUdzielone(context: Context): Boolean {
    return when (typ) {
        TypUprawnienia.Systemowe ->
            doZadania.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

        // NAKŁADKA: trzy źródła prawdy. Settings.canDrawOverlays bywa spóźnione
        // o kilka sekund po powrocie z ustawień, więc sprawdzamy też AppOps.
        TypUprawnienia.Nakladka -> {
            if (Settings.canDrawOverlays(context)) return true
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            val tryb = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                @Suppress("DEPRECATION")
                appOps?.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW,
                    Process.myUid(),
                    context.packageName
                )
            } else {
                appOps?.checkOpNoThrow(
                    AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW,
                    Process.myUid(),
                    context.packageName
                )
            }
            tryb == AppOpsManager.MODE_ALLOWED || Settings.canDrawOverlays(context)
        }

        // ROLA domyślnej aplikacji (SMS/Dialer) — sprawdzamy przez RoleManager.
        // Gdy rola niedostępna (starsze urządzenie) uznajemy za nadaną,
        // żeby nie blokować przejścia.
        TypUprawnienia.DomyslnaApka -> {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
            val roleManager = context.getSystemService(android.app.role.RoleManager::class.java)
                ?: return true
            val rola = rola ?: return true
            if (!roleManager.isRoleAvailable(rola)) return true
            roleManager.isRoleHeld(rola)
        }

        TypUprawnienia.PelnyEkran ->
            context.getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() == true
    }
}

// Otwiera ustawienia systemowe. Zwraca informacje, czy ekran sie otworzyl.
private fun otworzUstawienia(context: Context, intencja: Intent): Boolean = try {
    intencja.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intencja)
    true
} catch (e: Exception) {
    e.printStackTrace()
    false
}

/**
 * Przywraca aplikację na wierzch po nadaniu uprawnienia w Ustawieniach.
 *
 * Od Androida 11 nie da się już otworzyć ustawień nakładki bezpośrednio dla
 * aplikacji (system ignoruje package:) i nie istnieje API zamykające Ustawienia.
 * Ale aplikacja MOŻE przywrócić własne zadanie na wierzch z uprawnieniem
 * SYSTEM_ALERT_WINDOW — a dokładnie to uprawnienie właśnie przed chwilą nadano.
 * To jedyna legalna droga: nie wymaga REFLECT_USE_EXPLICIT_OVERRIDES ani
 * innych obejść z listy restricted permissions Google Play.
 */
private fun przywrocAplikacje(context: Context) {
    try {
        val menedzer = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        menedzer?.moveTaskToFront(context.packageName.hashCode(), ActivityManager.MOVE_TASK_NO_USER_ACTION
                or ActivityManager.MOVE_TASK_WITH_HOME, null)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

/** Intencja do ustawien "Wyswietlanie nad innymi aplikacjami" dla tej apki. */
private fun intencjaNakladki(context: Context): Intent =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        // Od Androida 11 system ignoruje package: i pokazuje liste - to jedyna droga
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
    } else {
        @Suppress("DEPRECATION")
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.packageName))
    }

/** Intencja do ustawien "Pelny ekran" (Android 14+). */
private fun intencjaPelnegoEkranu(context: Context): Intent =
    Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
        data = Uri.parse("package:" + context.packageName)
    }

/**
 * Ekran uprawnien: bez paska postepu i bez opisow.
 * Zostaje ikona, nazwa uprawnienia i przycisk.
 * Po nadaniu ostatniego uprawnienia leci krotka animacja i przejscie do dialera.
 */
@Composable
fun PermissionsScreen(
    context: Context,
    onFinished: () -> Unit
) {
    val uprawnienia = remember { zbudujListeUprawnien() }
    var odswiezenie by remember { mutableIntStateOf(0) }
    var konczenie by remember { mutableStateOf(false) }
    var czekamyNaUstawienia by remember { mutableStateOf(false) }

    val cykl = LocalLifecycleOwner.current
    DisposableEffect(cykl) {
        val obserwator = LifecycleEventObserver { _, zdarzenie ->
            if (zdarzenie == Lifecycle.Event.ON_RESUME) {
                czekamyNaUstawienia = false
                odswiezenie++
            }
        }
        cykl.lifecycle.addObserver(obserwator)
        onDispose { cykl.lifecycle.removeObserver(obserwator) }
    }

    // Strażnik powrotu z Ustawień.
    // Co 350 ms sprawdzamy, czy użytkownik włączył uprawnienie. Gdy tak się stało,
    // aplikacja SAMA wraca na wierzch (moveTaskToFront) — nie trzeba klikać "wstecz".
    // Działa tylko wtedy, gdy zadanie aplikacji jest w tle, czyli użytkownik
    // faktycznie jest w Ustawieniach. Po powrocie pętla kończy działanie.
    LaunchedEffect(czekamyNaUstawienia) {
        if (!czekamyNaUstawienia) return@LaunchedEffect
        repeat(200) { // maksymalnie ok. 70 sekund
            delay(350)
            val aktualne = uprawnienia.firstOrNull { !it.czyUdzielone(context) }
            // Uprawnienie nadane? Wracamy do aplikacji i odświeżamy stan.
            // Dla uprawnień otwierających Ustawienia (nakładka, pełny ekran, rola)
            // też sprawdzamy, czy użytkownik wrócił — aplikacja nie może zamknąć
            // Ustawień, więc samo wraca na wierzch po nadaniu uprawnienia.
            if (aktualne == null) {
                odswiezenie++
                przywrocAplikacje(context)
                czekamyNaUstawienia = false
                return@LaunchedEffect
            }
            odswiezenie++
        }
        czekamyNaUstawienia = false
    }

    // ŻĄDANIE GRUPOWE: RequestMultiplePermissions zamiast RequestPermission.
    // System pokazuje JEDEN dialog na grupę i przyznaje wszystkie pozycje z listy.
    // Pojedyncze żądanie dla READ_SMS/RECEIVE_SMS/READ_PHONE_STATE kończyło się
    // ciszą, bo aplikacja nie miała żadnego uprawnienia z danej grupy.
    val zapytanieOPermisje = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { odswiezenie++ }

    val stany = remember(odswiezenie) {
        uprawnienia.associate { it.id to it.czyUdzielone(context) }
    }

    val aktualne = remember(stany) { uprawnienia.firstOrNull { stany[it.id] != true } }

    // Po nadaniu ostatniego uprawnienia: krotka animacja, potem przejscie dalej
    LaunchedEffect(aktualne) {
        if (aktualne == null && !konczenie) {
            konczenie = true
            delay(700)
            onFinished()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        TloAmoled()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 22.dp)
                .padding(bottom = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = aktualne,
                transitionSpec = {
                    (
                        fadeIn(tween(320)) + slideInVertically(tween(420)) { it / 4 } +
                            scaleIn(tween(420), initialScale = 0.94f)
                        ) togetherWith (
                        fadeOut(tween(220)) + slideOutVertically(tween(260)) { -it / 6 } +
                            scaleOut(tween(260), targetScale = 0.96f)
                        )
                },
                label = "przejscieKarty"
            ) { uprawnienie ->
                if (uprawnienie != null) {
                    KartaUprawnieniaAmoled(
                        ikona = if (uprawnienie.ikonaTarcza) IkonyAplikacji.Tarcza else IkonyAplikacji.Klodka,
                        tytul = uprawnienie.tytul,
                        etykietaPrzycisku = if (uprawnienie.typ == TypUprawnienia.Systemowe) {
                            "Nadaj dostep"
                        } else if (uprawnienie.typ == TypUprawnienia.DomyslnaApka) {
                            "Ustaw domyslną"
                        } else {
                            "Otworz ustawienia"
                        },
                        modifier = Modifier.fillMaxWidth(),
                        onKlik = {
                            when (uprawnienie.typ) {
                                TypUprawnienia.Systemowe ->
                                    zapytanieOPermisje.launch(uprawnienie.doZadania.toTypedArray())

                                TypUprawnienia.DomyslnaApka -> {
                                    val zarzadcaRol = context.getSystemService(
                                        android.app.role.RoleManager::class.java
                                    )
                                    // ROLE_CALL_SCREENING i dialer: najpierw systemowy dialog roli.
                                    val intencjaRoli = uprawnienie.rola?.let {
                                        zarzadcaRol?.createRequestRoleIntent(it)
                                    }
                                    if (intencjaRoli != null && otworzUstawienia(context, intencjaRoli)) {
                                        czekamyNaUstawienia = true
                                    } else {
                                        val ustawienia = Intent(
                                            android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS
                                        )
                                        if (otworzUstawienia(context, ustawienia)) {
                                            czekamyNaUstawienia = true
                                        }
                                    }
                                }

                                TypUprawnienia.Nakladka -> {
                                    if (otworzUstawienia(context, intencjaNakladki(context))) {
                                        czekamyNaUstawienia = true
                                    }
                                }

                                TypUprawnienia.PelnyEkran -> {
                                    if (otworzUstawienia(context, intencjaPelnegoEkranu(context))) {
                                        czekamyNaUstawienia = true
                                    }
                                }
                            }
                        }
                    )
                } else {
                    AnimacjaZakonczenia(aktywna = konczenie)
                }
            }
        }
    }
}

/** Krotka, czysta animacja po nadaniu ostatniego uprawnienia - bez tekstu. */
@Composable
private fun AnimacjaZakonczenia(aktywna: Boolean) {
    val postep by animateFloatAsState(
        targetValue = if (aktywna) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "animacjaZakonczenia"
    )

    Box(contentAlignment = Alignment.Center) {
        // Poswiata
        Box(
            modifier = Modifier
                .size(160.dp)
                .scale(0.5f + 0.5f * postep)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            ZielonyMorski.copy(alpha = 0.30f * postep),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )
        // Zielone kolko z ptaszkiem
        Box(
            modifier = Modifier
                .size(88.dp)
                .scale(0.6f + 0.4f * postep)
                .background(
                    brush = Brush.linearGradient(colors = listOf(ZielonyMorski, ZielonyNeon)),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = IkonyAplikacji.Sprawdzenie,
                contentDescription = null,
                tint = Color(0xFF06231A),
                modifier = Modifier.size(46.dp)
            )
        }
        // Delikatny wskaznik, ze zaraz nastapi przejscie
        CircularProgressIndicator(
            modifier = Modifier.size(136.dp),
            color = ZielonyNeon.copy(alpha = 0.35f * postep),
            strokeWidth = 2.dp
        )
    }
}
