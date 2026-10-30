package dialer.dominik.pl.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min

/**
 * Styl „dokumentu Wiedźmina” — żółty pergamin, atrament, ozdobna ramka
 * (jak Work Release / pieczęć z Melitele).
 */
object Pergamin {
    val PapierJasny = Color(0xFFF0E0C0)
    val Papier = Color(0xFFE4CF9A)
    val PapierCiemny = Color(0xFFC9A66B)
    val Brzeg = Color(0xFF8B6914)
    val Atrament = Color(0xFF2A1810)
    val AtramentDrugi = Color(0xFF5C3A1E)
    val Pieczec = Color(0xFF8A1C1C)
    val Zloto = Color(0xFFC9A227)

    fun gradientPapieru(): Brush = Brush.verticalGradient(
        listOf(PapierJasny, Papier, PapierCiemny.copy(alpha = 0.95f))
    )
}

/** Tło + podwójna ramka + lekki „ziarnisty” brzeg pergaminu. */
fun Modifier.kafelekPergaminu(
    promien: Dp = 14.dp,
    ozdoby: Boolean = true
): Modifier {
    val shape = RoundedCornerShape(promien)
    return this
        .clip(shape)
        .background(Pergamin.gradientPapieru())
        .drawBehind {
            // winieta — ciemniejsze krawędzie jak stary papier
            drawRoundRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0x33000000)
                    ),
                    center = Offset(size.width * 0.5f, size.height * 0.35f),
                    radius = size.minDimension * 0.95f
                ),
                cornerRadius = CornerRadius(promien.toPx(), promien.toPx())
            )
            // Rzadsze ziarno — step 24dp zamiast 11 (mniej drawCircle przy scrollu).
            val step = 24.dp.toPx()
            var y = 0f
            var i = 0
            while (y < size.height) {
                var x = (i % 2) * (step * 0.5f)
                while (x < size.width) {
                    val n = ((x * 12.9898f + y * 78.233f) % 1f + 1f) % 1f
                    if (n > 0.78f) {
                        drawCircle(
                            color = Color(0x12000000),
                            radius = 1.2f + n,
                            center = Offset(x, y)
                        )
                    }
                    x += step
                }
                y += step
                i++
            }
        }
        .border(1.5.dp, Pergamin.Brzeg.copy(alpha = 0.85f), shape)
        .border(1.dp, Pergamin.Zloto.copy(alpha = 0.35f), shape)
        .then(
            if (ozdoby) Modifier.drawWithContent {
                drawContent()
                val stroke = 1.4.dp.toPx()
                val inset = 5.dp.toPx()
                val arm = min(14.dp.toPx(), size.minDimension * 0.12f)
                val ink = Pergamin.AtramentDrugi.copy(alpha = 0.55f)
                fun rog(ox: Float, oy: Float, sx: Float, sy: Float) {
                    val p = Path().apply {
                        moveTo(ox, oy + arm * sy)
                        lineTo(ox, oy)
                        lineTo(ox + arm * sx, oy)
                    }
                    drawPath(p, color = ink, style = Stroke(width = stroke))
                }
                rog(inset, inset, 1f, 1f)
                rog(size.width - inset, inset, -1f, 1f)
                rog(inset, size.height - inset, 1f, -1f)
                rog(size.width - inset, size.height - inset, -1f, -1f)
            } else Modifier
        )
}

@Composable
fun KafelekPergaminu(
    modifier: Modifier = Modifier,
    promien: Dp = 14.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .kafelekPergaminu(promien = promien),
        content = content
    )
}
