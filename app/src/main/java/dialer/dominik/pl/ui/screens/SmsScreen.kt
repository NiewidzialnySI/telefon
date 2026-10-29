package dialer.dominik.pl.ui.screens

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.graphics.graphicsLayer
import dialer.dominik.pl.sms.KanalWiadomosci
import dialer.dominik.pl.sms.pobierzKanal
import dialer.dominik.pl.sms.wyslijPrzezKomunikator
import dialer.dominik.pl.sms.zapamietajKanal
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dialer.dominik.pl.sms.OdbiorcaSms
import dialer.dominik.pl.ui.components.IkonyAplikacji
import dialer.dominik.pl.ui.theme.CzerwonyPolaczenia
import dialer.dominik.pl.ui.theme.ZielonyMorski
import dialer.dominik.pl.ui.theme.ZielonyNeon
import kotlin.math.roundToInt
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class SmsEkranStan {
    object Skrzynka : SmsEkranStan()
    data class Czat(val address: String, val startMsgId: Long? = null, val szukanaTresc: String? = null) : SmsEkranStan()
    data class NowaWiadomosc(val numer: String) : SmsEkranStan()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsScreen(
    context: Context,
    onBack: () -> Unit,
    numerStartowy: String = "",
    onCzatOtworzony: (Boolean) -> Unit = {},
    onSmsStartowySkonsumowany: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var stanEkranu by remember { mutableStateOf<SmsEkranStan>(SmsEkranStan.Skrzynka) }
            // Informatuj MainActivity, czy czat jest otwarty (do schowania dolnego paska w dół).
            LaunchedEffect(stanEkranu) {
                onCzatOtworzony(stanEkranu !is SmsEkranStan.Skrzynka)
            }
            var watki by remember { mutableStateOf<List<SmsWatek>>(emptyList()) }
            var zapytanieSzukania by remember { mutableStateOf("") }
            var odswiezenie by remember { mutableIntStateOf(0) }
            // Archiwum: true = widok ukrytych wiadomości; ukryte adresy trzymane trwale (prefs).
            var pokazArchiwum by remember { mutableStateOf(false) }
            // Lista filtruje się DOPIERO po animacji ikony — równoczesna podmiana
            // LazyColumn + produceState zabijała morph w TopAppBar (jank / brak animacji).
            // Wzorzec: animacja w fazie draw (graphicsLayer), ciężka praca z delay.
            var filtrArchiwum by remember { mutableStateOf(false) }
            LaunchedEffect(pokazArchiwum) {
                delay(280)
                filtrArchiwum = pokazArchiwum
            }
            val prefsUkryte = remember { context.getSharedPreferences("sms_ukryte", Context.MODE_PRIVATE) }
            var ukryteAdresy by remember {
                mutableStateOf(prefsUkryte.getStringSet("ukryte", emptySet()) ?: emptySet())
            }
            fun zapiszUkryte(nast: Set<String>) {
                            prefsUkryte.edit().putStringSet("ukryte", nast).apply()
                            ukryteAdresy = nast
                        }
                        // Adres otwartego czatu — zapamiętywany przez przełączenie zakładek
                        // (rememberSaveable działa przez AnimatedContent w MainActivity),
                        // żeby powrót na zakładkę Wiadomości nie wyrzucał z czatu.
                        var otwartyCzatAdres by rememberSaveable { mutableStateOf<String?>(null) }
                        // Debounce wrażliwych tapów: czas (ms) ostatniego otwarcia czatu — blokuje
                        // szybkie wielokrotne kliknięcia łapiące różne kafelki.
                        var czasOstatniegoOtwarcia by remember { mutableLongStateOf(0L) }
                        // Czy numer startowy (z historii/dialera) został już przetworzony — blokuje
                        // ponowne otwieranie "starego" czatu przy rekompozycji/zakładkach.
                        // Resetuje się przy KAŻDYM nowym numerze (klucz period) — dzięki temu
                        // kliknięcie kolejnego kontaktu zawsze otwiera nowy czat.
                        var numerStartowyPrzetworzony by rememberSaveable(numerStartowy) { mutableStateOf(false) }

                                // Wstecz wewnątrz ekranu Wiadomości: w czacie/nowej wiadomości cofa do skrzynki,
        // NIE do Main. Na samej skrzynce wstecz przejmuje MainActivity (no-op) — nie wyjdzie.
        androidx.activity.compose.BackHandler(enabled = stanEkranu !is SmsEkranStan.Skrzynka) {
                            stanEkranu = SmsEkranStan.Skrzynka
                            otwartyCzatAdres = null
                            // Wyjście z czatu — odśwież skrzynkę (pobierz ewentualny nowy szkic roboczy).
                            odswiezenie++
                        }

    val maSms = context.checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED ||
        context.checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED

    LaunchedEffect(odswiezenie, OdbiorcaSms.ostatniaWiadomosc) {
        if (maSms) {
            // Pobieranie skrzynki i rozwiązywanie nazw kontaktów to ciężka praca
            // (zapytania do ContentResolver). Odrywamy ją od wątku głównego,
            // żeby interfejs nie zacinał się przy większej liczbie wiadomości.
            watki = withContext(Dispatchers.IO) { dolaczRoboczeDoWatkow(context, pobierzWątkiSms(context, odswiez = odswiezenie > 0)) }
        }
    }

                // (Sekcja podmiany czatu usunięta — EkranCzatuSzczegoly sam odświeża wiadomości
                                // przez LaunchedEffect(watek.address), a zewnętrzna podmiana z `watki` powodowała
                                // samoczynne „zastępowanie" otwartego czatu innym obiektem.)

                    // Gdy przyszliśmy z dialera/historii z wpisanym numerem — otwórz CZAT
    // (widok z dymkami, taki sam jak w skrzynce), nie osobny kompozytor.
    LaunchedEffect(numerStartowy) {
                    android.util.Log.i("KlikSms", "SmsScreen numerStartowy='$numerStartowy' stan=$stanEkranu")
                    // Otwarcia z numeru startowego przetwarzamy TYLKO RAZ (aż do nowego kliknięcia
                    // z history/dialera). Zabezpieczenie przed ponownym otwieraniem "starego" czatu
                    // przy rekompozycji/zakładkach. Nie wymagamy bycia na Skrzynce — klik z ekranu
                    // głównego/kontaktów musi otworzyć czat z numeru NIEZALEŻNIE od obecnego stanu.
                    if (numerStartowy.isNotBlank() && !numerStartowyPrzetworzony) {
                        numerStartowyPrzetworzony = true
                    otwartyCzatAdres = numerStartowy
                // Znajdź istniejący wątek albo utwórz pusty czat dla tego numeru
                val znaleziony = watki.find { it.address == numerStartowy }
                if (znaleziony != null) {
                    stanEkranu = SmsEkranStan.Czat(znaleziony.address)
                } else {
                    // Na wypadek, gdyby wątki nie były jeszcze pobrane
                    val watekTemp = if (watki.isEmpty()) {
                                            withContext(Dispatchers.IO) { dolaczRoboczeDoWatkow(context, pobierzWątkiSms(context)) }
                                                .find { it.address == numerStartowy }
                                        } else null
                    if (watekTemp != null) {
                        stanEkranu = SmsEkranStan.Czat(watekTemp.address)
                    } else {
                        val nazwa = withContext(Dispatchers.IO) { resolveContactName(context, numerStartowy) }
                        stanEkranu = SmsEkranStan.Czat(numerStartowy)
                    }
                }
                            }
                            // Konsumpcja numeru startowego PO ustawieniu czatu — gdybyśmy czyścili wcześniej,
                            // LaunchedEffect z „"” anulowałby ten efekt zanim otworzyłby czat (log: numerStartowy='').
                            onSmsStartowySkonsumowany()
                        }

    // Wyszukiwanie „w wiadomościach”: filtrowanie wykonujemy na wątku IO z debounce,
    // bo pobierzPasujacaWiadomosc robi synchroniczny query do bazy SMS dla każdego wątku.
    // Wykonywanie tego na main przy każdym znaku blokowało UI (ANR "Input dispatching timed out").
    // Debounce 250 ms + próg 3 znaków: szukanie odpala się dopiero gdy user wpisze co najmniej
    // 3 znaki i zrobi krótką przerwę — mniej zapytań do bazy, nic nie blokuje interfejsu.
    val MIN_SZUKANE_ZN = 3
    val przefiltrowaneWatki by produceState(initialValue = watki, watki, zapytanieSzukania, filtrArchiwum, ukryteAdresy) {
        val baza = if (filtrArchiwum) watki.filter { it.address in ukryteAdresy }
                   else watki.filter { it.address !in ukryteAdresy }
        // Pusty zapytanie lub za krótkie → pokaż całą bazę (bez szukania w treści).
        if (zapytanieSzukania.trim().length < MIN_SZUKANE_ZN) {
            value = baza
        } else {
            delay(250) // debounce — poczekaj aż user przestanie pisać
            val q = zapytanieSzukania.trim()
            value = withContext(Dispatchers.IO) {
                // JEDNO zapytanie SQL LIKE do bazy SMS (zamiast osobnego pełnego query
                // dla każdego wątku — to było sekundy przy większej skrzynce).
                val trafienia = pobierzPasujaceSmsy(context, q)
                if (trafienia.isEmpty()) {
                    // Brak trafień w treści → zawęź do wątków pasujących po nazwie/numerze.
                    baza.filter { watek ->
                        normalizujPolski(watek.contactName ?: "").contains(normalizujPolski(q)) ||
                        normalizujPolski(watek.address).contains(normalizujPolski(q))
                    }.distinctBy { normalizujAdresSms(it.address) }
                } else {
                    baza.mapNotNull { watek ->
                        val trafienie = trafienia[normalizujAdresSms(watek.address)]
                        val pasujeNazwa = normalizujPolski(watek.contactName ?: "").contains(normalizujPolski(q)) ||
                                          normalizujPolski(watek.address).contains(normalizujPolski(q))
                        if (pasujeNazwa || trafienie != null) {
                            val podglad = if (!pasujeNazwa && trafienie != null) {
                                "Trafienie: ${trafienie.body}"
                            } else {
                                watek.lastMessage
                            }
                            watek.copy(
                                lastMessage = podglad,
                                searchMatchMsgId = trafienie?.id
                            )
                        } else null
                    }.distinctBy { normalizujAdresSms(it.address) }
                }
            }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                                    Text(
                                        text = when {
                                            stanEkranu is SmsEkranStan.Skrzynka && pokazArchiwum -> "ARCHIWUM (UKRYTE)"
                                            stanEkranu is SmsEkranStan.Skrzynka -> "SKRZYNKA ODBIORCZA"
                                            stanEkranu is SmsEkranStan.Czat -> {
                                                        val czatAdres = (stanEkranu as SmsEkranStan.Czat).address
                                                        // Imię/pseudonim kontaktu — dla nowego czatu (bez wątku) pobieramy z książki,
                                                        // żeby zamiast numeru pokazać pseudonim / imię i nazwisko.
                                                        watki.find { it.address == czatAdres }?.contactName?.takeIf { it.isNotBlank() }
                                                            ?: resolveContactName(context, czatAdres) ?: czatAdres
                                                    }
                                            else -> "NOWA WIADOMOŚĆ"
                                        },
                                        color = Color.White.copy(alpha = 0.42f),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        letterSpacing = 2.2.sp
                                    )
                                },
                                navigationIcon = {
                                    if (stanEkranu is SmsEkranStan.Skrzynka) {
                                        // Postęp morphu 0=archiwum, 1=powrót — w graphicsLayer (faza draw),
                                        // żeby przeładowanie listy nie kasowało animacji (AnimatedContent
                                        // w TopAppBar + równoczesny LazyColumn = brak widocznego morphu).
                                        val postepIkony by animateFloatAsState(
                                            targetValue = if (pokazArchiwum) 1f else 0f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessMediumLow
                                            ),
                                            label = "postepIkonyArchiwum"
                                        )
                                        val alphaKosza by animateFloatAsState(
                                            targetValue = if (pokazArchiwum) 0f else 1f,
                                            animationSpec = tween(200),
                                            label = "alphaKosza"
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            // Stały slot 48dp — bez expand/shrink (to też psuło TopAppBar).
                                            Box(
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .graphicsLayer { alpha = alphaKosza },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                IconButton(
                                                    onClick = {
                                                        if (pokazArchiwum) return@IconButton
                                                        scope.launch(Dispatchers.IO) {
                                                            val usuniete = usunStareKodyOtp(context)
                                                            withContext(Dispatchers.Main) {
                                                                if (usuniete > 0) {
                                                                    Toast.makeText(context, "Usunięto $usuniete starych kodów 2FA", Toast.LENGTH_SHORT).show()
                                                                    odswiezenie++
                                                                } else {
                                                                    Toast.makeText(context, "Brak starych kodów 2FA do usunięcia", Toast.LENGTH_SHORT).show()
                                                                }
                                                            }
                                                        }
                                                    },
                                                    enabled = !pokazArchiwum
                                                ) {
                                                    Icon(
                                                        imageVector = IkonyAplikacji.Kosz,
                                                        contentDescription = "Wyczyść kody 2FA",
                                                        tint = ZielonyNeon
                                                    )
                                                }
                                            }
                                            IconButton(onClick = { pokazArchiwum = !pokazArchiwum }) {
                                                Box(
                                                    modifier = Modifier.size(24.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = IkonyAplikacji.Archiwum,
                                                        contentDescription = null,
                                                        tint = ZielonyNeon,
                                                        modifier = Modifier
                                                            .size(24.dp)
                                                            .graphicsLayer {
                                                                alpha = 1f - postepIkony
                                                                val sk = 1f - 0.65f * postepIkony
                                                                scaleX = sk
                                                                scaleY = sk
                                                                rotationZ = 40f * postepIkony
                                                            }
                                                    )
                                                    Icon(
                                                        imageVector = IkonyAplikacji.WroćZArchiwum,
                                                        contentDescription = if (pokazArchiwum) {
                                                            "Wróć do skrzynki"
                                                        } else {
                                                            "Archiwum (ukryte)"
                                                        },
                                                        tint = ZielonyNeon,
                                                        modifier = Modifier
                                                            .size(24.dp)
                                                            .graphicsLayer {
                                                                alpha = postepIkony
                                                                val sk = 0.35f + 0.65f * postepIkony
                                                                scaleX = sk
                                                                scaleY = sk
                                                                rotationZ = -40f * (1f - postepIkony)
                                                            }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                },
                                                                                                        actions = {
                                                                                                        // Przycisk Zadzwoń — skrajna prawa, tylko gdy jesteśmy w czacie.
                                                                                                        if (stanEkranu is SmsEkranStan.Czat) {
                                                                                                        val czatAdres = (stanEkranu as SmsEkranStan.Czat).address
                                                                                                        IconButton(
                                                                                                        onClick = { wykonajPolaczenie(context, czatAdres) }
                                                                                                        ) {
                                                                                                        Icon(
                                                                                                        imageVector = IkonyAplikacji.TelefonLogo,
                                                                                                        contentDescription = "Zadzwoń",
                                                                                                        tint = ZielonyNeon
                                                                                                        )
                                                                                                        }
                                                                                                        }
                                                                                                        },
                                                                                                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                                                                                                        )
                                                                                                        },
        bottomBar = {
                                    if (stanEkranu is SmsEkranStan.Skrzynka) {
                                // Pasek wyszukiwania — spójny z ekranem Kontakty: bez zewnętrznej
                                // plamy tła (Surface), sam zaokrąglony pasek z ramką w bottomBar.
                                Row(
                                                                    modifier = Modifier
                                                                                                            .fillMaxWidth()
                                                                                                            .padding(horizontal = 16.dp, vertical = 8.dp)
                                                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color(0xFF131C18))
                                        .border(1.dp, ZielonyNeon.copy(alpha = 0.14f), RoundedCornerShape(16.dp))
                                        .padding(start = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    BasicTextField(
                                        value = zapytanieSzukania,
                                        onValueChange = { zapytanieSzukania = it },
                                        singleLine = true,
                                        textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                                        cursorBrush = SolidColor(ZielonyNeon),
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(vertical = 12.dp),
                                        decorationBox = { wewnetrzne ->
                                            if (zapytanieSzukania.isEmpty()) {
                                                Text(
                                                    text = "Szukaj w wiadomościach…",
                                                    color = Color.White.copy(alpha = 0.32f),
                                                    fontSize = 15.sp
                                                )
                                            }
                                            wewnetrzne()
                                        }
                                    )

                                    // Przycisk nowej wiadomości — dokładnie jak „+" w Kontaktsach:
                                    // przylega do prawej krawędzi pola (end-padding 0, w róg).
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(0.dp))
                                            .background(ZielonyNeon.copy(alpha = 0.18f))
                                            .clickable { stanEkranu = SmsEkranStan.NowaWiadomosc("") },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "+",
                                            color = ZielonyNeon,
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
    ) { odstepy ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = odstepy.calculateTopPadding(), start = odstepy.calculateLeftPadding(layoutDirection = LayoutDirection.Ltr), end = odstepy.calculateRightPadding(layoutDirection = LayoutDirection.Ltr), bottom = 0.dp)
        ) {
            AnimatedContent(
                targetState = stanEkranu,
                transitionSpec = {
                    // Płynne przejście (M3): ruch = spring (fizyka), fade = tween.
                    // Czat/skrzynka wjeżdżają z dołu (naturalne na ekranie wiadomości).
                    val typA = initialState::class
                    val typB = targetState::class
                    if (typA != typB) {
                        (slideInVertically(initialOffsetY = { it / 3 }, animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow
                        )) + fadeIn(tween(220)))
                            .togetherWith(slideOutVertically(targetOffsetY = { -it / 5 }, animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow
                            )) + fadeOut(tween(150)))
                    } else {
                        (fadeIn(tween(200))).togetherWith(fadeOut(tween(150)))
                    }
                },
                    contentKey = { it::class },
                label = "przejscie-ekranow"
            ) { stan ->
                when (stan) {
                is SmsEkranStan.Skrzynka -> {
                    if (przefiltrowaneWatki.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Brak wiadomości w skrzynce",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 15.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(przefiltrowaneWatki, key = { it.address }) { watek ->
                                                            KartaWatku(
                                                                                                                        context = context,
                                                                                                                        watek = watek,
                                                                                                                        onClick = {
                                                                                                                                                                                                                                                                                                                                                                                                                                    val teraz = System.currentTimeMillis()
                                                                                                                                                                                                                                                                                                                                                                                                                                    android.util.Log.i("CzatOpen", "onClick kafelek=${watek.address} otwartyCzatAdres=$otwartyCzatAdres")
                                                                                                                                                                                                                                                                                                                                                                                                                                    if (teraz - czasOstatniegoOtwarcia >= 350L) {
                                                                                                                                                                                                                                                                                                                                                                                                                                        czasOstatniegoOtwarcia = teraz
                                                                                                                                                                                                                                                                                                                                                                                                                                        otwartyCzatAdres = watek.address
                                                                                                                                                                                                                                                                                                                                                                                                                                        stanEkranu = SmsEkranStan.Czat(
                                                                                                                                                                                                                                                                                                                                                                                                                                            address = watek.address,
                                                                                                                                                                                                                                                                                                                                                                                                                                            startMsgId = watek.searchMatchMsgId,
                                                                                                                                                                                                                                                                                                                                                                                                                                            szukanaTresc = if (zapytanieSzukania.isNotBlank()) zapytanieSzukania else null
                                                                                                                                                                                                                                                                                                                                                                                                                                        )
                                                                                                                                                                                                                                                                                                                                                                                                                                    }
                                                                                                                                                                                                                                                                                                                                                                                                                                },
                                                                                                                        // W skrzynce: ukryj (dodaj do hidden); w archiwum: przywróć (usuń z hidden).
                                                                                                                        // filtrArchiwum = stan listy (po opóźnieniu), nie pokazArchiwum (ikona).
                                                                                                                        onUkryj = {
                                                                                                                            if (filtrArchiwum) {
                                                                                                                                zapiszUkryte(ukryteAdresy - watek.address)
                                                                                                                            } else {
                                                                                                                                zapiszUkryte(ukryteAdresy + watek.address)
                                                                                                                            }
                                                                                                                        },
                                                                                                                        onUsun = {
                                                                                                                                                                                                                                                    usunKonwersacje(context, watek.address)
                                                                                                                                                                                                                                                    // Usuwając czat, usuń też roboczy szkic danego numeru.
                                                                                                                                                                                                                                                    usunDraftCzatu(context, watek.address)
                                                                                                                                                                                                                                                    // Usunięty trwale — zdejmij też z ukrytych.
                                                                                                                                                                                                                                                    zapiszUkryte(ukryteAdresy - watek.address)
                                                                                                                                                                                                                                                    watki = watki.filter { it.address != watek.address }
                                                                                                                                                                                                                                                },
                                                                                                                        trybArchiwum = filtrArchiwum
                                                                                                                    )
                                                        }
                        }
                    }
                }

                is SmsEkranStan.Czat -> {
                                    val watekCzat = watki.find { it.address == stan.address }
                                        ?: SmsWatek(
                                            address = stan.address,
                                            contactName = resolveContactName(context, stan.address),
                                            lastMessage = "",
                                            lastDate = 0L,
                                            messages = emptyList()
                                        )
                                    EkranCzatuSzczegoly(
                                        context = context,
                                        watek = watekCzat,
                                        onWiadomoscWyslana = {
                                            odswiezenie++
                                        },
                                        startMsgId = stan.startMsgId,
                                        szukanaTresc = stan.szukanaTresc
                                    )
                                }

                is SmsEkranStan.NowaWiadomosc -> {
                                    EkranNowejWiadomosci(
                                        context = context,
                                        numerPoczatkowy = stan.numer,
                                        onWyslano = {
                                            stanEkranu = SmsEkranStan.Skrzynka
                                            odswiezenie++
                                        }
                                    )
                                }
                            }
                            }
                        }
                    }
                }

/** Wolne miejsce po przeniesieniu czyZnanaFirma do LogikaSms (internal jest współdzielone). */

/** Usuwa całą konwersację SMS (wszystkie wiadomości) dla danego numeru. */
private fun usunKonwersacje(context: Context, numer: String) {
    try {
        val uri = Telephony.Sms.CONTENT_URI
        context.contentResolver.delete(
            uri,
            "${Telephony.Sms.ADDRESS} = ?",
            arrayOf(numer)
        )
    } catch (e: Exception) {
                android.util.Log.e("SmsScreen", "Usuwanie konwersacji nieudane", e)
        }
    }

    /** Logo prawdziwej firmy (alfa-nadawcy SMS) — pobrane z internetu raz i cache'owane.
     *  Gdy sieć niedostępna / nie znaleziono → pokazuje inicjał (aż sieć wróci, kolejna
     *  recompozycja karty ponowi `pobierzLogoFirmy`). */
    @Composable
    private fun LogoFirmy(
        context: Context,
        nazwa: String,
        rozmiar: androidx.compose.ui.unit.Dp
    ) {
        val bitmap by produceState<Bitmap?>(initialValue = null, nazwa, context) {
            value = withContext(Dispatchers.IO) { pobierzLogoFirmy(context, nazwa) }
        }
        val bmp = bitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Logo firmy $nazwa",
                    modifier = Modifier.size(rozmiar)
                )
            } else {
            // Fallback do czasu pobrania logo / gdy firmy nie ma w Clearbit: inicjał.
            Text(
                text = nazwa.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                color = ZielonyNeon,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }

    @Composable
    private fun KartaWatku(
        context: Context,
        watek: SmsWatek,
        onClick: () -> Unit,
        onUkryj: () -> Unit = {},
        onUsun: () -> Unit = {},
        trybArchiwum: Boolean = false
    ) {
    val formatDaty = remember { SimpleDateFormat("dd.MM.yyyy, HH:mm", Locale.getDefault()) }
        val roboczy = watek.lastDate == Long.MAX_VALUE
        val dataTekst = if (roboczy) "Roboczy" else formatDaty.format(Date(watek.lastDate))
    // Numer z kontaktu → ciemniejsze tło; nieznany numer → jaśniejsze (wyróżnienie).
    val tloWatku = if (watek.isKontakt) Color(0xFF14221B) else Color(0xFF1D3430)
        val obramowanie = if (watek.isKontakt) Color(0xFF1E382C) else Color(0xFF2A4A42)
        val nieprzeczytana = watek.nieprzeczytane > 0

        // Swipe: przeciągnięcie przesuwa kartę i ujawnia tła akcji pod spodem.
        var zasieg by remember { mutableFloatStateOf(0f) }
        val gestyDensity = LocalDensity.current
        val prog = remember { with(gestyDensity) { 110.dp.toPx() } }

        Box(modifier = Modifier.fillMaxWidth()) {
            // Tło po PRAWĘJ — widoczne przy przeciągnięciu w LEWO (Usuń konwersację).
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CzerwonyPolaczenia.copy(alpha = 0.16f)),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (zasieg < 0f) {
                    Row(Modifier.padding(end = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = IkonyAplikacji.Kosz,
                            contentDescription = null,
                            tint = CzerwonyPolaczenia,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text("Usuń", color = CzerwonyPolaczenia, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            // Tło po LEWEJ — widoczne przy przeciągnięciu w PRAWO (Ukryj konwersację).
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(ZielonyMorski.copy(alpha = 0.20f)),
                contentAlignment = Alignment.CenterStart
            ) {
                if (zasieg > 0f) {
                    Row(Modifier.padding(start = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = IkonyAplikacji.Kosz,
                            contentDescription = null,
                            tint = ZielonyNeon,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                                                Text(if (trybArchiwum) "Przywróć" else "Ukryj", color = ZielonyNeon, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Gradient tła jak w kartach Ostatnie połączenia i Kontakty (0xFF131C18→0xFF0B120F),
            // zamiast płaskiego koloru — spójność z resztą aplikacji.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(zasieg.roundToInt(), 0) }
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF131C18), Color(0xFF0B120F))
                        )
                    )
                    .border(1.dp, obramowanie, RoundedCornerShape(16.dp))
                    .pointerInput(watek.address) {
                                            // Krótkie kliknięcie (bez przeciągania) otwiera czat.
                                            detectTapGestures(onTap = { onClick() })
                                        }
                                        .pointerInput(watek.address) {
                                            detectHorizontalDragGestures(
                                                onDragEnd = {
                                                    when {
                                                        // lewo → prawo (drag > 0) → ukryj konwersację
                                                        zasieg > prog -> onUkryj()
                                                        // prawo → lewo (drag < 0) → usuń konwersację
                                                        zasieg < -prog -> onUsun()
                                                        // NO else: po przeciągnięciu nie otwieramy czatu
                                                        // (czat tylko po krótkim klku — osobny detectTapGestures).
                                                    }
                                                    zasieg = 0f
                                                },
                                                onHorizontalDrag = { _, dystans ->
                                                    zasieg = (zasieg + dystans).coerceIn(-prog * 1.4f, prog * 1.4f)
                                                }
                                            )
                                        }
            ) {
                Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Awatar: znana firma → logo aplikacji; kontakt ze zdjęciem → zdjęcie;
                                                // w przeciwnym razie inicjał (dokładnie jak na resztach ekranów).
                                                val inicjal = (watek.pseudonim ?: watek.contactName ?: watek.address)
                                                    .trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
                                                if (czyZnanaFirma(watek.address) && watek.fotoUri == null) {
                                                                                                    // SMS od serwisu/firmy (alfa-nadawca, np. Microsoft,
                                                                                                    // MarketUL) → logo aplikacji, bez inicjału.
                                                                                                    Box(
                                                        modifier = Modifier
                                                            .size(44.dp)
                                                            .clip(CircleShape)
                                                            .background(
                                                                Brush.radialGradient(
                                                                    listOf(
                                                                        ZielonyMorski.copy(alpha = 0.55f),
                                                                        ZielonyMorski.copy(alpha = 0.18f)
                                                                    )
                                                                )
                                                            ),
                                                        contentAlignment = Alignment.Center
                                                                                                            ) {
                                                                                                                // Logo prawdziwej firmy — pobierane z internetu (raz,
                                                                                                                // cache lokalny), gdy brak to pokaż do czasu sieci inicjał.
                                                                                                                LogoFirmy(
                                                                                                                    context = context,
                                                                                                                    nazwa = watek.address,
                                                                                                                    rozmiar = 30.dp
                                                                                                                )
                                                                                                            }
                                                                                                        } else if (watek.fotoUri != null) {
                                                    // Kontakt ze zdjęciem w książce — pokaż zdjęcie (awatar IO).
                                                    AwatarKontaktu(
                                                        fotoUri = watek.fotoUri,
                                                        inicial = inicjal,
                                                        context = context,
                                                        rozmiar = 44.dp
                                                    )
                                                } else {
                                                    // Brak zdjęcia → inicjał na zielonym radialnym tle.
                                                    Box(
                                                        modifier = Modifier
                                                            .size(44.dp)
                                                            .clip(CircleShape)
                                                            .background(
                                                                Brush.radialGradient(
                                                                    listOf(
                                                                        ZielonyMorski.copy(alpha = if (nieprzeczytana) 0.55f else 0.4f),
                                                                        ZielonyMorski.copy(alpha = 0.18f)
                                                                    )
                                                                )
                                                            ),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = inicjal,
                                                            color = if (nieprzeczytana) Color.White else ZielonyNeon,
                                                            fontSize = 19.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                        // Ikona/kropka nieprzeczytanej wiadomości — czerwona.
                        if (nieprzeczytana) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(CzerwonyPolaczenia)
                        )
                    }
                    // Nagłówek wiersza: pseudonim (pierwszeństwo) → imię/nazwisko, a pod
                                        // spodem małą czcionką numer telefonu.
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = watek.pseudonim ?: watek.contactName ?: watek.address,
                                                color = if (nieprzeczytana) Color.White else Color.White.copy(alpha = 0.9f),
                                                fontSize = 16.sp,
                                                fontWeight = if (nieprzeczytana) FontWeight.Bold else FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            // Numer telefonu pokazywany, gdy nadawca ma pseudonim lub nazwę
                                            // (przy nieznanym numerze tytuł = numer, więc nie duplikuj).
                                            if (watek.pseudonim != null || watek.contactName != null) {
                                                Text(
                                                    text = watek.address,
                                                    color = Color.White.copy(alpha = 0.4f),
                                                    fontSize = 11.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                    // Plakietka z liczbą nieprzeczytanych — czerwona.
                    if (nieprzeczytana) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(CzerwonyPolaczenia)
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = watek.nieprzeczytane.toString(),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Text(
                    text = dataTekst,
                    color = if (nieprzeczytana) CzerwonyPolaczenia.copy(alpha = 0.85f)
                    else Color.White.copy(alpha = 0.4f),
                    fontSize = 11.sp,
                    fontWeight = if (nieprzeczytana) FontWeight.Medium else FontWeight.Normal
                )
            }
            Text(
                                        text = watek.lastMessage,
                                        color = if (roboczy) ZielonyNeon
                                                else if (nieprzeczytana) Color.White.copy(alpha = 0.85f)
                                                else Color.White.copy(alpha = 0.7f),
                                        fontSize = 14.sp,
                                        fontWeight = if (nieprzeczytana) FontWeight.SemiBold else FontWeight.Normal,
                                        maxLines = 1
                        )
                    }
                        }
                }
            }

