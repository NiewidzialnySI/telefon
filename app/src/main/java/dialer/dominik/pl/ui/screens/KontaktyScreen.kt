package dialer.dominik.pl.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.telecom.TelecomManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dialer.dominik.pl.sms.KanalWiadomosci
import dialer.dominik.pl.sms.czyKanalDostepny
import dialer.dominik.pl.sms.nazwa
import dialer.dominik.pl.sms.pobierzKanal
import dialer.dominik.pl.sms.pobierzKontoKomunikatora
import dialer.dominik.pl.sms.pobierzNumerDoDzwonienia
import dialer.dominik.pl.sms.pobierzNumerDoWiadomosci
import dialer.dominik.pl.sms.zapamietajNumerDoDzwonienia
import dialer.dominik.pl.sms.zapamietajNumerDoWiadomosci
import dialer.dominik.pl.sms.wyslijPrzezKomunikator
import dialer.dominik.pl.sms.zapamietajKanal
import dialer.dominik.pl.sms.zapamietajKontoKomunikatora
import dialer.dominik.pl.ui.components.IkonyAplikacji
import dialer.dominik.pl.ui.theme.CzarnyAmoled
import dialer.dominik.pl.ui.theme.ZielonyMorski
import dialer.dominik.pl.ui.theme.ZielonyNeon
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Jeden numer telefonu przypisany do kontaktu.
 *
 * [normalizowany] służy do porównań i deduplikacji (sam numer bez formatowania),
 * [surowy] zachowuje zapis z książki telefonicznej do wyświetlania i dzwonienia.
 */
/**
 * Zakładka Kontakty — spójna z resztą aplikacji:
 * czarne tło AMOLED, ciemnozielone karty, neonowe akcenty.
 * Modele (NumerKontaktu, KontaktItem) i logika w LogikaKontaktow.kt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KontaktyScreen(
    context: Context,
    onBack: () -> Unit = {},
    onOpenSms: ((String) -> Unit)? = null,
    pokazPasekWyszukiwania: Boolean = true
) {
    val paletaKontakty = dialer.dominik.pl.ui.theme.LocalMotyw.current
    var kontakty by remember { mutableStateOf<List<KontaktItem>>(emptyList()) }
        var zapytanie by remember { mutableStateOf("") }
        // Debounce szukania — bez tego każde literowanie filtruje całą książkę na Main.
        var zapytanieDebounced by remember { mutableStateOf("") }
        var rozwiniete by remember { mutableStateOf(setOf<Long>()) }
        var odswiezenie by remember { mutableIntStateOf(0) }
        var edytowanyKontakt by remember { mutableStateOf<KontaktItem?>(null) }
        // Czy pokazać okno dodawania nowego kontaktu (przycisk „+" obok szukania).
        var pokazNowyKontakt by remember { mutableStateOf(false) }
        val prefsUlubione = remember { context.getSharedPreferences("kontakty_ulubione", Context.MODE_PRIVATE) }
        var ulubione by remember {
            mutableStateOf(prefsUlubione.getStringSet("nazwy", emptySet()) ?: emptySet())
        }

        val maKontakty = context.checkSelfPermission(Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

        LaunchedEffect(zapytanie) {
            kotlinx.coroutines.delay(180)
            zapytanieDebounced = zapytanie
        }

        LaunchedEffect(odswiezenie, maKontakty) {
            kontakty = if (maKontakty) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    pobierzKontakty(context)
                }
            } else {
                emptyList()
            }
        }

        // Kontakty są już zgrupowane po osobie (CONTACT_ID) i pozbawione duplikatów numerów
        // w [pobierzKontakty]. Tu zostaje wyszukiwanie + filtr ulubionych.
        val przefiltrowane = remember(kontakty, zapytanieDebounced) {
            if (zapytanieDebounced.isBlank()) {
                kontakty
            } else {
                val q = zapytanieDebounced.lowercase()
                val qCyfry = q.filter { c -> c.isDigit() }
                kontakty.filter { kontakt ->
                    kontakt.name.lowercase().contains(q) ||
                        kontakt.pseudonim?.lowercase()?.contains(q) == true ||
                        kontakt.firma?.lowercase()?.contains(q) == true ||
                        (qCyfry.isNotEmpty() && kontakt.numery.any {
                            it.normalizowany.contains(qCyfry)
                        })
                }
            }
        }

        // Pasek A–Z z boku: stan listy + podział kontaktów na sekcje po pierwszej literze.
        val stanListy = rememberLazyListState()
        val koro = rememberCoroutineScope()
        val haptyka = LocalHapticFeedback.current
        val sekcje = remember(przefiltrowane) {
            przefiltrowane
                .groupBy { k ->
                    k.nazwaWyswietlana.trim().firstOrNull()?.uppercaseChar() ?: '#'
                }
                .toSortedMap()
                .toList()
        }
        // Pozycja (index w LazyColumn) pierwszego elementu każdej sekcji — do przewijania.
        val pozycjeSekcji = remember(sekcje) {
            val m = mutableMapOf<Char, Int>()
            var idx = 0
            for ((litera, lista) in sekcje) {
                m[litera] = idx
                idx += 1 + lista.size
            }
            m
        }
        val litery = remember(sekcje) { sekcje.map { it.first } }

    Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = "KONTAKTY",
                            color = paletaKontakty.naTle.copy(alpha = 0.42f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 2.2.sp
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            // Pasek szukania przypięty na samym dole ekranu — tuż nad dolnym
            // paskiem zakładek, w zasięgu kciuka.
            bottomBar = {
                                        if (pokazPasekWyszukiwania && maKontakty && kontakty.isNotEmpty()) {
                                            // Pasek szukania — przypięty do dołu, bez własnego imePadding
                                            // (przesunięcie nad klawiaturę robi główny Box w MainActivity).
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                                    .clip(RoundedCornerShape(16.dp))
                                                    .background(dialer.dominik.pl.ui.theme.LocalMotyw.current.kafelek)
                                                    .border(1.dp, ZielonyNeon.copy(alpha = 0.14f), RoundedCornerShape(16.dp))
                                                    .padding(start = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                val kolorTekstuSzukaj =
                                                    dialer.dominik.pl.ui.theme.LocalMotyw.current.naTle
                                                BasicTextField(
                                                    value = zapytanie,
                                                    onValueChange = { zapytanie = it },
                                                    singleLine = true,
                                                    textStyle = TextStyle(color = kolorTekstuSzukaj, fontSize = 15.sp),
                                                    cursorBrush = SolidColor(ZielonyNeon),
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .padding(vertical = 12.dp),
                                                    decorationBox = { wewnetrzne ->
                                                        if (zapytanie.isEmpty()) {
                                                            Text(
                                                                text = "Szukaj kontaktu…",
                                                                color = kolorTekstuSzukaj.copy(alpha = 0.32f),
                                                                fontSize = 15.sp
                                                            )
                                                        }
                                                        wewnetrzne()
                                                    }
                                                )

                                                // Przycisk dodania kontaktu — dokładnie przylega do prawej
                                                // krawędzi pola (end-padding 0, przycisk wsuwa się w róg).
                                                Box(
                                                    modifier = Modifier
                                                        .size(48.dp)
                                                        .clip(RoundedCornerShape(0.dp))
                                                        .background(ZielonyNeon.copy(alpha = 0.18f))
                                                        .clickable { pokazNowyKontakt = true },
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
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(odstepy)
                ) {
                    when {
                !maKontakty -> {
                    KomunikatKontakty(
                        tytul = "Brak dostępu do kontaktów",
                        opis = "Nadaj uprawnienie „Kontakty”, żeby zobaczyć listę"
                    )
                }

                kontakty.isEmpty() -> {
                    KomunikatKontakty(
                        tytul = "Brak kontaktów",
                        opis = "W książce telefonicznej nie ma jeszcze żadnych wpisów"
                    )
                }

                przefiltrowane.isEmpty() -> {
                    KomunikatKontakty(
                        tytul = "Nic nie znaleziono",
                        opis = "Zmień frazę szukania"
                    )
                }

                else -> {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        LazyColumn(
                            state = stanListy,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 52.dp, top = 8.dp, bottom = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            for ((litera, grupa) in sekcje) {
                                item(
                                    key = "lit-$litera",
                                    contentType = "naglowek"
                                ) {
                                    NaglowekSekcji(litera = litera)
                                }
                                items(
                                    items = grupa,
                                    key = { it.contactId },
                                    contentType = { "kontakt" }
                                ) { kontakt ->
                                    val id = kontakt.contactId
                                    KartaKontaktu(
                                        kontakt = kontakt,
                                        context = context,
                                        rozwinieta = id in rozwiniete,
                                        ulubiony = kontakt.name in ulubione,
                                        onToggleUlubione = {
                                            val nowe = if (kontakt.name in ulubione) {
                                                ulubione - kontakt.name
                                            } else {
                                                ulubione + kontakt.name
                                            }
                                            ulubione = nowe
                                            prefsUlubione.edit().putStringSet("nazwy", nowe).apply()
                                        },
                                        onRozwin = {
                                            rozwiniete = if (id in rozwiniete) {
                                                rozwiniete - id
                                            } else {
                                                // Jedna rozwinięta karta — mniej layoutu przy scrollu.
                                                setOf(id)
                                            }
                                        },
                                        onZmianaPreferencji = { odswiezenie++ },
                                        onOpenSms = onOpenSms,
                                        onEdytuj = { edytowanyKontakt = kontakt }
                                    )
                                }
                            }
                        }
                        // Pionowy pasek A–Z po prawej krawędzi.
                        // Klik na literę = animacja do jej sekcji; przytrzymanie i
                        // przesuwanie palca (szorowanie) = płynne przewijanie listy
                        // + wyskakująca dymka z aktualną literą (jak w Kontaktach Google).
                        var literaGestu by remember { mutableStateOf<Char?>(null) }
                                                var czySzorujemy by remember { mutableStateOf(false) }
                                                var wysokoscPaska by remember { mutableIntStateOf(0) }
                                                // Pozycja palca na pasku (px od góry), kumulowana z delt dragu.
                                                var pozycjaPionowa by remember { mutableFloatStateOf(0f) }
                                                val literaZPozycji: (Float) -> Char? = { y ->
                                                    if (wysokoscPaska <= 0 || litery.isEmpty()) {
                                                        null
                                                    } else {
                                                        val indeks =
                                                            ((y / (wysokoscPaska.toFloat() / litery.size)).toInt())
                                                                .coerceIn(0, litery.size - 1)
                                                        litery[indeks]
                                                    }
                                                }
                                                // Lista par (pozycjaStartowa, litera) — do odczytu litery z indeksu listy.
                                                val sekcjePosortowane = remember(pozycjeSekcji) {
                                                    pozycjeSekcji.entries.sortedBy { it.value }.map { it.value to it.key }
                                                }
                                                // Litera sekcji, w której znajduje się item o podanym indeksie listy.
                                                // Dymka pokazuje literę zgodną z faktyczną pozycją listy (nie z palcem),
                                                // więc zawsze zgadza się z tym, co widzisz.
                                                val literaZIndeksuListy: (Int) -> Char? = { indeks ->
                                                    sekcjePosortowane.lastOrNull { it.first <= indeks }?.second
                                                }
                                                val gestPaska = Modifier.pointerInput(litery, pozycjeSekcji) {
                                                    // Współczynnik przelicza przesunięcie palca po pasku (piksele)
                                                    // na przewinięcie listy — żeby lista płynnie podążała za palcem
                                                    // 1:1 (jak w Kontaktach Google), a nie „skakała” między literami.
                                                    var roznica = 1f
                                                    detectVerticalDragGestures(
                                                        onDragStart = { pozycja ->
                                                            czySzorujemy = true
                                                            pozycjaPionowa = pozycja.y
                                                            pozycjeSekcji[literaZPozycji(pozycjaPionowa)]?.let { koro.launch { stanListy.scrollToItem(it) } }
                                                            // Współczynnik: pełna lista (tyle, ile jest itemów) podzielona
                                                            // przez wysokość paska — żeby szorowanie A→Z przeszło całą listę.
                                                            val info = stanListy.layoutInfo
                                                            if (info.totalItemsCount > 0 && wysokoscPaska > 0) {
                                                                roznica = info.totalItemsCount / (wysokoscPaska / 76f)
                                                            }
                                                            // Dymka pokazuje literę, przy której realnie jest lista.
                                                            literaGestu = literaZIndeksuListy(stanListy.firstVisibleItemIndex)
                                                            if (literaGestu != null) {
                                                                haptyka.performHapticFeedback(
                                                                    androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress
                                                                )
                                                            }
                                                        },
                                                        onVerticalDrag = { _, dragAmount ->
                                                            czySzorujemy = true
                                                            pozycjaPionowa = (pozycjaPionowa + dragAmount).coerceIn(0f, wysokoscPaska.toFloat())
                                                            // Przewijanie 1:1 z palcem po pasku (płynne, bez skoków).
                                                            val delta = dragAmount * roznica
                                                            if (delta != 0f) stanListy.dispatchRawDelta(delta)
                                                            // Dymka = faktyczna pozycja listy (zgodnie z tym, co widzisz).
                                                            val nowaLitera = literaZIndeksuListy(stanListy.firstVisibleItemIndex)
                                                            if (nowaLitera != null && nowaLitera != literaGestu) {
                                                                literaGestu = nowaLitera
                                                                haptyka.performHapticFeedback(
                                                                    androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress
                                                                )
                                                            }
                                                        },
                                                        onDragEnd = { czySzorujemy = false },
                                                        onDragCancel = { czySzorujemy = false }
                                                    )
                                                }
                        Column(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 6.dp)
                                .onSizeChanged { wysokoscPaska = it.height }
                                .then(gestPaska),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            litery.forEach { litera ->
                                Text(
                                    text = litera.toString(),
                                    color = if (literaGestu == litera)
                                        ZielonyMorski else ZielonyNeon,
                                    fontSize = 11.sp,
                                    fontWeight = if (literaGestu == litera)
                                        FontWeight.Bold else FontWeight.SemiBold,
                                    modifier = Modifier
                                        .clickable {
                                            haptyka.performHapticFeedback(
                                                androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress
                                            )
                                            pozycjeSekcji[litera]?.let {
                                                koro.launch { stanListy.animateScrollToItem(it) }
                                            }
                                        }
                                        .padding(horizontal = 4.dp, vertical = 3.dp)
                                )
                            }
                        }
                        // Wyskakująca dymka z literą podczas szorowania — na środku ekranu.
                        val skalaDymki by animateFloatAsState(
                            targetValue = if (czySzorujemy && literaGestu != null) 1f else 0f,
                            animationSpec = tween(150),
                            label = "skalaDymki"
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .size(76.dp)
                                .scale(skalaDymki)
                                .clip(RoundedCornerShape(24.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(ZielonyMorski.copy(alpha = 0.9f), Color(0xFF0D1611))
                                    )
                                )
                                .border(
                                    1.dp,
                                    ZielonyNeon.copy(alpha = 0.5f),
                                    RoundedCornerShape(24.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = (literaGestu ?: '?').toString(),
                                color = ZielonyNeon,
                                fontSize = 40.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        }
                        }
                    }
                    }
                    }

    if (edytowanyKontakt != null) {
            DialogEdytujKontakt(
                context = context,
                kontakt = edytowanyKontakt!!,
                onDismiss = { edytowanyKontakt = null },
                onZapisano = {
                    // Po edycji kontaktu unieważnij cache, żeby odświeżenie
                    // pobrało świeże dane (inaczej cache 30s zwraca stare — zmiany
                    // widoczne dopiero po wyjściu i ponownym wejściu w aplikację).
                    uniewaznijCacheKontaktow()
                    odswiezenie++
                }
            )
        }

        // Okno dodawania nowego kontaktu (przycisk „+" obok szukania).
        if (pokazNowyKontakt) {
            DialogDodajKontakt(
                context = context,
                domyslnyNumer = "",
                onDismiss = { pokazNowyKontakt = false },
                onZapisano = {
                    pokazNowyKontakt = false
                    // Nowy kontakt musi pojawić się na liście → unieważnij cache.
                    uniewaznijCacheKontaktow()
                    odswiezenie++
                }
            )
        }
    }

/** Przyklejony nagłówek sekcji A–Z nad grupą kontaktów. */

@Composable
private fun NaglowekSekcji(litera: Char) {
                    Text(
                        text = litera.toString(),
                        color = ZielonyNeon.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp, bottom = 2.dp)
                    )
                }

                /** Karta pojedynczego kontaktu — rozwija się do wyboru kanału wiadomości. */
