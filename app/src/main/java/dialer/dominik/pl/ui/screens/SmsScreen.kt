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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.graphics.graphicsLayer
import dialer.dominik.pl.sms.KanalWiadomosci
import dialer.dominik.pl.sms.MmsPobranyIZapisanyReceiver
import dialer.dominik.pl.sms.dociagnijOczekujaceMms
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dialer.dominik.pl.sms.OdbiorcaSms
import dialer.dominik.pl.ui.components.IkonyAplikacji
import dialer.dominik.pl.ui.theme.CzerwonyPolaczenia
import dialer.dominik.pl.ui.theme.ZielonyMorski
import dialer.dominik.pl.ui.theme.NaPrzycisku
import dialer.dominik.pl.ui.theme.ZielonyNeon
import dialer.dominik.pl.ui.theme.tloSrebrnyPolysk
import dialer.dominik.pl.ui.theme.CzcionkiWiedzmina
import dialer.dominik.pl.ui.theme.MglaNaPrzycisku
import dialer.dominik.pl.ui.theme.czyMotywWiedzmin
import dialer.dominik.pl.ui.theme.motywAkcent
import dialer.dominik.pl.ui.theme.gradientKafelka
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
    val paletaSms = dialer.dominik.pl.ui.theme.LocalMotyw.current
    val wiedzmin = czyMotywWiedzmin()
    val akcent = motywAkcent()
    val akcentMorski = if (wiedzmin) akcent else dialer.dominik.pl.ui.theme.ZielonyMorski
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
            // Domyślna aplikacja SMS — bez roli system nie dostarcza przychodzących.
            var jestDomyslnaSms by remember { mutableStateOf(czyJestDomyslnaAplikacjaSms(context)) }
            val launcherDomyslnejSms = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) {
                jestDomyslnaSms = czyJestDomyslnaAplikacjaSms(context)
            }
            val cyklZycia = LocalLifecycleOwner.current
            DisposableEffect(cyklZycia) {
                val obserwator = LifecycleEventObserver { _, zdarzenie ->
                    if (zdarzenie == Lifecycle.Event.ON_RESUME) {
                        jestDomyslnaSms = czyJestDomyslnaAplikacjaSms(context)
                    }
                }
                cyklZycia.lifecycle.addObserver(obserwator)
                onDispose { cyklZycia.lifecycle.removeObserver(obserwator) }
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

        // Wstecz z Nowej wiadomości → skrzynka. Wyjście z CZATU robi EkranCzatuSzczegoly
        // (animacja wyjścia, potem onWyjscie) — ten handler nie przejmuje czatu.
        androidx.activity.compose.BackHandler(enabled = stanEkranu is SmsEkranStan.NowaWiadomosc) {
            stanEkranu = SmsEkranStan.Skrzynka
            otwartyCzatAdres = null
            odswiezenie++
        }

    val maSms = context.checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED ||
        context.checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED

    LaunchedEffect(odswiezenie, OdbiorcaSms.ostatniaWiadomosc, MmsPobranyIZapisanyReceiver.ostatniePobranieMs) {
        if (maSms) {
            watki = withContext(Dispatchers.IO) {
                dolaczRoboczeDoWatkow(context, pobierzWątkiSms(context, odswiez = odswiezenie > 0))
            }
        }
    }
    // Wiszące MMS: raz przy starcie skrzynki + przy ręcznym odświeżeniu (nie przy każdym bus tick).
    LaunchedEffect(Unit) {
        if (maSms) dociagnijOczekujaceMms(context)
    }
    LaunchedEffect(odswiezenie) {
        if (maSms && odswiezenie > 0) dociagnijOczekujaceMms(context)
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
    // Dwie listy naraz — AnimatedContent skrzynka↔archiwum musi trzymać starą listę
    // podczas wyjścia (gdyby była jedna przefiltrowana, obie fazy pokazywałyby to samo).
    data class ListySkrzynki(val skrzynka: List<SmsWatek>, val archiwum: List<SmsWatek>)
    val listySkrzynki by produceState(
        initialValue = ListySkrzynki(watki, emptyList()),
        watki, zapytanieSzukania, ukryteAdresy
    ) {
        fun podziel(poSzukaniu: List<SmsWatek>) = ListySkrzynki(
            skrzynka = poSzukaniu.filter { it.address !in ukryteAdresy },
            archiwum = poSzukaniu.filter { it.address in ukryteAdresy }
        )
        if (zapytanieSzukania.trim().length < MIN_SZUKANE_ZN) {
            value = podziel(watki)
        } else {
            delay(250)
            val q = zapytanieSzukania.trim()
            value = withContext(Dispatchers.IO) {
                val trafienia = pobierzPasujaceSmsy(context, q)
                val poSzukaniu = if (trafienia.isEmpty()) {
                    watki.filter { watek ->
                        normalizujPolski(watek.contactName ?: "").contains(normalizujPolski(q)) ||
                            normalizujPolski(watek.address).contains(normalizujPolski(q))
                    }.distinctBy { normalizujAdresSms(it.address) }
                } else {
                    watki.mapNotNull { watek ->
                        val trafienie = trafienia[normalizujAdresSms(watek.address)]
                        val pasujeNazwa = normalizujPolski(watek.contactName ?: "").contains(normalizujPolski(q)) ||
                            normalizujPolski(watek.address).contains(normalizujPolski(q))
                        if (pasujeNazwa || trafienie != null) {
                            watek.copy(
                                lastMessage = if (!pasujeNazwa && trafienie != null) {
                                    "Trafienie: ${trafienie.body}"
                                } else watek.lastMessage,
                                searchMatchMsgId = trafienie?.id
                            )
                        } else null
                    }.distinctBy { normalizujAdresSms(it.address) }
                }
                podziel(poSzukaniu)
            }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    if (stanEkranu is SmsEkranStan.Skrzynka) {
                        // Morph „SKRZYNKA ODBIORCZA” ↔ „ARCHIWUM” — ten sam spring co ikona archiwum.
                        val postepTytulu by animateFloatAsState(
                            targetValue = if (pokazArchiwum) 1f else 0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            ),
                            label = "postepTytuluArchiwum"
                        )
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "SKRZYNKA ODBIORCZA",
                                color = paletaSms.naTle,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 2.2.sp,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.graphicsLayer {
                                    val t = postepTytulu
                                    alpha = 0.42f * (1f - t)
                                    translationY = -10f * t
                                    val sk = 1f - 0.12f * t
                                    scaleX = sk
                                    scaleY = sk
                                }
                            )
                            Text(
                                text = "ARCHIWUM",
                                color = paletaSms.naTle,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 2.2.sp,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.graphicsLayer {
                                    val t = postepTytulu
                                    alpha = 0.42f * t
                                    translationY = 10f * (1f - t)
                                    val sk = 0.88f + 0.12f * t
                                    scaleX = sk
                                    scaleY = sk
                                }
                            )
                        }
                    } else if (stanEkranu is SmsEkranStan.Czat) {
                        val czatAdres = (stanEkranu as SmsEkranStan.Czat).address
                        val watekNaglowka = watki.find { it.address == czatAdres }
                        val nazwaKontaktu = watekNaglowka?.contactName?.takeIf { it.isNotBlank() }
                            ?: resolveContactName(context, czatAdres)
                        val tytulCzatu = watekNaglowka?.pseudonim?.takeIf { it.isNotBlank() }
                            ?: nazwaKontaktu
                            ?: czatAdres
                        val pokazNumer = !nazwaKontaktu.isNullOrBlank() ||
                            !watekNaglowka?.pseudonim.isNullOrBlank()
                        Crossfade(
                            targetState = tytulCzatu to czatAdres,
                            animationSpec = tween(220, easing = FastOutSlowInEasing),
                            label = "tytulCzatSms"
                        ) { (tytul, numer) ->
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = tytul,
                                    color = paletaSms.naTle,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                if (pokazNumer) {
                                    Text(
                                        text = numer,
                                        color = paletaSms.naTle.copy(alpha = 0.45f),
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    } else {
                        Crossfade(
                            targetState = "NOWA WIADOMOŚĆ",
                            animationSpec = tween(220, easing = FastOutSlowInEasing),
                            label = "tytulSms"
                        ) { tekst ->
                            Text(
                                text = tekst,
                                color = paletaSms.naTle.copy(alpha = 0.42f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 2.2.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                                navigationIcon = {
                                    if (stanEkranu is SmsEkranStan.Skrzynka) {
                                        // Postęp morphu 0=archiwum, 1=powrót — w graphicsLayer (faza draw),
                                        // żeby przeładowanie listy nie kasowało animacji (AnimatedContent
                                        // w TopAppBar + równoczesny LazyColumn = brak widocznego morphu).
                                        val postepIkony by animateFloatAsState(
                                            targetValue = if (pokazArchiwum) 1f else 0f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioNoBouncy,
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
                                                        tint = akcent
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
                                                        tint = akcent,
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
                                                            "Archiwum"
                                                        },
                                                        tint = akcent,
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
                                    } else if (stanEkranu is SmsEkranStan.Czat) {
                                        // Ten sam slot co actions (IconButton 48dp) — bez tego
                                        // CenterAlignedTopAppBar przesuwa tytuł w lewo.
                                        Spacer(Modifier.size(48.dp))
                                    }
                                },
                                                                                                        actions = {
                                                                                                        // Przycisk Zadzwoń — logo aplikacji, tylko w czacie.
                                                                                                        if (stanEkranu is SmsEkranStan.Czat) {
                                                                                                        val czatAdres = (stanEkranu as SmsEkranStan.Czat).address
                                                                                                        IconButton(
                                                                                                        onClick = { wykonajPolaczenie(context, czatAdres) }
                                                                                                        ) {
                                                                                                        Icon(
                                                                                                            imageVector = IkonyAplikacji.TelefonLogo,
                                                                                                            contentDescription = "Zadzwoń",
                                                                                                            tint = akcent,
                                                                                                            modifier = Modifier.size(26.dp)
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
                                        .background(dialer.dominik.pl.ui.theme.LocalMotyw.current.kafelek)
                                        .border(1.dp, akcent.copy(alpha = 0.14f), RoundedCornerShape(16.dp))
                                        .padding(start = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    BasicTextField(
                                        value = zapytanieSzukania,
                                        onValueChange = { zapytanieSzukania = it },
                                        singleLine = true,
                                        textStyle = TextStyle(
                                            color = dialer.dominik.pl.ui.theme.LocalMotyw.current.naTle,
                                            fontSize = 15.sp
                                        ),
                                        cursorBrush = SolidColor(akcent),
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(vertical = 12.dp),
                                        decorationBox = { wewnetrzne ->
                                            if (zapytanieSzukania.isEmpty()) {
                                                Text(
                                                    text = "Szukaj w wiadomościach…",
                                                    color = paletaSms.naTle.copy(alpha = 0.32f),
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
                                            .then(
                                                if (wiedzmin) Modifier.tloSrebrnyPolysk(RoundedCornerShape(0.dp))
                                                else Modifier.background(akcent.copy(alpha = 0.18f))
                                            )
                                            .clickable { stanEkranu = SmsEkranStan.NowaWiadomosc("") },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "+",
                                            color = if (wiedzmin) Color(0xFF1A1C1E) else akcent,
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
                .padding(
                    top = odstepy.calculateTopPadding(),
                    start = odstepy.calculateLeftPadding(layoutDirection = LayoutDirection.Ltr),
                    end = odstepy.calculateRightPadding(layoutDirection = LayoutDirection.Ltr),
                    // Uwzględnij pasek „Szukaj” — wcześniej bottom=0 zasłaniał ostatni kafelek.
                    bottom = odstepy.calculateBottomPadding()
                )
        ) {
            AnimatedContent(
                targetState = stanEkranu,
                transitionSpec = {
                    val doCzatu = targetState is SmsEkranStan.Czat && initialState is SmsEkranStan.Skrzynka
                    val zeCzatu = initialState is SmsEkranStan.Czat && targetState is SmsEkranStan.Skrzynka
                    val doNowej = targetState is SmsEkranStan.NowaWiadomosc
                    val zeNowej = initialState is SmsEkranStan.NowaWiadomosc
                    // Jeden owner przejścia: tylko oś pozioma + fade (bez skosu i bez drugiej animacji skrzynki).
                    val fadeInSpec = tween<Float>(180, easing = FastOutSlowInEasing)
                    val fadeOutSpec = tween<Float>(160, easing = FastOutSlowInEasing)
                    val slideSpec = tween<IntOffset>(240, easing = FastOutSlowInEasing)
                    when {
                        doCzatu || doNowej -> {
                            (fadeIn(fadeInSpec) + slideInHorizontally(slideSpec) { it / 8 })
                                .togetherWith(
                                    fadeOut(fadeOutSpec) + slideOutHorizontally(slideSpec) { -it / 10 }
                                )
                        }
                        zeCzatu -> {
                            (fadeIn(fadeInSpec) + slideInHorizontally(slideSpec) { -it / 8 })
                                .togetherWith(
                                    fadeOut(fadeOutSpec) + slideOutHorizontally(slideSpec) { it / 10 }
                                )
                        }
                        zeNowej -> {
                            (fadeIn(fadeInSpec) + slideInHorizontally(slideSpec) { -it / 10 })
                                .togetherWith(
                                    fadeOut(fadeOutSpec) + slideOutHorizontally(slideSpec) { it / 8 }
                                )
                        }
                        else -> {
                            (fadeIn(tween(220))).togetherWith(fadeOut(tween(160)))
                        }
                    }.using(SizeTransform(clip = false))
                },
                contentKey = {
                    when (it) {
                        is SmsEkranStan.Skrzynka -> "skrzynka"
                        is SmsEkranStan.Czat -> "czat:${it.address}"
                        is SmsEkranStan.NowaWiadomosc -> "nowa"
                    }
                },
                label = "przejscie-ekranow"
            ) { stan ->
                when (stan) {
                is SmsEkranStan.Skrzynka -> {
                    Box(Modifier.fillMaxSize()) {
                        // Przejście skrzynka ↔ archiwum: skrzynka znika, archiwum wjeżdża (i odwrotnie).
                        AnimatedContent(
                            targetState = pokazArchiwum,
                            modifier = Modifier.fillMaxSize(),
                            transitionSpec = {
                                val doArchiwum = targetState && !initialState
                                val slideIn = if (doArchiwum) {
                                    slideInVertically(tween(340, easing = FastOutSlowInEasing)) { it / 8 }
                                } else {
                                    slideInVertically(tween(340, easing = FastOutSlowInEasing)) { -it / 8 }
                                }
                                val slideOut = if (doArchiwum) {
                                    slideOutVertically(tween(260, easing = FastOutSlowInEasing)) { -it / 10 }
                                } else {
                                    slideOutVertically(tween(260, easing = FastOutSlowInEasing)) { it / 10 }
                                }
                                (fadeIn(tween(300, easing = FastOutSlowInEasing)) + slideIn)
                                    .togetherWith(fadeOut(tween(220)) + slideOut)
                                    .using(SizeTransform(clip = false))
                            },
                            label = "przejscieArchiwum"
                        ) { archiwum ->
                            val lista = if (archiwum) listySkrzynki.archiwum else listySkrzynki.skrzynka
                            // LazyColumn zawsze zamontowany — animateItem może odtworzyć fade/slide
                            // przy filtrowaniu wyszukiwarki (znikające i pojawiające się kafelki).
                            Box(Modifier.fillMaxSize()) {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 20.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(lista, key = { it.address }) { watek ->
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
                                            onUkryj = {
                                                if (archiwum) {
                                                    zapiszUkryte(ukryteAdresy - watek.address)
                                                } else {
                                                    zapiszUkryte(ukryteAdresy + watek.address)
                                                }
                                            },
                                            onUsun = {
                                                val adres = watek.address
                                                val norm = normalizujAdresSms(adres)
                                                // Najpierw UI — kafelek znika od razu (bez bounce „w miejscu”).
                                                watki = watki.filter { normalizujAdresSms(it.address) != norm }
                                                WatekCache.watki = watki
                                                zapiszUkryte(
                                                    ukryteAdresy.filter { normalizujAdresSms(it) != norm }.toSet()
                                                )
                                                usunDraftCzatu(context, adres)
                                                scope.launch(Dispatchers.IO) {
                                                    usunKonwersacje(context, adres)
                                                }
                                            },
                                            trybArchiwum = archiwum,
                                            modifier = Modifier.animateItem(
                                                fadeInSpec = tween(260, easing = FastOutSlowInEasing),
                                                fadeOutSpec = tween(180, easing = FastOutSlowInEasing),
                                                placementSpec = spring(
                                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                                    stiffness = Spring.StiffnessMediumLow
                                                )
                                            )
                                        )
                                    }
                                }
                                AnimatedVisibility(
                                    visible = lista.isEmpty() && (jestDomyslnaSms || archiwum),
                                    enter = fadeIn(tween(220)),
                                    exit = fadeOut(tween(160)),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text(
                                            text = when {
                                                zapytanieSzukania.trim().length >= MIN_SZUKANE_ZN ->
                                                    "Brak wyników wyszukiwania"
                                                archiwum -> "Brak wiadomości w archiwum"
                                                else -> "Brak wiadomości w skrzynce"
                                            },
                                            color = paletaSms.naTle.copy(alpha = 0.5f),
                                            fontSize = 15.sp
                                        )
                                    }
                                }
                            }
                        }

                        // Brak roli SMS → przycisk na środku → systemowy wybór domyślnej aplikacji.
                        AnimatedVisibility(
                            visible = !jestDomyslnaSms && !pokazArchiwum,
                            enter = fadeIn(tween(280)) + slideInVertically(tween(320)) { it / 6 },
                            exit = fadeOut(tween(200)),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(paletaSms.tlo.copy(alpha = 0.72f))
                                    .clickable(enabled = false) { /* blokuje listę pod spodem */ },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(horizontal = 32.dp)
                                ) {
                                    Text(
                                        text = "Aby odbierać i wysyłać wiadomości,\nustaw tę aplikację jako domyślną do SMS",
                                        color = paletaSms.naTle.copy(alpha = 0.72f),
                                        fontSize = 14.sp,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 20.sp,
                                        modifier = Modifier.padding(bottom = 18.dp)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(akcent.copy(alpha = 0.18f))
                                            .border(1.dp, akcent.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                                            .clickable {
                                                launcherDomyslnejSms.launch(intencjaUstawDomyslnaSms(context))
                                            }
                                            .padding(horizontal = 22.dp, vertical = 14.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Ustaw jako domyślną do SMS",
                                            color = akcent,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                is SmsEkranStan.Czat -> {
                                    val watekCzat = run {
                                        val znaleziony = watki.find { it.address == stan.address }
                                        val nazwa = znaleziony?.contactName
                                            ?: resolveContactName(context, stan.address)
                                        val pseudo = znaleziony?.pseudonim
                                            ?: nazwa?.let { pobierzPseudonim(context, it) }
                                        (znaleziony ?: SmsWatek(
                                            address = stan.address,
                                            contactName = nazwa,
                                            lastMessage = "",
                                            lastDate = 0L,
                                            messages = emptyList()
                                        )).copy(
                                            contactName = nazwa,
                                            pseudonim = pseudo
                                        )
                                    }
                                    EkranCzatuSzczegoly(
                                        context = context,
                                        watek = watekCzat,
                                        onWiadomoscWyslana = {
                                            odswiezenie++
                                        },
                                        startMsgId = stan.startMsgId,
                                        szukanaTresc = stan.szukanaTresc,
                                        onWyjscie = {
                                            stanEkranu = SmsEkranStan.Skrzynka
                                            otwartyCzatAdres = null
                                            odswiezenie++
                                        }
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

    /** Logo prawdziwej firmy (alfa-nadawcy SMS) — pobrane z internetu raz i cache'owane.
     *  Gdy sieć niedostępna / nie znaleziono → pokazuje inicjał (aż sieć wróci, kolejna
     *  recompozycja karty ponowi `pobierzLogoFirmy`). */
    @Composable
    private fun LogoFirmy(
        context: Context,
        nazwa: String,
        rozmiar: androidx.compose.ui.unit.Dp
    ) {
        val akcent = motywAkcent()
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
            // Fallback do czasu pobrania logo / gdy brak domeny: inicjał.
            Text(
                text = nazwa.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                color = akcent,
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
        trybArchiwum: Boolean = false,
        modifier: Modifier = Modifier
    ) {
        val paletaSms = dialer.dominik.pl.ui.theme.LocalMotyw.current
        val wiedzmin = czyMotywWiedzmin()
        val akcent = motywAkcent()
        val akcentMorski = if (wiedzmin) akcent else dialer.dominik.pl.ui.theme.ZielonyMorski
        val ksztaltKafla = RoundedCornerShape(paletaSms.promienKafla.dp)
        val formatDaty = remember { SimpleDateFormat("dd.MM.yyyy, HH:mm", Locale.getDefault()) }
        val roboczy = watek.lastDate == Long.MAX_VALUE
        val dataTekst = if (roboczy) "Roboczy" else formatDaty.format(Date(watek.lastDate))
    // Numer z kontaktu → ciemniejsze tło; nieznany numer → jaśniejsze (wyróżnienie).
        val nieprzeczytana = watek.nieprzeczytane > 0
        val obramowanie = when {
            nieprzeczytana -> akcent.copy(alpha = 0.40f)
            else -> paletaSms.kafelekRamka
        }
        val gradientKarty = paletaSms.gradientKafelka()

        // Swipe: przeciągnięcie przesuwa kartę i ujawnia tła akcji pod spodem.
        var zasieg by remember { mutableFloatStateOf(0f) }
        val prog = with(LocalDensity.current) { 72.dp.toPx() }
        val scopeSwipe = rememberCoroutineScope()
        val aktualneUsun by rememberUpdatedState(onUsun)
        val aktualneUkryj by rememberUpdatedState(onUkryj)
        val aktualneKlik by rememberUpdatedState(onClick)

        fun animujPowrot() {
            val start = zasieg
            scopeSwipe.launch {
                androidx.compose.animation.core.Animatable(start).animateTo(
                    0f,
                    spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                ) { zasieg = value }
            }
        }

        Box(modifier = modifier.fillMaxWidth()) {
            // Tło po PRAWĘJ — widoczne przy przeciągnięciu w LEWO (Usuń konwersację).
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(ksztaltKafla)
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
                    .clip(ksztaltKafla)
                    .background(akcentMorski.copy(alpha = 0.20f)),
                contentAlignment = Alignment.CenterStart
            ) {
                if (zasieg > 0f) {
                    Row(Modifier.padding(start = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = IkonyAplikacji.Kosz,
                            contentDescription = null,
                            tint = akcent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(if (trybArchiwum) "Przywróć" else "Ukryj", color = akcent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(zasieg.roundToInt(), 0) }
                    .clip(ksztaltKafla)
                    .background(
                        if (wiedzmin) paletaSms.gradientKafelka(alfaGora = 0.82f) else gradientKarty
                    )
                    .border(1.dp, obramowanie, ksztaltKafla)
                    .pointerInput(watek.address) {
                        // Krótkie kliknięcie (bez przeciągania) otwiera czat.
                        detectTapGestures(onTap = { aktualneKlik() })
                    }
                    .pointerInput(watek.address, prog) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                val przekroczono = when {
                                    // w prawo → ukryj / przywróć
                                    zasieg > prog -> {
                                        aktualneUkryj()
                                        true
                                    }
                                    // w lewo → usuń
                                    zasieg < -prog -> {
                                        aktualneUsun()
                                        true
                                    }
                                    else -> false
                                }
                                // Bounce tylko gdy próg nieosiągnięty; po akcji kafelek znika z listy.
                                if (!przekroczono) animujPowrot()
                            },
                            onDragCancel = { animujPowrot() },
                            onHorizontalDrag = { _, dystans ->
                                zasieg = (zasieg + dystans).coerceIn(-prog * 1.5f, prog * 1.5f)
                            }
                        )
                    }
            ) {
                // Mgła tylko przy nieprzeczytanych (jak kafelek główny historii)
                if (wiedzmin && nieprzeczytana) {
                    MglaNaPrzycisku(
                        Modifier.matchParentSize(),
                        sila = 0.55f,
                        seed = watek.address.hashCode(),
                        lekka = true
                    )
                }
                Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        // Miejsce na kółko licznika w prawym górnym rogu kafelka.
                        .padding(end = if (nieprzeczytana) 26.dp else 0.dp),
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
                                                                        akcentMorski.copy(alpha = 0.55f),
                                                                        akcentMorski.copy(alpha = 0.18f)
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
                                                        rozmiar = 44.dp,
                                                        wariant = if (wiedzmin) AwatarWariant.Wiedzmin else AwatarWariant.Domyslny,
                                                        kolorWiedzmina = if (wiedzmin) kolorZnakuWiedzmina(watek.address.hashCode()) else null,
                                                        indeksUlubionego = watek.address.hashCode()
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
                                                                        akcentMorski.copy(alpha = if (nieprzeczytana) 0.55f else 0.4f),
                                                                        akcentMorski.copy(alpha = 0.18f)
                                                                    )
                                                                )
                                                            ),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = inicjal,
                                                            color = if (nieprzeczytana) paletaSms.naTle else akcent,
                                                            fontSize = 19.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                        // Kropka nieprzeczytanej — neon zielony (spójnie z resztą UI).
                        if (nieprzeczytana) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(akcent)
                        )
                    }
                    // Nagłówek wiersza: pseudonim (pierwszeństwo) → imię/nazwisko, a pod
                                        // spodem małą czcionką numer telefonu.
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = watek.pseudonim ?: watek.contactName ?: watek.address,
                                                color = if (nieprzeczytana) paletaSms.naTle else paletaSms.naTle.copy(alpha = 0.9f),
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
                                                    color = paletaSms.naTle.copy(alpha = 0.4f),
                                                    fontSize = 11.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                }
                Text(
                    text = dataTekst,
                    color = if (nieprzeczytana) akcent.copy(alpha = 0.88f)
                    else paletaSms.naTle.copy(alpha = 0.4f),
                    fontSize = 11.sp,
                    fontWeight = if (nieprzeczytana) FontWeight.Medium else FontWeight.Normal
                )
            }
            Text(
                                        text = when {
                                            watek.lastMessage.isBlank() -> if (roboczy) "" else "MMS"
                                            watek.lastMessage == "📷 [MMS]" -> "MMS"
                                            else -> watek.lastMessage
                                        },
                                        color = if (roboczy) akcent
                                                else if (nieprzeczytana) paletaSms.naTle.copy(alpha = 0.85f)
                                                else paletaSms.naTle.copy(alpha = 0.7f),
                                        fontSize = 14.sp,
                                        fontWeight = if (nieprzeczytana) FontWeight.SemiBold else FontWeight.Normal,
                                        maxLines = 1
                        )
                    }
                // Licznik nieprzeczytanych — kółko w prawym górnym rogu kafelka.
                if (nieprzeczytana) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(akcent),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = watek.nieprzeczytane.toString(),
                            color = NaPrzycisku,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                        }
                }
            }

