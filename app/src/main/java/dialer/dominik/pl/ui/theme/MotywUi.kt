package dialer.dominik.pl.ui.theme

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.widget.ImageView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import dialer.dominik.pl.R
import kotlin.math.cos
import kotlin.math.sin

/** Tekst główny zależny od motywu (zamiast sztywnego Color.White). */
@Composable
fun motywTekst(alpha: Float = 1f): Color =
    LocalMotyw.current.naTle.copy(alpha = alpha)

@Composable
fun motywTekstDrugi(alpha: Float = 1f): Color =
    LocalMotyw.current.tekstDrugorzedny.copy(alpha = alpha.coerceIn(0f, 1f))

@Composable
fun motywKafelek(): Color = LocalMotyw.current.kafelek

@Composable
fun motywRamka(): Color = LocalMotyw.current.kafelekRamka

/** Neon / srebro — główny akcent UI zależny od palety (Wiedźmin = srebro). */
@Composable
fun motywAkcent(): Color = LocalMotyw.current.ikona

@Composable
fun motywAkcentMorski(): Color = LocalMotyw.current.accentMorski

@Composable
fun czyMotywWiedzmin(): Boolean {
    val stan by MotywStan.stan.collectAsState()
    return stan.paletaId == "wiedzmin"
}

/** Klasyczny gradient kafelka (góra → dół), czytelny w jasnym i ciemnym motywie. */
fun MotywPaleta.gradientKafelka(alfaGora: Float = 1f): Brush {
    val gora = kafelek.copy(alpha = alfaGora.coerceIn(0f, 1f))
    val dol = if (ciemny) {
        Color(
            red = (kafelek.red * 0.52f).coerceIn(0f, 1f),
            green = (kafelek.green * 0.58f).coerceIn(0f, 1f),
            blue = (kafelek.blue * 0.55f).coerceIn(0f, 1f),
            alpha = 1f
        )
    } else {
        Color(
            red = (kafelek.red * 0.90f).coerceIn(0f, 1f),
            green = (kafelek.green * 0.90f).coerceIn(0f, 1f),
            blue = (kafelek.blue * 0.90f).coerceIn(0f, 1f),
            alpha = 1f
        )
    }
    return Brush.verticalGradient(listOf(gora, dol))
}

/**
 * Lekka mgła — wolniejsze, miękkie smugi (bez „plamkowych” kół).
 * Rysować POD treścią, NAD brukiem / tłem kafelka.
 */
@Composable
fun MglaNaKafelku(
    modifier: Modifier = Modifier,
    sila: Float = 1f
) {
    val transition = rememberInfiniteTransition(label = "mglaKafelek")
    val faza by transition.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(22_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "fazaMgły"
    )
    val s = sila.coerceIn(0.25f, 1.3f)
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        // poziome smugi mgły (bardziej „las” niż kółka)
        val smugi = listOf(
            Triple(0.25f, 0.72f, 0.55f),
            Triple(0.70f, 0.40f, 0.48f),
            Triple(0.45f, 0.88f, 0.60f)
        )
        smugi.forEachIndexed { i, (bx, by, rNorm) ->
            val phase = faza * (0.55f + i * 0.12f) + i * 0.9f
            val cx = w * (bx + cos(phase) * 0.06f)
            val cy = h * (by + sin(phase * 0.7f) * 0.05f)
            val rx = w * rNorm
            val ry = h * (rNorm * 0.38f)
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.07f * s),
                        Color(0xFFB0B8C0).copy(alpha = 0.04f * s),
                        Color.Transparent
                    ),
                    center = Offset(cx, cy),
                    radius = maxOf(rx, ry)
                ),
                topLeft = Offset(cx - rx, cy - ry),
                size = androidx.compose.ui.geometry.Size(rx * 2f, ry * 2f)
            )
        }
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.03f * s),
                    Color(0xFF9AA8B0).copy(alpha = 0.07f * s)
                )
            )
        )
    }
}

/**
 * Mgła z GIF — **współdzielony** drawable (max 2 w procesie).
 * [lekka]=true → tylko lekki Canvas (listy / awatary — bez GIF-ów na wiersz).
 */
@Composable
fun MglaNaPrzycisku(
    modifier: Modifier = Modifier,
    sila: Float = 1f,
    seed: Int = 0,
    lekka: Boolean = false
) {
    if (lekka) {
        MglaNaKafelku(modifier = modifier.fillMaxSize(), sila = sila * 0.75f)
        return
    }
    val context = LocalContext.current
    val cyklMs = 10_500
    // Jedna współdzielona para GIF — nie dekoduj per kafelek.
    val warstwaA = remember { MglaGifCache.warstwaA(context) }
    val warstwaB = remember { MglaGifCache.warstwaB(context) }
    DisposableEffect(warstwaA, warstwaB) {
        MglaGifCache.retain()
        warstwaA?.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
        warstwaB?.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
        if (warstwaA?.isRunning != true) warstwaA?.start()
        val h = android.os.Handler(android.os.Looper.getMainLooper())
        val startB = Runnable {
            if (warstwaB?.isRunning != true) warstwaB?.start()
        }
        h.postDelayed(startB, (cyklMs / 2L) + (seed % 7) * 40L)
        onDispose {
            h.removeCallbacks(startB)
            MglaGifCache.release()
        }
    }
    val transition = rememberInfiniteTransition(label = "mglaCross")
    val faza by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(cyklMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "fazaMgly"
    )
    val mix = 0.5f + 0.5f * kotlin.math.sin(faza * (Math.PI * 2).toFloat())
    val baza = (0.62f * sila.coerceIn(0.35f, 1.4f)).coerceIn(0.28f, 0.92f)
    if (warstwaA != null) {
        Box(modifier = modifier.fillMaxSize()) {
            AndroidView(
                factory = { ctx ->
                    ImageView(ctx).apply {
                        setImageDrawable(warstwaA)
                        scaleType = ImageView.ScaleType.CENTER_CROP
                    }
                },
                update = { if (it.drawable !== warstwaA) it.setImageDrawable(warstwaA) },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = baza * (0.35f + 0.65f * mix)
                        blendMode = BlendMode.Screen
                    }
            )
            if (warstwaB != null) {
                AndroidView(
                    factory = { ctx ->
                        ImageView(ctx).apply {
                            setImageDrawable(warstwaB)
                            scaleType = ImageView.ScaleType.CENTER_CROP
                        }
                    },
                    update = { if (it.drawable !== warstwaB) it.setImageDrawable(warstwaB) },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = baza * (0.35f + 0.65f * (1f - mix))
                            blendMode = BlendMode.Screen
                        }
                )
            }
        }
    } else {
        MglaNaKafelku(modifier = modifier.fillMaxSize(), sila = sila)
    }
}

/** Cache GIF mgły — jedno dekodowanie na proces. */
private object MglaGifCache {
    private var a: AnimatedImageDrawable? = null
    private var b: AnimatedImageDrawable? = null
    private var refs = 0

    @Synchronized
    fun warstwaA(context: android.content.Context): AnimatedImageDrawable? {
        if (a == null) a = dekodujMglaGif(context.applicationContext)
        return a
    }

    @Synchronized
    fun warstwaB(context: android.content.Context): AnimatedImageDrawable? {
        if (b == null) b = dekodujMglaGif(context.applicationContext)
        return b
    }

    @Synchronized
    fun retain() {
        refs++
    }

    @Synchronized
    fun release() {
        refs = (refs - 1).coerceAtLeast(0)
        if (refs == 0) {
            a?.stop()
            b?.stop()
        }
    }
}

private fun dekodujMglaGif(context: android.content.Context): AnimatedImageDrawable? =
    runCatching {
        val src = ImageDecoder.createSource(context.resources, R.raw.mgla_wiedzmin)
        ImageDecoder.decodeDrawable(src) { decoder, _, _ ->
            decoder.setTargetSampleSize(3)
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        } as? AnimatedImageDrawable
    }.getOrNull()

/** Tekst zawsze czytelny na ciemnym pasku/dekoracji (nie zależy od jasnego motywu). */
val TekstNaCiemnym = Color.White
val TekstNaCiemnymDrugi = Color.White.copy(alpha = 0.85f)
