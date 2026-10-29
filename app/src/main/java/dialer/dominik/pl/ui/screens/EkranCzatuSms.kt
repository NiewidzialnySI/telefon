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
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import dialer.dominik.pl.sms.OdbiorcaSms.Companion.kopiujDoSchowka
import dialer.dominik.pl.sms.pobierzKanal
import dialer.dominik.pl.ui.components.IkonyAplikacji
import dialer.dominik.pl.ui.theme.CzerwonyPolaczenia
import dialer.dominik.pl.ui.theme.ZielonyMorski
import dialer.dominik.pl.ui.theme.ZielonyNeon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    szukanaTresc: String? = null
) {
    val adres = watek.address
    val wybranaPlatforma = pobierzKanal(context, adres)

    // Roboczy czat (draft) startuje z ostatniej treści.
    var tresc by remember { mutableStateOf(pobierzDraftCzatu(context, adres) ?: "") }
    var wiadomosci by remember(adres) { mutableStateOf<List<SmsWpis>>(emptyList()) }

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

    // Załącznik zdjęcia (MMS).
    var zalacznikUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var zalacznikPodglad by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var pokazWyborZalacznika by remember { mutableStateOf(false) }
    val tymczasowyZdjecieFile = remember { mutableStateOf<java.io.File?>(null) }

    // Wybór z galerii / kamery.
    val launcherGaleria = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val bmp = try {
                android.graphics.BitmapFactory.decodeStream(context.contentResolver.openInputStream(it))?.let { o ->
                    val skala = minOf(1f, 1024f / maxOf(o.width, o.height))
                    if (skala < 1f) android.graphics.Bitmap.createScaledBitmap(o, (o.width * skala).toInt(), (o.height * skala).toInt(), true) else o
                }
            } catch (e: Exception) { e.printStackTrace(); null }
            if (bmp != null) { zalacznikUri = przygotujZdjecieDoMms(context, it); zalacznikPodglad = bmp }
            else Toast.makeText(context, "Nie udało się dodać zdjęcia", Toast.LENGTH_SHORT).show()
        }
    }
    val launcherKamera = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.TakePicture()
    ) { ok ->
        if (ok) {
            val plik = tymczasowyZdjecieFile.value
            if (plik != null) {
                val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".fileprovider", plik)
                val bmp = try {
                    android.graphics.BitmapFactory.decodeStream(context.contentResolver.openInputStream(uri))?.let { o ->
                        val skala = minOf(1f, 1024f / maxOf(o.width, o.height))
                        if (skala < 1f) android.graphics.Bitmap.createScaledBitmap(o, (o.width * skala).toInt(), (o.height * skala).toInt(), true) else o
                    }
                } catch (e: Exception) { e.printStackTrace(); null }
                if (bmp != null) { zalacznikUri = uri; zalacznikPodglad = bmp }
                else Toast.makeText(context, "Nie udało się dodać zdjęcia", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Przypięte (pin) wiadomości — zestaw id z prefs.
    val przypieteSet = prefsCzatu.getStringSet("przypiete_$adres", emptySet()) ?: emptySet()
    var przypiete by remember(adres, odswiezPoAkcji) {
        mutableStateOf(przypieteSet)
    }

    // Ładowanie wiadomości (IO) + czytanie + planowane.
    LaunchedEffect(adres, odswiezPoAkcji) {
        oznaczWatekJakoPrzeczytany(context, adres)
        val wpisy = withContext(Dispatchers.IO) { 
            if (odswiezPoAkcji > 0) uniewaznijCacheSkrzynki()
            pobierzWiadomosciDlaAdresu(context, adres) 
        }
        wiadomosci = wpisy.distinctBy { "${it.id}_${it.type}_${it.date}" }
        planowane = pobierzPlanowaneSms(context, adres)
    }
    LaunchedEffect(Unit) {
        // Czat otwarty — ukrywa dolny pasek (MainActivity).
    }

    // Nasłuchiwanie zmian w bazie SMS/MMS w czasie rzeczywistym (Live Updates)
    DisposableEffect(adres) {
        val observer = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: android.net.Uri?) {
                super.onChange(selfChange, uri)
                odswiezPoAkcji++
            }
        }
        context.contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, observer)
        context.contentResolver.registerContentObserver(android.provider.Telephony.Mms.CONTENT_URI, true, observer)
        onDispose {
            context.contentResolver.unregisterContentObserver(observer)
        }
    }

    Column(
        Modifier.fillMaxSize().padding(vertical = 4.dp).windowInsetsPadding(WindowInsets.ime)
    ) {
        // Nagłówek (tylko w czacie-w-dymku / BubbleChatActivity).
        if (pokazNaglowek) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(watek.contactName ?: adres, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(pobierzKanal(context, adres).name, color = ZielonyNeon, fontSize = 12.sp)
            }
        }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var aktualnyPinIndeks by remember { mutableIntStateOf(0) }

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
    if (przypisaneWpisy.isNotEmpty()) {
        val bezpiecznyIndeks = aktualnyPinIndeks.coerceIn(0, przypisaneWpisy.size - 1)
        val pinWpis = przypisaneWpisy[bezpiecznyIndeks]

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.horizontalGradient(listOf(Color(0xFF0F2A1F), Color(0xFF072018))))
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
                    color = Color.White.copy(alpha = 0.9f),
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
    if (trybSzukaniaCzat) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(14.dp)).background(Color(0xFF131C18))
                .border(1.dp, ZielonyNeon.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                .padding(start = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(IkonyAplikacji.Lupa, contentDescription = null, tint = ZielonyNeon, modifier = Modifier.size(18.dp))
            BasicTextField(
                value = szukaneWCzacie,
                onValueChange = { szukaneWCzacie = it },
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                cursorBrush = SolidColor(ZielonyNeon),
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 12.dp).focusRequester(poleSzukaniaFocus),
                decorationBox = { inner ->
                    if (szukaneWCzacie.isEmpty()) Text("Szukaj w tej rozmowie…", color = Color.White.copy(alpha = 0.35f), fontSize = 15.sp)
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
                Icon(IkonyAplikacji.Zamknij, contentDescription = "Zamknij", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
            }
        }
    }

    // Lista wiadomości + przezroczysta nakładka nawigacji po trafieniach (nie odpycha treści).
    Box(Modifier.weight(1f).fillMaxWidth()) {
        // Lista wiadomości (pull-to-refresh: przeciągnij w dół, by przeładować WSZYSTKIE).
        LazyColumn(
            state = listState,
            modifier = Modifier.matchParentSize()
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
                    state = rememberPullToRefreshState()
                ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            reverseLayout = true
        ) {
            // Planowane (na górze, wyróżnione).
            items(planowane, key = { "pl_" + it.second }) { (_, czas, trescPl) ->
                val podgladCzasu = java.text.SimpleDateFormat("dd.MM, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(czas))
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF0F2A1F)).border(1.dp, ZielonyNeon.copy(alpha = 0.2f), RoundedCornerShape(14.dp)).padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        dialer.dominik.pl.ui.components.icons.IkonaZegaraCzasu(czasMs = czas, modifier = Modifier.size(18.dp), kolor = ZielonyNeon)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Zaplanowano: $podgladCzasu", color = ZielonyNeon, fontSize = 11.sp)
                            Text(trescPl, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            itemsIndexed(wiadomosci, key = { _, w -> "${w.id}_${w.type}_${w.date}" }) { idx, wpis ->
                KartaWiadomosciNowoczesna(
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
                    Icon(IkonyAplikacji.Zamknij, contentDescription = "Zamknij wyszukiwanie", tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(18.dp))
                }
            }
        }
    }

                // Pasek informacyjny planowania.
        if (planPodglad != null) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clip(RoundedCornerShape(12.dp)).background(Brush.horizontalGradient(listOf(Color(0xFF06231A), Color(0xFF0A2E22)))).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                dialer.dominik.pl.ui.components.icons.IkonaZegaraCzasu(czasMs = planCzasMs ?: 0L, modifier = Modifier.size(16.dp), kolor = ZielonyNeon)
                Spacer(Modifier.width(8.dp))
                Text("Wiadomość zostanie wysłana: $planPodglad — kliknij wyślij, aby potwierdzić", color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = { planCzasMs = null; planPodglad = null }, modifier = Modifier.size(28.dp)) {
                    Icon(IkonyAplikacji.Zamknij, contentDescription = "Anuluj plan", tint = CzerwonyPolaczenia, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        // Podgląd cytowanej wiadomości (Odpowiedź)
        cytowanaWiadomosc?.let { cytat ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF111C17))
                    .border(1.dp, ZielonyNeon.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text("Odpowiadasz na:", color = ZielonyNeon, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(cytat.body, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { cytowanaWiadomosc = null }, modifier = Modifier.size(30.dp)) {
                    Icon(IkonyAplikacji.Zamknij, contentDescription = "Anuluj odpowiedź", tint = CzerwonyPolaczenia, modifier = Modifier.size(18.dp))
                }
            }
        }

        // Podgląd załącznika (zdjęcie do MMS).
        zalacznikPodglad?.let { bmp ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF111C17)).border(1.dp, ZielonyNeon.copy(alpha = 0.2f), RoundedCornerShape(14.dp)).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(bitmap = bmp.asImageBitmap(), contentDescription = "Załączone zdjęcie", modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                Spacer(Modifier.width(10.dp))
                Text("Zdjęcie — wyślesz jako MMS", color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = { zalacznikUri = null; zalacznikPodglad = null }, modifier = Modifier.size(30.dp)) {
                    Icon(IkonyAplikacji.Zamknij, contentDescription = "Usuń załącznik", tint = CzerwonyPolaczenia, modifier = Modifier.size(18.dp))
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

        // Pasek wpisywania z przyciskiem załącznika — przyciski przyklejone do zaokrąglonych
        // rogów kontenera (wypełniają całą wysokość), wspólny kształt paska.
// Pasek wpisywania — Box z przyciskami przyklejonymi do zaokrąglonych rogów i wypełniającymi pełną wysokość.
        Box(
            Modifier.fillMaxWidth().padding(4.dp, 6.dp, 4.dp, 12.dp).height(54.dp)
                .clip(RoundedCornerShape(16.dp)).background(Color(0xFF131C18))
                .border(1.dp, ZielonyNeon.copy(alpha = 0.14f), RoundedCornerShape(16.dp))
        ) {
            // Przycisk załącznika — przyklejony do LEWEGO rogu, pełna wysokość.
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(58.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                    .then(if (zalacznikUri != null) Modifier.background(Brush.horizontalGradient(listOf(ZielonyMorski, ZielonyNeon))) else Modifier.background(ZielonyNeon.copy(alpha = 0.16f)))
                    .combinedClickable(
                        onClick = { pokazWyborZalacznika = true },
                        onLongClick = { pokazWyborZalacznika = true }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = IkonyAplikacji.Zlacznik,
                    contentDescription = "Dodaj załącznik",
                    tint = if (zalacznikUri != null) Color(0xFF06231A) else ZielonyNeon,
                    modifier = Modifier.size(28.dp)
                )
                // Menu opcji załącznika — „wysuwa się" z paska wpisywania w górę:
                // wspólny kolor/ramka z paskiem, dolne rogi proste, płynne rozwijanie.
                if (pokazWyborZalacznika) {
                    Popup(
                        alignment = Alignment.TopStart,
                        offset = IntOffset(0, -400),
                        properties = PopupProperties(usePlatformDefaultWidth = false, focusable = false),
                        onDismissRequest = { pokazWyborZalacznika = false }
                    ) {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = pokazWyborZalacznika,
                            enter = expandVertically(
                                expandFrom = Alignment.Bottom,
                                animationSpec = tween(220, easing = FastOutSlowInEasing)
                            ) + fadeIn(tween(180)),
                            exit = shrinkVertically(
                                shrinkTowards = Alignment.Bottom,
                                animationSpec = tween(160)
                            ) + androidx.compose.animation.fadeOut(tween(140))
                        ) {
                            Column(
                                Modifier
                                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                                    .background(Color(0xFF131C18))
                                    .border(1.dp, ZielonyNeon.copy(alpha = 0.2f), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                                    .padding(vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                OpcjaZalacznika("Galeria", IkonyAplikacji.Galeria) {
                                    pokazWyborZalacznika = false
                                    launcherGaleria.launch("image/*")
                                }
                                OpcjaZalacznika("Zrób zdjęcie", IkonyAplikacji.Kamera) {
                                    pokazWyborZalacznika = false
                                    val plik = java.io.File(context.cacheDir, "images").apply { mkdirs() }
                                    val cel = java.io.File(plik, "cam_" + System.currentTimeMillis() + ".jpg")
                                    tymczasowyZdjecieFile.value = cel
                                    val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".fileprovider", cel)
                                    launcherKamera.launch(uri)
                                }
                                // Wyszukiwanie w bieżącym czacie — otwiera pasek szukania nad listą.
                                OpcjaZalacznika("Szukaj w czacie", IkonyAplikacji.Lupa) {
                                    pokazWyborZalacznika = false
                                    trybSzukaniaCzat = true
                                    szukaneWCzacie = ""
                                    podswietloneId = null
                                }
                            }
                        }
                    }
                }
            }
            // Pole wpisywania — odsunięte od obu przycisków.
            BasicTextField(
                value = tresc,
                onValueChange = {
                    tresc = zamienEmotikonNaKoncu(it)
                    zapiszDraftCzatu(context, adres, tresc)
                },
                textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                cursorBrush = SolidColor(ZielonyNeon),
                maxLines = 6,
                modifier = Modifier.align(Alignment.CenterStart).fillMaxWidth().padding(start = 64.dp, end = 64.dp).padding(vertical = 6.dp),
                decorationBox = { inner ->
                    if (tresc.isEmpty()) Text("Napisz wiadomość…", color = Color.White.copy(alpha = 0.32f), fontSize = 15.sp)
                    inner()
                }
            )
            // Przycisk wysyłki — przyklejony do PRAWEGO rogu, pełna wysokość.
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(58.dp)
                    .clip(RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp))
                    .then(if (tresc.isNotBlank() || zalacznikUri != null) Modifier.background(Brush.horizontalGradient(listOf(ZielonyMorski, ZielonyNeon))) else Modifier.background(ZielonyNeon.copy(alpha = 0.18f)))
                    .combinedClickable(
                        onClick = {
                            if (zalacznikUri != null) {
                                // MMS — wysyłka zdjęcia (+ tekst jako osobny SMS).
                                val bmp = zalacznikPodglad
                                zalacznikUri?.let { uri -> wyslijMms(context, adres, uri, tresc) }
                                bmp?.let { pic ->
                                    val wyslanyMmsWpis = SmsWpis(
                                        id = -System.currentTimeMillis(),
                                        address = adres,
                                        body = if (tresc.isNotBlank()) tresc else "📷 [MMS]",
                                        date = System.currentTimeMillis(),
                                        type = Telephony.Sms.MESSAGE_TYPE_SENT,
                                        read = true,
                                        bitmap = pic
                                    )
                                    wiadomosci = wiadomosci + wyslanyMmsWpis
                                    SkrzynkaCache.wpisy = (SkrzynkaCache.wpisy ?: emptyList()) + wyslanyMmsWpis
                                }
                                usunDraftCzatu(context, adres)
                                cytowanaWiadomosc = null
                                tresc = ""
                                zalacznikUri = null
                                zalacznikPodglad = null
                                onWiadomoscWyslana()
                                odswiezPoAkcji++
                            } else if (planCzasMs != null) {
                                // Potwierdzenie zaplanowanej wysyłki.
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
                                                            wyslijWiadomosc(context, adres, doWys, wybranaPlatforma)
                                                            // Pokaż wysłany SMS NATYCHMIAST (lokalny wpis + dopisz do cache).
                                                            val wyslanyWpis = SmsWpis(
                                                                id = -System.currentTimeMillis(),
                                                                address = adres,
                                                                body = doWys,
                                                                date = System.currentTimeMillis(),
                                                                type = Telephony.Sms.MESSAGE_TYPE_SENT,
                                                                read = true
                                                            )
                                                            wiadomosci = wiadomosci + wyslanyWpis
                                                            SkrzynkaCache.wpisy = (SkrzynkaCache.wpisy ?: emptyList()) + wyslanyWpis
                                                            tresc = ""
                                                            usunDraftCzatu(context, adres)
                                                            cytowanaWiadomosc = null
                                                            onWiadomoscWyslana()
                                                            odswiezPoAkcji++
                                                        }
                        },
                        onLongClick = {
                            if (tresc.isNotBlank() && planCzasMs == null) pokazWyborCzasu = true
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (planCzasMs != null) {
                    dialer.dominik.pl.ui.components.icons.IkonaZegaraCzasu(czasMs = planCzasMs, modifier = Modifier.size(26.dp), kolor = Color(0xFF06231A))
                } else {
                    Icon(IkonyAplikacji.Strzalka, contentDescription = "Wyślij", tint = if (tresc.isNotBlank() || zalacznikUri != null) Color(0xFF06231A) else ZielonyNeon, modifier = Modifier.size(26.dp))
                }
            }
        }
    }

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
        ModalBottomSheet(
            onDismissRequest = { wybranyWpis = null },
            containerColor = Color(0xFF0B120F)
        ) {
            Column(Modifier.padding(bottom = 24.dp)) {
                if (wpis.body.isNotBlank() && wpis.body != "📷 [MMS]" && wpis.body != "MMS") {
                    Text(wpis.body, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp), maxLines = 3, overflow = TextOverflow.Ellipsis)
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
                    Icon(IkonyAplikacji.Zamknij, contentDescription = "Zamknij", tint = Color.White, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

internal enum class SpanType { LINK, PHONE, CODE }

internal data class TextSpan(
    val start: Int,
    val end: Int,
    val type: SpanType,
    val value: String
)

internal fun parseTextSpans(text: String): List<TextSpan> {
    val spans = mutableListOf<TextSpan>()
    
    // 1. URLs
    val urlMatcher = android.util.Patterns.WEB_URL.matcher(text)
    while (urlMatcher.find()) {
        spans.add(TextSpan(urlMatcher.start(), urlMatcher.end(), SpanType.LINK, urlMatcher.group()))
    }
    
    // 2. Phones
    val phoneMatcher = android.util.Patterns.PHONE.matcher(text)
    while (phoneMatcher.find()) {
        val start = phoneMatcher.start()
        val end = phoneMatcher.end()
        if (spans.none { start < it.end && end > it.start }) {
            val phone = phoneMatcher.group()
            if (phone.length >= 7) {
                spans.add(TextSpan(start, end, SpanType.PHONE, phone))
            }
        }
    }
    
    // 3. Verification codes (4 to 8 digits)
    val codeRegex = Regex("\\b\\d{4,8}\\b")
    codeRegex.findAll(text).forEach { match ->
        val start = match.range.first
        val end = match.range.last + 1
        if (spans.none { start < it.end && end > it.start }) {
            spans.add(TextSpan(start, end, SpanType.CODE, match.value))
        }
    }
    
    return spans.sortedBy { it.start }
}

/** Karta (dymek) wiadomości nowoczesnej: przychodząca ciemna, wychodząca zielony gradient. */
@Composable
private fun KartaWiadomosciNowoczesna(
    wpis: SmsWpis,
    onLongPress: () -> Unit,
    przypieta: Boolean,
    onImageClick: (android.graphics.Bitmap) -> Unit,
    podswietlona: Boolean = false,
    podswietlonaGlowna: Boolean = false,
    szukanaFraza: String? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val wychodzaca = wpis.type == android.provider.Telephony.Sms.MESSAGE_TYPE_SENT

    // Pulsująca ramka dla AKTUALNego trafienia wyszukiwania.
    val ramkaAlpha by animateFloatAsState(
        targetValue = if (podswietlonaGlowna) 1f else 0f,
        animationSpec = if (podswietlonaGlowna) infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse
        ) else tween(0),
        label = "highlightPulse"
    )

    // Leniwe ładowanie bitmapy MMS dla widocznego kafelka na ekranie
    var mmsBitmap by remember(wpis.id) { mutableStateOf(wpis.bitmap) }
    LaunchedEffect(wpis.mmsMsgId) {
        if (mmsBitmap == null && wpis.mmsMsgId != null) {
            mmsBitmap = withContext(Dispatchers.IO) {
                pobierzCzescObrazu(context, wpis.mmsMsgId)
            }
        }
    }

    val hasText = wpis.body.isNotBlank() && wpis.body != "📷 [MMS]" && wpis.body != "MMS"
    val hasImage = mmsBitmap != null || wpis.mmsMsgId != null
    if (!hasText && !hasImage) return

    val czasFormat = remember(wpis.date) {
        val format = if (android.text.format.DateUtils.isToday(wpis.date)) {
            java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        } else {
            java.text.SimpleDateFormat("dd.MM.yyyy, HH:mm", java.util.Locale.getDefault())
        }
        format.format(java.util.Date(wpis.date))
    }

    val annotatedString = remember(wpis.body, podswietlona, szukanaFraza) {
        val spans = parseTextSpans(wpis.body)
        androidx.compose.ui.text.buildAnnotatedString {
            var lastIdx = 0
            for (span in spans) {
                if (span.start > lastIdx) {
                    append(wpis.body.substring(lastIdx, span.start))
                }
                val urlOrAction = when (span.type) {
                    SpanType.LINK -> {
                        var u = span.value
                        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://$u"
                        u
                    }
                    SpanType.PHONE -> "tel:${span.value}"
                    SpanType.CODE -> "code:${span.value}"
                }

                pushLink(
                    androidx.compose.ui.text.LinkAnnotation.Url(
                        url = urlOrAction,
                        styles = androidx.compose.ui.text.TextLinkStyles(
                            style = androidx.compose.ui.text.SpanStyle(
                                color = if (wychodzaca) Color(0xFF033023) else ZielonyNeon,
                                textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                                fontWeight = FontWeight.Medium
                            )
                        ),
                        linkInteractionListener = { link ->
                            val item = (link as? androidx.compose.ui.text.LinkAnnotation.Url)?.url ?: ""
                            if (item.startsWith("tel:")) {
                                val phone = item.removePrefix("tel:")
                                dialer.dominik.pl.AppNavigationState.docelowaZakladka = dialer.dominik.pl.Zakladka.Glowny
                                dialer.dominik.pl.AppNavigationState.numerDoWybrania = phone
                            } else if (item.startsWith("code:")) {
                                val code = item.removePrefix("code:")
                                kopiujDoSchowka(context, code)
                                Toast.makeText(context, "Skopiowano kod: $code", Toast.LENGTH_SHORT).show()
                            } else {
                                try {
                                    uriHandler.openUri(item)
                                } catch (_: Exception) {
                                    Toast.makeText(context, "Nie można otworzyć linku", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                )
                append(wpis.body.substring(span.start, span.end))
                pop()
                lastIdx = span.end
            }
            if (lastIdx < wpis.body.length) {
                append(wpis.body.substring(lastIdx))
            }
            // Wyróżnij wyszukiwaną frazę bursztynowym kolorem (tylko w aktualnym trafieniu).
            val fraza = szukanaFraza?.trim().orEmpty()
            if (fraza.isNotEmpty()) {
                val frazaL = fraza.lowercase()
                val cialoL = wpis.body.lowercase()
                var od = 0
                while (true) {
                    val idx = cialoL.indexOf(frazaL, startIndex = od)
                    if (idx == -1) break
                    addStyle(
                        androidx.compose.ui.text.SpanStyle(
                            color = Color(0xFFFFD54F),
                            fontWeight = FontWeight.Bold,
                            background = androidx.compose.ui.graphics.Color(0x33FFEB3B)
                        ),
                        idx,
                        idx + fraza.length
                    )
                    od = idx + fraza.length
                }
            }
        }
    }

    Box(Modifier.fillMaxWidth(), contentAlignment = if (wychodzaca) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(
            horizontalAlignment = if (wychodzaca) Alignment.End else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = czasFormat,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Column(
                Modifier
                    .widthIn(max = 300.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .then(
                        if (wychodzaca)
                            Modifier.background(Brush.horizontalGradient(listOf(ZielonyMorski, ZielonyNeon)))
                        else
                            Modifier.background(Brush.verticalGradient(listOf(Color(0xFF131C18), Color(0xFF0B120F))))
                    )
                    .then(
                        if (podswietlonaGlowna) Modifier.border(2.dp, ZielonyNeon.copy(alpha = ramkaAlpha), RoundedCornerShape(18.dp))
                        else if (podswietlona) Modifier.border(1.5.dp, ZielonyNeon.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
                        else if (przypieta && wychodzaca) Modifier.border(1.dp, ZielonyNeon.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
                        else if (przypieta) Modifier.border(1.dp, ZielonyNeon.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
                        else Modifier
                    )
                    .combinedClickable(
                        onClick = {
                            mmsBitmap?.let { bmp ->
                                onImageClick(bmp)
                            }
                        },
                        onLongClick = onLongPress
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                mmsBitmap?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "MMS",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
                if (hasText) {
                    Text(
                        text = annotatedString,
                        // Podświetlenie rozjaśnia tło — wybielamy tekst, by był czytelny.
                        color = when {
                            podswietlona -> Color.White
                            wychodzaca -> Color(0xFF06231A)
                            else -> Color.White
                        },
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

private fun zapiszZdjecieDoGalerii(context: Context, bitmap: android.graphics.Bitmap) {
    try {
        val filename = "MMS_${System.currentTimeMillis()}.jpg"
        val resolver = context.contentResolver
        val contentValues = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/TelefonMMS")
        }
        val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        val fos = uri?.let { resolver.openOutputStream(it) }
        fos?.use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 100, it)
            Toast.makeText(context, "Zapisano zdjęcie w Galerii", Toast.LENGTH_SHORT).show()
        } ?: Toast.makeText(context, "Błąd zapisu zdjęcia", Toast.LENGTH_SHORT).show()
    } catch (_: Exception) {
        Toast.makeText(context, "Błąd zapisu zdjęcia", Toast.LENGTH_SHORT).show()
    }
}

/** Pojedynczy wiersz w menu akcji wiadomości. */
@Composable
private fun ListRowMenu(tekst: String, ikona: androidx.compose.ui.graphics.vector.ImageVector, czerwony: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 22.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(ikona, contentDescription = null, tint = if (czerwony) CzerwonyPolaczenia else Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(tekst, color = if (czerwony) CzerwonyPolaczenia else Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

/** Pojedyncza opcja w menu załącznika — bez tła (przezroczyste), tylko ikona + tekst. */
@Composable
private fun OpcjaZalacznika(tekst: String, ikona: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(ikona, contentDescription = null, tint = ZielonyNeon, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(tekst, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

private fun kopiujDoSchowka(context: Context, tekst: String) {
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
