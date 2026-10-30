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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import dialer.dominik.pl.ui.theme.NaPrzycisku
import dialer.dominik.pl.ui.theme.ZielonyNeon
import dialer.dominik.pl.ui.theme.gradientKafelka
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

                @Composable
                internal fun KartaKontaktu(
                                                    kontakt: KontaktItem,
                                                    context: Context,
                                                    rozwinieta: Boolean,
                                                    ulubiony: Boolean,
                                                    onToggleUlubione: () -> Unit,
                                                    onRozwin: () -> Unit,
                                                    onZmianaPreferencji: () -> Unit,
                                                    onOpenSms: ((String) -> Unit)?,
                                                    onEdytuj: () -> Unit = {}
                                                ) {
    // Który numer jest aktywny — przy jednym numerze zawsze pierwszy.
    // Preferencję z prefs ładujemy dopiero po rozwinięciu (nie przy każdym scrollu).
    var indeksNumeru by remember(kontakt.contactId) { mutableIntStateOf(0) }
    LaunchedEffect(rozwinieta, kontakt.contactId) {
        if (!rozwinieta) return@LaunchedEffect
        val numerZap = pobierzNumerDoDzwonienia(context, kontakt.name) ?: return@LaunchedEffect
        val idx = kontakt.numery.indexOfFirst { it.surowy == numerZap }
        if (idx >= 0) indeksNumeru = idx
    }
    val wybranyNumer = kontakt.numery.getOrNull(indeksNumeru) ?: kontakt.numery.first()
    val inicial = remember(kontakt.name) {
        kontakt.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    }

    // Przesunięcie karty przy przeciągnięciu (swipe) — jak w historii połączeń.
    var zasieg by remember { mutableFloatStateOf(0f) }
    val gestyDensity = LocalDensity.current
    val prog = remember { with(gestyDensity) { 110.dp.toPx() } }
    val scopeGestu = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxWidth()) {
        // Tło po PRAWĘJ — widoczne przy przeciągnięciu w LEWO (Wyślij wiadomość)
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(20.dp))
                .background(ZielonyMorski.copy(alpha = 0.18f)),
            contentAlignment = Alignment.CenterEnd
        ) {
            if (zasieg < 0f) {
                Row(
                    modifier = Modifier.padding(end = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = IkonyAplikacji.Wiadomosc,
                        contentDescription = null,
                        tint = ZielonyNeon,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = "Wyślij",
                        color = ZielonyNeon,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        // Tło po LEWEJ — widoczne przy przeciągnięciu w PRAWO (Zadzwoń)
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(20.dp))
                .background(ZielonyMorski.copy(alpha = 0.25f)),
            contentAlignment = Alignment.CenterStart
        ) {
            if (zasieg > 0f) {
                Row(
                    modifier = Modifier.padding(start = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = IkonyAplikacji.TelefonLogo,
                        contentDescription = null,
                        tint = ZielonyNeon,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = "Zadzwoń",
                        color = ZielonyNeon,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

    val paletaMotywu = dialer.dominik.pl.ui.theme.LocalMotyw.current
    val alfaTla by animateFloatAsState(
        targetValue = if (rozwinieta) 1f else 0f,
        animationSpec = tween(220),
        label = "alfa"
    )
    val kolorRamki = if (rozwinieta) ZielonyNeon.copy(alpha = 0.10f + 0.08f * alfaTla)
    else paletaMotywu.kafelekRamka

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(zasieg.roundToInt(), 0) }
            .clip(RoundedCornerShape(20.dp))
            .background(paletaMotywu.gradientKafelka(alfaGora = 0.85f + 0.15f * alfaTla))
            .border(1.dp, kolorRamki, RoundedCornerShape(20.dp))
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        when {
                            // w prawo → zadzwoń
                            zasieg > prog -> zadzwonDoKontaktu(context, wybranyNumer.surowy)
                            // w lewo → wyślij wiadomość
                            zasieg < -prog -> {
                                val numerWiad = pobierzNumerDoWiadomosci(context, kontakt.name)
                                    ?: kontakt.numery.firstOrNull {
                                        pobierzKanal(context, it.surowy) != KanalWiadomosci.Sms
                                    }?.surowy
                                    ?: wybranyNumer.surowy
                                napiszDoKontaktu(context, numerWiad, onOpenSms)
                            }
                            // bez przesunięcia → rozwiń
                            else -> onRozwin()
                        }
                        val start = zasieg
                        scopeGestu.launch {
                            Animatable(start).animateTo(
                                0f,
                                spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMedium
                                )
                            ) { zasieg = value }
                        }
                    },
                    onHorizontalDrag = { _, dystans ->
                        zasieg = (zasieg + dystans).coerceIn(-prog * 1.4f, prog * 1.4f)
                    }
                )
            }
            .combinedClickable(
                onClick = onRozwin,
                onLongClick = onEdytuj
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Awatar — zdjęcie z książki (jeśli jest), inaczej inicjał.
            AwatarKontaktu(
                fotoUri = kontakt.fotoUri,
                inicial = inicial,
                context = context,
                rozmiar = 44.dp
            )

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = kontakt.nazwaWyswietlana,
                    color = paletaMotywu.naTle,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = wybranyNumer.surowy,
                        color = paletaMotywu.tekstDrugorzedny,
                        fontSize = 12.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (kontakt.maWieleNumerow) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(ZielonyNeon.copy(alpha = 0.14f))
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "${kontakt.numery.size} numery",
                                color = ZielonyNeon.copy(alpha = 0.85f),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Mała gwiazdka ulubionych — zawsze widoczna na kafelku.
            // Toggle włącza/wyłącza ulubione jednym dotknięciem.
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onToggleUlubione),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (ulubiony) IkonyAplikacji.Gwiazda
                    else IkonyAplikacji.GwiazdaPusta,
                    contentDescription = if (ulubiony) "Usuń z ulubionych" else "Dodaj do ulubionych",
                    tint = if (ulubiony) ZielonyNeon else paletaMotywu.naTle.copy(alpha = 0.35f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // Rozwinięcie: wybór kanału wiadomości
        AnimatedVisibility(
            visible = rozwinieta,
            enter = expandVertically(animationSpec = tween(220, easing = FastOutSlowInEasing)) +
                fadeIn(tween(180)),
            exit = shrinkVertically(animationSpec = tween(180, easing = FastOutSlowInEasing)) +
                fadeOut(tween(140))
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(paletaMotywu.naTle.copy(alpha = 0.06f))
                )
                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                            // Wybór numeru — pojawia się tylko gdy kontakt ma ich kilka
                            if (kontakt.maWieleNumerow) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Numer telefonu",
                                color = paletaMotywu.naTle.copy(alpha = 0.42f),
                                fontSize = 12.sp
                            )
                            kontakt.numery.forEachIndexed { indeks, numer ->
                                val aktywny = indeks == indeksNumeru
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (aktywny) ZielonyNeon.copy(alpha = 0.10f)
                                            else paletaMotywu.naTle.copy(alpha = 0.04f)
                                        )
                                        .border(
                                            1.dp,
                                            if (aktywny) ZielonyNeon.copy(alpha = 0.40f)
                                            else paletaMotywu.naTle.copy(alpha = 0.07f),
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable { indeksNumeru = indeks }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Znacznik wyboru
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (aktywny) ZielonyNeon
                                                else paletaMotywu.naTle.copy(alpha = 0.12f)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (aktywny) {
                                            Text(
                                                text = "✓",
                                                color = NaPrzycisku,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = numer.surowy,
                                        color = if (aktywny) paletaMotywu.naTle else paletaMotywu.naTle.copy(alpha = 0.55f),
                                        fontSize = 14.sp,
                                        fontWeight = if (aktywny) FontWeight.Medium else FontWeight.Normal,
                                        modifier = Modifier.weight(1f)
                                    )

                                    // Telefon: dzwoni i zapisuje numer jako domyślny do dzwonienia.
                                    // Wiadomość: otwiera kanał, bez zapisu preferencji numeru.
                                    val numerDoDzwon = pobierzNumerDoDzwonienia(context, kontakt.name)
                                    val jestDomyslnyDzwon = numerDoDzwon == numer.surowy
                                    val jestDoWiadomosci = numer.surowy == numer.surowy  // zawsze aktualny numer; bez podświetlenia preferencji

                                    IconMini(
                                        ikona = IkonyAplikacji.TelefonLogo,
                                        opis = "Zadzwoń na ${numer.surowy}",
                                        kolor = ZielonyNeon,
                                        onClick = {
                                            zapamietajNumerDoDzwonienia(context, kontakt.name, numer.surowy)
                                            indeksNumeru = kontakt.numery.indexOfFirst { it.surowy == numer.surowy }.coerceAtLeast(0)
                                            zadzwonDoKontaktu(context, numer.surowy)
                                        }
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    IconMini(
                                        ikona = IkonyAplikacji.Wiadomosc,
                                        opis = "Pisz na ${numer.surowy}",
                                        kolor = paletaMotywu.naTle.copy(alpha = 0.55f),
                                        podswietlona = false,
                                        onClick = {
                                            // Otwarcie wiadomości na aktualny numer + kanał; bez zapisu preferencji numeru
                                            val kanal = pobierzKanal(context, numer.surowy)
                                            napiszDoKontaktu(context, numer.surowy, onOpenSms)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = "Wyślij wiadomość przez",
                        color = paletaMotywu.naTle.copy(alpha = 0.42f),
                        fontSize = 12.sp
                    )

                    // Trzy kanały w jednym rzędzie: SMS, Messenger, Signal
                    val wybrany = pobierzKanal(context, wybranyNumer.surowy)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        KanalWiadomosci.entries.forEach { kanal ->
                            ElementKanalu(
                                kanal = kanal,
                                wybrany = wybrany == kanal,
                                dostepny = czyKanalDostepny(context, kanal),
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    zapamietajKanal(context, wybranyNumer.surowy, kanal)
                                    onZmianaPreferencji()
                                }
                            )
                        }
                    }

                    // Pole na ID konta Messenger — tylko gdy wybrany
                    if (wybrany == KanalWiadomosci.Messenger) {
                        var idKonta by remember {
                            mutableStateOf(
                                pobierzKontoKomunikatora(
                                    context,
                                    wybranyNumer.surowy,
                                    KanalWiadomosci.Messenger
                                ) ?: ""
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "ID konta Messenger (Facebook)",
                                color = paletaMotywu.naTle.copy(alpha = 0.42f),
                                fontSize = 11.5.sp
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(paletaMotywu.naTle.copy(alpha = 0.05f))
                                    .border(
                                        1.dp,
                                        ZielonyNeon.copy(alpha = 0.18f),
                                        RoundedCornerShape(12.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                BasicTextField(
                                    value = idKonta,
                                    onValueChange = {
                                        idKonta = it
                                        zapamietajKontoKomunikatora(
                                            context,
                                            wybranyNumer.surowy,
                                            KanalWiadomosci.Messenger,
                                            it
                                        )
                                        onZmianaPreferencji()
                                    },
                                    singleLine = true,
                                    textStyle = TextStyle(color = paletaMotywu.naTle, fontSize = 14.sp),
                                    cursorBrush = SolidColor(ZielonyNeon),
                                    modifier = Modifier.fillMaxWidth(),
                                    decorationBox = { wewnetrzne ->
                                        if (idKonta.isEmpty()) {
                                            Text(
                                                text = "np. 100005727832736",
                                                color = paletaMotywu.naTle.copy(alpha = 0.3f),
                                                fontSize = 14.sp
                                            )
                                        }
                                        wewnetrzne()
                                    }
                                )
                            }
                            // Instrukcja krok po kroku — Messenger nie przyjmuje numeru
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(ZielonyNeon.copy(alpha = 0.06f))
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Jak znaleźć ID konta",
                                    color = ZielonyNeon.copy(alpha = 0.85f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "1. Otwórz profil osoby na facebook.com w przeglądarce\n" +
                                        "2. Spójrz na adres — fragment po ?id= to właśnie ID\n" +
                                        "   np. facebook.com/profile.php?id=100005727832736\n" +
                                        "3. Skopiuj same cyfry i wklej powyżej",
                                    color = paletaMotywu.naTle.copy(alpha = 0.42f),
                                    fontSize = 10.5.sp,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    }
}

/** Mała okrągła ikonka akcji przy numerze. */
