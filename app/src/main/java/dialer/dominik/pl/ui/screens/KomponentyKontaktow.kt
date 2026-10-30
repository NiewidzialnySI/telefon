package dialer.dominik.pl.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.telecom.TelecomManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dialer.dominik.pl.sms.KanalWiadomosci
import dialer.dominik.pl.sms.czyKanalDostepny
import dialer.dominik.pl.sms.nazwa
import dialer.dominik.pl.sms.pobierzKanal
import dialer.dominik.pl.sms.pobierzKontoKomunikatora
import dialer.dominik.pl.sms.pobierzNumerDoDzwonienia
import dialer.dominik.pl.sms.pobierzNumerDoWiadomosci
import dialer.dominik.pl.sms.zapamietajNumerDoDzwonienia
import dialer.dominik.pl.sms.zapamietajNumerDoWiadomosci
import dialer.dominik.pl.sms.wyslijPrzezKomunikator
import dialer.dominik.pl.sms.zapamietajKanal
import dialer.dominik.pl.sms.zapamietajKontoKomunikatora
import dialer.dominik.pl.ui.components.IkonyAplikacji
import dialer.dominik.pl.ui.theme.CzarnyAmoled
import dialer.dominik.pl.ui.theme.MglaNaPrzycisku
import dialer.dominik.pl.ui.theme.ZielonyMorski
import dialer.dominik.pl.ui.theme.ZielonyNeon
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Wariant awatara — [Wiedzmin] = obramowanie + świecący inicjał jak znaki. */
internal enum class AwatarWariant { Domyslny, Wiedzmin }

/** Kolory znaków wg kolejności ulubionych: 1 Yrden, 2 Quen, 3 Igni, 4 Axii, 5 Aard. */
internal val KoloryZnakowWiedzmina: List<Color> = listOf(
    Color(0xFF8B4DFF), // Yrden
    Color(0xFFE0B84A), // Quen
    Color(0xFFE53935), // Igni
    Color(0xFF69F0AE), // Axii
    Color(0xFF40C4FF)  // Aard
)

internal fun kolorZnakuWiedzmina(indeks: Int): Color =
    KoloryZnakowWiedzmina[indeks.floorMod(KoloryZnakowWiedzmina.size)]

private fun Int.floorMod(m: Int): Int = ((this % m) + m) % m

@Composable
internal fun IconMini(
    ikona: androidx.compose.ui.graphics.vector.ImageVector,
    opis: String,
    kolor: Color,
    podswietlona: Boolean = false,
    onClick: () -> Unit
) {
    val akcent = dialer.dominik.pl.ui.theme.motywAkcent()
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(
                if (podswietlona) akcent.copy(alpha = 0.16f)
                else Color.White.copy(alpha = 0.07f)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = ikona,
            contentDescription = opis,
            tint = kolor,
            modifier = Modifier.size(15.dp)
        )
    }
}

/** Pojedynczy przycisk kanału wiadomości. */
@Composable
internal fun ElementKanalu(
    kanal: KanalWiadomosci,
    wybrany: Boolean,
    dostepny: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val akcent = dialer.dominik.pl.ui.theme.motywAkcent()
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (wybrany) akcent.copy(alpha = 0.16f)
                else Color.White.copy(alpha = 0.05f)
            )
            .border(
                1.dp,
                if (wybrany) akcent.copy(alpha = 0.5f)
                else Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(50)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = kanal.nazwa(),
            color = when {
                wybrany -> akcent
                dostepny -> Color.White.copy(alpha = 0.55f)
                else -> Color.White.copy(alpha = 0.22f)
            },
            fontSize = 11.sp,
            fontWeight = if (wybrany) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1
        )
    }
}

/**
 * Wspólny komunikat pustego stanu / braku uprawnienia.
 */
@Composable
internal fun KomunikatKontakty(tytul: String, opis: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = tytul,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Text(
                            text = opis,
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

/** Cache miniaturek awatarów — bez LRU każde przewinięcie listy dekoduje od nowa. */
internal object AwatarBitmapCache {
    private val cache = object : android.util.LruCache<String, androidx.compose.ui.graphics.ImageBitmap>(
        (Runtime.getRuntime().maxMemory() / 1024L / 16L).toInt().coerceIn(1_024, 8_192)
    ) {
        override fun sizeOf(key: String, value: androidx.compose.ui.graphics.ImageBitmap): Int =
            ((value.width * value.height * 4) / 1024).coerceAtLeast(1)
    }

    fun get(uri: String): androidx.compose.ui.graphics.ImageBitmap? =
        synchronized(cache) { cache.get(uri) }

    fun put(uri: String, bmp: androidx.compose.ui.graphics.ImageBitmap) {
        synchronized(cache) { cache.put(uri, bmp) }
    }
}

/**
 * Animowana ramka (sweep + rotate + SrcIn) — wzorzec z
 * SmartToolFactory/Jetpack-Compose-Tutorials (AnimatedRainbowBorder).
 * Kolory wokół bazowego znaku (Yrden/Quen/…), faza przesunięta per ulubiony.
 */
private fun Modifier.animowanaRamkaZnaku(
    bazowy: Color,
    grubosc: Dp = 2.5.dp,
    fazaStart: Float = 0f,
    durationMs: Int = 2800
): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "ramkaZnaku")
    val kat by transition.animateFloat(
        initialValue = fazaStart,
        targetValue = fazaStart + 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "katRamki"
    )
    val kolory = remember(bazowy) {
        listOf(
            bazowy.copy(alpha = 0.25f),
            bazowy,
            Color.White.copy(alpha = 0.95f),
            bazowy,
            bazowy.copy(alpha = 0.35f),
            Color.Black.copy(alpha = 0.55f),
            bazowy.copy(alpha = 0.25f)
        )
    }
    Modifier.drawWithCache {
        val stroke = grubosc.toPx()
        onDrawWithContent {
            drawContent()
            with(drawContext.canvas.nativeCanvas) {
                val warstwa = saveLayer(null, null)
                // maska okręgu (destination)
                drawCircle(
                    color = Color.White,
                    radius = (size.minDimension - stroke) / 2f,
                    style = Stroke(width = stroke)
                )
                // źródło: obracający się sweep
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

/** Awatar kontaktu — zdjęcie z książki (jeśli jest), inaczej inicjał na zielonym tle. */
@Composable
internal fun AwatarKontaktu(
    fotoUri: String?,
    inicial: String,
    context: Context,
    rozmiar: androidx.compose.ui.unit.Dp,
    wariant: AwatarWariant = AwatarWariant.Domyslny,
    /** Stały kolor znaku (Yrden/Quen/…) — tylko przy [AwatarWariant.Wiedzmin]. */
    kolorWiedzmina: Color? = null,
    /** Indeks ulubionego — przesuwa fazę animacji ramki. */
    indeksUlubionego: Int = 0
) {
    val density = LocalDensity.current
    val wiedzmin = wariant == AwatarWariant.Wiedzmin
    val kolorZnaku = kolorWiedzmina ?: KoloryZnakowWiedzmina.first()
    // 2× rozmiar Dp → px (ekrany xxhdpi); downsample zamiast pełnego JPEG z książki.
    val maxPx = remember(rozmiar, density) {
        with(density) { (rozmiar.toPx() * 2f).toInt().coerceIn(64, 256) }
    }
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(
        initialValue = fotoUri?.let { AwatarBitmapCache.get(it) },
        key1 = fotoUri,
        key2 = maxPx
    ) {
        if (fotoUri.isNullOrBlank()) {
            value = null
            return@produceState
        }
        AwatarBitmapCache.get(fotoUri)?.let {
            value = it
            return@produceState
        }
        value = withContext(Dispatchers.IO) {
            try {
                dekodujAwatarZUri(context, android.net.Uri.parse(fotoUri), maxPx)?.also {
                    AwatarBitmapCache.put(fotoUri, it)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
    val obraz = bitmap
    if (wiedzmin) {
        AwatarWiedzmin(
            obraz = obraz,
            inicial = inicial,
            rozmiar = rozmiar,
            kolorZnaku = kolorZnaku,
            indeks = indeksUlubionego
        )
        return
    }
    if (obraz != null) {
        Image(
            bitmap = obraz,
            contentDescription = null,
            modifier = Modifier
                .size(rozmiar)
                .clip(CircleShape)
                .border(1.dp, ZielonyNeon.copy(alpha = 0.22f), CircleShape),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop
        )
    } else {
        Box(
            modifier = Modifier
                .size(rozmiar)
                .clip(CircleShape)
                .background(Color(0xFF12362A))
                .border(1.dp, ZielonyNeon.copy(alpha = 0.22f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = inicial,
                color = ZielonyNeon,
                fontSize = (rozmiar * 0.4f).value.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun AwatarWiedzmin(
    obraz: androidx.compose.ui.graphics.ImageBitmap?,
    inicial: String,
    rozmiar: Dp,
    kolorZnaku: Color,
    indeks: Int
) {
    val faza = indeks * 72f
    val transition = rememberInfiniteTransition(label = "napisZnaku$indeks")
    val puls by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200 + indeks * 280, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulsNapis"
    )
    val kolorNapis = lerp(
        kolorZnaku.copy(alpha = 0.72f),
        Color.White.copy(alpha = 0.95f),
        0.15f + puls * 0.55f
    )
    val blur = 10f + puls * 16f
    Box(
        modifier = Modifier
            .size(rozmiar)
            .shadow(14.dp, CircleShape, spotColor = kolorZnaku.copy(alpha = 0.55f + puls * 0.35f))
            .clip(CircleShape)
            .animowanaRamkaZnaku(
                bazowy = kolorZnaku,
                grubosc = 2.5.dp,
                fazaStart = faza,
                durationMs = 5200 + indeks * 400
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            kolorZnaku.copy(alpha = 0.18f),
                            Color(0xFF12100C)
                        )
                    )
                )
        )
        if (obraz != null) {
            Image(
                bitmap = obraz,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(2.dp)
                    .clip(CircleShape),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
        }
        // Bez GIF na awatarze listy — za drogie przy scrollu (lekka Canvas mgła).
        MglaNaPrzycisku(
            modifier = Modifier.fillMaxSize(),
            sila = if (obraz != null) 0.35f else 0.55f,
            seed = inicial.hashCode(),
            lekka = true
        )
        if (obraz == null) {
            Text(
                text = inicial,
                color = kolorNapis,
                fontSize = (rozmiar * 0.4f).value.sp,
                fontWeight = FontWeight.SemiBold,
                style = TextStyle(
                    shadow = androidx.compose.ui.graphics.Shadow(
                        color = kolorZnaku.copy(alpha = 0.85f + puls * 0.15f),
                        blurRadius = blur
                    )
                )
            )
        }
    }
}

/** Dekoduje miniaturkę z inSampleSize — bez pełnego decode + createScaledBitmap. */
private fun dekodujAwatarZUri(
    context: Context,
    uri: android.net.Uri,
    maxPx: Int
): androidx.compose.ui.graphics.ImageBitmap? {
    val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use {
        android.graphics.BitmapFactory.decodeStream(it, null, opts)
    }
    var sample = 1
    val h = opts.outHeight
    val w = opts.outWidth
    if (h > maxPx || w > maxPx) {
        var halfH = h / 2
        var halfW = w / 2
        while (halfH / sample >= maxPx && halfW / sample >= maxPx) {
            sample *= 2
        }
    }
    val decode = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
    val bmp = context.contentResolver.openInputStream(uri)?.use {
        android.graphics.BitmapFactory.decodeStream(it, null, decode)
    } ?: return null
    return bmp.asImageBitmap()
}
