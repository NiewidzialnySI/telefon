package dialer.dominik.pl.ui.screens

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.provider.Telephony
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dialer.dominik.pl.nagranie.adb.AdbParowanieDostepnosc
import dialer.dominik.pl.ui.components.IkonyAplikacji
import dialer.dominik.pl.ui.components.KartaUprawnieniaAmoled
import dialer.dominik.pl.ui.components.TloAmoled
import dialer.dominik.pl.ui.theme.LocalMotyw
import dialer.dominik.pl.ui.theme.NaPrzycisku
import dialer.dominik.pl.ui.theme.ZielonyMorski
import dialer.dominik.pl.ui.theme.ZielonyNeon
import kotlinx.coroutines.delay

private enum class TypUprawnienia {
    Systemowe,
    Nakladka,
    PelnyEkran,
    DomyslnaApka,
    ExactAlarm,
    NieznaneZrodla,
    Dostepnosc,
}

private data class Uprawnienie(
    val id: String,
    val tytul: String,
    val typ: TypUprawnienia,
    val doZadania: List<String> = emptyList(),
    val rola: String? = null,
    val opis: String? = null,
    val moznaPominac: Boolean = true
)

/**
 * Pełna lista uprawnień pierwszego uruchomienia:
 * runtime (telefon, kontakty, SMS, mikrofon, BT, powiadomienia)
 * + role (dialer / SMS / screening)
 * + ustawienia specjalne (nakładka, pełny ekran, exact alarm, instalacja APK, a11y).
 *
 * WRITE_SECURE_SETTINGS nie da się nadać dialogiem — pojawia się później przez ADB/Shizuku.
 */
private fun zbudujListeUprawnien(): List<Uprawnienie> = buildList {
    add(
        Uprawnienie(
            id = "telefon",
            tytul = "Telefon i historia",
            typ = TypUprawnienia.Systemowe,
            doZadania = listOf(
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_PHONE_NUMBERS,
                Manifest.permission.CALL_PHONE,
                Manifest.permission.ANSWER_PHONE_CALLS,
                Manifest.permission.READ_CALL_LOG
            ),
            moznaPominac = false
        )
    )
    add(
        Uprawnienie(
            id = "kontakty",
            tytul = "Kontakty",
            typ = TypUprawnienia.Systemowe,
            doZadania = listOf(
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.WRITE_CONTACTS
            ),
            opis = "Odczyt i zapis książki telefonicznej",
            moznaPominac = true
        )
    )
    add(
        Uprawnienie(
            id = "sms",
            tytul = "Wiadomości SMS",
            typ = TypUprawnienia.Systemowe,
            doZadania = listOf(
                Manifest.permission.SEND_SMS,
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_SMS,
                Manifest.permission.RECEIVE_MMS
            ),
            opis = "Pełny dostęp po ustawieniu jako domyślnej aplikacji SMS",
            moznaPominac = true
        )
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(
            Uprawnienie(
                id = "powiadomienia",
                tytul = "Powiadomienia",
                typ = TypUprawnienia.Systemowe,
                doZadania = listOf(Manifest.permission.POST_NOTIFICATIONS)
            )
        )
    }
    add(
        Uprawnienie(
            id = "mikrofon",
            tytul = "Mikrofon",
            typ = TypUprawnienia.Systemowe,
            doZadania = listOf(Manifest.permission.RECORD_AUDIO),
            opis = "Nagrywanie i transkrypcja rozmów"
        )
    )
    add(
        Uprawnienie(
            id = "bluetooth",
            tytul = "Bluetooth",
            typ = TypUprawnienia.Systemowe,
            doZadania = listOf(Manifest.permission.BLUETOOTH_CONNECT),
            opis = "Nazwy urządzeń audio podczas rozmowy"
        )
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        add(
            Uprawnienie(
                id = "domyslna_sms",
                tytul = "Domyślna aplikacja SMS",
                typ = TypUprawnienia.DomyslnaApka,
                rola = RoleManager.ROLE_SMS,
                moznaPominac = true
            )
        )
        add(
            Uprawnienie(
                id = "domyslny_dialer",
                tytul = "Domyślny dialer",
                typ = TypUprawnienia.DomyslnaApka,
                rola = RoleManager.ROLE_DIALER,
                moznaPominac = true
            )
        )
        add(
            Uprawnienie(
                id = "identyfikacja_spamu",
                tytul = "Identyfikacja i spam",
                typ = TypUprawnienia.DomyslnaApka,
                rola = RoleManager.ROLE_CALL_SCREENING,
                moznaPominac = true
            )
        )
    }
    add(
        Uprawnienie(
            id = "nakladka",
            tytul = "Wyświetlanie nad innymi",
            typ = TypUprawnienia.Nakladka,
            opis = "Dymek rozmowy po zminimalizowaniu połączenia",
            moznaPominac = true
        )
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        add(
            Uprawnienie(
                id = "pelny_ekran",
                tytul = "Pełny ekran",
                typ = TypUprawnienia.PelnyEkran,
                opis = "Powiadomienia połączeń i SMS na zablokowanym ekranie",
                moznaPominac = true
            )
        )
    }
    add(
        Uprawnienie(
            id = "alarm",
            tytul = "Dokładne alarmy",
            typ = TypUprawnienia.ExactAlarm,
            opis = "Planowane wysyłanie SMS o wskazanej godzinie",
            moznaPominac = true
        )
    )
    add(
        Uprawnienie(
            id = "instalacja",
            tytul = "Instalacja aktualizacji",
            typ = TypUprawnienia.NieznaneZrodla,
            opis = "Pozwala zainstalować pobrane aktualizacje aplikacji",
            moznaPominac = true
        )
    )
    add(
        Uprawnienie(
            id = "dostepnosc",
            tytul = "Dostępność (parowanie ADB)",
            typ = TypUprawnienia.Dostepnosc,
            opis = "Odczyt kodu Wireless debugging przy nagrywaniu rozmów",
            moznaPominac = true
        )
    )
}

private fun Uprawnienie.ikona(): ImageVector = when (id) {
    "telefon", "domyslny_dialer", "identyfikacja_spamu" -> IkonyAplikacji.Tarcza
    "kontakty" -> IkonyAplikacji.DodajKontakt
    "sms", "domyslna_sms" -> IkonyAplikacji.Wiadomosc
    "mikrofon" -> IkonyAplikacji.Nagrywanie
    "bluetooth" -> IkonyAplikacji.Bluetooth
    "nakladka", "pelny_ekran" -> IkonyAplikacji.Dymek
    "alarm" -> IkonyAplikacji.Zegar
    "instalacja" -> IkonyAplikacji.Ustawienia
    "dostepnosc" -> IkonyAplikacji.AdbWifi
    else -> IkonyAplikacji.Klodka
}

private fun Uprawnienie.czyUdzielone(context: Context): Boolean = when (typ) {
    TypUprawnienia.Systemowe ->
        doZadania.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    TypUprawnienia.Nakladka -> {
        if (Settings.canDrawOverlays(context)) true
        else {
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
            tryb == AppOpsManager.MODE_ALLOWED
        }
    }

    TypUprawnienia.DomyslnaApka -> gdyRolaUdzielona(context, rola)

    TypUprawnienia.PelnyEkran ->
        context.getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() == true

    TypUprawnienia.ExactAlarm ->
        context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true

    TypUprawnienia.NieznaneZrodla ->
        context.packageManager.canRequestPackageInstalls()

    TypUprawnienia.Dostepnosc ->
        AdbParowanieDostepnosc.czyWlaczona(context)
}

private fun gdyRolaUdzielona(context: Context, rola: String?): Boolean {
    if (rola == null) return true
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
    val rm = context.getSystemService(RoleManager::class.java) ?: return true
    if (!rm.isRoleAvailable(rola)) return true
    if (rm.isRoleHeld(rola)) return true
    // Fallback SMS: niektóre OEM (ColorOS) opóźniają RoleManager.
    if (rola == RoleManager.ROLE_SMS) {
        return Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
    }
    return false
}

private fun intencjaRoli(context: Context, rola: String): Intent? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
    val rm = context.getSystemService(RoleManager::class.java) ?: return null
    if (!rm.isRoleAvailable(rola)) return null
    return try {
        rm.createRequestRoleIntent(rola)
    } catch (_: Exception) {
        null
    }
}

private fun intencjaNakladki(context: Context): Intent =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
            .takeIf { it.resolveActivity(context.packageManager) != null }
            ?: Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
    } else {
        @Suppress("DEPRECATION")
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
    }

private fun intencjaPelnegoEkranu(context: Context): Intent =
    Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
        data = Uri.parse("package:${context.packageName}")
    }

private fun intencjaExactAlarm(context: Context): Intent =
    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
        data = Uri.parse("package:${context.packageName}")
    }

private fun intencjaNieznaneZrodla(context: Context): Intent =
    Intent(
        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:${context.packageName}")
    )

private fun intencjaDostepnosci(): Intent =
    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

private fun przywrocAplikacje(context: Context) {
    val activity = context as? Activity
    try {
        if (activity != null) {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            @Suppress("DEPRECATION")
            am?.moveTaskToFront(activity.taskId, ActivityManager.MOVE_TASK_NO_USER_ACTION)
        }
    } catch (_: Exception) {
        // Brak REORDER_TASKS — fallback niżej.
    }
    val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        ?: return
    launch.addFlags(
        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
            Intent.FLAG_ACTIVITY_SINGLE_TOP or
            Intent.FLAG_ACTIVITY_NEW_TASK,
    )
    runCatching { context.startActivity(launch) }
}

@Composable
fun PermissionsScreen(
    context: Context,
    onFinished: () -> Unit
) {
    val motyw = LocalMotyw.current
    val uprawnienia = remember { zbudujListeUprawnien() }
    var odswiezenie by remember { mutableIntStateOf(0) }
    var konczenie by remember { mutableStateOf(false) }
    var pominiete by remember { mutableStateOf(setOf<String>()) }
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

    // Activity Result — role i ustawienia BEZ FLAG_ACTIVITY_NEW_TASK
    // (NEW_TASK psuło ROLE_SMS: dialog wracał, a apka nie dostawała wyniku / ON_RESUME).
    val launcherIntencji = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        odswiezenie++
        czekamyNaUstawienia = false
    }

    val zapytanieOPermisje = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { odswiezenie++ }

    LaunchedEffect(czekamyNaUstawienia) {
        if (!czekamyNaUstawienia) return@LaunchedEffect
        repeat(120) {
            delay(400)
            odswiezenie++
            val biezace = uprawnienia.firstOrNull { up ->
                up.id !in pominiete && !up.czyUdzielone(context)
            }
            if (biezace == null || biezace.czyUdzielone(context)) {
                przywrocAplikacje(context)
                czekamyNaUstawienia = false
                return@LaunchedEffect
            }
        }
        czekamyNaUstawienia = false
    }

    val stany = remember(odswiezenie, pominiete) {
        uprawnienia.associate { it.id to (it.czyUdzielone(context) || it.id in pominiete) }
    }
    val aktualne = remember(stany) {
        uprawnienia.firstOrNull { stany[it.id] != true }
    }

    LaunchedEffect(aktualne) {
        if (aktualne == null && !konczenie) {
            konczenie = true
            delay(650)
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
                        fadeIn(tween(300)) + slideInVertically(tween(380)) { it / 5 } +
                            scaleIn(tween(380), initialScale = 0.95f)
                        ) togetherWith (
                        fadeOut(tween(200)) + slideOutVertically(tween(220)) { -it / 8 } +
                            scaleOut(tween(220), targetScale = 0.97f)
                        )
                },
                label = "przejscieKarty",
                modifier = Modifier.fillMaxWidth()
            ) { uprawnienie ->
                if (uprawnienie != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        KartaUprawnieniaAmoled(
                            ikona = uprawnienie.ikona(),
                            tytul = uprawnienie.tytul,
                            opis = uprawnienie.opis,
                            etykietaPrzycisku = when (uprawnienie.typ) {
                                TypUprawnienia.Systemowe -> "Nadaj dostęp"
                                TypUprawnienia.DomyslnaApka -> "Ustaw domyślną"
                                else -> "Otwórz ustawienia"
                            },
                            modifier = Modifier.fillMaxWidth(),
                            onKlik = {
                                when (uprawnienie.typ) {
                                    TypUprawnienia.Systemowe ->
                                        zapytanieOPermisje.launch(
                                            uprawnienie.doZadania.toTypedArray()
                                        )

                                    TypUprawnienia.DomyslnaApka -> {
                                        val rola = uprawnienie.rola
                                        val intent = rola?.let { intencjaRoli(context, it) }
                                            ?: Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                                        czekamyNaUstawienia = true
                                        runCatching { launcherIntencji.launch(intent) }
                                            .onFailure {
                                                czekamyNaUstawienia = false
                                                runCatching {
                                                    launcherIntencji.launch(
                                                        Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                                                    )
                                                    czekamyNaUstawienia = true
                                                }
                                            }
                                    }

                                    TypUprawnienia.Nakladka -> {
                                        czekamyNaUstawienia = true
                                        launcherIntencji.launch(intencjaNakladki(context))
                                    }

                                    TypUprawnienia.PelnyEkran -> {
                                        czekamyNaUstawienia = true
                                        launcherIntencji.launch(intencjaPelnegoEkranu(context))
                                    }

                                    TypUprawnienia.ExactAlarm -> {
                                        czekamyNaUstawienia = true
                                        launcherIntencji.launch(intencjaExactAlarm(context))
                                    }

                                    TypUprawnienia.NieznaneZrodla -> {
                                        czekamyNaUstawienia = true
                                        launcherIntencji.launch(intencjaNieznaneZrodla(context))
                                    }

                                    TypUprawnienia.Dostepnosc -> {
                                        czekamyNaUstawienia = true
                                        AdbParowanieDostepnosc.oznaczWrocPoWlaczeniu()
                                        launcherIntencji.launch(intencjaDostepnosci())
                                    }
                                }
                            }
                        )
                        if (uprawnienie.moznaPominac) {
                            TextButton(
                                onClick = {
                                    pominiete = pominiete + uprawnienie.id
                                    odswiezenie++
                                }
                            ) {
                                Text(
                                    text = "Pomiń na razie",
                                    color = motyw.tekstDrugorzedny,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                } else {
                    AnimacjaZakonczenia(aktywna = konczenie)
                }
            }
        }
    }
}

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
                tint = NaPrzycisku,
                modifier = Modifier.size(46.dp)
            )
        }
        CircularProgressIndicator(
            modifier = Modifier.size(136.dp),
            color = ZielonyNeon.copy(alpha = 0.35f * postep),
            strokeWidth = 2.dp
        )
    }
}
