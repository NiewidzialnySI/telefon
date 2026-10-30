package dialer.dominik.pl.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import dialer.dominik.pl.ui.theme.LocalMotyw
import kotlin.math.min

/**
 * Animowane logo aplikacji — kolory z aktualnej palety motywu
 * ([LocalMotyw]: accent / przycisk / aurora).
 *
 * @param wejscieAnimacji postęp animacji wejścia 0..1 (scale + alpha przy starcie)
 * @param pokazPierscien czy rysować obracający się pierścień (wyłącz np. przy loaderze czatu)
 * @param trybLekki tylko delikatny puls rdzenia — toolbar / status / przycisk dzwonienia
 */
@Composable
fun LogoAnimowane(
    modifier: Modifier = Modifier,
    wejscieAnimacji: Float = 1f,
    pokazPierscien: Boolean = true,
    trybLekki: Boolean = false
) {
    if (trybLekki) {
        LogoAnimowaneLekkie(modifier, wejscieAnimacji)
    } else {
        LogoAnimowanePelne(modifier, wejscieAnimacji, pokazPierscien)
    }
}

@Composable
private fun LogoAnimowaneLekkie(modifier: Modifier, wejscieAnimacji: Float) {
    val animacja = rememberInfiniteTransition(label = "logoLekkie")
    val puls by animacja.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2_400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulsLekki"
    )
    LogoCanvas(
        modifier = modifier,
        wejscieAnimacji = wejscieAnimacji,
        puls = puls,
        falaA = 0f,
        falaB = 0f,
        falaC = 0f,
        obrot = 0f,
        pokazPierscien = false,
        pokazFale = false
    )
}

@Composable
private fun LogoAnimowanePelne(
    modifier: Modifier,
    wejscieAnimacji: Float,
    pokazPierscien: Boolean
) {
    val animacja = rememberInfiniteTransition(label = "logoAnimowane")

    val puls by animacja.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3_600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulsRdzenia"
    )

    val falaA by animacja.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 6600
                0f at 0
                1f at 2400 using LinearOutSlowInEasing
                1f at 6600
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "falaA"
    )
    val falaB by animacja.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 6600
                0f at 0
                0f at 900
                1f at 3300 using LinearOutSlowInEasing
                1f at 6600
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "falaB"
    )
    val falaC by animacja.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 6600
                0f at 0
                0f at 1800
                1f at 4200 using LinearOutSlowInEasing
                1f at 6600
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "falaC"
    )

    val obrot by animacja.animateFloat(
        initialValue = 0f,
        targetValue = if (pokazPierscien) 360f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (pokazPierscien) 26_000 else 1,
                easing = androidx.compose.animation.core.LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "obrotPierscienia"
    )

    LogoCanvas(
        modifier = modifier,
        wejscieAnimacji = wejscieAnimacji,
        puls = puls,
        falaA = falaA,
        falaB = falaB,
        falaC = falaC,
        obrot = obrot,
        pokazPierscien = pokazPierscien,
        pokazFale = true
    )
}

@Composable
private fun LogoCanvas(
    modifier: Modifier,
    wejscieAnimacji: Float,
    puls: Float,
    falaA: Float,
    falaB: Float,
    falaC: Float,
    obrot: Float,
    pokazPierscien: Boolean,
    pokazFale: Boolean
) {
    val motyw = LocalMotyw.current
    // Zawsze z aktualnej palety (accent / secondary) — nie aurora*, bo mogą zostać
    // „przyklejone” po Vault-Tec i logo nie reaguje na zmianę motywu.
    val neon = motyw.accent
    val morski = motyw.accentMorski
    val rdzen = Color(
        red = (neon.red * 0.22f).coerceIn(0f, 1f),
        green = (neon.green * 0.28f).coerceIn(0f, 1f),
        blue = (neon.blue * 0.25f).coerceIn(0f, 1f),
        alpha = 1f
    )
    val rdzenCiemny = Color(
        red = (rdzen.red * 0.45f).coerceIn(0f, 1f),
        green = (rdzen.green * 0.45f).coerceIn(0f, 1f),
        blue = (rdzen.blue * 0.45f).coerceIn(0f, 1f),
        alpha = 1f
    )
    val kolorPoswiaty = lerp(
        morski,
        neon,
        puls.let { (it - 0.92f) / 0.14f }.coerceIn(0f, 1f)
    )
    val skala = 0.82f + 0.18f * wejscieAnimacji

    Canvas(modifier = modifier) {
        val srodek = Offset(size.width / 2f, size.height / 2f)
        val promienBazy = min(size.width, size.height) / 2f
        val grubosc = promienBazy * 0.055f
        val widocznosc = wejscieAnimacji.coerceIn(0f, 1f)

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    kolorPoswiaty.copy(alpha = 0.30f * widocznosc),
                    rdzen.copy(alpha = 0.14f * widocznosc),
                    Color.Transparent
                ),
                center = srodek,
                radius = promienBazy * 1.05f
            ),
            radius = promienBazy * 1.05f,
            center = srodek
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    rdzen.copy(alpha = widocznosc),
                    rdzenCiemny.copy(alpha = widocznosc)
                ),
                center = Offset(srodek.x - promienBazy * 0.15f, srodek.y - promienBazy * 0.2f),
                radius = promienBazy * 0.95f
            ),
            radius = promienBazy * 0.72f * skala,
            center = srodek
        )

        if (pokazPierscien) {
            rotate(obrot, pivot = srodek) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color.Transparent,
                            morski.copy(alpha = 0.15f),
                            morski.copy(alpha = 0.9f),
                            neon,
                            Color.Transparent
                        ),
                        center = srodek
                    ),
                    startAngle = 0f,
                    sweepAngle = 300f,
                    useCenter = false,
                    topLeft = Offset(
                        srodek.x - promienBazy * 0.86f,
                        srodek.y - promienBazy * 0.86f
                    ),
                    size = Size(promienBazy * 1.72f, promienBazy * 1.72f),
                    style = Stroke(width = grubosc, cap = StrokeCap.Round),
                    alpha = widocznosc
                )
            }
        }

        fun rysujFale(postep: Float, bazowyPromien: Float, alfaMaks: Float) {
            if (postep <= 0f || postep >= 1f) return
            val rozstaw = promienBazy * bazowyPromien * (0.70f + 0.30f * postep)
            val alfa = alfaMaks * (1f - postep * 0.80f) * widocznosc
            drawArc(
                color = kolorPoswiaty.copy(alpha = alfa),
                startAngle = -52f,
                sweepAngle = 104f,
                useCenter = false,
                topLeft = Offset(srodek.x - rozstaw, srodek.y - rozstaw),
                size = Size(rozstaw * 2f, rozstaw * 2f),
                style = Stroke(width = grubosc * 1.15f, cap = StrokeCap.Round)
            )
        }

        if (pokazFale) {
            rysujFale(falaA, 0.30f, 0.95f)
            rysujFale(falaB, 0.48f, 0.70f)
            rysujFale(falaC, 0.66f, 0.45f)
        }

        val kropka = srodek
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    neon.copy(alpha = widocznosc),
                    morski.copy(alpha = widocznosc)
                ),
                center = kropka,
                radius = promienBazy * 0.14f
            ),
            radius = promienBazy * 0.088f * puls,
            center = kropka
        )

        val refleksSrodek = Offset(
            srodek.x - promienBazy * 0.26f,
            srodek.y - promienBazy * 0.38f
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.055f * widocznosc),
                    Color.Transparent
                ),
                center = refleksSrodek,
                radius = promienBazy * 0.20f
            ),
            radius = promienBazy * 0.20f,
            center = refleksSrodek
        )
    }
}
