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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dialer.dominik.pl.sms.pobierzNumerDoDzwonienia
import dialer.dominik.pl.sms.zapamietajNumerDoDzwonienia
import dialer.dominik.pl.ui.components.IkonyAplikacji
import dialer.dominik.pl.ui.theme.CzarnyAmoled
import dialer.dominik.pl.ui.theme.CzcionkiWiedzmina
import dialer.dominik.pl.ui.theme.NaPrzycisku
import dialer.dominik.pl.ui.theme.Pergamin
import dialer.dominik.pl.ui.theme.czyMotywWiedzmin
import dialer.dominik.pl.ui.theme.gradientKafelka
import dialer.dominik.pl.ui.theme.kafelekPergaminu
import dialer.dominik.pl.ui.theme.motywAkcent
import dialer.dominik.pl.ui.theme.motywAkcentMorski
import androidx.compose.ui.text.font.FontFamily
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
    onEdytuj: () -> Unit = {},
    indeksZnaku: Int = 0
) {
    // Główny numer — od razu z prefs (także przy zwiniętej karcie / swipe), nie tylko po rozwinięciu.
    var indeksNumeru by remember(kontakt.contactId) {
        val pref = pobierzNumerDoDzwonienia(context, kontakt.contactId, kontakt.name)
        mutableIntStateOf(indeksNumeruDoDzwonienia(kontakt, pref))
    }
    LaunchedEffect(kontakt.contactId, kontakt.numery) {
        val pref = pobierzNumerDoDzwonienia(context, kontakt.contactId, kontakt.name)
        indeksNumeru = indeksNumeruDoDzwonienia(kontakt, pref)
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
    val paletaMotywu = dialer.dominik.pl.ui.theme.LocalMotyw.current
    val wiedzmin = czyMotywWiedzmin()
    val akcent = motywAkcent()
    val akcentMorski = if (wiedzmin) akcent else motywAkcentMorski()
    val kolorZnaku = kolorZnakuWiedzmina(indeksZnaku)

    Box(modifier = Modifier.fillMaxWidth()) {
        // Tło po PRAWĘJ — widoczne przy przeciągnięciu w LEWO (Wyślij wiadomość)
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(20.dp))
                .background(akcentMorski.copy(alpha = 0.18f)),
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
                        tint = akcent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = "Wyślij",
                        color = akcent,
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
                .background(akcentMorski.copy(alpha = 0.25f)),
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
                        tint = akcent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = "Zadzwoń",
                        color = akcent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

    val alfaTla by animateFloatAsState(
        targetValue = if (rozwinieta) 1f else 0f,
        animationSpec = tween(220),
        label = "alfa"
    )
    val kolorRamki = if (rozwinieta) akcent.copy(alpha = 0.10f + 0.08f * alfaTla)
    else paletaMotywu.kafelekRamka
    val atrament = if (wiedzmin) Pergamin.Atrament else paletaMotywu.naTle
    val atramentDrugi = if (wiedzmin) Pergamin.AtramentDrugi else paletaMotywu.tekstDrugorzedny

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { translationX = zasieg }
            .then(
                if (wiedzmin) {
                    Modifier.kafelekPergaminu(promien = 16.dp)
                } else {
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            paletaMotywu.gradientKafelka(alfaGora = 0.85f + 0.15f * alfaTla)
                        )
                        .border(1.dp, kolorRamki, RoundedCornerShape(20.dp))
                }
            )
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        when {
                            // w prawo → zadzwoń na główny (wybrany) numer
                            zasieg > prog -> zadzwonDoKontaktu(context, wybranyNumer.surowy)
                            // w lewo → SMS na ten sam główny numer
                            zasieg < -prog -> napiszDoKontaktu(context, wybranyNumer.surowy, onOpenSms)
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
                rozmiar = 44.dp,
                wariant = if (wiedzmin) AwatarWariant.Wiedzmin else AwatarWariant.Domyslny,
                kolorWiedzmina = if (wiedzmin) kolorZnaku else null,
                indeksUlubionego = indeksZnaku
            )

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = kontakt.nazwaWyswietlana,
                    color = atrament,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = if (wiedzmin) CzcionkiWiedzmina.HelveticaNeue else FontFamily.Default,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = wybranyNumer.surowy,
                        color = atramentDrugi,
                        fontSize = 12.5.sp,
                        fontFamily = FontFamily.Default,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (kontakt.maWieleNumerow) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (wiedzmin) Pergamin.Pieczec.copy(alpha = 0.18f)
                                    else akcent.copy(alpha = 0.14f)
                                )
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "${kontakt.numery.size} numery",
                                color = if (wiedzmin) Pergamin.Pieczec else akcent.copy(alpha = 0.85f),
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
                    tint = when {
                        ulubiony && wiedzmin -> Pergamin.Pieczec
                        ulubiony -> akcent
                        wiedzmin -> Pergamin.AtramentDrugi.copy(alpha = 0.45f)
                        else -> paletaMotywu.naTle.copy(alpha = 0.35f)
                    },
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
                        .background(
                            if (wiedzmin) Pergamin.Brzeg.copy(alpha = 0.35f)
                            else paletaMotywu.naTle.copy(alpha = 0.06f)
                        )
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
                                color = atramentDrugi.copy(alpha = 0.75f),
                                fontSize = 12.sp
                            )
                            kontakt.numery.forEachIndexed { indeks, numer ->
                                val aktywny = indeks == indeksNumeru
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (aktywny) akcent.copy(alpha = 0.10f)
                                            else paletaMotywu.naTle.copy(alpha = 0.04f)
                                        )
                                        .border(
                                            1.dp,
                                            if (aktywny) akcent.copy(alpha = 0.40f)
                                            else paletaMotywu.naTle.copy(alpha = 0.07f),
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable {
                                            // Klik w numer = ustaw jako główny (zapamiętany po wyjściu z karty).
                                            indeksNumeru = indeks
                                            zapamietajNumerDoDzwonienia(
                                                context, kontakt.contactId, numer.surowy
                                            )
                                            onZmianaPreferencji()
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Znacznik wyboru (główny numer)
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (aktywny) akcent
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
                                        color = if (aktywny) atrament else atramentDrugi.copy(alpha = 0.75f),
                                        fontSize = 14.sp,
                                        fontWeight = if (aktywny) FontWeight.Medium else FontWeight.Normal,
                                        modifier = Modifier.weight(1f)
                                    )

                                    IconMini(
                                        ikona = IkonyAplikacji.TelefonLogo,
                                        opis = "Zadzwoń na ${numer.surowy}",
                                        kolor = akcent,
                                        onClick = {
                                            zapamietajNumerDoDzwonienia(
                                                context, kontakt.contactId, numer.surowy
                                            )
                                            indeksNumeru = indeks
                                            onZmianaPreferencji()
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
                                            napiszDoKontaktu(context, numer.surowy, onOpenSms)
                                        }
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
}

/** Mała okrągła ikonka akcji przy numerze. */
