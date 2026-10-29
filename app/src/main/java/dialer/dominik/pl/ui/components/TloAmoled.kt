package dialer.dominik.pl.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import dialer.dominik.pl.ui.theme.CzarnyAmoled
import dialer.dominik.pl.ui.theme.ZielonyGleboki
import dialer.dominik.pl.ui.theme.ZielonyJasny
import kotlin.math.cos
import kotlin.math.sin

/**
 * Tło AMOLED: czysta czerń (#000000 — piksele wyłączone) plus dwie wolno
 * dryfujące poświaty (aurora) w zieleniach marki. Rysowane na Canvas,
 * bez obrazków i bez zewnętrznych animacji — działa płynnie i nie migocze.
 */
@Composable
fun TloAmoled(modifier: Modifier = Modifier) {
    val animacja = rememberInfiniteTransition(label = "tloAmoled")

    // Wolny dryf poświat — pełny cykl w 48 s (bardzo spokojne tło)
    val faza by animacja.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 48_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "fazaAurory"
    )

    // Delikatna pulsacja jasności — 14 s w obie strony
    val puls by animacja.animateFloat(
        initialValue = 0.78f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 14_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulsAurory"
    )

    Box(modifier = modifier.fillMaxSize().background(CzarnyAmoled)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val szerokosc = size.width
            val wysokosc = size.height
            val najwiekszy = maxOf(szerokosc, wysokosc)

            // --- Górna lewa poświata: ciemna zieleń, wolny ruch po okręgu ---
            val srodekLewy = Offset(
                x = szerokosc * 0.18f + cos(faza) * szerokosc * 0.10f,
                y = wysokosc * 0.22f + sin(faza) * wysokosc * 0.07f
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        ZielonyGleboki.copy(alpha = 0.55f * puls),
                        ZielonyGleboki.copy(alpha = 0.16f * puls),
                        Color.Transparent
                    ),
                    center = srodekLewy,
                    radius = najwiekszy * 0.62f
                ),
                radius = najwiekszy * 0.62f,
                center = srodekLewy
            )

            // --- Dolna prawa poświata: jasna zieleń, ruch przeciwny (faza ujemna) ---
            val srodekPrawy = Offset(
                x = szerokosc * 0.86f - cos(faza) * szerokosc * 0.09f,
                y = wysokosc * 0.78f - sin(faza) * wysokosc * 0.08f
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        ZielonyJasny.copy(alpha = 0.20f * (1.72f - puls)),
                        ZielonyJasny.copy(alpha = 0.05f * (1.72f - puls)),
                        Color.Transparent
                    ),
                    center = srodekPrawy,
                    radius = najwiekszy * 0.52f
                ),
                radius = najwiekszy * 0.52f,
                center = srodekPrawy
            )

            // --- Bardzo subtelna winieta: przygasza krawędzie, środek zostaje czysty ---
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Transparent, CzarnyAmoled.copy(alpha = 0.85f)),
                    center = Offset(szerokosc / 2f, wysokosc / 2f),
                    radius = najwiekszy * 0.78f
                )
            )
        }
    }
}
