package dialer.dominik.pl.call

import android.content.Context
import android.content.Intent
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Bundle
import android.view.WindowManager
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import dialer.dominik.pl.R
import dialer.dominik.pl.ui.components.IkonyAplikacji
import dialer.dominik.pl.ui.components.icons.IkonyEaster420
import dialer.dominik.pl.ui.theme.CzcionkiWiedzmina
import dialer.dominik.pl.ui.theme.NaPrzycisku
import dialer.dominik.pl.ui.theme.TelefonTheme
import dialer.dominik.pl.ui.theme.ZielonyNeon
import dialer.dominik.pl.ui.theme.tloMokrejKrwi
import kotlinx.coroutines.delay

/** Easter egg: 420 → fejkowe połączenie „Palecze” + GIF + beat offline. */
object FejkowePolaczenie420 {
    const val NUMER = "420"
    const val NAZWA = "Palecze"

    fun czyNumer(numer: String): Boolean =
        numer.filter { it.isDigit() } == NUMER

    fun uruchom(context: Context) {
        val intent = Intent(context, FejkowePolaczenie420Activity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        }
        context.startActivity(intent)
    }
}

class FejkowePolaczenie420Activity : ComponentActivity() {
    private var player: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
        uruchomDzwiek()
        setContent {
            TelefonTheme {
                EkranFejk420(
                    player = player,
                    onRozlacz = { zakoncz() }
                )
            }
        }
    }

    private fun uruchomDzwiek() {
        runCatching {
            player?.release()
            player = MediaPlayer.create(this, R.raw.palecze_420)?.apply {
                isLooping = true
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                start()
            }
        }
    }

    private fun zakoncz() {
        runCatching {
            player?.stop()
            player?.release()
        }
        player = null
        finish()
    }

    override fun onDestroy() {
        runCatching {
            player?.stop()
            player?.release()
        }
        player = null
        super.onDestroy()
    }
}

@Composable
private fun EkranFejk420(player: MediaPlayer?, onRozlacz: () -> Unit) {
    BackHandler(onBack = onRozlacz)
    val context = LocalContext.current
    var sekundy by remember { mutableIntStateOf(0) }
    var wyciszony by remember { mutableStateOf(false) }
    var mlynek by remember { mutableStateOf(false) }
    var boombox by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            sekundy++
        }
    }

    LaunchedEffect(wyciszony) {
        player?.setVolume(if (wyciszony) 0f else 1f, if (wyciszony) 0f else 1f)
    }

    LaunchedEffect(boombox) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return@LaunchedEffect
        runCatching {
            am.mode = AudioManager.MODE_NORMAL
            am.isSpeakerphoneOn = boombox
        }
    }

    val czas = remember(sekundy) {
        "%d:%02d".format(sekundy / 60, sekundy % 60)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GifSantaiTlo(mod = Modifier.fillMaxSize())
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xCC0A0B0D),
                            Color(0x880A1A0C),
                            Color(0xDD0A0B0D)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 44.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Połączenie",
                    color = Color(0xFF69F0AE).copy(alpha = 0.9f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.5.sp
                )
                Spacer(Modifier.height(24.dp))
                Box(
                    modifier = Modifier
                        .size(118.dp)
                        .clip(CircleShape)
                        .background(Color.Black)
                        .padding(10.dp)
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.avatar_420),
                        contentDescription = FejkowePolaczenie420.NAZWA,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    text = FejkowePolaczenie420.NAZWA,
                    color = Color(0xFFF2F4F7),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = CzcionkiWiedzmina.HelveticaNeue,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = FejkowePolaczenie420.NUMER,
                    color = Color(0xFFC5CAD3),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = czas,
                    color = Color(0xFF69F0AE),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Tylko: joint / młynek / boombox — bez klawiatury, nagrywania, notatek
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Przycisk420(
                        ikona = IkonyEaster420.Joint,
                        etykieta = "Joint",
                        aktywny = wyciszony,
                        onKlik = { wyciszony = !wyciszony }
                    )
                    Przycisk420(
                        ikona = IkonyEaster420.Mlynek,
                        etykieta = "Młynek",
                        aktywny = mlynek,
                        onKlik = { mlynek = !mlynek }
                    )
                    Przycisk420(
                        ikona = IkonyEaster420.Boombox,
                        etykieta = "Boombox",
                        aktywny = boombox,
                        onKlik = { boombox = !boombox }
                    )
                }

                Spacer(Modifier.height(28.dp))

                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .tloMokrejKrwi(CircleShape)
                        .clickable(onClick = onRozlacz),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = IkonyAplikacji.Sluchawka,
                        contentDescription = "Rozłącz",
                        tint = Color(0xFFFFF5F3),
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Rozłącz",
                    color = Color(0xFFFFF5F3).copy(alpha = 0.75f),
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun Przycisk420(
    ikona: ImageVector,
    etykieta: String,
    aktywny: Boolean,
    onKlik: () -> Unit
) {
    val tlo by animateColorAsState(
        targetValue = if (aktywny) ZielonyNeon else Color.White.copy(alpha = 0.10f),
        animationSpec = tween(220),
        label = "tlo420"
    )
    val tint by animateColorAsState(
        targetValue = if (aktywny) NaPrzycisku else Color.White,
        animationSpec = tween(220),
        label = "tint420"
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(tlo)
                .clickable(onClick = onKlik),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = ikona,
                contentDescription = etykieta,
                tint = tint,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(etykieta, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
    }
}

@Composable
private fun GifSantaiTlo(mod: Modifier = Modifier) {
    val context = LocalContext.current
    val gif = remember {
        runCatching {
            val src = ImageDecoder.createSource(context.resources, R.raw.santai_420)
            ImageDecoder.decodeDrawable(src) { decoder, _, _ ->
                decoder.setTargetSampleSize(1)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            } as? AnimatedImageDrawable
        }.getOrNull()
    }
    DisposableEffect(gif) {
        gif?.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
        gif?.start()
        onDispose { gif?.stop() }
    }
    if (gif != null) {
        AndroidView(
            factory = { ctx ->
                ImageView(ctx).apply {
                    setImageDrawable(gif)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                }
            },
            modifier = mod
        )
    } else {
        Box(modifier = mod.background(Color(0xFF0A1A0C)))
    }
}
