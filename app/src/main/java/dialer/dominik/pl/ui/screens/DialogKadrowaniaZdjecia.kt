package dialer.dominik.pl.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dialer.dominik.pl.ui.components.IkonyAplikacji
import dialer.dominik.pl.ui.theme.NaPrzycisku
import dialer.dominik.pl.ui.theme.ZielonyNeon
import kotlin.math.max
import kotlin.math.roundToInt

/** Rozmiar wyjściowego zdjęcia kontaktu (px). */
private const val BOK_WYJSCIA = 512

/** Okno kadrowania zdjęcia kontaktu: okrągły kadr, przesuwanie palcem i szczypanie (zoom). */
@Composable
internal fun DialogKadrowaniaZdjecia(
    context: Context,
    uri: Uri,
    onCancel: () -> Unit,
    onGotowy: (ByteArray) -> Unit
) {
    val bitmapa: Bitmap? = remember(uri) { wczytajBitmape(context, uri) }

    if (bitmapa == null) {
        Dialog(onDismissRequest = onCancel) {
            Box(Modifier.clip(RoundedCornerShape(24.dp)).background(Color(0xFF0D1611)).padding(24.dp)) {
                Text("Nie udało się otworzyć zdjęcia", color = Color.White)
            }
        }
        return
    }

    val obraz = remember(bitmapa) { bitmapa.asImageBitmap() }
    var zoom by remember { mutableFloatStateOf(1f) }
    var przesuniecie by remember { mutableStateOf(Offset.Zero) }
    var widok by remember { mutableStateOf(Size.Zero) }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF0D1611))
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Dopasuj zdjęcie",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp
                    ),
                    color = Color.White
                )
                Text(
                    text = "Przesuń palcem · przybliż dwoma palcami",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp
                )

                Box(
                    modifier = Modifier
                        .size(300.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF0B120F))
                        .onSizeChanged { widok = Size(it.width.toFloat(), it.height.toFloat()) }
                        .pointerInput(bitmapa) {
                            detectTransformGestures { _, pan, zmiana, _ ->
                                zoom = (zoom * zmiana).coerceIn(1f, 4f)
                                przesuniecie = ograniczPrzesuniecie(
                                    przesuniecie + pan, bitmapa, widok, zoom
                                )
                            }
                        }
                ) {
                    // Zdjęcie + przyciemnienie z wyciętym kołem (BlendMode.Clear wymaga Offscreen).
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    ) {
                        val g = geometriaKadru(bitmapa, size, zoom, przesuniecie)
                        drawImage(
                            image = obraz,
                            dstOffset = IntOffset(g.lewo.roundToInt(), g.gora.roundToInt()),
                            dstSize = IntSize(g.szer.roundToInt(), g.wys.roundToInt())
                        )
                        // Przyciemnienie tylko poza kołem (Difference), zdjęcie w kole zostaje nietknięte.
                        val kolo = Path().apply {
                            addOval(
                                Rect(
                                    size.width / 2f - g.srednica / 2f,
                                    size.height / 2f - g.srednica / 2f,
                                    size.width / 2f + g.srednica / 2f,
                                    size.height / 2f + g.srednica / 2f
                                )
                            )
                        }
                        clipPath(kolo, clipOp = ClipOp.Difference) {
                            drawRect(Color(0xB8000000))
                        }
                        drawCircle(
                            color = ZielonyNeon,
                            radius = g.srednica / 2f,
                            center = Offset(size.width / 2f, size.height / 2f),
                            style = Stroke(width = 2.5.dp.toPx())
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(0.5f)
                            .height(52.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .clickable { onCancel() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = IkonyAplikacji.Zamknij,
                            contentDescription = "Anuluj",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(ZielonyNeon.copy(alpha = 0.9f))
                            .clickable {
                                val przyciete = wytnijKwadrat(bitmapa, widok, zoom, przesuniecie)
                                if (przyciete != null) {
                                    val bajty = java.io.ByteArrayOutputStream().use { wyjscie ->
                                        przyciete.compress(Bitmap.CompressFormat.JPEG, 92, wyjscie)
                                        wyjscie.toByteArray()
                                    }
                                    onGotowy(bajty)
                                } else {
                                    Toast.makeText(context, "Nie udało się przyciąć", Toast.LENGTH_SHORT).show()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = IkonyAplikacji.Sprawdzenie,
                                contentDescription = "Zapisz",
                                tint = NaPrzycisku,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Zapisz zdjęcie", color = NaPrzycisku, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/** Geometria obrazu względem okrągłego kadru (wszystko w px widoku). */
private class GeometriaKadru(
    val srednica: Float,
    val skala: Float,
    val lewo: Float,
    val gora: Float,
    val szer: Float,
    val wys: Float
)

/** Skala „cover” (obraz wypełnia koło) × zoom; przesunięcie liczone od środka widoku. */
private fun geometriaKadru(bitmapa: Bitmap, widok: Size, zoom: Float, przesuniecie: Offset): GeometriaKadru {
    val d = minOf(widok.width, widok.height) * 0.92f
    val s0 = max(d / bitmapa.width, d / bitmapa.height)
    val s = s0 * zoom
    val w = bitmapa.width * s
    val h = bitmapa.height * s
    val cx = widok.width / 2f + przesuniecie.x
    val cy = widok.height / 2f + przesuniecie.y
    return GeometriaKadru(d, s, cx - w / 2f, cy - h / 2f, w, h)
}

/** Nie pozwala przesunąć obrazu poza krawędź koła (nie ma pustego tła w kadrze). */
private fun ograniczPrzesuniecie(przesuniecie: Offset, bitmapa: Bitmap, widok: Size, zoom: Float): Offset {
    if (widok.width <= 0f || widok.height <= 0f) return przesuniecie
    val g = geometriaKadru(bitmapa, widok, zoom, Offset.Zero)
    val maxX = max(0f, (g.szer - g.srednica) / 2f)
    val maxY = max(0f, (g.wys - g.srednica) / 2f)
    return Offset(
        przesuniecie.x.coerceIn(-maxX, maxX),
        przesuniecie.y.coerceIn(-maxY, maxY)
    )
}

/** Wycina kwadrat opisany na kole kadru i skaluje do [BOK_WYJSCIA]. */
private fun wytnijKwadrat(bitmapa: Bitmap, widok: Size, zoom: Float, przesuniecie: Offset): Bitmap? {
    return try {
        val g = geometriaKadru(bitmapa, widok, zoom, przesuniecie)
        val lewoKola = widok.width / 2f - g.srednica / 2f
        val goraKola = widok.height / 2f - g.srednica / 2f
        val x0 = ((lewoKola - g.lewo) / g.skala).roundToInt().coerceIn(0, bitmapa.width - 1)
        val y0 = ((goraKola - g.gora) / g.skala).roundToInt().coerceIn(0, bitmapa.height - 1)
        val bok = (g.srednica / g.skala).roundToInt()
            .coerceIn(1, minOf(bitmapa.width - x0, bitmapa.height - y0))
        val wyciete = Bitmap.createBitmap(bitmapa, x0, y0, bok, bok)
        Bitmap.createScaledBitmap(wyciete, BOK_WYJSCIA, BOK_WYJSCIA, true)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

/** Wczytuje zdjęcie zmniejszone do ~1600 px (oszczędność pamięci). */
private fun wczytajBitmape(context: Context, uri: Uri): Bitmap? {
    return try {
        val granice = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, granice) }
        val najdluzszy = max(granice.outWidth, granice.outHeight)
        var probkowanie = 1
        while (najdluzszy / probkowanie > 1600) probkowanie *= 2
        val opcje = BitmapFactory.Options().apply { inSampleSize = probkowanie }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opcje) }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
