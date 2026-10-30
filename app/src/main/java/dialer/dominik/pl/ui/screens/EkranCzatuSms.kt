package dialer.dominik.pl.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.provider.Telephony
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import dialer.dominik.pl.sms.OdbiorcaSms.Companion.kopiujDoSchowka
import dialer.dominik.pl.sms.StatusWiadomosciBus
import dialer.dominik.pl.sms.StanWysylki
import dialer.dominik.pl.sms.pobierzKanal
import dialer.dominik.pl.sms.priorytetStanu
import dialer.dominik.pl.sms.stanZKolumnSms
import dialer.dominik.pl.ui.components.IkonyAplikacji
import dialer.dominik.pl.ui.components.LogoAnimowane
import dialer.dominik.pl.ui.theme.CzerwonyPolaczenia
import dialer.dominik.pl.ui.theme.ZielonyMorski
import dialer.dominik.pl.ui.theme.NaPrzycisku
import dialer.dominik.pl.ui.theme.ZielonyNeon
import dialer.dominik.pl.ui.theme.gradientKafelka
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * Ekran CZATU (rozmowa SMS z jednym numerem).
 * Funkcje: wysyłka SMS, planowanie (długie przytrzymanie -> zegar -> potwierdzenie),
 * cytowanie, menu (kopiuj/usun/przypnij/odpowiedz), robocze czaty (drafty),
 * auto-emoji, oraz ZDJĘCIA/MMS (galeria + kamera).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EkranCzatuSzczegoly(
    context: Context,
    watek: SmsWatek,
    onWiadomoscWyslana: () -> Unit,
    pokazNaglowek: Boolean = false,
    startMsgId: Long? = null,
    szukanaTresc: String? = null,
    /** Wywołane po animacji wyjścia (Wstecz) — rodzic przełącza na skrzynkę. */
    onWyjscie: (() -> Unit)? = null
) {
    val adres = watek.address
    val wybranaPlatforma = pobierzKanal(context, adres)
    val density = LocalDensity.current

    // Lekkie wejście UI — wyjście z czatu robi AnimatedContent w SmsScreen (jeden owner).
    var wejscieUi by remember(adres) { mutableStateOf(false) }
    var pokazWyborZalacznika by remember { mutableStateOf(false) }
    LaunchedEffect(adres) {
        wejscieUi = false
        pokazWyborZalacznika = false
        delay(16)
        wejscieUi = true
    }
    BackHandler(enabled = onWyjscie != null && !pokazWyborZalacznika) {
        onWyjscie?.invoke()
    }
    val alphaListy by animateFloatAsState(
        targetValue = if (wejscieUi) 1f else 0f,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "alphaListyCzatu"
    )
    val przesuniecieListyPx by animateFloatAsState(
        targetValue = if (wejscieUi) 0f else with(density) { 10.dp.toPx() },
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "offsetListyCzatu"
    )
    val alphaPaska by animateFloatAsState(
        targetValue = if (wejscieUi) 1f else 0f,
        animationSpec = tween(200, delayMillis = 40, easing = FastOutSlowInEasing),
        label = "alphaPaskaCzatu"
    )
    val przesunieciePaskaPx by animateFloatAsState(
        targetValue = if (wejscieUi) 0f else with(density) { 16.dp.toPx() },
        animationSpec = tween(220, delayMillis = 30, easing = FastOutSlowInEasing),
        label = "offsetPaskaCzatu"
    )

    // Roboczy czat (draft) startuje z ostatniej treści.
    var tresc by remember { mutableStateOf(pobierzDraftCzatu(context, adres) ?: "") }
    var wiadomosci by remember(adres) { mutableStateOf<List<SmsWpis>>(emptyList()) }
    // Pierwsze załadowanie wiadomości — osobny fade, żeby dymki nie „strzelały” z pustki.
    var listaZaladowana by remember(adres) { mutableStateOf(false) }

    // Menu akcji po długim przytrzymaniu wiadomości.
    var wybranyWpis by remember { mutableStateOf<SmsWpis?>(null) }
    var cytowanaWiadomosc by remember { mutableStateOf<SmsWpis?>(null) }
    var pokazWyborCzasu by remember { mutableStateOf(false) }
    var planCzasMs by remember { mutableStateOf<Long?>(null) }
    var planPodglad by remember { mutableStateOf<String?>(null) }
    var planowane by remember { mutableStateOf<List<Triple<String, Long, String>>>(emptyList()) }
    var pelnyEkranBmp by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    val prefsCzatu = remember { context.getSharedPreferences("czat_akcje", Context.MODE_PRIVATE) }
    var odswiezPoAkcji by remember { mutableIntStateOf(0) }
    /** Lekki tick z ContentObserver — przeładowuje tylko ten wątek (bez czyszczenia całej skrzynki). */
    var deltaTick by remember { mutableIntStateOf(0) }
    var maWiecejHistorii by remember(adres) { mutableStateOf(true) }
    var ladujeStarsze by remember { mutableStateOf(false) }

    // Załączniki zdjęć (MMS) — wiele naraz.
    var zalaczniki by remember { mutableStateOf<List<ZalacznikSms>>(emptyList()) }
    var edytowanyZalacznikKlucz by remember { mutableStateOf<Long?>(null) }
    val tymczasowyZdjecieFile = remember { mutableStateOf<java.io.File?>(null) }
    val maZalaczniki = zalaczniki.isNotEmpty()
    val coroutineScope = rememberCoroutineScope()

    fun dodajZalacznikiZUri(uris: List<android.net.Uri>, juzPrzygotowane: Boolean = false) {
        val wolne = MAX_ZALACZNIKOW_SMS - zalaczniki.size
        if (wolne <= 0) {
            Toast.makeText(context, "Maks. $MAX_ZALACZNIKOW_SMS zdjęć", Toast.LENGTH_SHORT).show()
            return
        }
        val doDodania = uris.take(wolne)
        coroutineScope.launch {
            val nowe = withContext(Dispatchers.IO) {
                doDodania.mapNotNull { zrodlo ->
                    val uriWysylki = if (juzPrzygotowane) zrodlo else przygotujZdjecieDoMms(context, zrodlo)
                    val bmp = dekodujBitmapuZUri(context, zrodlo, maxDim = 512)
                    if (uriWysylki != null && bmp != null) {
                        ZalacznikSms(System.nanoTime(), uriWysylki, bmp)
                    } else null
                }
            }
            if (nowe.isEmpty()) {
                Toast.makeText(context, "Nie udało się dodać zdjęcia", Toast.LENGTH_SHORT).show()
                return@launch
            }
            zalaczniki = zalaczniki + nowe
            if (uris.size > wolne) {
                Toast.makeText(
                    context,
                    "Dodano $wolne z ${uris.size} (limit $MAX_ZALACZNIKOW_SMS)",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // Wybór z galerii (wiele) / kamery (jedno).
    val launcherGaleria = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) dodajZalacznikiZUri(uris)
    }
    val launcherKamera = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.TakePicture()
    ) { ok ->
        if (ok) {
            val plik = tymczasowyZdjecieFile.value
            if (plik != null) {
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context, context.packageName + ".fileprovider", plik
                )
                dodajZalacznikiZUri(listOf(uri), juzPrzygotowane = true)
            }
        }
    }

    // Przypięte (pin) wiadomości — zestaw id z prefs.
    val przypieteSet = prefsCzatu.getStringSet("przypiete_$adres", emptySet()) ?: emptySet()
    var przypiete by remember(adres, odswiezPoAkcji) {
        mutableStateOf(przypieteSet)
    }

    val listState = rememberLazyListState()
    val pullRefreshState = rememberPullToRefreshState()

    /** reverseLayout: index 0 = najnowsza. „Przy dole” = firstVisible ≈ 0. */
    fun jestPrzyDoleCzatu(): Boolean {
        val info = listState.layoutInfo
        if (info.totalItemsCount == 0) return true
        val first = info.visibleItemsInfo.firstOrNull()?.index ?: 0
        return first <= 1
    }

    fun przewinDoNajnowszej(animacja: Boolean = true) {
        coroutineScope.launch {
            // Poczekaj na rekompozycję LazyColumn po dopisaniu dymka.
            kotlinx.coroutines.yield()
            delay(32)
            if (wiadomosci.isEmpty() && planowane.isEmpty()) return@launch
            try {
                if (animacja) listState.animateScrollToItem(0)
                else listState.scrollToItem(0)
            } catch (_: Exception) {
                runCatching { listState.scrollToItem(0) }
            }
        }
    }

    // Pierwsze otwarcie / pull-to-refresh — strona CZAT_PAGE_SIZE.
    LaunchedEffect(adres, odswiezPoAkcji) {
        if (odswiezPoAkcji == 0) {
            listaZaladowana = false
            maWiecejHistorii = true
        }
        oznaczWatekJakoPrzeczytany(context, adres)
        val poprzednie = wiadomosci
        val wpisy = withContext(Dispatchers.IO) {
            if (odswiezPoAkcji > 0) {
                uniewaznijCacheWatku(adres)
                uniewaznijCacheListyWatkow()
            }
            pobierzWiadomosciDlaAdresu(
                context = context,
                adres = adres,
                limit = CZAT_PAGE_SIZE,
                uzyjCache = odswiezPoAkcji == 0
            )
        }
        wiadomosci = scalWiadomosciCzatu(wpisyZBazy = wpisy, lokalne = poprzednie)
        if (wpisy.size < CZAT_PAGE_SIZE) maWiecejHistorii = false
        planowane = pobierzPlanowaneSms(context, adres)
        listaZaladowana = true
    }

    // Delta z providera — tylko ten wątek, bez uniewaznijCacheSkrzynki().
    LaunchedEffect(adres, deltaTick) {
        if (deltaTick == 0) return@LaunchedEffect
        val przyDole = jestPrzyDoleCzatu()
        val poprzednie = wiadomosci
        val lim = max(CZAT_PAGE_SIZE, poprzednie.size.coerceAtMost(CZAT_PAGE_SIZE * 3))
        val wpisy = withContext(Dispatchers.IO) {
            uniewaznijCacheWatku(adres)
            pobierzWiadomosciDlaAdresu(
                context = context,
                adres = adres,
                limit = lim,
                uzyjCache = false
            )
        }
        val przedIds = poprzednie.map { it.id }.toSet()
        wiadomosci = scalWiadomosciCzatu(wpisyZBazy = wpisy, lokalne = poprzednie)
        val doszlaNowa = wiadomosci.any { it.id !in przedIds }
        if (przyDole && doszlaNowa) przewinDoNajnowszej(animacja = true)
    }

    // Live status SENT / DELIVERED — bez pełnego reloadu.
    LaunchedEffect(adres) {
        StatusWiadomosciBus.aktualizacje.collect { akt ->
            wiadomosci = wiadomosci.map { w ->
                val match = (akt.lokalnyKlucz != null && w.lokalnyKlucz == akt.lokalnyKlucz) ||
                    (akt.messageId != null && w.id == akt.messageId)
                if (!match) return@map w

                val nowyStan = akt.stan
                val staryStan = stanZKolumnSms(w.type, w.status)
                if (priorytetStanu(nowyStan) < priorytetStanu(staryStan)) return@map w

                w.copy(
                    type = akt.type ?: when (nowyStan) {
                        StanWysylki.BLAD -> Telephony.Sms.MESSAGE_TYPE_FAILED
                        StanWysylki.WYSYLANIE -> Telephony.Sms.MESSAGE_TYPE_OUTBOX
                        else -> Telephony.Sms.MESSAGE_TYPE_SENT
                    },
                    status = akt.status ?: when (nowyStan) {
                        StanWysylki.DOSTARCZONO -> Telephony.Sms.STATUS_COMPLETE
                        StanWysylki.BLAD -> Telephony.Sms.STATUS_FAILED
                        StanWysylki.WYSŁANO -> Telephony.Sms.STATUS_PENDING
                        StanWysylki.WYSYLANIE -> Telephony.Sms.STATUS_PENDING
                    }
                )
            }
            // Nieznany URI (np. MMS) → lekka delta tego wątku.
            if (akt.lokalnyKlucz == null && akt.messageId == null) {
                deltaTick++
            }
        }
    }
    val alphaDymkow by animateFloatAsState(
        targetValue = if (listaZaladowana) 1f else 0f,
        animationSpec = tween(280, easing = FastOutSlowInEasing),
        label = "alphaDymkowCzatu"
    )

    DisposableEffect(adres) {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        var zaplanowane: Runnable? = null
        val observer = object : android.database.ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: android.net.Uri?) {
                super.onChange(selfChange, uri)
                zaplanowane?.let { handler.removeCallbacks(it) }
                val zadanie = Runnable { deltaTick++ }
                zaplanowane = zadanie
                handler.postDelayed(zadanie, 280L)
            }
        }
        context.contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, observer)
        context.contentResolver.registerContentObserver(android.provider.Telephony.Mms.CONTENT_URI, true, observer)
        onDispose {
            zaplanowane?.let { handler.removeCallbacks(it) }
            context.contentResolver.unregisterContentObserver(observer)
        }
    }

    Column(
        Modifier.fillMaxSize().padding(vertical = 4.dp).windowInsetsPadding(WindowInsets.ime)
    ) {
        // Nagłówek (tylko w czacie-w-dymku / BubbleChatActivity):
        // pseudonim (albo imię), pod nim numer; w prawo przycisk dzwonienia z logo.
        if (pokazNaglowek) {
            val tytulNaglowka = watek.pseudonim?.takeIf { it.isNotBlank() }
                ?: watek.contactName
                ?: adres
            val pokazNumerPod = !watek.pseudonim.isNullOrBlank() || !watek.contactName.isNullOrBlank()
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tytulNaglowka,
                        color = dialer.dominik.pl.ui.theme.LocalMotyw.current.naTle,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (pokazNumerPod) {
                        Text(
                            text = adres,
                            color = dialer.dominik.pl.ui.theme.LocalMotyw.current.naTle.copy(alpha = 0.45f),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.horizontalGradient(listOf(ZielonyMorski, ZielonyNeon))
                        )
                        .clickable { zadzwonDoKontaktu(context, adres) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = IkonyAplikacji.TelefonLogo,
                        contentDescription = "Zadzwoń",
                        tint = Color(0xFF06231A),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

    var aktualnyPinIndeks by remember { mutableIntStateOf(0) }

    // Dociąganie starszej historii przy scrollu w górę (reverseLayout: wysoki index).
    LaunchedEffect(listState, adres, maWiecejHistorii) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
        }
            .filterNotNull()
            .distinctUntilChanged()
            .collect { lastIdx ->
                if (!maWiecejHistorii || ladujeStarsze || !listaZaladowana) return@collect
                if (lastIdx < wiadomosci.lastIndex - 4) return@collect
                val oldest = wiadomosci.minByOrNull { it.date }?.date ?: return@collect
                ladujeStarsze = true
                val starsze = withContext(Dispatchers.IO) {
                    pobierzWiadomosciDlaAdresu(
                        context = context,
                        adres = adres,
                        limit = CZAT_PAGE_SIZE,
                        starszeNizMs = oldest,
                        uzyjCache = false
                    )
                }
                if (starsze.isEmpty()) {
                    maWiecejHistorii = false
                } else {
                    wiadomosci = scalWiadomosciCzatu(
                        wpisyZBazy = (wiadomosci + starsze),
                        lokalne = wiadomosci
                    )
                }
                ladujeStarsze = false
            }
    }

    // Wyszukiwanie w czacie: trafienia (indeksy w `wiadomosci`) + wyszukiwana fraza.
    var trafieniaIndeksy by remember { mutableStateOf<IntArray?>(null) }
    var aktualneTrafienie by remember { mutableIntStateOf(0) }
    var podswietloneId by remember { mutableStateOf<Long?>(null) }
    var trybSzukaniaCzat by remember { mutableStateOf(false) }
    var szukaneWCzacie by remember { mutableStateOf("") }
    val poleSzukaniaFocus = remember { FocusRequester() }
    val klawiatura = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    // Przy zmianie czatu resetujemy tryb wyszukiwania i podświetlenia.
    LaunchedEffect(adres) {
        trybSzukaniaCzat = false
        szukaneWCzacie = ""
        trafieniaIndeksy = null
        aktualneTrafienie = 0
        podswietloneId = null
    }

    // Auto-focus + pokazanie klawiatury po otwarciu wyszukiwania w czacie.
    LaunchedEffect(trybSzukaniaCzat) {
        if (trybSzukaniaCzat) {
            try {
                poleSzukaniaFocus.requestFocus()
                klawiatura?.show()
            } catch (_: Exception) {}
        }
    }

    val przeliczTrafienia = { s: String ->
        val q = normalizujPolski(s).trim()
        wiadomosci.mapIndexedNotNull { i, w ->
            if (q.isNotEmpty() && normalizujPolski(w.body).contains(q)) i else null
        }
    }

    // Automatyczny skok do szukanej wiadomości po wejściu z wyszukiwarki.
    // Wyszukiwanie WEWNĄTRZ czatu también korzysta z tego efektu.
    LaunchedEffect(wiadomosci, startMsgId, szukanaTresc, trybSzukaniaCzat, szukaneWCzacie) {
        if (wiadomosci.isNotEmpty()) {
            // Wyszukiwanie wewnątrz czatu — przelicz na żywo.
            if (trybSzukaniaCzat) {
                val fraza = normalizujPolski(szukaneWCzacie.trim())
                if (fraza.isNotEmpty()) {
                    val trafienia = wiadomosci.mapIndexedNotNull { i, w ->
                        if (normalizujPolski(w.body).contains(fraza)) i else null
                    }
                    if (trafienia.isNotEmpty()) {
                        trafieniaIndeksy = trafienia.toIntArray()
                        aktualneTrafienie = 0
                        val pierwsz = trafienia.first()
                        podswietloneId = wiadomosci[pierwsz].id
                        val doCelu = planowane.size + pierwsz
                        listState.scrollToItem((doCelu - 1).coerceAtLeast(0))
                        listState.animateScrollToItem(doCelu, scrollOffset = 0)
                    } else {
                        trafieniaIndeksy = null; aktualneTrafienie = 0; podswietloneId = null
                    }
                } else {
                    trafieniaIndeksy = null; aktualneTrafienie = 0; podswietloneId = null
                }
                return@LaunchedEffect
            }
            // Wejście z wyszukiwarki globalnej (startMsgId / szukanaTresc).
            if (startMsgId != null || !szukanaTresc.isNullOrBlank()) {
                if (!szukanaTresc.isNullOrBlank()) {
                    trafieniaIndeksy = przeliczTrafienia(szukanaTresc).toIntArray()
                } else trafieniaIndeksy = null
                val targetIdx = if (startMsgId != null) wiadomosci.indexOfFirst { it.id == startMsgId }
                                else wiadomosci.indexOfFirst {
                                    !szukanaTresc.isNullOrBlank() && normalizujPolski(it.body).contains(normalizujPolski(szukanaTresc))
                                }
                if (targetIdx != -1) {
                    aktualneTrafienie = trafieniaIndeksy?.indexOf(targetIdx)?.takeIf { it >= 0 } ?: 0
                    podswietloneId = wiadomosci[targetIdx].id
                    val doCelu = planowane.size + targetIdx
                    listState.scrollToItem((doCelu - 1).coerceAtLeast(0))
                    listState.animateScrollToItem(doCelu, scrollOffset = 0)
                }
            }
        }
    }

    // Pasek przypisanych (pinned) wiadomości na górze czatu (wielu przypisanych)
    val przypisaneWpisy = remember(wiadomosci, przypiete) {
        wiadomosci.filter { it.id.toString() in przypiete }
    }
    AnimatedVisibility(
        visible = przypisaneWpisy.isNotEmpty(),
        enter = expandVertically(tween(200, easing = FastOutSlowInEasing)) + fadeIn(tween(160)),
        exit = shrinkVertically(tween(160)) + fadeOut(tween(120))
    ) {
        val bezpiecznyIndeks = aktualnyPinIndeks.coerceIn(0, (przypisaneWpisy.size - 1).coerceAtLeast(0))
        if (przypisaneWpisy.isEmpty()) return@AnimatedVisibility
        val pinWpis = przypisaneWpisy[bezpiecznyIndeks]

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(dialer.dominik.pl.ui.theme.LocalMotyw.current.gradientKafelka())
                .border(1.dp, ZielonyNeon.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                .clickable {
                    val idx = wiadomosci.indexOfFirst { it.id == pinWpis.id }
                    if (idx != -1) {
                        coroutineScope.launch {
                            listState.animateScrollToItem(idx)
                        }
                    } else if (przypisaneWpisy.size > 1) {
                        aktualnyPinIndeks = (aktualnyPinIndeks + 1) % przypisaneWpisy.size
                    }
                }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(IkonyAplikacji.Gwiazda, contentDescription = "Przypięta", tint = ZielonyNeon, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Przypięta wiadomość", color = ZielonyNeon, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    if (przypisaneWpisy.size > 1) {
                        Text("(${bezpiecznyIndeks + 1}/${przypisaneWpisy.size})", color = ZielonyNeon.copy(alpha = 0.7f), fontSize = 10.sp)
                    }
                }
                Text(
                    text = if (pinWpis.bitmap != null) "📷 [MMS]" else pinWpis.body,
                    color = dialer.dominik.pl.ui.theme.LocalMotyw.current.naTle.copy(alpha = 0.9f),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (przypisaneWpisy.size > 1) {
                IconButton(
                    onClick = { aktualnyPinIndeks = (aktualnyPinIndeks + 1) % przypisaneWpisy.size },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(IkonyAplikacji.Strzalka, contentDescription = "Następna", tint = ZielonyNeon, modifier = Modifier.size(14.dp))
                }
            }
            IconButton(
                onClick = {
                    togglePin(context, adres, pinWpis.id, prefsCzatu)
                    odswiezPoAkcji++
                },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(IkonyAplikacji.Zamknij, contentDescription = "Odepnij", tint = CzerwonyPolaczenia, modifier = Modifier.size(16.dp))
            }
        }
    }

    // Pasek wyszukiwania WE WNĄTRZ czatu — pojawia się po wybraniu „Szukaj w czacie".
    AnimatedVisibility(
        visible = trybSzukaniaCzat,
        enter = expandVertically(tween(200, easing = FastOutSlowInEasing)) + fadeIn(tween(160)),
        exit = shrinkVertically(tween(160)) + fadeOut(tween(120))
    ) {
        val kolorTekstuSzukaj = dialer.dominik.pl.ui.theme.LocalMotyw.current.naTle
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(dialer.dominik.pl.ui.theme.LocalMotyw.current.kafelek)
                .border(1.dp, ZielonyNeon.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                .padding(start = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(IkonyAplikacji.Lupa, contentDescription = null, tint = ZielonyNeon, modifier = Modifier.size(18.dp))
            BasicTextField(
                value = szukaneWCzacie,
                onValueChange = { szukaneWCzacie = it },
                singleLine = true,
                textStyle = TextStyle(color = kolorTekstuSzukaj, fontSize = 15.sp),
                cursorBrush = SolidColor(ZielonyNeon),
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 12.dp).focusRequester(poleSzukaniaFocus),
                decorationBox = { inner ->
                    if (szukaneWCzacie.isEmpty()) Text("Szukaj w tej rozmowie…", color = kolorTekstuSzukaj.copy(alpha = 0.35f), fontSize = 15.sp)
                    inner()
                }
            )
            IconButton(onClick = {
                trybSzukaniaCzat = false
                szukaneWCzacie = ""
                trafieniaIndeksy = null
                aktualneTrafienie = 0
                podswietloneId = null
            }) {
                Icon(IkonyAplikacji.Zamknij, contentDescription = "Zamknij", tint = dialer.dominik.pl.ui.theme.LocalMotyw.current.naTle.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
            }
        }
    }

    // Lista wiadomości + loader z logo przy pierwszym ładowaniu.
    Box(
        Modifier
            .weight(1f)
            .fillMaxWidth()
            .graphicsLayer {
                alpha = alphaListy
                translationY = przesuniecieListyPx
            }
    ) {
        // Lista wiadomości (pull-to-refresh: przeciągnij w dół, by przeładować WSZYSTKIE).
        LazyColumn(
            state = listState,
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer { alpha = alphaDymkow }
                .pointerInput(Unit) {
                    detectTapGestures {
                        podswietloneId = null
                        // Klik w wiadomości chowa klawiaturę (wyszukiwanie zostaje aktywne).
                        klawiatura?.hide()
                    }
                }
                .pullToRefresh(
                    onRefresh = { odswiezPoAkcji++ },
                    isRefreshing = false,
                    state = pullRefreshState
                ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            reverseLayout = true
        ) {
            // Planowane (na górze, wyróżnione).
            items(planowane, key = { "pl_" + it.second }) { (_, czas, trescPl) ->
                val podgladCzasu = java.text.SimpleDateFormat("dd.MM, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(czas))
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(dialer.dominik.pl.ui.theme.LocalMotyw.current.kafelek).border(1.dp, ZielonyNeon.copy(alpha = 0.2f), RoundedCornerShape(14.dp)).padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        dialer.dominik.pl.ui.components.icons.IkonaZegaraCzasu(czasMs = czas, modifier = Modifier.size(18.dp), kolor = ZielonyNeon)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Zaplanowano: $podgladCzasu", color = ZielonyNeon, fontSize = 11.sp)
                            Text(trescPl, color = dialer.dominik.pl.ui.theme.LocalMotyw.current.naTle.copy(alpha = 0.85f), fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            itemsIndexed(
                wiadomosci,
                key = { _, w -> w.lokalnyKlucz ?: w.id }
            ) { idx, wpis ->
                KartaWiadomosciNowoczesna(
                    modifier = Modifier.animateItem(
                        fadeInSpec = tween(220, easing = FastOutSlowInEasing),
                        fadeOutSpec = tween(160, easing = FastOutSlowInEasing),
                        placementSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ),
                    wpis = wpis,
                    onLongPress = { wybranyWpis = wpis },
                    przypieta = wpis.id.toString() in przypiete,
                    onImageClick = { pelnyEkranBmp = it },
                    // Podświetlenie trafień: wszystkie lekko, aktualne (podswietlonaGlowna) pulsuje.
                    podswietlona = trafieniaIndeksy?.contains(idx) == true,
                    podswietlonaGlowna = (idx == (trafieniaIndeksy?.getOrNull(aktualneTrafienie) ?: -1)),
                    szukanaFraza = if (idx == (trafieniaIndeksy?.getOrNull(aktualneTrafienie) ?: -1)) (szukanaTresc ?: szukaneWCzacie) else null
                )
            }
        }

        // Loader z logo aplikacji — tylko przy pierwszym ładowaniu rozmowy.
        androidx.compose.animation.AnimatedVisibility(
            visible = !listaZaladowana,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(tween(220)) + scaleIn(initialScale = 0.88f, animationSpec = tween(280, easing = FastOutSlowInEasing)),
            exit = fadeOut(tween(260, easing = FastOutSlowInEasing)) +
                scaleOut(targetScale = 0.92f, animationSpec = tween(260, easing = FastOutSlowInEasing))
        ) {
            LogoAnimowane(
                modifier = Modifier.size(108.dp),
                wejscieAnimacji = 1f,
                pokazPierscien = false,
                trybLekki = false
            )
        }

        // NAKŁADKA nawigacji po trafieniach — przezroczysta, nad wiadomościami.
        val trafNakl = trafieniaIndeksy
        if (trafNakl != null && trafNakl.size > 1) {
            Column(
                Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Strzałka w górę — poprzednie trafienie.
                IconButton(
                    onClick = {
                        val nowy = ((aktualneTrafienie - 1) + trafNakl.size) % trafNakl.size
                        aktualneTrafienie = nowy
                        val idx = trafNakl[nowy]
                        podswietloneId = wiadomosci[idx].id
                        coroutineScope.launch {
                            val doCelu = planowane.size + idx
                            listState.scrollToItem((doCelu - 1).coerceAtLeast(0))
                            listState.animateScrollToItem(doCelu, scrollOffset = 0)
                        }
                    },
                    modifier = Modifier.size(48.dp).background(Color.Black.copy(alpha = 0.45f), CircleShape)
                ) {
                    Icon(IkonyAplikacji.Strzalka, contentDescription = "Poprzednie", tint = ZielonyNeon, modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = -90f })
                }
                // Licznik trafień.
                Text(
                    text = "${aktualneTrafienie + 1}/${trafNakl.size}",
                    color = ZielonyNeon,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 4.dp).background(Color.Black.copy(alpha = 0.45f), CircleShape).padding(horizontal = 6.dp, vertical = 2.dp)
                )
                // Strzałka w dół — następne trafienie.
                IconButton(
                    onClick = {
                        val nowy = (aktualneTrafienie + 1) % trafNakl.size
                        aktualneTrafienie = nowy
                        val idx = trafNakl[nowy]
                        podswietloneId = wiadomosci[idx].id
                        coroutineScope.launch {
                            val doCelu = planowane.size + idx
                            listState.scrollToItem((doCelu - 1).coerceAtLeast(0))
                            listState.animateScrollToItem(doCelu, scrollOffset = 0)
                        }
                    },
                    modifier = Modifier.size(48.dp).background(Color.Black.copy(alpha = 0.45f), CircleShape)
                ) {
                    Icon(IkonyAplikacji.Strzalka, contentDescription = "Następne", tint = ZielonyNeon, modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = 90f })
                }
                // ✕ — wyłącza tryb wyszukiwania (na dole).
                IconButton(
                    onClick = {
                        trafieniaIndeksy = null
                        aktualneTrafienie = 0
                        podswietloneId = null
                    },
                    modifier = Modifier.size(48.dp).background(Color.Black.copy(alpha = 0.45f), CircleShape)
                ) {
                    Icon(IkonyAplikacji.Zamknij, contentDescription = "Zamknij wyszukiwanie", tint = dialer.dominik.pl.ui.theme.LocalMotyw.current.naTle.copy(alpha = 0.9f), modifier = Modifier.size(18.dp))
                }
            }
        }
    }

    // Dół czatu (planowanie / cytat / załącznik / pole pisania) — wjeżdża od dołu.
    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = alphaPaska
                translationY = przesunieciePaskaPx
            }
    ) {
        // Pasek informacyjny planowania.
        AnimatedVisibility(
            visible = planPodglad != null,
            enter = expandVertically(tween(200)) + fadeIn(tween(160)),
            exit = shrinkVertically(tween(160)) + fadeOut(tween(120))
        ) {
            Column {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clip(RoundedCornerShape(12.dp)).background(dialer.dominik.pl.ui.theme.LocalMotyw.current.gradientKafelka()).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    dialer.dominik.pl.ui.components.icons.IkonaZegaraCzasu(czasMs = planCzasMs ?: 0L, modifier = Modifier.size(16.dp), kolor = ZielonyNeon)
                    Spacer(Modifier.width(8.dp))
                    Text("Wiadomość zostanie wysłana: $planPodglad — kliknij wyślij, aby potwierdzić", color = dialer.dominik.pl.ui.theme.LocalMotyw.current.naTle.copy(alpha = 0.9f), fontSize = 12.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = { planCzasMs = null; planPodglad = null }, modifier = Modifier.size(28.dp)) {
                        Icon(IkonyAplikacji.Zamknij, contentDescription = "Anuluj plan", tint = CzerwonyPolaczenia, modifier = Modifier.size(16.dp))
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }

        // Podgląd cytowanej wiadomości (Odpowiedź)
        AnimatedVisibility(
            visible = cytowanaWiadomosc != null,
            enter = expandVertically(tween(200)) + fadeIn(tween(160)),
            exit = shrinkVertically(tween(160)) + fadeOut(tween(120))
        ) {
            val cytat = cytowanaWiadomosc ?: return@AnimatedVisibility
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(dialer.dominik.pl.ui.theme.LocalMotyw.current.kafelek)
                    .border(1.dp, ZielonyNeon.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text("Odpowiadasz na:", color = ZielonyNeon, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(cytat.body, color = dialer.dominik.pl.ui.theme.LocalMotyw.current.naTle.copy(alpha = 0.85f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { cytowanaWiadomosc = null }, modifier = Modifier.size(30.dp)) {
                    Icon(IkonyAplikacji.Zamknij, contentDescription = "Anuluj odpowiedź", tint = CzerwonyPolaczenia, modifier = Modifier.size(18.dp))
                }
            }
        }

        if (tresc.isNotBlank()) {
            val licznik = obliczLicznikSms(tresc)
            Text(
                text = licznik,
                color = ZielonyNeon.copy(alpha = 0.7f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.End).padding(end = 16.dp, bottom = 2.dp)
            )
        }

        // Pasek + menu spinacza: jeden Surface (jak Jetchat UserInput) —
        // wspólne tło/obramowanie, menu expandVertically w górę jako część paska.
        BackHandler(enabled = pokazWyborZalacznika) {
            pokazWyborZalacznika = false
        }
        val ksztaltPaska = RoundedCornerShape(16.dp)
        val kolorTlaPaska = dialer.dominik.pl.ui.theme.LocalMotyw.current.kafelek
        val kolorRamkiPaska = dialer.dominik.pl.ui.theme.LocalMotyw.current.kafelekRamka
        val pasekRozwiniety = pokazWyborZalacznika || maZalaczniki
        Column(
            Modifier
                .fillMaxWidth()
                .padding(4.dp, 6.dp, 4.dp, 12.dp)
                .clip(ksztaltPaska)
                .background(kolorTlaPaska)
                .border(1.dp, kolorRamkiPaska, ksztaltPaska)
        ) {
            AnimatedVisibility(
                visible = pokazWyborZalacznika,
                enter = expandVertically(
                    expandFrom = Alignment.Bottom,
                    animationSpec = tween(280, easing = FastOutSlowInEasing)
                ) + fadeIn(tween(200)),
                exit = shrinkVertically(
                    shrinkTowards = Alignment.Bottom,
                    animationSpec = tween(200, easing = FastOutSlowInEasing)
                ) + fadeOut(tween(160))
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    OpcjaZalacznika("Galeria", IkonyAplikacji.Galeria) {
                        pokazWyborZalacznika = false
                        launcherGaleria.launch("image/*")
                    }
                    OpcjaZalacznika("Zrób zdjęcie", IkonyAplikacji.Kamera) {
                        pokazWyborZalacznika = false
                        val plik = java.io.File(context.cacheDir, "images").apply { mkdirs() }
                        val cel = java.io.File(plik, "cam_${System.nanoTime()}.jpg")
                        tymczasowyZdjecieFile.value = cel
                        val uri = androidx.core.content.FileProvider.getUriForFile(
                            context, context.packageName + ".fileprovider", cel
                        )
                        launcherKamera.launch(uri)
                    }
                    OpcjaZalacznika("Szukaj w czacie", IkonyAplikacji.Lupa) {
                        pokazWyborZalacznika = false
                        trybSzukaniaCzat = true
                        szukaneWCzacie = ""
                        podswietloneId = null
                    }
                    if (!maZalaczniki) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 4.dp)
                                .height(1.dp)
                                .background(ZielonyNeon.copy(alpha = 0.12f))
                        )
                    }
                }
            }

            // Podgląd załączników — wysuwa się w pasku (expandVertically + scale miniatur).
            AnimatedVisibility(
                visible = maZalaczniki,
                enter = expandVertically(
                    expandFrom = Alignment.Bottom,
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                ) + fadeIn(tween(220)),
                exit = shrinkVertically(
                    shrinkTowards = Alignment.Bottom,
                    animationSpec = tween(220, easing = FastOutSlowInEasing)
                ) + fadeOut(tween(160))
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when (zalaczniki.size) {
                                1 -> "1 zdjęcie · MMS"
                                in 2..4 -> "${zalaczniki.size} zdjęcia · MMS"
                                else -> "${zalaczniki.size} zdjęć · MMS"
                            },
                            color = ZielonyNeon.copy(alpha = 0.75f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "Wyczyść",
                            color = CzerwonyPolaczenia.copy(alpha = 0.9f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { zalaczniki = emptyList() }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(zalaczniki, key = { it.klucz }) { z ->
                            MiniaturaZalacznika(
                                bitmap = z.podglad,
                                onKlik = { edytowanyZalacznikKlucz = z.klucz },
                                onUsun = { zalaczniki = zalaczniki.filterNot { it.klucz == z.klucz } }
                            )
                        }
                        if (zalaczniki.size < MAX_ZALACZNIKOW_SMS) {
                            item(key = "dodaj_kolejne") {
                                PrzyciskDodajZalacznik(
                                    onClick = { launcherGaleria.launch("image/*") }
                                )
                            }
                        }
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 2.dp)
                            .height(1.dp)
                            .background(ZielonyNeon.copy(alpha = 0.12f))
                    )
                }
            }

            Box(Modifier.fillMaxWidth().height(54.dp)) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxHeight()
                        .width(58.dp)
                        .clip(
                            if (pasekRozwiniety) RoundedCornerShape(bottomStart = 16.dp)
                            else RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
                        )
                        .then(
                            if (maZalaczniki) Modifier.background(
                                Brush.horizontalGradient(listOf(ZielonyMorski, ZielonyNeon))
                            ) else Modifier.background(ZielonyNeon.copy(alpha = 0.16f))
                        )
                        .combinedClickable(
                            onClick = { pokazWyborZalacznika = !pokazWyborZalacznika },
                            onLongClick = { pokazWyborZalacznika = true }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = IkonyAplikacji.Zlacznik,
                        contentDescription = "Dodaj załącznik",
                        tint = if (maZalaczniki) NaPrzycisku else ZielonyNeon,
                        modifier = Modifier.size(28.dp)
                    )
                }
                BasicTextField(
                    value = tresc,
                    onValueChange = {
                        if (pokazWyborZalacznika) pokazWyborZalacznika = false
                        tresc = zamienEmotikonNaKoncu(it)
                        zapiszDraftCzatu(context, adres, tresc)
                    },
                    textStyle = TextStyle(color = dialer.dominik.pl.ui.theme.LocalMotyw.current.naTle, fontSize = 15.sp),
                    cursorBrush = SolidColor(ZielonyNeon),
                    maxLines = 6,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxWidth()
                        .padding(start = 64.dp, end = 64.dp)
                        .padding(vertical = 6.dp),
                    decorationBox = { inner ->
                        if (tresc.isEmpty()) {
                            Text("Napisz wiadomość…", color = dialer.dominik.pl.ui.theme.LocalMotyw.current.naTle.copy(alpha = 0.32f), fontSize = 15.sp)
                        }
                        inner()
                    }
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(58.dp)
                        .clip(
                            if (pasekRozwiniety) RoundedCornerShape(bottomEnd = 16.dp)
                            else RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
                        )
                        .then(
                            if (tresc.isNotBlank() || maZalaczniki) Modifier.background(
                                Brush.horizontalGradient(listOf(ZielonyMorski, ZielonyNeon))
                            ) else Modifier.background(ZielonyNeon.copy(alpha = 0.18f))
                        )
                        .combinedClickable(
                            onClick = {
                                if (zalaczniki.isNotEmpty()) {
                                    val doWyslania = zalaczniki
                                    val tekst = tresc
                                    val noweWpisy = doWyslania.mapIndexed { index, z ->
                                        val klucz = System.nanoTime() + index
                                        // Nie przekazuj miniatury (512 px) — wyslijMms ładuje pełną jakość z URI.
                                        wyslijMms(
                                            context = context,
                                            numer = adres,
                                            contentUri = z.uri,
                                            tekst = if (index == 0) tekst else "",
                                            lokalnyKlucz = klucz
                                        )
                                        SmsWpis(
                                            id = -klucz,
                                            address = adres,
                                            body = when {
                                                index == 0 && tekst.isNotBlank() -> tekst
                                                else -> "📷 [MMS]"
                                            },
                                            date = System.currentTimeMillis() + index,
                                            type = Telephony.Sms.MESSAGE_TYPE_OUTBOX,
                                            read = true,
                                            bitmap = z.podglad,
                                            status = Telephony.Sms.STATUS_PENDING,
                                            lokalnyKlucz = klucz
                                        )
                                    }
                                    // reverseLayout: index 0 = dół czatu → lista musi być DESC po dacie
                                    wiadomosci = (wiadomosci + noweWpisy).sortedByDescending { it.date }
                                    uniewaznijCacheWatku(adres)
                                    usunDraftCzatu(context, adres)
                                    cytowanaWiadomosc = null
                                    tresc = ""
                                    zalaczniki = emptyList()
                                    onWiadomoscWyslana()
                                    przewinDoNajnowszej(animacja = true)
                                } else if (planCzasMs != null) {
                                    val czas = planCzasMs ?: 0L
                                    zaplanujWiadomosc(context, adres, tresc, czas)
                                    tresc = ""
                                    usunDraftCzatu(context, adres)
                                    planCzasMs = null
                                    planPodglad = null
                                    cytowanaWiadomosc = null
                                    onWiadomoscWyslana()
                                    odswiezPoAkcji++
                                } else if (tresc.isNotBlank()) {
                                    val cytat = cytowanaWiadomosc?.body
                                    val doWys = if (cytat != null) "\u201e" + cytat + "\u00bb\n\n" + tresc else tresc
                                    val klucz = System.nanoTime()
                                    val wynik = wyslijWiadomosc(
                                        context, adres, doWys, wybranaPlatforma, lokalnyKlucz = klucz
                                    )
                                    val wyslanyWpis = SmsWpis(
                                        id = wynik?.messageId ?: -klucz,
                                        address = adres,
                                        body = doWys,
                                        date = System.currentTimeMillis(),
                                        type = Telephony.Sms.MESSAGE_TYPE_OUTBOX,
                                        read = true,
                                        status = Telephony.Sms.STATUS_PENDING,
                                        lokalnyKlucz = klucz
                                    )
                                    wiadomosci = (wiadomosci + wyslanyWpis).sortedByDescending { it.date }
                                    uniewaznijCacheWatku(adres)
                                    tresc = ""
                                    usunDraftCzatu(context, adres)
                                    cytowanaWiadomosc = null
                                    onWiadomoscWyslana()
                                    przewinDoNajnowszej(animacja = true)
                                }
                            },
                            onLongClick = {
                                if (tresc.isNotBlank() && planCzasMs == null) pokazWyborCzasu = true
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (planCzasMs != null) {
                        dialer.dominik.pl.ui.components.icons.IkonaZegaraCzasu(
                            czasMs = planCzasMs,
                            modifier = Modifier.size(26.dp),
                            kolor = NaPrzycisku
                        )
                    } else {
                        Icon(
                            IkonyAplikacji.Strzalka,
                            contentDescription = "Wyślij",
                            tint = if (tresc.isNotBlank() || maZalaczniki) NaPrzycisku else ZielonyNeon,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }
    } // Column: dół czatu (animacja wejścia)
    } // Column: cały ekran czatu

    // Dialog planowania (zegar) — długie przytrzymanie.
    if (pokazWyborCzasu) {
        DialogPlanowaniaSms(
            context = context,
            numer = adres,
            tresc = tresc,
            onZaplanowano = { czasMs ->
                planCzasMs = czasMs
                planPodglad = java.text.SimpleDateFormat("dd.MM, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(czasMs))
                pokazWyborCzasu = false
            },
            onAnulowano = { pokazWyborCzasu = false }
        )
    }

    // Menu akcji na wiadomości (długie przytrzymanie).
    if (wybranyWpis != null) {
        val wpis = wybranyWpis!!
        val jestMms = wpis.bitmap != null || wpis.mmsMsgId != null
        val scope = rememberCoroutineScope()
        val paletaSheet = dialer.dominik.pl.ui.theme.LocalMotyw.current
        ModalBottomSheet(
            onDismissRequest = { wybranyWpis = null },
            containerColor = paletaSheet.tlo
        ) {
            Column(Modifier.padding(bottom = 24.dp)) {
                if (wpis.body.isNotBlank() && wpis.body != "📷 [MMS]" && wpis.body != "MMS") {
                    Text(wpis.body, color = paletaSheet.tekstDrugorzedny, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp), maxLines = 3, overflow = TextOverflow.Ellipsis)
                }

                if (jestMms) {
                    ListRowMenu("Zapisz w galerii", IkonyAplikacji.Galeria, czerwony = false) {
                        scope.launch(Dispatchers.IO) {
                            val bmpToSave = wpis.bitmap ?: wpis.mmsMsgId?.let { pobierzCzescObrazu(context, it) }
                            bmpToSave?.let {
                                withContext(Dispatchers.Main) {
                                    zapiszZdjecieDoGalerii(context, it)
                                }
                            }
                        }
                        wybranyWpis = null
                    }
                } else {
                    ListRowMenu("Kopiuj", IkonyAplikacji.Wiadomosc, czerwony = false) {
                        kopiujDoSchowka(context, wpis.body)
                        wybranyWpis = null
                    }
                    ListRowMenu("Odpowiedz", IkonyAplikacji.Gwiazda, czerwony = false) {
                        cytowanaWiadomosc = wpis
                        wybranyWpis = null
                    }
                }

                val jestPrzypiety = wpis.id.toString() in przypiete
                ListRowMenu(if (jestPrzypiety) "Odepnij" else "Przypnij", IkonyAplikacji.Gwiazda, czerwony = false) {
                    togglePin(context, adres, wpis.id, prefsCzatu)
                    wybranyWpis = null
                    odswiezPoAkcji++
                }
                ListRowMenu("Usuń", IkonyAplikacji.Kosz, czerwony = true) {
                    usunWiadomosc(context, wpis.id)
                    wybranyWpis = null
                    odswiezPoAkcji++
                }
            }
        }
    }

    // Edycja załącznika przed wysyłką (podgląd + przycięcie / strzałka / kwadrat / koło).
    edytowanyZalacznikKlucz?.let { klucz ->
        val z = zalaczniki.firstOrNull { it.klucz == klucz }
        if (z != null) {
            DialogEdycjiZalacznika(
                context = context,
                uri = z.uri,
                onAnuluj = { edytowanyZalacznikKlucz = null },
                onZapisz = { noweUri, podglad ->
                    zalaczniki = zalaczniki.map {
                        if (it.klucz == klucz) it.copy(uri = noweUri, podglad = podglad) else it
                    }
                    edytowanyZalacznikKlucz = null
                }
            )
        } else {
            edytowanyZalacznikKlucz = null
        }
    }

    // Pełny ekran zdjęcia MMS (z przybliżaniem, przyciskiem zapisu i obsługą przycisku Wstecz)
    if (pelnyEkranBmp != null) {
        val bmp = pelnyEkranBmp!!
        BackHandler {
            pelnyEkranBmp = null
        }

        var scale by remember { mutableStateOf(1f) }
        var offsetX by remember { mutableStateOf(0f) }
        var offsetY by remember { mutableStateOf(0f) }

        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        if (scale > 1f) {
                            offsetX += pan.x
                            offsetY += pan.y
                        } else {
                            offsetX = 0f
                            offsetY = 0f
                        }
                    }
                }
                .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) {
                    if (scale == 1f) pelnyEkranBmp = null
                },
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "MMS Pełny ekran",
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    )
                    .padding(16.dp),
                contentScale = ContentScale.Fit
            )

            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Przycisk Zapisz
                IconButton(
                    onClick = { zapiszZdjecieDoGalerii(context, bmp) },
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(IkonyAplikacji.Galeria, contentDescription = "Zapisz", tint = ZielonyNeon, modifier = Modifier.size(24.dp))
                }
                // Przycisk Zamknij (X)
                IconButton(
                    onClick = { pelnyEkranBmp = null },
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(IkonyAplikacji.Zamknij, contentDescription = "Zamknij", tint = dialer.dominik.pl.ui.theme.LocalMotyw.current.naTle, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

private const val MAX_ZALACZNIKOW_SMS = 10

private data class ZalacznikSms(
    val klucz: Long,
    val uri: android.net.Uri,
    val podglad: android.graphics.Bitmap
)

private fun dekodujPodgladZdjecia(
    context: Context,
    uri: android.net.Uri,
    maxBok: Int = 512
): android.graphics.Bitmap? {
    return try {
        android.graphics.BitmapFactory.decodeStream(context.contentResolver.openInputStream(uri))?.let { o ->
            val skala = minOf(1f, maxBok.toFloat() / maxOf(o.width, o.height))
            if (skala < 1f) {
                android.graphics.Bitmap.createScaledBitmap(
                    o, (o.width * skala).toInt(), (o.height * skala).toInt(), true
                )
            } else o
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

@Composable
private fun PrzyciskDodajZalacznik(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ZielonyNeon.copy(alpha = 0.10f))
            .border(1.dp, ZielonyNeon.copy(alpha = 0.28f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "+",
            color = ZielonyNeon,
            fontSize = 32.sp,
            fontWeight = FontWeight.Light,
            modifier = Modifier.offset(y = (-2).dp)
        )
    }
}

@Composable
private fun MiniaturaZalacznika(
    bitmap: android.graphics.Bitmap,
    onKlik: () -> Unit,
    onUsun: () -> Unit
) {
    var widoczna by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { widoczna = true }
    val skala by animateFloatAsState(
        targetValue = if (widoczna) 1f else 0.82f,
        animationSpec = tween(280, easing = FastOutSlowInEasing),
        label = "skalaMiniatury"
    )
    val alfa by animateFloatAsState(
        targetValue = if (widoczna) 1f else 0f,
        animationSpec = tween(220),
        label = "alfaMiniatury"
    )
    Box(
        Modifier
            .size(72.dp)
            .graphicsLayer {
                scaleX = skala
                scaleY = skala
                alpha = alfa
            }
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onKlik)
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Edytuj zdjęcie",
            modifier = Modifier
                .fillMaxSize()
                .border(1.dp, ZielonyNeon.copy(alpha = 0.22f), RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 4.dp, y = (-4).dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(Color(0xFF1A100F))
                .border(1.dp, CzerwonyPolaczenia.copy(alpha = 0.55f), CircleShape)
                .clickable(onClick = onUsun),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                IkonyAplikacji.Zamknij,
                contentDescription = "Usuń zdjęcie",
                tint = CzerwonyPolaczenia,
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

@Composable
private fun OpcjaZalacznika(tekst: String, ikona: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(ikona, contentDescription = null, tint = ZielonyNeon, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(tekst, color = dialer.dominik.pl.ui.theme.LocalMotyw.current.naTle, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

/**
 * Scala listę z Telephony z tym, co już widać w czacie:
 * - zachowuje lokalnyKlucz (dopasowanie broadcastów SENT/DELIVERED),
 * - nie cofa lepszego statusu (Dostarczono nie wraca do Wysłano),
 * - zostawia optymistyczne dymki jeszcze niewidoczne w bazie.
 */
internal fun scalWiadomosciCzatu(
    wpisyZBazy: List<SmsWpis>,
    lokalne: List<SmsWpis>
): List<SmsWpis> {
    val wynik = wpisyZBazy.map { zBazy ->
        val lokalny = lokalne.find { l ->
            l.id == zBazy.id ||
                (l.lokalnyKlucz != null && l.lokalnyKlucz == zBazy.lokalnyKlucz) ||
                (l.lokalnyKlucz != null && l.id == zBazy.id)
        }
        if (lokalny == null) zBazy
        else {
            val stanBaza = stanZKolumnSms(zBazy.type, zBazy.status)
            val stanLok = stanZKolumnSms(lokalny.type, lokalny.status)
            val bierzLokalnyStatus = priorytetStanu(stanLok) > priorytetStanu(stanBaza)
            zBazy.copy(
                lokalnyKlucz = lokalny.lokalnyKlucz ?: zBazy.lokalnyKlucz,
                type = if (bierzLokalnyStatus) lokalny.type else zBazy.type,
                status = if (bierzLokalnyStatus) lokalny.status else zBazy.status,
                bitmap = zBazy.bitmap ?: lokalny.bitmap,
                mmsMsgId = zBazy.mmsMsgId ?: lokalny.mmsMsgId
            )
        }
    }
    val pendingLokalne = lokalne.filter { w ->
        w.lokalnyKlucz != null &&
            stanZKolumnSms(w.type, w.status) == StanWysylki.WYSYLANIE &&
            wynik.none { b -> b.id == w.id || b.lokalnyKlucz == w.lokalnyKlucz }
    }
    return (wynik + pendingLokalne)
        .distinctBy { it.lokalnyKlucz?.let { k -> "L$k" } ?: "I${it.id}" }
        .sortedByDescending { it.date }
}

internal fun kopiujDoSchowka(context: Context, tekst: String) {
    try {
        val clip = ClipData.newPlainText("SMS", tekst)
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
    } catch (e: Exception) { e.printStackTrace() }
}

private fun usunWiadomosc(context: Context, id: Long) {
    try {
        val usuniete = context.contentResolver.delete(Telephony.Sms.CONTENT_URI, "_id = ?", arrayOf(id.toString()))
        // Odświeżenie robi LaunchedEffect(odswiezenie) po onWiadomoscWyslana/akcjach.
    } catch (e: Exception) { e.printStackTrace() }
}

private fun togglePin(context: Context, adres: String, id: Long, prefs: SharedPreferences) {
    val klucz = "przypiete_$adres"
    val zestaw = (prefs.getStringSet(klucz, emptySet()) ?: emptySet()).toMutableSet()
    if (!zestaw.remove(id.toString())) zestaw.add(id.toString())
    prefs.edit().putStringSet(klucz, zestaw).apply()
}
