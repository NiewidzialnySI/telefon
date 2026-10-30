package dialer.dominik.pl.ui.components

import android.graphics.Bitmap
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.hypot

/**
 * Lekkie przejście jasny ↔ ciemny (wzorowane na yasc / circular-reveal z GitHuba):
 * zrzut UI → zmiana motywu pod spodem → rosnąca dziura w bitmapie.
 * `DynamicMaterialTheme(animate=false)` — bez animowania całej palety co klatkę.
 */
@Composable
fun ZapamietajPrzejscieMotywu(
    ciemny: Boolean,
    content: @Composable (wyswietlanyCiemny: Boolean) -> Unit
) {
    val view = LocalView.current
    var wyswietlany by remember { mutableStateOf(ciemny) }
    var overlay by remember { mutableStateOf<ImageBitmap?>(null) }
    val postep = remember { Animatable(1f) }

    LaunchedEffect(ciemny) {
        if (ciemny == wyswietlany) return@LaunchedEffect
        val zrzut = zrzutWidoku(view)
        if (zrzut != null) {
            overlay = zrzut
            postep.snapTo(0f)
            wyswietlany = ciemny
            postep.animateTo(
                1f,
                animationSpec = tween(400, easing = FastOutSlowInEasing)
            )
            overlay = null
        } else {
            wyswietlany = ciemny
        }
    }

    Box(Modifier.fillMaxSize()) {
        content(wyswietlany)
        val bmp = overlay
        if (bmp != null) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            ) {
                rysujReveal(bmp, postep.value)
            }
        }
    }
}

private fun DrawScope.rysujReveal(bmp: ImageBitmap, p: Float) {
    val dst = IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1))
    drawImage(image = bmp, dstOffset = IntOffset.Zero, dstSize = dst)
    val maxR = hypot(size.width.toDouble(), size.height.toDouble()).toFloat()
    drawCircle(
        color = Color.Black,
        radius = maxR * p.coerceIn(0f, 1f),
        center = Offset(size.width * 0.5f, size.height * 0.38f),
        blendMode = BlendMode.Clear
    )
}

private suspend fun zrzutWidoku(view: View): ImageBitmap? = withContext(Dispatchers.Main.immediate) {
    val w = view.width
    val h = view.height
    if (w <= 0 || h <= 0) return@withContext null
    runCatching {
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        view.draw(canvas)
        bitmap.asImageBitmap()
    }.getOrNull()
}
