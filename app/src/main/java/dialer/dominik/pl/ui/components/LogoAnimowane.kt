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
import dialer.dominik.pl.ui.theme.ZielonyGleboki
import dialer.dominik.pl.ui.theme.ZielonyJasny
import dialer.dominik.pl.ui.theme.ZielonyMorski
import dialer.dominik.pl.ui.theme.ZielonyNeon
import kotlin.math.min

/**
 * Animowane logo aplikacji — rysowane na Canvas.
 *
 * @param wejscieAnimacji postęp animacji wejścia 0..1 (scale + alpha przy starcie)
 * @param pokazPierscien czy rysować obracający się pierścień (wyłącz np. przy loaderze czatu)
 */
@Composable
fun LogoAnimowane(
    modifier: Modifier = Modifier,
    wejscieAnimacji: Float = 1f,
    pokazPierscien: Boolean = true
) {
    val animacja = rememberInfiniteTransition(label = "logoAnimowane")

    // Puls rdzenia — 3.6 s, płynnie w obie strony
    val puls by animacja.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3_600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulsRdzenia"
    )

    // Fale: jeden cykl 6.6 s — fala rozchodzi się ~2.4 s, potem chwila ciszy.
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

    // Obrót pierścienia tylko gdy włączony (powitanie).
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

    val kolorPoswiaty = lerp(ZielonyJasny, ZielonyNeon, puls.let { (it - 0.92f) / 0.14f }.coerceIn(0f, 1f))
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
                    ZielonyGleboki.copy(alpha = 0.14f * widocznosc),
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
                    ZielonyGleboki.copy(alpha = widocznosc),
                    Color(0xFF061F17).copy(alpha = widocznosc)
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
                            ZielonyJasny.copy(alpha = 0.15f),
                            ZielonyMorski.copy(alpha = 0.9f),
                            ZielonyNeon,
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

        rysujFale(falaA, 0.30f, 0.95f)
        rysujFale(falaB, 0.48f, 0.70f)
        rysujFale(falaC, 0.66f, 0.45f)

        val kropka = srodek
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    ZielonyNeon.copy(alpha = widocznosc),
                    ZielonyMorski.copy(alpha = widocznosc)
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
