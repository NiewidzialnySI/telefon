package dialer.dominik.pl.ui.screens

import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable

import android.Manifest
import android.app.role.RoleManager
import android.content.ContentProviderOperation
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.CallLog
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.EaseInCubic
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dialer.dominik.pl.data.BazaSpamu
import dialer.dominik.pl.call.NagrywanieRozmowService
import dialer.dominik.pl.call.NotatkiRozmow
import dialer.dominik.pl.ui.components.IkonyAplikacji
import dialer.dominik.pl.ui.theme.CzerwonyPolaczenia
import dialer.dominik.pl.ui.theme.ZielonyGleboki
import dialer.dominik.pl.ui.theme.ZielonyMorski
import dialer.dominik.pl.ui.theme.ZielonyNeon
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dialer.dominik.pl.data.GrupaPolaczen
import dialer.dominik.pl.data.domyslnyZakres
import dialer.dominik.pl.data.filtrujUkryte
import dialer.dominik.pl.data.grupujPolaczenia
import dialer.dominik.pl.data.nastepnyZakres
import dialer.dominik.pl.data.normalizujNumer

data class AkcjaUlubionego(
    val kontakt: KontaktItem,
    val pivotX: Float,
    val pivotY: Float
)

/** Stan dostępu do danych telefonu. */
internal data class StanDanych(
    val maKontakty: Boolean,
    val maHistorie: Boolean
)

internal fun maUprawnienie(context: Context, uprawnienie: String): Boolean =
    context.checkSelfPermission(uprawnienie) == android.content.pm.PackageManager.PERMISSION_GRANTED

private fun sprawdzStanDanych(context: Context) = StanDanych(
    maKontakty = maUprawnienie(context, Manifest.permission.READ_CONTACTS),
    maHistorie = maUprawnienie(context, Manifest.permission.READ_CALL_LOG)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainDialerScreen(
    context: Context,
    onOpenSms: (String) -> Unit,
    poczatkowyNumer: String = "",
    onNumerSkonsumowany: () -> Unit = {}
) {
    var wprowadzonyNumer by remember { mutableStateOf(poczatkowyNumer) }
    var pokazKlawiaturę by remember { mutableStateOf(poczatkowyNumer.isNotBlank()) }

    LaunchedEffect(poczatkowyNumer) {
        if (poczatkowyNumer.isNotBlank()) {
            wprowadzonyNumer = poczatkowyNumer
            pokazKlawiaturę = true
            onNumerSkonsumowany()
        }
    }
    // Stan ekranu głównego zachowywany między przełączaniem zakładek (SaveableStateHolder).
    // Dzięki temu powrót na tę zakładkę NIE pokazuje ponownie spinnera ładowania.
    var historia by rememberSaveable(stateSaver = EstadoSaverCallLog) { mutableStateOf<List<CallLogEntry>>(emptyList()) }
    var czyLadujeHistorie by rememberSaveable { mutableStateOf(true) }
    var najnowszyIdCache by remember { mutableLongStateOf(-1L) }
    var sugestieKontakty by remember { mutableStateOf<List<ContactSuggestion>>(emptyList()) }
    var jestDomyslny by remember { mutableStateOf(true) }
    // Ulubione kontakty — pokazywane na górze historii połączeń jako kółka.
    var ulubioneNazwy by rememberSaveable { mutableStateOf<Set<String>>(emptySet()) }
    var ulubioneKontakty by rememberSaveable(stateSaver = EstadoSaverKontakt) { mutableStateOf<List<KontaktItem>>(emptyList()) }
    // Ulubiony wybrany kliknięciem → okno wychodzące bezpośrednio z klikniętego kółka.
    var ulubionyDoAkcji by remember { mutableStateOf<AkcjaUlubionego?>(null) }
    var odswiezenie by remember { mutableIntStateOf(0) }
    var stanDanych by remember { mutableStateOf(StanDanych(false, false)) }
    var pokazDialogKontakt by remember { mutableStateOf(false) }
    // Numer z kafelka historii (long-press), dla którego otwieramy dodanie kontaktu.
    var kontaktDoDodania by remember { mutableStateOf<String?>(null) }
    // Numer czekający na potwierdzenie dodania do spamu (przeciągnięcie w prawo).
    var spamDoPotwierdzenia by remember { mutableStateOf<String?>(null) }
    // Czy pokazać menu wklejania po długim przytrzymaniu pola numeru.
    var pokazMenuWklejania by remember { mutableStateOf(false) }

    var zakresOd by remember { mutableLongStateOf(domyslnyZakres()) }
        var rozwinieci by remember { mutableStateOf(setOf<String>()) }
        // Ile wpisów rozwiniętej grupy renderujemy naraz. Doładowujemy o 10, gdy
        // lista zbliża się do końca (żeby duża grupa 97× nie generowała wszystkich
        // wierszy od razu i nie lagowała). Reset przy zamknięciu grupy.
        var limitWpisowGrupy by remember { mutableIntStateOf(10) }

            val prefs = remember { context.getSharedPreferences("telefon_polaczenia", Context.MODE_PRIVATE) }
    var ukryteDo by remember {
        mutableStateOf(
            prefs.all.mapNotNull { (klucz, wartosc) ->
                (wartosc as? Long)?.let { klucz to it }
            }.toMap()
        )
    }

    // Gdy klawiatura numeryczna jest otwarta, przycisk/gest wstecz chowa ją
    // (i czyści wpisany numer) zamiast zamykać aplikację czy wracać na inny ekran.
    // Ten wewnętrzny BackHandler ma pierwszeństwo nad globalnym w MainActivity.
    val klawiaturaOtwarta = pokazKlawiaturę || wprowadzonyNumer.isNotEmpty()
    BackHandler(enabled = klawiaturaOtwarta) {
        pokazKlawiaturę = false
        wprowadzonyNumer = ""
    }

    val cykl = LocalLifecycleOwner.current
    DisposableEffect(cykl) {
        val obserwator = LifecycleEventObserver { _, zdarzenie ->
            if (zdarzenie == Lifecycle.Event.ON_RESUME) odswiezenie++
        }
        cykl.lifecycle.addObserver(obserwator)
        onDispose { cykl.lifecycle.removeObserver(obserwator) }
    }

    LaunchedEffect(odswiezenie) {
        val stan = sprawdzStanDanych(context)
        stanDanych = stan

        // Pobieramy historię tylko, gdy CallLog realnie się zmienił (najnowsze
        // ID połączenia jest inne niż w cache) albo jeszcze nigdy nie pobrano.
        // Powrót na ekran bez nowych połączeń nie wywołuje pełnego zapytania.
        // Zapytania do CallLog wykonujemy na wątku IO — poza UI.
        if (stan.maHistorie) {
            val nowaHistoria = withContext(Dispatchers.IO) {
                val najnowszyId = pobierzNajnowszyIdPolaczenia(context)
                if (najnowszyId != najnowszyIdCache) {
                    // Wracamy na wątek główny z nową historią + id do aktualizacji cache.
                    Pair(najnowszyId, pobierzOstatniePolaczenia(context))
                } else {
                    null
                }
            }
            if (nowaHistoria != null) {
                najnowszyIdCache = nowaHistoria.first
                historia = nowaHistoria.second
            }
        } else {
                    historia = emptyList()
                    najnowszyIdCache = -1L
                }
                // Pierwsze pobranie zakończone — zwolnij stan ładowania OD RAZU (ulubione i historia
                // są już w pamięci; brak spinnera przy powrocie na zakładkę).
                czyLadujeHistorie = false
                jestDomyslny = czyJestDomyslnymDialerem(context)
        // Wczytaj ulubione kontakty (te same „ulubione”, co na ekranie Kontakty).
        if (stan.maKontakty) {
            val prefsU = context.getSharedPreferences(
                "kontakty_ulubione", Context.MODE_PRIVATE
            )
            val nazwy = prefsU.getStringSet("nazwy", emptySet()) ?: emptySet()
            ulubioneNazwy = nazwy
            ulubioneKontakty = withContext(Dispatchers.IO) {
                pobierzKontakty(context).filter { it.name in nazwy }
            }
        } else {
            ulubioneNazwy = emptySet()
            ulubioneKontakty = emptyList()
        }
    }

    LaunchedEffect(wprowadzonyNumer, stanDanych.maKontakty) {
        // Debounce: czekamy chwilę po ostatniej wpisanej cyfrze, zamiast wysyłać
        // zapytanie do kontaktów przy każdej cyfrze. Szybkie „123456" odpalają
        // 1 zapytanie zamiast 6.
        if (wprowadzonyNumer.isNotBlank() && stanDanych.maKontakty) {
            kotlinx.coroutines.delay(250)
            // Zapytanie do ContentResolver na wątku IO — poza UI.
            sugestieKontakty = withContext(Dispatchers.IO) {
                wyszukajKontakty(context, wprowadzonyNumer)
            }
        } else {
            sugestieKontakty = emptyList()
        }
    }

    if (pokazDialogKontakt) {
        DialogDodajKontakt(
                    context = context,
                    domyslnyNumer = wprowadzonyNumer,
                    onDismiss = { pokazDialogKontakt = false },
                    onZapisano = {
                        pokazDialogKontakt = false
                        uniewaznijCacheKontaktow()
                        odswiezenie++
                    }
                )
            }

            // Long-press na numer bez kontaktu → dodanie do książki.
            kontaktDoDodania?.let { numer ->
                DialogDodajKontakt(
                    context = context,
                    domyslnyNumer = numer,
                    onDismiss = { kontaktDoDodania = null },
                    onZapisano = {
                        kontaktDoDodania = null
                        uniewaznijCacheKontaktow()
                        odswiezenie++
                    }
                )
            }

    // Przeciągnięcie w prawo → potwierdzenie dodania do spamu.
    spamDoPotwierdzenia?.let { numer ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { spamDoPotwierdzenia = null },
            containerColor = Color(0xFF141F19),
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.8f),
            title = { Text("Dodać do spamu?") },
            text = { Text("Numer $numer zostanie zablokowany. Na pewno chcesz dodać go do spamu?") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    BazaSpamu.dodajDoSpamu(context, numer)
                    spamDoPotwierdzenia = null
                    Toast.makeText(context, "Dodano do spamu", Toast.LENGTH_SHORT).show()
                    odswiezenie++
                }) {
                    Text("Zablokuj", color = CzerwonyPolaczenia, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { spamDoPotwierdzenia = null }) {
                    Text("Anuluj", color = Color.White.copy(alpha = 0.7f))
                }
            }
        )
    }

    // Ulubiony kliknięty → okno dialogowe rozwijane i zwijane bezpośrednio z punktu klikniętego kółka.
    ulubionyDoAkcji?.let { akcja ->
        val kontakt = akcja.kontakt
        val numerGłówny = kontakt.number.ifBlank { kontakt.numery.firstOrNull()?.surowy ?: "" }
        val inicjał = kontakt.nazwaWyswietlana.firstOrNull()?.uppercaseChar()?.toString() ?: "?"

        var widocznyDialog by remember(kontakt) { mutableStateOf(false) }
        val coroutineScope = rememberCoroutineScope()

        // Aktywujemy animację wejścia po zamontowaniu
        LaunchedEffect(kontakt) {
            widocznyDialog = true
        }

        // Pomocnicza funkcja do płynnego zamknięcia z animacją wyjścia w stronę kółka
        val zamknijZAnimacja: (() -> Unit) -> Unit = { poAnimacji ->
            if (widocznyDialog) {
                widocznyDialog = false
                coroutineScope.launch {
                    delay(150)
                    ulubionyDoAkcji = null
                    poAnimacji()
                }
            } else {
                ulubionyDoAkcji = null
                poAnimacji()
            }
        }

        androidx.compose.ui.window.Dialog(
            onDismissRequest = { zamknijZAnimacja {} },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            AnimatedVisibility(
                visible = widocznyDialog,
                enter = fadeIn(animationSpec = tween(150)) +
                        scaleIn(
                            initialScale = 0.05f,
                            transformOrigin = TransformOrigin(akcja.pivotX, akcja.pivotY),
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        ),
                exit = fadeOut(animationSpec = tween(140, easing = EaseInCubic)) +
                       scaleOut(
                           targetScale = 0.05f,
                           transformOrigin = TransformOrigin(akcja.pivotX, akcja.pivotY),
                           animationSpec = tween(140)
                       )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.88f)
                        .clip(RoundedCornerShape(30.dp))
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF1B2A22),
                                    Color(0xFF0F1813)
                                )
                            )
                        )
                        .border(
                            border = BorderStroke(1.dp, ZielonyNeon.copy(alpha = 0.25f)),
                            shape = RoundedCornerShape(30.dp)
                        )
                        .padding(24.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // --- Awatar (Inicjał w kole z gradientem i poświatą) ---
                        AwatarKontaktu(
                            fotoUri = kontakt.fotoUri,
                            inicial = inicjał,
                            context = context,
                            rozmiar = 56.dp
                        )

                        Spacer(Modifier.height(12.dp))

                        // --- Nazwa i numer ---
                        Text(
                            text = kontakt.nazwaWyswietlana,
                            color = Color.White,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (numerGłówny.isNotBlank()) {
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = numerGłówny,
                                color = Color.White.copy(alpha = 0.55f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Normal
                            )
                        }

                        Spacer(Modifier.height(20.dp))

                        // --- Kafelki akcji: Zadzwoń / Wiadomość ---
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            KafelekAkcjiUlubionego(
                                ikona = IkonyAplikacji.TelefonLogo,
                                etykieta = "Zadzwoń",
                                onClick = {
                                    zamknijZAnimacja {
                                        if (numerGłówny.isNotBlank()) wykonajPolaczenie(context, numerGłówny)
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                            KafelekAkcjiUlubionego(
                                ikona = IkonyAplikacji.Wiadomosc,
                                etykieta = "Wiadomość",
                                onClick = {
                                    zamknijZAnimacja {
                                        if (numerGłówny.isNotBlank()) onOpenSms(numerGłówny)
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }

    // Long-press na pole numeru → wklej ze schowka (z czyszczeniem).
    if (pokazMenuWklejania) {
        val schowek = LocalClipboardManager.current
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pokazMenuWklejania = false },
            containerColor = Color(0xFF141F19),
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.8f),
            title = { Text("Wklej numer") },
            text = {
                Text(
                    "Wklej skopiowany numer. Spacje, myślniki i wiodący „+\" (dla polskich numerów) zostaną usunięte.",
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    val wklejony = schowek.getText()?.text ?: ""
                    if (wklejony.isNotBlank()) {
                        wprowadzonyNumer = oczyscNumerDoDzwonienia(wklejony)
                    } else {
                        Toast.makeText(context, "Schowek pusty", Toast.LENGTH_SHORT).show()
                    }
                    pokazMenuWklejania = false
                }) {
                    Text("Wklej", color = ZielonyNeon, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { pokazMenuWklejania = false }) {
                    Text("Anuluj", color = Color.White.copy(alpha = 0.7f))
                }
            }
        )
    }

    Scaffold(
            containerColor = Color.Transparent,
        topBar = {
                            // Nagłówek na samej górze, potem ulubione (zawsze widoczne, poza listą).
                            Column {
                                CenterAlignedTopAppBar(
                                title = {
                                    Text(
                                        text = "OSTATNIE POŁĄCZENIA",
                                        color = Color.White.copy(alpha = 0.42f),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        letterSpacing = 2.2.sp
                                    )
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = Color.Transparent
                                )
                            )
                                if (ulubioneKontakty.isNotEmpty()) {
                                    PasekUlubionych(
                                        kontakty = ulubioneKontakty,
                                        onKlik = { kontakt, px, py ->
                                            ulubionyDoAkcji = AkcjaUlubionego(kontakt, px, py)
                                        },
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    )
                                }
                            }
                        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!jestDomyslny) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0xFF15271F))
                            .border(1.dp, ZielonyNeon.copy(alpha = 0.22f), RoundedCornerShape(18.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Ustaw jako domyślny dialer",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.weight(1f)
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(ZielonyNeon)
                                    .clickable { ustawJakoDomyslnyDialer(context) }
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    "Ustaw",
                                    color = Color(0xFF06231A),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                // Eleganckie kafelki sugestii kontaktów podczas wpisywania numeru
                                // Przewijana lista z ograniczoną wysokością — nie rozpycha klawiatury w dół.
                                if (sugestieKontakty.isNotEmpty() && wprowadzonyNumer.isNotEmpty()) {
                                    LazyColumn(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 240.dp)
                                            .clip(RoundedCornerShape(18.dp))
                                            .background(Color(0xFF131C18))
                                            .border(1.dp, ZielonyNeon.copy(alpha = 0.15f), RoundedCornerShape(18.dp))
                                            .padding(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        items(sugestieKontakty) { sugestia ->
                                                                                    Row(
                                                                                        modifier = Modifier
                                                                                            .fillMaxWidth()
                                                                                            .clip(RoundedCornerShape(12.dp))
                                                                                            .background(Color.White.copy(alpha = 0.04f))
                                                                                            .clickable {
                                                                                                // Uzupełniamy numerem, który faktycznie wpisujemy (pasuje do fragmentu),
                                                                                                // a nie zawsze pierwszym. Gdy kontakt ma 2 numery i wpiszesz „733",
                                                                                                // wybierzemy ten zawierający „733", nie pierwszy w książce.
                                                                                                val wpisane = wprowadzonyNumer.filter { it.isDigit() }
                                                                                                wprowadzonyNumer = sugestia.numery.firstOrNull { it.contains(wpisane) }
                                                                                                    ?: sugestia.numery.firstOrNull()
                                                                                                    ?: sugestia.number
                                                                                                    ?: ""
                                                                                            }
                                                                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                val inicial = sugestia.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
                                                Box(
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .clip(CircleShape)
                                                        .background(ZielonyNeon.copy(alpha = 0.15f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = inicial,
                                                        color = ZielonyNeon,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 14.sp
                                                    )
                                                }
                                                Spacer(Modifier.width(12.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                                                                    Text(
                                                                                                        text = sugestia.name,
                                                                                                        color = Color.White,
                                                                                                        fontSize = 15.sp,
                                                                                                        fontWeight = FontWeight.SemiBold
                                                                                                    )
                                                                                                    if (sugestia.numery.isNotEmpty()) {
                                                                                                        Text(
                                                                                                            text = sugestia.numery.joinToString("  •  ") { it },
                                                                                                            color = Color.White.copy(alpha = 0.5f),
                                                                                                            fontSize = 12.sp,
                                                                                                            maxLines = 2,
                                                                                                            overflow = TextOverflow.Ellipsis
                                                                                                        )
                                                                                                    }
                                                                                                }
                                            }
                                        }
                                    }
                                }

                // Wiersz z wpisanym numerem i prostym przyciskiem cofania (bez koła)
                if (wprowadzonyNumer.isNotEmpty() || pokazKlawiaturę) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Spacer(modifier = Modifier.width(48.dp))

                        Text(
                            text = wprowadzonyNumer.ifEmpty { "Wpisz numer…" },
                            color = if (wprowadzonyNumer.isEmpty()) {
                                Color.White.copy(alpha = 0.3f)
                            } else {
                                Color.White
                            },
                            fontSize = if (wprowadzonyNumer.isEmpty()) 22.sp else 32.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 1.5.sp,
                            maxLines = 1,
                            modifier = Modifier
                                .weight(1f)
                                .combinedClickable(
                                    onClick = { /* kliknięcie pola nic nie robi — klawiatura już widoczna */ },
                                    // Długie przytrzymanie pola → wklej ze schowka
                                    onLongClick = { pokazMenuWklejania = true }
                                ),
                            textAlign = TextAlign.Center
                        )

                        // Prostoliniowy przycisk cofania — sama ikona, bez tła koła
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clickable(
                                    enabled = wprowadzonyNumer.isNotEmpty(),
                                    onClick = { wprowadzonyNumer = wprowadzonyNumer.dropLast(1) }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (wprowadzonyNumer.isNotEmpty()) {
                                Icon(
                                    imageVector = IkonyAplikacji.Cofnij,
                                    contentDescription = "Usuń cyfrę",
                                    tint = ZielonyNeon,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                    }
                }

                // Klawiatura numeryczna — w 100% płynna animacja GPU z dynamicznym pomiarem wysokości
                val klawiaturaOtwarta = pokazKlawiaturę || wprowadzonyNumer.isNotEmpty()
                val klawiaturaProgress by animateFloatAsState(
                    targetValue = if (klawiaturaOtwarta) 1f else 0f,
                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                    label = "animacjaKlawiatury"
                )

                var klawiaturaHeight by remember { mutableFloatStateOf(0f) }

                if (klawiaturaProgress > 0.01f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onSizeChanged { if (it.height > 0) klawiaturaHeight = it.height.toFloat() }
                            .graphicsLayer {
                                val h = if (klawiaturaHeight > 0f) klawiaturaHeight else 700f
                                translationY = (1f - klawiaturaProgress) * h
                                alpha = klawiaturaProgress
                            }
                    ) {
                        KlawiaturaNumeryczna(
                            onCyfraKliknieta = { cyfra -> wprowadzonyNumer += cyfra },
                            onZadzwon = {
                                if (wprowadzonyNumer.isNotBlank()) {
                                    wykonajPolaczenie(context, wprowadzonyNumer)
                                }
                            },
                            onWiadomosc = { onOpenSms(wprowadzonyNumer) },
                            onNowyKontakt = { pokazDialogKontakt = true }
                        )
                    }
                }

                if (!pokazKlawiaturę && wprowadzonyNumer.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                detectVerticalDragGestures { _, dystans ->
                                    if (dystans < -40f) pokazKlawiaturę = true
                                }
                            }
                            .clickable { pokazKlawiaturę = true },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Color.White.copy(alpha = 0.06f))
                        )
                        Text(
                            text = "︿",
                            color = ZielonyNeon.copy(alpha = 0.75f),
                            fontSize = 30.sp,
                            lineHeight = 30.sp,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    ) { odstepy ->
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                !stanDanych.maHistorie -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(odstepy)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Brak dostępu do historii połączeń",
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Nadaj uprawnienie „Historia połączeń”, żeby zobaczyć listę rozmów",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                historia.isEmpty() || czyLadujeHistorie -> {
                                    Crossfade(targetState = czyLadujeHistorie, label = "pustkaVsLadowanie") { laduje ->
                                        if (laduje) {
                                                                                    // Ulubione widoczne OD RAZU (nie czekają na spinner/historię),
                                                                                    // spinner tylko w miejscu, gdzie będzie lista historii.
                                                                                    Column(Modifier.fillMaxSize().padding(odstepy)) {
                                                                                        if (ulubioneKontakty.isNotEmpty()) {
                                                                                            PasekUlubionych(
                                                                                                kontakty = ulubioneKontakty,
                                                                                                onKlik = { kontakt, px, py ->
                                                                                                    ulubionyDoAkcji = AkcjaUlubionego(kontakt, px, py)
                                                                                                }
                                                                                            )
                                                                                            Spacer(Modifier.height(24.dp))
                                                                                        }
                                                                                        Box(
                                                                                            modifier = Modifier.weight(1f).fillMaxWidth(),
                                                                                            contentAlignment = Alignment.Center
                                                                                        ) {
                                                                                            CircularProgressIndicator(
                                                                                                color = ZielonyNeon,
                                                                                                strokeWidth = 2.5.dp,
                                                                                                modifier = Modifier.size(36.dp)
                                                                                            )
                                                                                        }
                                                                                    }
                                                                                } else {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(odstepy),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "Brak historii połączeń",
                                                    color = Color.White.copy(alpha = 0.5f),
                                                    fontSize = 15.sp
                                                )
                                            }
                                        }
                                    }
                                }

                else -> {
                    // Grupowanie (kosztowne: sortowanie + groupBy) liczymy tylko,
                    // gdy zmieni się historia lub zakres. Filtrowanie ukrytych
                    // (tanie) oddzielamy, żeby ukrycie jednego numeru nie
                    // przeliczało całej listy od zera.
                    val pogrupowane = remember(historia, zakresOd) {
                        grupujPolaczenia(historia, zakresOd)
                    }
                    val grupy = remember(pogrupowane, ukryteDo) {
                        filtrujUkryte(pogrupowane, ukryteDo)
                    }
                    val najstarsza = remember(historia) {
                        historia.minOfOrNull { it.date } ?: 0L
                    }

                    val stanListy = rememberLazyListState()
                                        // Zawsze pokazuj od góry — scroll na samą górę po każdej zmianie danych.
                                        LaunchedEffect(historia, zakresOd) {
                                            stanListy.scrollToItem(0)
                                        }
                                        val przyKoncu by remember {
                                            derivedStateOf {
                                                val info = stanListy.layoutInfo
                                                val ostatni = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                                                info.totalItemsCount > 0 && ostatni >= info.totalItemsCount - 2
                                            }
                                        }
                                        // Pokaż strzałkę „wróć na górę", gdy przewinięto wystarczająco daleko.
                                                                                val pokazStrzalkeWGore by remember {
                                                                                    derivedStateOf { stanListy.firstVisibleItemIndex > 6 }
                                                                                }
                                                                                                    val scopeWGore = rememberCoroutineScope()
                                                                                                    LaunchedEffect(przyKoncu, zakresOd, najstarsza, rozwinieci) {
                                                                                                                            if (!przyKoncu) return@LaunchedEffect
                                                                                                                            // Doładuj kolejne wpisy rozwiniętej grupy, gdy lista zbliża się
                                                                                                                            // do końca — np. grupa 97× rośnie partiami po 10 zamiast naraz.
                                                                                                                            if (rozwinieci.isNotEmpty()) {
                                                                                                                                val maks = grupy
                                                                                                                                    .filter { it.numer in rozwinieci }
                                                                                                                                    .maxOfOrNull { it.wpisy.size } ?: 0
                                                                                                                                if (limitWpisowGrupy < maks) {
                                                                                                                                    limitWpisowGrupy = (limitWpisowGrupy + 10).coerceAtMost(maks)
                                                                                                                                    return@LaunchedEffect
                                                                                                                                }
                                                                                                                            }
                                                                                                                            val nowy = nastepnyZakres(zakresOd, najstarsza)
                                                                                                                            if (nowy != zakresOd) {
                                                                                                                                kotlinx.coroutines.delay(200)
                                                                                                                                zakresOd = nowy
                                                                                                                            }
                                                                                                                        }

                                        // WARSTWA LISTY — Box pozwala nałożyć pływającą strzałkę „wróć na górę".
                                                                                                    Box(modifier = Modifier.fillMaxSize()) {
                                        LazyColumn(
                                                                                    state = stanListy,
                                                                                    modifier = Modifier
                                                                                        .fillMaxSize()
                                                                                        .padding(odstepy)
                                                                                        .pointerInput(pokazKlawiaturę) {
                                                    if (pokazKlawiaturę) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            val event = awaitPointerEvent(
                                                androidx.compose.ui.input.pointer.PointerEventPass.Initial
                                            )
                                            if (event.type == androidx.compose.ui.input.pointer.PointerEventType.Press) {
                                                pokazKlawiaturę = false
                                            }
                                        }
                                    }
                                }
                            },
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                items(grupy, key = { it.numer }) { grupa ->
                                                    val rozwiniety = grupa.numer in rozwinieci
                                                    GrupaPolaczenKarta(
                                                                                                            grupa = grupa,
                                                                                                            widoczne = if (rozwiniety) grupa.wpisy.take(limitWpisowGrupy) else grupa.wpisy.take(1),
                                                                                                            rozwinieta = rozwiniety,
                                                                                                            onRozwin = {
                                                                    if (rozwiniety) {
                                                                        rozwinieci = rozwinieci - grupa.numer
                                                                        // Zamknięcie → przywróć limit do 10, żeby
                                                                        // kolejne otwarcie znów zaczynało od małej partii.
                                                                        limitWpisowGrupy = 10
                                                                    } else {
                                                                        rozwinieci = rozwinieci + grupa.numer
                                                                    }
                                                                },
                                onZadzwon = { wykonajPolaczenie(context, grupa.numer) },
                                onWiadomosc = { onOpenSms(grupa.numer) },
                                onUkryj = {
                                    val klucz = normalizujNumer(grupa.numer)
                                    val czas = System.currentTimeMillis()
                                    prefs.edit().putLong(klucz, czas).apply()
                                    ukryteDo = ukryteDo + (klucz to czas)
                                },
                                // Long-press na numer bez kontaktu → dodanie do książki.
                                onDodajKontakt = {
                                    if (grupa.nazwa == null) {
                                        kontaktDoDodania = grupa.numer
                                    }
                                },
                                // Przeciągnięcie w prawo → potwierdzenie blokady.
                                onSpam = { spamDoPotwierdzenia = grupa.numer }
                                                            )
                                                        }
                                                    }

                                                    // Pływająca strzałka „wróć na górę" — pojawia się po przewinięciu w dół.
                                                    if (pokazStrzalkeWGore) {
                                                                                                            Box(
                                                                                                                modifier = Modifier
                                                                                                                    .align(Alignment.BottomEnd)
                                                                                                                    .padding(20.dp)
                                                                                                                    .size(48.dp)
                                                                                                                    .clip(CircleShape)
                                                                                                                    .background(ZielonyNeon)
                                                                                                                    .clickable {
                                                                                                                                    scopeWGore.launch {
                                                                                                                                        stanListy.animateScrollToItem(0)
                                                                                                                                    }
                                                                                                                                },
                                                                                                                                                                                            contentAlignment = Alignment.Center
                                                                                                                                                                                        ) {
                                                                                                                                                                                            Icon(
                                                                                                                                                                                                imageVector = IkonyAplikacji.StrzalkaWGore,
                                                                                                                                                                                                contentDescription = "Wróć na górę",
                                                                                                                                                                                                tint = Color(0xFF06231A),
                                                                                                                                                                                                modifier = Modifier.size(26.dp)
                                                                                                                                                                                            )
                                                                                                                                                                                        }
                                                                                                                                                                                    }
                                        }
                                                }
                                            }
                                        }
                                                                            }
                                                                                                            }

                                                                            /**
                                     * Pasek ulubionych kontaktów — kółka z awatarem i pseudonimem/imieniem
 * na górze historii połączeń. Dotknięcie otwiera małe pole wyboru akcji.
 */

/**
 * Czyści wklejony/wpisany numer do dzwonienia:
 * - usuwa spacje, myślniki, nawiasy i kropki (kosmetyka zapisu),
 * - wiodący „+" i „00" usuwa TYLKO, gdy to polski numer (po skróceniu 9 cyfr)
 *   — dla numerów zagranicznych „+" zostawia, bo jest wymagany przy wybieraniu.
 */
private fun oczyscNumerDoDzwonienia(surowy: String): String {
    val oczy = surowy.filter { it.isDigit() || it == '+' }
    if (oczy.startsWith("+48") && oczy.length == 12) {
        return oczy.removePrefix("+48")
    }
    if (oczy.startsWith("0048") && oczy.length == 13) {
        return oczy.removePrefix("0048")
    }
    return oczy
}


/** Zapisuje List<CallLogEntry> do Bundle (zachowuje stan między przełączaniem zakładek). */
private val EstadoSaverCallLog = listSaver<List<CallLogEntry>, String>(
    save = { lista -> lista.map { "${it.id}|${it.number}|${it.name}|${it.date}|${it.type}|${it.duration}" } },
    restore = { zapisane -> zapisane.map { linia ->
        val p = linia.split("|")
        CallLogEntry(p[0].toLong(), p[1], p[2].takeIf { it != "null" }, p[3].toLong(), p[4].toInt(), p[5].toLong())
    } }
)


/** Zachowuje ulubione kontakty (tylko nazwy + numery) między przełączaniem zakładek. */
private val EstadoSaverKontakt = listSaver<List<KontaktItem>, String>(
    save = { lista -> lista.map { k ->
        k.name + "#" + k.numery.joinToString(";") { n -> n.surowy } 
    } },
    restore = { zapisane -> zapisane.mapNotNull { linia ->
        val p = linia.split("#", limit = 2)
        if (p.size != 2) return@mapNotNull null
        val nazwa = p[0]
        val numery = p[1].split(";").filter { it.isNotBlank() }.map { NumerKontaktu(it, it, it.filter { c -> c.isDigit() }) }
        if (nazwa.isBlank() || numery.isEmpty()) null
        else KontaktItem(contactId = 0L, name = nazwa, numery = numery, pseudonim = null, fotoUri = null)
    } }
)
