package dialer.dominik.pl.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dialer.dominik.pl.ui.components.IkonyAplikacji
import dialer.dominik.pl.ui.theme.CzcionkiWiedzmina
import dialer.dominik.pl.ui.theme.LocalMotyw
import dialer.dominik.pl.ui.theme.MotywPaletyPresety
import dialer.dominik.pl.ui.theme.MotywStan
import dialer.dominik.pl.ui.theme.pamietajPedzelSrebrnegoTekstu
import dialer.dominik.pl.ui.theme.tloSrebrnyPolysk
import dialer.dominik.pl.ui.theme.ZielonyMorski
import dialer.dominik.pl.ui.theme.ZielonyNeon
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import kotlin.math.sin

/** Indeks klawisza na padzie → kolor znaku (jak układ 1…#). */
internal fun indeksKlawisza(znak: Char): Int = when (znak) {
    '1' -> 0; '2' -> 1; '3' -> 2
    '4' -> 3; '5' -> 4; '6' -> 5
    '7' -> 6; '8' -> 7; '9' -> 8
    '*' -> 9; '0' -> 10; '#' -> 11
    '+' -> 9
    else -> 0
}

internal fun kolorRamkiKlawisza(znak: Char): Color =
    kolorZnakuWiedzmina(indeksKlawisza(znak))

/** Klawiatura numeryczna z przyciskami akcji: wiadomość, dzwoń, dodaj kontakt. */
@Composable
internal fun KlawiaturaNumeryczna(
    onCyfraKliknieta: (String) -> Unit,
    onZadzwon: () -> Unit,
    onWiadomosc: () -> Unit,
    onNowyKontakt: () -> Unit
) {
    val przyciski = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("*", "0", "#")
    )

    val motyw = LocalMotyw.current
    val motywStan by MotywStan.stan.collectAsState()
    val wiedzmin = motywStan.paletaId == "wiedzmin"

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        przyciski.forEachIndexed { indeksRzedu, rzad ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                rzad.forEachIndexed { indeksWRzedzie, znak ->
                    val indeks = indeksRzedu * 3 + indeksWRzedzie
                    val kolorZnaku = if (wiedzmin) kolorZnakuWiedzmina(indeks) else null
                    PrzyciskCyfryKlawiatury(
                        znak = znak,
                        onKlik = { onCyfraKliknieta(znak) },
                        onDlugie = if (znak == "*") ({ onCyfraKliknieta("+") }) else null,
                        kolorWiedzmina = kolorZnaku,
                        indeksFazy = indeks
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(0.92f),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val srebro = MotywPaletyPresety.AkcentSrebrnyWiedzmin
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(if (wiedzmin) Color(0xFF1C1E22) else motyw.kafelek)
                    .then(
                        if (wiedzmin) {
                            Modifier.border(
                                width = 1.25.dp,
                                brush = Brush.verticalGradient(
                                    listOf(
                                        Color.White.copy(alpha = 0.75f),
                                        srebro,
                                        srebro.copy(alpha = 0.45f)
                                    )
                                ),
                                shape = CircleShape
                            )
                        } else Modifier
                    )
                    .clickable(onClick = onWiadomosc),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = IkonyAplikacji.Wiadomosc,
                    contentDescription = "Wiadomość",
                    tint = if (wiedzmin) srebro else motyw.naTle.copy(alpha = 0.7f),
                    modifier = Modifier.size(22.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .then(
                        if (wiedzmin) {
                            Modifier.tloSrebrnyPolysk(CircleShape)
                        } else {
                            Modifier.background(
                                Brush.horizontalGradient(listOf(ZielonyMorski, ZielonyNeon))
                            )
                        }
                    )
                    .clickable(onClick = onZadzwon),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = IkonyAplikacji.TelefonLogo,
                    contentDescription = "Zadzwoń",
                    tint = if (wiedzmin) Color(0xFF1A1C1E) else Color(0xFF06231A),
                    modifier = Modifier.size(34.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(if (wiedzmin) Color(0xFF1C1E22) else motyw.kafelek)
                    .then(
                        if (wiedzmin) {
                            Modifier.border(
                                width = 1.25.dp,
                                brush = Brush.verticalGradient(
                                    listOf(
                                        Color.White.copy(alpha = 0.75f),
                                        srebro,
                                        srebro.copy(alpha = 0.45f)
                                    )
                                ),
                                shape = CircleShape
                            )
                        } else Modifier
                    )
                    .clickable(onClick = onNowyKontakt),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = IkonyAplikacji.DodajKontakt,
                    contentDescription = "Utwórz kontakt",
                    tint = if (wiedzmin) srebro else motyw.naTle.copy(alpha = 0.7f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun PrzyciskCyfryKlawiatury(
    znak: String,
    onKlik: () -> Unit,
    onDlugie: (() -> Unit)?,
    kolorWiedzmina: Color? = null,
    indeksFazy: Int = 0
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val skala by animateFloatAsState(
        targetValue = if (pressed) 0.90f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "skalaCyfry"
    )
    val klikniety = if (onDlugie != null) {
        Modifier.combinedClickable(
            interactionSource = interaction,
            indication = null,
            onClick = onKlik,
            onLongClick = onDlugie
        )
    } else {
        Modifier.clickable(
            interactionSource = interaction,
            indication = null,
            onClick = onKlik
        )
    }

    val motyw = LocalMotyw.current
    val accent = kolorWiedzmina
    val srebrnyTekst = if (accent != null) pamietajPedzelSrebrnegoTekstu(3800) else null

    Box(
        modifier = Modifier
            .size(62.dp)
            .graphicsLayer {
                scaleX = skala
                scaleY = skala
            }
            .then(
                if (accent != null && pressed) {
                    Modifier.shadow(14.dp, CircleShape, spotColor = accent.copy(alpha = 0.85f))
                } else {
                    Modifier
                }
            )
            .clip(CircleShape)
            .background(
                if (accent != null && pressed) accent.copy(alpha = 0.18f)
                else motyw.kafelek
            )
            .then(klikniety),
        contentAlignment = Alignment.Center
    ) {
        if (srebrnyTekst != null && accent != null) {
            CyfraZeSrebremIObrysem(
                tekst = znak,
                srebrny = srebrnyTekst,
                kolorObrysu = accent,
                fontSize = 26.sp,
                gruboscObrysu = if (pressed) 5.5f else 4.2f,
                fazaStart = indeksFazy * 0.4f,
                pressed = pressed
            )
        } else {
            Text(
                text = znak,
                color = motyw.naTle,
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Srebrna cyfra z obrysem konturu litery —
 * obrys mieni się w kolorze znaku.
 */
@Composable
private fun CyfraZeSrebremIObrysem(
    tekst: String,
    srebrny: Brush,
    kolorObrysu: Color,
    fontSize: TextUnit,
    gruboscObrysu: Float,
    fazaStart: Float = 0f,
    pressed: Boolean = false
) {
    val t = rememberInfiniteTransition(label = "obrysCyfry")
    val faza by t.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(4200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "fazaObrysu"
    )
    val blysk = 0.55f + 0.45f * (0.5f + 0.5f * sin(faza + fazaStart))
    val obrys = kolorObrysu.copy(alpha = (0.55f + 0.45f * blysk).coerceIn(0.45f, 1f))
    val bialyBlysk = Color.White.copy(alpha = 0.35f + 0.45f * blysk)
    // System — Handvetica nie ma czytelnych cyfr; DIN na padzie też bywa kruchy.
    val font = FontFamily.Default
    Box(contentAlignment = Alignment.Center) {
        Text(
            text = tekst,
            modifier = Modifier.semantics { invisibleToUser() },
            style = TextStyle(
                color = bialyBlysk,
                fontFamily = font,
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                drawStyle = Stroke(
                    width = gruboscObrysu + 2.2f,
                    join = StrokeJoin.Round
                )
            )
        )
        Text(
            text = tekst,
            modifier = Modifier.semantics { invisibleToUser() },
            style = TextStyle(
                color = obrys,
                fontFamily = font,
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                drawStyle = Stroke(
                    width = gruboscObrysu,
                    join = StrokeJoin.Round
                )
            )
        )
        Text(
            text = tekst,
            style = TextStyle(
                brush = srebrny,
                fontFamily = font,
                fontSize = fontSize,
                fontWeight = FontWeight.SemiBold,
                shadow = Shadow(
                    color = Color.White.copy(alpha = if (pressed) 0.45f else 0.2f),
                    blurRadius = if (pressed) 10f else 5f
                )
            )
        )
    }
}

/**
 * Wyświetlacz numeru — srebrne cyfry z obrysem konturu litery
 * w kolorze klawisza (bez mini-kółek).
 */
@Composable
internal fun WyswietlaczNumeruWiedzmin(
    numer: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    onLongClick: () -> Unit = {}
) {
    val motyw = LocalMotyw.current
    var poprzedniaDlugosc by remember { mutableIntStateOf(0) }
    val rosnacy = numer.length > poprzedniaDlugosc
    LaunchedEffect(numer.length) { poprzedniaDlugosc = numer.length }

    if (numer.isEmpty()) {
        Text(
            text = placeholder,
            color = motyw.naTle.copy(alpha = 0.3f),
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.5.sp,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = modifier.combinedClickable(
                onClick = {},
                onLongClick = onLongClick
            )
        )
        return
    }
    Row(
        modifier = modifier.combinedClickable(
            onClick = {},
            onLongClick = onLongClick
        ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        numer.forEachIndexed { indeks, znak ->
            CyfraNumeruWiedzmin(
                klucz = "$indeks:$znak",
                znak = znak,
                kolorObrysu = kolorRamkiKlawisza(znak),
                animujWejscie = rosnacy && indeks == numer.lastIndex,
                faza = indeks * 0.45f
            )
        }
    }
}

@Composable
private fun CyfraNumeruWiedzmin(
    klucz: String,
    znak: Char,
    kolorObrysu: Color,
    animujWejscie: Boolean,
    faza: Float
) {
    val skala = remember(klucz) { Animatable(if (animujWejscie) 0.4f else 1f) }
    val alfa = remember(klucz) { Animatable(if (animujWejscie) 0.15f else 1f) }
    LaunchedEffect(klucz, animujWejscie) {
        if (animujWejscie) {
            skala.snapTo(0.4f)
            alfa.snapTo(0.15f)
            skala.animateTo(
                1f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
            )
            alfa.animateTo(1f, tween(160))
        } else {
            skala.snapTo(1f)
            alfa.snapTo(1f)
        }
    }
    val srebrny = pamietajPedzelSrebrnegoTekstu(4200)
    Box(
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .graphicsLayer {
                scaleX = skala.value
                scaleY = skala.value
                alpha = alfa.value
            },
        contentAlignment = Alignment.Center
    ) {
        CyfraZeSrebremIObrysem(
            tekst = znak.toString(),
            srebrny = srebrny,
            kolorObrysu = kolorObrysu,
            fontSize = 26.sp,
            gruboscObrysu = 3.6f,
            fazaStart = faza
        )
    }
}
