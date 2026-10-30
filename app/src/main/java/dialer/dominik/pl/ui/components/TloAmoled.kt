package dialer.dominik.pl.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.TextureView
import android.widget.FrameLayout
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import dialer.dominik.pl.R
import dialer.dominik.pl.ui.theme.AnimacjaTla
import dialer.dominik.pl.ui.theme.LocalMotyw
import dialer.dominik.pl.ui.theme.MotywStan
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

/**
 * Alfa przy zawijaniu pętli parametru 0..1 — zanika przy krańcach,
 * żeby nie było widać styku końca i początku cyklu.
 */
private fun alfaPetli01(t: Float, krawedz: Float = 0.14f): Float {
    val x = ((t % 1f) + 1f) % 1f
    return when {
        x < krawedz -> (x / krawedz).coerceIn(0f, 1f)
        x > 1f - krawedz -> ((1f - x) / krawedz).coerceIn(0f, 1f)
        else -> 1f
    }
}

/**
 * Tło aplikacji: kolor z motywu + wybrana animacja poświat
 * ([AnimacjaTla] w ustawieniach).
 *
 * Faza 0→2π + wyłącznie sin/cos = pętla bez „skoku”.
 * Ruch z modulo (deszcz, iskry, CRT) tłumi [alfaPetli01].
 *
 * @param animuj gdy false — statyczna klatka (oszczędność GPU w tle).
 */
@Composable
fun TloAmoled(
    modifier: Modifier = Modifier,
    animuj: Boolean = true
) {
    val motyw = LocalMotyw.current
    val styl = motyw.animacjaTla
    if (styl == AnimacjaTla.Wylaczona || !motyw.auroraWlaczona) {
        Box(modifier = modifier.fillMaxSize().background(motyw.tlo))
        return
    }
    if (styl == AnimacjaTla.Wiedzmin) {
        TloWiedzmin(modifier = modifier, animuj = animuj, tlo = motyw.tlo)
        return
    }
    if (animuj) {
        TloAmoledAnimowane(modifier, styl)
    } else {
        TloAmoledStatyczne(modifier, styl)
    }
}

/**
 * Tło motywu Wiedźmin:
 * - własne zdjęcie (ustawienia),
 * - albo statyczny kadr z filmu,
 * - albo pętla wideo z `res/raw/wiedzmin_tlo`.
 */
@Composable
private fun TloWiedzmin(
    modifier: Modifier,
    animuj: Boolean,
    tlo: Color
) {
    val context = LocalContext.current
    val stan by MotywStan.stan.collectAsState()
    val wlasne = stan.wiedzminTloWlasneSciezka
    val statyczne = stan.wiedzminTloStatyczne || !animuj

    Box(modifier = modifier.fillMaxSize().background(tlo)) {
        when {
            !wlasne.isNullOrBlank() && File(wlasne).isFile -> {
                val bmp = remember(wlasne) {
                    runCatching {
                        BitmapFactory.decodeFile(wlasne)?.asImageBitmap()
                    }.getOrNull()
                }
                if (bmp != null) {
                    Image(
                        bitmap = bmp,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
            statyczne -> {
                val kadr = remember(context) {
                    runCatching {
                        kadrZWiedzminTlo(context)?.asImageBitmap()
                    }.getOrNull()
                }
                if (kadr != null) {
                    Image(
                        bitmap = kadr,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
            else -> {
                val kontroler = remember { WiedzminTloKontroler(context) }
                DisposableEffect(Unit) {
                    onDispose { kontroler.zwolnij() }
                }
                DisposableEffect(animuj) {
                    kontroler.ustawAnimacje(animuj)
                    onDispose { }
                }
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx -> kontroler.utworzWidok(ctx) },
                    update = { kontroler.odswiezCrop() }
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.42f))
        )
    }
}

private fun kadrZWiedzminTlo(context: android.content.Context): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        val afd = context.resources.openRawResourceFd(R.raw.wiedzmin_tlo)
        retriever.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
        afd.close()
        retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
    } catch (_: Exception) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}

/** Crossfade pętli: aktywny film + zapasowy startujący od 0 przed końcem. */
private class WiedzminTloKontroler(context: android.content.Context) {
    private val appCtx = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    /** Fade przy pętli — film jest już wolniejszy w pliku (bez setSpeed). */
    private val fadeMs = 2800L
    private val tickMs = 32L
    /** 1.0 — płynne klatki; spowolnienie jest w encode (setpts), nie w playerze. */
    private val predkoscOdtwarzania = 1.0f

    private val players: Array<MediaPlayer?> = arrayOf(
        MediaPlayer.create(appCtx, R.raw.wiedzmin_tlo),
        MediaPlayer.create(appCtx, R.raw.wiedzmin_tlo)
    )
    private val textures = arrayOfNulls<TextureView>(2)
    private var aktywny = 0
    private var wTrakcieFade = false
    private var animuj = true
    private var root: FrameLayout? = null

    private val ticker = object : Runnable {
        override fun run() {
            if (!animuj) return
            val p = players[aktywny] ?: return
            val dur = runCatching { p.duration.toLong() }.getOrDefault(0L)
            val pos = runCatching { p.currentPosition.toLong() }.getOrDefault(0L)
            if (dur > fadeMs && !wTrakcieFade && pos >= dur - fadeMs) {
                startFade()
            }
            handler.postDelayed(this, tickMs)
        }
    }

    init {
        players.forEach { mp ->
            mp?.apply {
                isLooping = false
                setVolume(0f, 0f)
                ustawPredkosc(this)
            }
        }
    }

    private fun ustawPredkosc(mp: MediaPlayer) {
        runCatching {
            mp.playbackParams = PlaybackParams().setSpeed(predkoscOdtwarzania)
        }
    }

    fun utworzWidok(ctx: android.content.Context): FrameLayout {
        val fl = FrameLayout(ctx)
        root = fl
        repeat(2) { i ->
            val tv = TextureView(ctx)
            tv.alpha = if (i == aktywny) 1f else 0f
            textures[i] = tv
            podlacz(i, tv)
            fl.addView(
                tv,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
        }
        handler.post(ticker)
        return fl
    }

    private fun podlacz(indeks: Int, tv: TextureView) {
        val player = players[indeks] ?: return
        player.setOnVideoSizeChangedListener { _, _, _ ->
            tv.post { dopasujCenterCrop(tv, player.videoWidth, player.videoHeight) }
        }
        tv.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, w: Int, h: Int) {
                player.setSurface(Surface(surface))
                dopasujCenterCrop(tv, player.videoWidth, player.videoHeight)
                if (indeks == aktywny && animuj) {
                    runCatching {
                        ustawPredkosc(player)
                        if (!player.isPlaying) player.start()
                    }
                }
            }

            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, w: Int, h: Int) {
                dopasujCenterCrop(tv, player.videoWidth, player.videoHeight)
            }

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                player.setSurface(null)
                return true
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
        }
    }

    fun odswiezCrop() {
        textures.forEachIndexed { i, tv ->
            val p = players[i] ?: return@forEachIndexed
            if (tv != null) dopasujCenterCrop(tv, p.videoWidth, p.videoHeight)
        }
    }

    fun ustawAnimacje(wlacz: Boolean) {
        animuj = wlacz
        handler.removeCallbacks(ticker)
        if (wlacz) {
            runCatching {
                val p = players[aktywny]
                if (p != null) {
                    ustawPredkosc(p)
                    if (!p.isPlaying) p.start()
                }
            }
            handler.post(ticker)
        } else {
            players.forEach { runCatching { if (it?.isPlaying == true) it.pause() } }
        }
    }

    private fun startFade() {
        val z = aktywny
        val na = 1 - z
        val pZ = players[z] ?: return
        val pNa = players[na] ?: return
        val tvZ = textures[z] ?: return
        val tvNa = textures[na] ?: return
        wTrakcieFade = true
        runCatching {
            pNa.seekTo(0)
            ustawPredkosc(pNa)
            if (!pNa.isPlaying) pNa.start()
            // po start() niektóre OEM resetują speed — ustaw jeszcze raz
            ustawPredkosc(pNa)
        }
        tvNa.alpha = 0f
        val t0 = android.os.SystemClock.uptimeMillis()
        val krok = object : Runnable {
            override fun run() {
                val t = ((android.os.SystemClock.uptimeMillis() - t0).toFloat() / fadeMs)
                    .coerceIn(0f, 1f)
                // smoothstep — mniej „ostro” niż liniowo
                val s = t * t * (3f - 2f * t)
                tvZ.alpha = 1f - s
                tvNa.alpha = s
                if (t < 1f) {
                    handler.postDelayed(this, tickMs)
                } else {
                    tvZ.alpha = 0f
                    tvNa.alpha = 1f
                    runCatching { pZ.pause(); pZ.seekTo(0) }
                    aktywny = na
                    wTrakcieFade = false
                }
            }
        }
        handler.post(krok)
    }

    fun zwolnij() {
        handler.removeCallbacksAndMessages(null)
        players.forEach { mp ->
            runCatching {
                mp?.stop()
                mp?.release()
            }
        }
        players[0] = null
        players[1] = null
        textures[0] = null
        textures[1] = null
        root = null
    }
}

/**
 * TextureView najpierw rozciąga klatkę na cały widok — transformacja
 * skaluje od środka tak, by zachować proporcje i wykropkować (center-crop),
 * bez widocznej krawędzi / pasków.
 */
private fun dopasujCenterCrop(
    textureView: TextureView,
    videoW: Int,
    videoH: Int
) {
    val viewW = textureView.width
    val viewH = textureView.height
    if (viewW <= 0 || viewH <= 0 || videoW <= 0 || videoH <= 0) return
    val viewRatio = viewW.toFloat() / viewH.toFloat()
    val videoRatio = videoW.toFloat() / videoH.toFloat()
    var scaleX = 1f
    var scaleY = 1f
    if (videoRatio > viewRatio) {
        // film szerszy niż ekran → przytnij boki
        scaleX = videoRatio / viewRatio
    } else {
        // film wyższy / węższy → przytnij górę/dół
        scaleY = viewRatio / videoRatio
    }
    // lekki zapas, żeby nie było 1px szwu na krawędziach
    scaleX *= 1.02f
    scaleY *= 1.02f
    val m = Matrix()
    m.setScale(scaleX, scaleY, viewW / 2f, viewH / 2f)
    textureView.setTransform(m)
}

@Composable
private fun TloAmoledAnimowane(modifier: Modifier, styl: AnimacjaTla) {
    val animacja = rememberInfiniteTransition(label = "tloAmoled")
    val faza by animacja.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (styl) {
                    AnimacjaTla.Iskry -> 22_000
                    AnimacjaTla.Fale -> 16_000
                    AnimacjaTla.Siatka -> 12_000
                    AnimacjaTla.PipBoy -> 10_000
                    AnimacjaTla.Wiedzmin -> 10_000
                    AnimacjaTla.Orbity -> 36_000
                    else -> 48_000
                },
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "fazaTla"
    )
    val puls by animacja.animateFloat(
        initialValue = 0.78f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (styl) {
                    AnimacjaTla.Siatka -> 6_000
                    AnimacjaTla.Iskry -> 4_500
                    AnimacjaTla.PipBoy -> 900
                    AnimacjaTla.Wiedzmin -> 900
                    else -> 14_000
                },
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulsTla"
    )
    TloAmoledCanvas(modifier, styl, faza, puls)
}

@Composable
private fun TloAmoledStatyczne(modifier: Modifier, styl: AnimacjaTla) {
    TloAmoledCanvas(modifier, styl, faza = 0.8f, puls = 0.9f)
}

@Composable
private fun TloAmoledCanvas(
    modifier: Modifier,
    styl: AnimacjaTla,
    faza: Float,
    puls: Float
) {
    val motyw = LocalMotyw.current
    val tlo = motyw.tlo
    val a1 = motyw.aurora1
    val a2 = motyw.aurora2
    val accent = motyw.accent
    val sila = if (motyw.ciemny) 1f else 0.45f
    val vaultBoy: ImageBitmap? =
        if (styl == AnimacjaTla.PipBoy) ImageBitmap.imageResource(R.drawable.vault_dweller_thumbs) else null
    val pipBoy: ImageBitmap? =
        if (styl == AnimacjaTla.PipBoy) ImageBitmap.imageResource(R.drawable.pipboy_device) else null

    Box(modifier = modifier.fillMaxSize().background(tlo)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val maxR = maxOf(w, h)

            when (styl) {
                AnimacjaTla.Wylaczona -> Unit

                AnimacjaTla.Aurora -> rysujAurora(w, h, maxR, faza, puls, sila, a1, a2, tlo, motyw.ciemny)

                AnimacjaTla.Orbity -> rysujOrbity(w, h, maxR, faza, puls, sila, a1, a2, accent, tlo, motyw.ciemny)

                AnimacjaTla.Fale -> rysujFale(w, h, faza, puls, sila, a1, a2, accent)

                AnimacjaTla.Siatka -> rysujSiatke(w, h, faza, puls, sila, accent, a2)

                AnimacjaTla.Iskry -> rysujIskry(w, h, faza, puls, sila, accent, a1, a2)

                AnimacjaTla.PipBoy -> rysujPipBoy(
                    w, h, faza, puls, sila,
                    fosfor = a2,
                    zolty = accent,
                    niebieski = motyw.accentMorski,
                    tlo = tlo,
                    ciemny = motyw.ciemny,
                    vaultBoy = vaultBoy,
                    pipBoy = pipBoy
                )

                // Obsługiwane osobno w [TloWiedzminWideo].
                AnimacjaTla.Wiedzmin -> Unit
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.rysujAurora(
    w: Float,
    h: Float,
    maxR: Float,
    faza: Float,
    puls: Float,
    sila: Float,
    a1: Color,
    a2: Color,
    tlo: Color,
    ciemny: Boolean
) {
    val srodekLewy = Offset(
        x = w * 0.18f + cos(faza) * w * 0.10f,
        y = h * 0.22f + sin(faza) * h * 0.07f
    )
    // Trzecia, wolniejsza plama — głębia (wcześniej tylko 2)
    val srodekDol = Offset(
        x = w * 0.55f + cos(faza * 0.55f + 1.2f) * w * 0.12f,
        y = h * 0.78f + sin(faza * 0.7f) * h * 0.05f
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                a2.copy(alpha = 0.22f * puls * sila),
                a1.copy(alpha = 0.08f * puls * sila),
                Color.Transparent
            ),
            center = srodekDol,
            radius = maxR * 0.55f
        ),
        radius = maxR * 0.55f,
        center = srodekDol
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                a1.copy(alpha = 0.55f * puls * sila),
                a1.copy(alpha = 0.16f * puls * sila),
                Color.Transparent
            ),
            center = srodekLewy,
            radius = maxR * 0.62f
        ),
        radius = maxR * 0.62f,
        center = srodekLewy
    )
    val srodekPrawy = Offset(
        x = w * 0.86f - cos(faza) * w * 0.09f,
        y = h * 0.78f - sin(faza) * h * 0.08f
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                a2.copy(alpha = 0.20f * (1.72f - puls) * sila),
                a2.copy(alpha = 0.05f * (1.72f - puls) * sila),
                Color.Transparent
            ),
            center = srodekPrawy,
            radius = maxR * 0.52f
        ),
        radius = maxR * 0.52f,
        center = srodekPrawy
    )
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.Transparent,
                tlo.copy(alpha = if (ciemny) 0.85f else 0.35f)
            ),
            center = Offset(w / 2f, h / 2f),
            radius = maxR * 0.78f
        )
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.rysujOrbity(
    w: Float,
    h: Float,
    maxR: Float,
    faza: Float,
    puls: Float,
    sila: Float,
    a1: Color,
    a2: Color,
    accent: Color,
    tlo: Color,
    ciemny: Boolean
) {
    val cx = w / 2f
    val cy = h / 2f
    val orby = listOf(
        Triple(a1, 0.28f * maxR, faza),
        Triple(a2, 0.38f * maxR, -faza * 1.3f),
        Triple(accent, 0.22f * maxR, faza * 0.7f + 1.2f)
    )
    orby.forEachIndexed { i, (kolor, promien, kat) ->
        val ox = cx + cos(kat) * promien
        val oy = cy + sin(kat * (1f + i * 0.15f)) * promien * 0.72f
        val r = maxR * (0.28f + i * 0.06f) * puls
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    kolor.copy(alpha = 0.42f * sila),
                    kolor.copy(alpha = 0.08f * sila),
                    Color.Transparent
                ),
                center = Offset(ox, oy),
                radius = r
            ),
            radius = r,
            center = Offset(ox, oy)
        )
    }
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(Color.Transparent, tlo.copy(alpha = if (ciemny) 0.8f else 0.3f)),
            center = Offset(cx, cy),
            radius = maxR * 0.85f
        )
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.rysujFale(
    w: Float,
    h: Float,
    faza: Float,
    puls: Float,
    sila: Float,
    a1: Color,
    a2: Color,
    accent: Color
) {
    val kolory = listOf(a1, accent, a2)
    for (i in 0 until 3) {
        val yBaza = h * (0.22f + i * 0.28f)
        val amp = h * 0.045f * (1.1f - i * 0.15f) * puls
        val path = Path()
        path.moveTo(0f, yBaza)
        var x = 0f
        while (x <= w) {
            val y = yBaza + sin(x / w * 4f * Math.PI.toFloat() + faza + i) * amp
            path.lineTo(x, y)
            x += 8f
        }
        drawPath(
            path = path,
            color = kolory[i].copy(alpha = (0.22f - i * 0.04f) * sila),
            style = Stroke(
                width = 3.5f + i * 1.2f,
                pathEffect = PathEffect.cornerPathEffect(12f)
            )
        )
        // miękka poświata pod falą
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    kolory[i].copy(alpha = 0.18f * sila * puls),
                    Color.Transparent
                ),
                center = Offset(w * (0.3f + i * 0.2f), yBaza),
                radius = w * 0.45f
            ),
            radius = w * 0.45f,
            center = Offset(w * (0.3f + i * 0.2f), yBaza)
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.rysujSiatke(
    w: Float,
    h: Float,
    faza: Float,
    puls: Float,
    sila: Float,
    accent: Color,
    a2: Color
) {
    val krok = 48f
    val alfaLinii = 0.10f * sila * puls
    var x = 0f
    while (x <= w) {
        val przesun = sin(faza + x / w * 3f) * 6f
        drawLine(
            color = accent.copy(alpha = alfaLinii),
            start = Offset(x + przesun, 0f),
            end = Offset(x - przesun, h),
            strokeWidth = 1.2f
        )
        x += krok
    }
    var y = 0f
    while (y <= h) {
        val przesun = cos(faza + y / h * 3f) * 6f
        drawLine(
            color = a2.copy(alpha = alfaLinii * 0.85f),
            start = Offset(0f, y + przesun),
            end = Offset(w, y - przesun),
            strokeWidth = 1.2f
        )
        y += krok
    }
    // węzły
    x = krok
    while (x < w) {
        y = krok
        while (y < h) {
            val p = (sin(faza * 2f + x * 0.02f + y * 0.015f) + 1f) * 0.5f
            if (p > 0.55f) {
                drawCircle(
                    color = accent.copy(alpha = 0.25f * sila * p * puls),
                    radius = 2.2f + p * 2f,
                    center = Offset(x, y)
                )
            }
            y += krok
        }
        x += krok
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.rysujIskry(
    w: Float,
    h: Float,
    faza: Float,
    puls: Float,
    sila: Float,
    accent: Color,
    a1: Color,
    a2: Color
) {
    val kolory = listOf(accent, a1, a2)
    // Deterministyczne „gwiazdki” — bez Random przy każdej klatce.
    for (i in 0 until 28) {
        val seed = i * 17.13f
        val bx = ((sin(seed * 1.7f) + 1f) * 0.5f) * w
        val by = ((cos(seed * 2.3f) + 1f) * 0.5f) * h
        val dryfX = cos(faza + seed) * 18f
        val dryfY = sin(faza * 0.8f + seed) * 24f - (faza % 6.28f) / 6.28f * 40f
        val mig = ((sin(faza * 3f + seed) + 1f) * 0.5f)
        val r = (1.5f + (i % 4) * 1.1f) * (0.6f + 0.4f * puls)
        val kolor = kolory[i % kolory.size]
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    kolor.copy(alpha = 0.55f * sila * mig),
                    kolor.copy(alpha = 0.08f * sila * mig),
                    Color.Transparent
                ),
                center = Offset(bx + dryfX, (by + dryfY + h) % h),
                radius = r * 6f
            ),
            radius = r * 6f,
            center = Offset(bx + dryfX, (by + dryfY + h) % h)
        )
        drawCircle(
            color = kolor.copy(alpha = 0.7f * sila * mig),
            radius = r,
            center = Offset(bx + dryfX, (by + dryfY + h) % h)
        )
    }
}


/**
 * Tapeta Pip-Boy: prawdziwe grafiki (vault dweller + urządzenie) + warstwa CRT.
 * Assety: drawable-nodpi/vault_dweller_thumbs, pipboy_device (+ CC0 pixel w ATTRIBUTION).
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.rysujPipBoy(
    w: Float,
    h: Float,
    faza: Float,
    puls: Float,
    sila: Float,
    fosfor: Color,
    zolty: Color,
    niebieski: Color,
    tlo: Color,
    ciemny: Boolean,
    vaultBoy: ImageBitmap?,
    pipBoy: ImageBitmap?
) {
    val alfaBazy = if (ciemny) 1f else 0.55f
    val flicker = 0.88f + 0.12f * puls + 0.04f * sin(faza * 11f)
    val a = sila * alfaBazy * flicker.coerceIn(0.75f, 1.08f)
    val cx = w * 0.5f
    val cy = h * 0.40f

    // Ambient fosfor
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                fosfor.copy(alpha = 0.30f * a * puls),
                fosfor.copy(alpha = 0.08f * a),
                Color.Transparent
            ),
            center = Offset(cx, cy),
            radius = maxOf(w, h) * 0.65f
        ),
        radius = maxOf(w, h) * 0.65f,
        center = Offset(cx, cy)
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(niebieski.copy(alpha = 0.18f * a), Color.Transparent),
            center = Offset(w * 0.15f, h * 0.12f),
            radius = maxOf(w, h) * 0.38f
        ),
        radius = maxOf(w, h) * 0.38f,
        center = Offset(w * 0.15f, h * 0.12f)
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(zolty.copy(alpha = 0.12f * a), Color.Transparent),
            center = Offset(w * 0.88f, h * 0.16f),
            radius = maxOf(w, h) * 0.30f
        ),
        radius = maxOf(w, h) * 0.30f,
        center = Offset(w * 0.88f, h * 0.16f)
    )

    // Vault dweller (grafika) — lekki bob + tint fosforu
    val boy = vaultBoy
    if (boy != null) {
        val bob = sin(faza * 2.1f) * (h * 0.012f)
        val targetH = h * 0.52f
        val scale = targetH / boy.height.toFloat()
        val dw = (boy.width * scale).toInt().coerceAtLeast(1)
        val dh = (boy.height * scale).toInt().coerceAtLeast(1)
        val left = ((w - dw) / 2f).toInt()
        val top = ((cy - dh * 0.45f) + bob).toInt()
        drawImage(
            image = boy,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(boy.width, boy.height),
            dstOffset = IntOffset(left, top),
            dstSize = IntSize(dw, dh),
            alpha = (0.55f + 0.35f * puls) * a,
            colorFilter = ColorFilter.tint(fosfor.copy(alpha = 0.92f), BlendMode.Modulate),
            filterQuality = FilterQuality.Medium
        )
    }

    // Pip-Boy device (prawy dół)
    val device = pipBoy
    if (device != null) {
        val sk = minOf(w, h) * 0.42f
        val scale = sk / device.width.toFloat()
        val dw = (device.width * scale).toInt().coerceAtLeast(1)
        val dh = (device.height * scale).toInt().coerceAtLeast(1)
        val left = (w * 0.58f).toInt()
        val top = (h * 0.62f).toInt()
        withTransform({
            translate(left.toFloat() + dw / 2f, top.toFloat() + dh / 2f)
            rotate(degrees = sin(faza * 0.35f) * 3f, pivot = Offset.Zero)
            translate(-dw / 2f, -dh / 2f)
        }) {
            drawImage(
                image = device,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(device.width, device.height),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(dw, dh),
                alpha = 0.92f * a,
                filterQuality = FilterQuality.Medium
            )
            // żółty rant Vault-Tec
            drawRoundRect(
                color = zolty.copy(alpha = 0.35f * a * puls),
                topLeft = Offset(0f, 0f),
                size = Size(dw.toFloat(), dh.toFloat()),
                cornerRadius = CornerRadius(dw * 0.06f, dw * 0.06f),
                style = Stroke(width = 2.5f)
            )
        }
    }

    // Paski HP / AP / RAD
    val pasekY = h * 0.78f
    val pasekH = h * 0.015f
    val pasekW = w * 0.55f
    val pasekX = w * 0.08f
    fun pasek(oy: Float, fill: Float, kolor: Color) {
        drawRect(fosfor.copy(alpha = 0.12f * a), Offset(pasekX, pasekY + oy), Size(pasekW, pasekH))
        drawRect(kolor.copy(alpha = 0.7f * a * puls), Offset(pasekX, pasekY + oy), Size(pasekW * fill.coerceIn(0.05f, 1f), pasekH))
        drawRect(fosfor.copy(alpha = 0.5f * a), Offset(pasekX, pasekY + oy), Size(pasekW, pasekH), style = Stroke(1.2f))
    }
    pasek(0f, 0.78f + 0.05f * sin(faza * 0.7f), fosfor)
    pasek(pasekH * 2.3f, 0.55f + 0.12f * sin(faza * 1.3f), fosfor)
    pasek(pasekH * 4.6f, 0.22f + 0.1f * ((sin(faza * 3.2f) + 1f) * 0.5f), zolty)

    // —— CRT overlay ——
    val scanGap = 5f
    var sy = ((faza * 22f) % scanGap)
    while (sy < h) {
        drawRect(Color.Black.copy(alpha = 0.16f * a), Offset(0f, sy), Size(w, 1.5f))
        sy += scanGap
    }
    // CRT sweep — płynna pętla (zanik przy zawinięciu)
    val sweepT = ((faza / (2f * Math.PI.toFloat())) % 1f + 1f) % 1f
    val beamFade = alfaPetli01(sweepT, 0.12f)
    val beamY = sweepT * (h + 80f) - 40f
    drawRect(
        brush = Brush.verticalGradient(
            listOf(
                Color.Transparent,
                fosfor.copy(alpha = 0.20f * a * puls * beamFade),
                Color.Transparent
            ),
            startY = beamY - 32f,
            endY = beamY + 32f
        ),
        topLeft = Offset(0f, beamY - 32f),
        size = Size(w, 64f)
    )
    drawLine(
        Color.Black.copy(alpha = 0.3f * a * beamFade),
        Offset(0f, beamY),
        Offset(w, beamY),
        2.2f
    )

    for (i in 0 until 28) {
        val seed = i * 19.17f + faza * 3f
        val nx = ((sin(seed * 2.1f) + 1f) * 0.5f) * w
        val ny = ((cos(seed * 1.7f) + 1f) * 0.5f) * h
        val mig = ((sin(faza * 17f + seed) + 1f) * 0.5f)
        drawRect(fosfor.copy(alpha = 0.10f * a * mig), Offset(nx, ny), Size(1.5f + (i % 3), 1.5f))
    }

    val glitch = sin(faza * 7.3f)
    if (glitch > 0.93f) {
        val gy = ((sin(faza * 13f) + 1f) * 0.5f) * h
        val shift = (glitch - 0.93f) * 90f
        drawRect(fosfor.copy(alpha = 0.14f * a), Offset(shift, gy), Size(w, 5f))
    }

    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.Transparent,
                fosfor.copy(alpha = 0.06f * a),
                Color.Black.copy(alpha = if (ciemny) 0.55f * a else 0.22f * a)
            ),
            center = Offset(cx, cy),
            radius = maxOf(w, h) * 0.78f
        )
    )
}

