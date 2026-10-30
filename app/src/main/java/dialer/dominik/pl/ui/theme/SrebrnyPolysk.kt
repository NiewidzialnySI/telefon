package dialer.dominik.pl.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Połyskujący chrom / srebro — shimmer + bieg po obramowaniu
 * (od lewego dolnego → prawy górny, potem dalej po obwodzie).
 */
object SrebrnyPolysk {

    val KoloryChrome: List<Color> = listOf(
        Color(0xFF5C6168),
        Color(0xFF8B919A),
        Color(0xFFC5CAD3),
        Color(0xFFF2F4F7),
        Color(0xFFFFFFFF),
        Color(0xFFF2F4F7),
        Color(0xFFC5CAD3),
        Color(0xFF8B919A),
        Color(0xFF5C6168)
    )

    val KoloryBlysk: List<Color> = listOf(
        MotywPaletyPresety.AkcentSrebrnyWiedzmin.copy(alpha = 0.15f),
        MotywPaletyPresety.AkcentSrebrnyWiedzmin.copy(alpha = 0.55f),
        Color.White.copy(alpha = 0.95f),
        MotywPaletyPresety.AkcentSrebrnyWiedzmin.copy(alpha = 0.55f),
        MotywPaletyPresety.AkcentSrebrnyWiedzmin.copy(alpha = 0.15f)
    )

    /** Czarna stal — pasek zakładek (ciemny brushed metal). */
    val KoloryCzarnejStali: List<Color> = listOf(
        Color(0xFF070809),
        Color(0xFF141618),
        Color(0xFF2A2E34),
        Color(0xFF3E444C),
        Color(0xFF1C1F24),
        Color(0xFF0C0D10),
        Color(0xFF070809)
    )
}

fun pedzelSrebrnegoPolysku(
    size: IntSize,
    progress: Float,
    colors: List<Color> = SrebrnyPolysk.KoloryChrome
): Brush {
    val w = size.width.coerceAtLeast(1).toFloat()
    val h = size.height.coerceAtLeast(1).toFloat()
    // Lewy dół → prawy góra (~135° od osi X w standardzie Compose: dx>0, dy<0)
    val angleRad = Math.toRadians(-45.0)
    val dx = cos(angleRad).toFloat()
    val dy = sin(angleRad).toFloat()
    val travel = (w + h) * 1.7f
    val startDist = progress * travel - travel * 0.4f
    val band = maxOf(w, h) * 0.42f
    return Brush.linearGradient(
        colors = colors,
        start = Offset(startDist * dx, h + startDist * dy),
        end = Offset((startDist + band) * dx, h + (startDist + band) * dy),
        tileMode = TileMode.Clamp
    )
}

@Composable
fun pamietajPostepPolysku(durationMs: Int = 5200): Float {
    val t = rememberInfiniteTransition(label = "srebrnyPolysk")
    val p by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "postepPolysku"
    )
    return p
}

/**
 * Animowana ramka w kolorze znaku — obracający się sweepGradient + SrcIn
 * (wzorzec jak lokile/compose-animated-border Rotate + ShinyButton shine).
 */
fun Modifier.ramkaPolyskKoloru(
    bazowy: Color,
    grubosc: Dp = 2.dp,
    fazaStart: Float = 0f,
    durationMs: Int = 4800
): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "ramkaKoloru")
    val kat by transition.animateFloat(
        initialValue = fazaStart,
        targetValue = fazaStart + 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "katRamkiKoloru"
    )
    val kolory = remember(bazowy) {
        listOf(
            bazowy.copy(alpha = 0.12f),
            bazowy.copy(alpha = 0.55f),
            bazowy.copy(alpha = 0.95f),
            Color.White.copy(alpha = 0.98f),
            bazowy,
            bazowy.copy(alpha = 0.40f),
            Color.Black.copy(alpha = 0.45f),
            bazowy.copy(alpha = 0.18f)
        )
    }
    Modifier.drawWithCache {
        val stroke = grubosc.toPx()
        onDrawWithContent {
            drawContent()
            val r = (size.minDimension - stroke) / 2f
            // spokojna baza w kolorze znaku
            drawCircle(
                color = bazowy.copy(alpha = 0.35f),
                radius = r,
                style = Stroke(width = stroke * 0.85f)
            )
            // mieniący się błysk wokół obwodu
            with(drawContext.canvas.nativeCanvas) {
                val warstwa = saveLayer(null, null)
                drawCircle(
                    color = Color.White,
                    radius = r,
                    style = Stroke(width = stroke)
                )
                rotate(kat) {
                    drawCircle(
                        brush = Brush.sweepGradient(kolory),
                        radius = size.maxDimension,
                        blendMode = BlendMode.SrcIn
                    )
                }
                restoreToCount(warstwa)
            }
        }
    }
}

/** Połyskujący srebrny pędzel do tekstu (cyfry klawiatury / numer). */
@Composable
fun pamietajPedzelSrebrnegoTekstu(durationMs: Int = 3600): Brush {
    var size by remember { mutableStateOf(IntSize(120, 40)) }
    val progress = pamietajPostepPolysku(durationMs)
    return remember(size, progress) {
        pedzelSrebrnegoPolysku(
            size = size,
            progress = progress,
            colors = listOf(
                Color(0xFF7A8088),
                Color(0xFFB0B6BE),
                Color(0xFFE8EAEE),
                Color(0xFFFFFFFF),
                Color(0xFFE8EAEE),
                Color(0xFFB0B6BE),
                Color(0xFF7A8088)
            )
        )
    }
}

/** Tło chromu (przycisk dzwoń) — wolniejszy sweep. [animuj]=false → statyczny brush (listy). */
fun Modifier.tloSrebrnyPolysk(
    shape: Shape,
    durationMs: Int = 5200,
    animuj: Boolean = true
): Modifier = composed {
    if (!animuj) {
        return@composed this.background(
            brush = Brush.linearGradient(SrebrnyPolysk.KoloryChrome),
            shape = shape
        )
    }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val progress = pamietajPostepPolysku(durationMs)
    val brush = remember(size, progress) {
        pedzelSrebrnegoPolysku(size, progress, SrebrnyPolysk.KoloryChrome)
    }
    this
        .onSizeChanged { size = it }
        .background(brush = brush, shape = shape)
}

/** Tło czarnej stali (pasek zakładek). */
fun Modifier.tloCzarnaStal(
    shape: Shape,
    durationMs: Int = 7200
): Modifier = composed {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val progress = pamietajPostepPolysku(durationMs)
    val brush = remember(size, progress) {
        pedzelSrebrnegoPolysku(size, progress, SrebrnyPolysk.KoloryCzarnejStali)
    }
    this
        .onSizeChanged { size = it }
        .background(brush = brush, shape = shape)
}

/**
 * Kolor ikony mieniący się przez kolory znaków (Yrden→…→Aard).
 * [fazaStart] przesuwa fazę per zakładka.
 */
@Composable
fun pamietajKolorZnakuMieniacy(
    kolory: List<Color>,
    fazaStart: Float = 0f,
    durationMs: Int = 5600
): Color {
    if (kolory.isEmpty()) return Color.White
    if (kolory.size == 1) return kolory.first()
    val t = rememberInfiniteTransition(label = "znakMieni")
    val p by t.animateFloat(
        initialValue = 0f,
        targetValue = kolory.size.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "fazaZnaku"
    )
    val pos = (p + fazaStart).mod(kolory.size.toFloat())
    val i = pos.toInt().coerceIn(0, kolory.lastIndex)
    val j = (i + 1) % kolory.size
    val frac = pos - i
    return androidx.compose.ui.graphics.lerp(kolory[i], kolory[j], frac)
}

/**
 * Neonowa aureola — wzorowana na GlowNeonButton / Compose setShadowLayer
 * (SO: „Neon glowing button”). Miękka poświata + zewnętrzny glow.
 */
fun Modifier.aureolaSwiecaca(
    kolor: Color,
    intensywnosc: Float = 1f,
    rozmiar: Dp = 22.dp
): Modifier = drawBehind {
    val i = intensywnosc.coerceIn(0f, 1.4f)
    if (i <= 0.02f) return@drawBehind
    val r = rozmiar.toPx() * (0.85f + i * 0.35f)
    val c = center
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0.0f to kolor.copy(alpha = (0.72f * i).coerceAtMost(0.95f)),
                0.35f to kolor.copy(alpha = 0.38f * i),
                0.7f to kolor.copy(alpha = 0.12f * i),
                1.0f to Color.Transparent
            ),
            center = c,
            radius = r
        ),
        radius = r,
        center = c
    )
}

/**
 * Neonowa ramka (Paint.setShadowLayer) — jak w odpowiedziach Compose neon glow.
 */
fun Modifier.neonowaRamka(
    kolor: Color,
    promien: Dp,
    intensywnosc: Float = 1f,
    grubosc: Dp = 1.2.dp
): Modifier = drawWithContent {
    drawContent()
    val i = intensywnosc.coerceIn(0f, 1.5f)
    if (i <= 0.02f) return@drawWithContent
    val r = promien.toPx()
    val stroke = grubosc.toPx()
    val paint = Paint().apply {
        style = PaintingStyle.Stroke
        strokeWidth = stroke * 8f
    }
    val fp = paint.asFrameworkPaint()
    fp.color = android.graphics.Color.TRANSPARENT
    fp.setShadowLayer(
        18f * i,
        0f,
        0f,
        kolor.copy(alpha = (0.75f * i).coerceAtMost(0.95f)).toArgb()
    )
    drawIntoCanvas { canvas ->
        canvas.drawRoundRect(
            left = stroke,
            top = stroke,
            right = size.width - stroke,
            bottom = size.height - stroke,
            radiusX = r,
            radiusY = r,
            paint = paint
        )
    }
    drawRoundRect(
        color = kolor.copy(alpha = (0.55f + 0.35f * i).coerceAtMost(0.95f)),
        topLeft = Offset(stroke / 2f, stroke / 2f),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(r, r),
        style = Stroke(width = stroke)
    )
}

/**
 * Ramka: błysk biegnie PO OBRAMOWANIU od lewego dolnego rogu
 * w stronę prawego górnego (i dalej wokół).
 */
fun Modifier.ramkaSrebrnyPolysk(
    width: Dp = 1.5.dp,
    shape: Shape,
    durationMs: Int = 5600
): Modifier = composed {
    val progress = pamietajPostepPolysku(durationMs)
    val strokeDp = width
    Modifier.drawWithContent {
        drawContent()
        val stroke = strokeDp.toPx()
        val inset = stroke / 2f
        val w = size.width
        val h = size.height
        if (w <= stroke * 2 || h <= stroke * 2) return@drawWithContent

        val radius = when (shape) {
            is RoundedCornerShape -> min(w, h) * 0.12f
            CircleShape -> min(w, h) / 2f - inset
            else -> 0f
        }.coerceAtMost(min(w, h) / 2f - inset)

        val path = sciezkaOdLewegoDolnego(
            size = Size(w, h),
            inset = inset,
            radius = radius.coerceAtLeast(0f)
        )
        val measure = PathMeasure()
        measure.setPath(path, forceClosed = true)
        val len = measure.length
        if (len <= 0f) return@drawWithContent

        // spokojna bazowa ramka
        drawPath(
            path = path,
            color = MotywPaletyPresety.AkcentSrebrnyWiedzmin.copy(alpha = 0.28f),
            style = Stroke(width = stroke)
        )
        // biegnący błysk (~14% obwodu)
        val glowLen = (len * 0.14f).coerceIn(28f, len * 0.22f)
        val phase = -progress * len
        drawPath(
            path = path,
            brush = Brush.linearGradient(
                colors = SrebrnyPolysk.KoloryBlysk,
                start = Offset(0f, h),
                end = Offset(w, 0f)
            ),
            style = Stroke(
                width = stroke * 1.15f,
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(
                    floatArrayOf(glowLen, len),
                    phase
                )
            )
        )
    }
}

/**
 * Obwód zaczynający się w LEWYM DOLNYM rogu, zgodnie z ruchem wskazówek zegara:
 * w górę (lewa krawędź) → prawo (góra, przez prawy górny) → dół → lewo.
 */
private fun sciezkaOdLewegoDolnego(size: Size, inset: Float, radius: Float): Path {
    val left = inset
    val top = inset
    val right = size.width - inset
    val bottom = size.height - inset
    // Standardowy addRoundRect startuje u góry — budujemy ręcznie od BL.
    val r = radius
    return Path().apply {
        // start: dół lewej krawędzi (tuż nad łukiem BL) → w górę do TR…
        moveTo(left, bottom - r)
        lineTo(left, top + r)
        if (r > 0.5f) {
            arcTo(Rect(left, top, left + 2 * r, top + 2 * r), 180f, 90f, false)
        }
        lineTo(right - r, top)
        if (r > 0.5f) {
            arcTo(Rect(right - 2 * r, top, right, top + 2 * r), 270f, 90f, false)
        }
        lineTo(right, bottom - r)
        if (r > 0.5f) {
            arcTo(Rect(right - 2 * r, bottom - 2 * r, right, bottom), 0f, 90f, false)
        }
        lineTo(left + r, bottom)
        if (r > 0.5f) {
            arcTo(Rect(left, bottom - 2 * r, left + 2 * r, bottom), 90f, 90f, false)
        }
        close()
    }
}
