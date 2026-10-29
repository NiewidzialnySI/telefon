package dialer.dominik.pl.ui.components.icons

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.sin

/**
 * Zegar pokazujący godzinę zaplanowanej wysyłki.
 * Gdy [czasMs] == null — wskazówki na 12:00 (neutralny). Kolor [kolor] jak w Icon(tint).
 */
@Composable
fun IkonaZegaraCzasu(
    czasMs: Long?,
    modifier: Modifier = Modifier,
    kolor: Color = Color.Black
) {
    val (katGodz, katMin) = remember(czasMs) {
        val kal = Calendar.getInstance().apply {
            timeInMillis = czasMs ?: System.currentTimeMillis()
        }
        val min = kal.get(Calendar.MINUTE).toFloat()
        val godz = (kal.get(Calendar.HOUR_OF_DAY) % 12).toFloat() + min / 60f
        val katGodz = (godz / 12f) * 360f
        val katMin = (min / 60f) * 360f
        katGodz to katMin
    }

    Canvas(modifier) {
        val r = size.minDimension / 2f
        val c = center
        // Tarcza — okrąg konturowy.
        drawCircle(kolor, radius = r, style = Stroke(width = size.minDimension * 0.10f))
        // Wskazówka minutowa (długa).
        val mik = Math.toRadians(katMin.toDouble() - 90.0)
        val mEnd = Offset(c.x + (cos(mik) * r * 0.62).toFloat(), c.y + (sin(mik) * r * 0.62).toFloat())
        drawLine(kolor, c, mEnd, strokeWidth = r * 0.13f, cap = StrokeCap.Round)
        // Wskazówka godzinowa (krótsza, grubsza).
        val gk = Math.toRadians(katGodz.toDouble() - 90.0)
        val gEnd = Offset(c.x + (cos(gk) * r * 0.42).toFloat(), c.y + (sin(gk) * r * 0.42).toFloat())
        drawLine(kolor, c, gEnd, strokeWidth = r * 0.17f, cap = StrokeCap.Round)
        // Oś.
        drawCircle(kolor, radius = r * 0.12f)
    }
}