package dialer.dominik.pl.call

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dialer.dominik.pl.R
import dialer.dominik.pl.ui.components.IkonyAplikacji
import dialer.dominik.pl.ui.theme.CzcionkiWiedzmina
import dialer.dominik.pl.ui.theme.TelefonTheme
import dialer.dominik.pl.ui.theme.tloMokrejKrwi
import kotlinx.coroutines.delay

/** Easter egg: numer 2137 → fejkowe połączenie z Janem Pawłem II. */
object FejkowePolaczenieJp2 {
    const val NUMER = "2137"
    const val NAZWA = "Jan Paweł II"

    fun czyNumer(numer: String): Boolean =
        numer.filter { it.isDigit() } == NUMER

    fun uruchom(context: Context) {
        val intent = Intent(context, FejkowePolaczenieJp2Activity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        }
        context.startActivity(intent)
    }
}

class FejkowePolaczenieJp2Activity : ComponentActivity() {
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
                EkranFejkJp2(onRozlacz = { zakoncz() })
            }
        }
    }

    private fun uruchomDzwiek() {
        runCatching {
            player?.release()
            player = MediaPlayer.create(this, R.raw.jp2_bajeczka)?.apply {
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
private fun EkranFejkJp2(onRozlacz: () -> Unit) {
    BackHandler(onBack = onRozlacz)
    var sekundy by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            sekundy++
        }
    }
    DisposableEffect(Unit) { onDispose { } }

    val czas = remember(sekundy) {
        val m = sekundy / 60
        val s = sekundy % 60
        "%d:%02d".format(m, s)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0A0B0D), Color(0xFF1A1520), Color(0xFF0A0B0D))
                )
            )
            .padding(horizontal = 28.dp, vertical = 48.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Połączenie",
                    color = Color(0xFFE0B84A).copy(alpha = 0.85f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.5.sp
                )
                Spacer(Modifier.height(28.dp))
                Box(
                    modifier = Modifier
                        .size(112.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(Color(0xFFE0B84A).copy(alpha = 0.45f), Color(0xFF2A2418))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "II",
                        color = Color(0xFFF2F4F7),
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = CzcionkiWiedzmina.HelveticaNeue
                    )
                }
                Spacer(Modifier.height(22.dp))
                Text(
                    text = FejkowePolaczenieJp2.NAZWA,
                    color = Color(0xFFF2F4F7),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = CzcionkiWiedzmina.HelveticaNeue,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = FejkowePolaczenieJp2.NUMER,
                    color = Color(0xFFC5CAD3),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = czas,
                    color = Color(0xFFE0B84A),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
