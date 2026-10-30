package dialer.dominik.pl.nagranie

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlin.math.PI
import kotlin.math.sin

/**
 * Niesłyszalny „keepalive” ścieżki audio podczas nagrywania.
 *
 * Problem (jak BT A2DP / HAL rozmowy): po ciszy framework schodzi w idle
 * i pierwsze ~100–500 ms kolejnego dźwięku giną. Zerowy/ultradźwiękowy
 * AudioTrack trzyma mix aktywny bez słyszalnego tonu u rozmówcy.
 *
 * Tryby:
 * - [Tryb.Cisza] — same zera (jak bt-keepalive-patch): zero audybilności
 * - [Tryb.Ultradzwiek] — 18.5 kHz, amplituda ~1/32768…4/32768 (niesłyszalne)
 *
 * USAGE_ASSISTANCE_SONIFICATION + CONTENT_TYPE_SONIFICATION — nie idzie
 * w tor VOICE_COMMUNICATION do sieci (w przeciwieństwie do tonu nagrywania AOSP).
 */
internal class KeepaliveAudio(
    private val sampleRate: Int = 48_000,
    private val tryb: Tryb = Tryb.Ultradzwiek,
) {
    enum class Tryb { Cisza, Ultradzwiek }

    @Volatile private var track: AudioTrack? = null
    @Volatile private var dziala = false

    fun start() {
        if (dziala) return
        try {
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            if (minBuf <= 0) {
                Log.w(TAG, "keepalive minBuf=$minBuf")
                return
            }
            val bufSamples = maxOf(minBuf / 2, sampleRate / 10) // ~100 ms
            val pcm = ShortArray(bufSamples)
            when (tryb) {
                Tryb.Cisza -> { /* zera */ }
                Tryb.Ultradzwiek -> {
                    // 18500 Hz — poza słyszalnością większości dorosłych; amp=2 ≈ -84 dBFS
                    val f = 18_500.0
                    val amp = 2
                    for (i in pcm.indices) {
                        pcm[i] = (sin(2.0 * PI * f * i / sampleRate) * amp).toInt().toShort()
                    }
                }
            }
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
            val t = AudioTrack.Builder()
                .setAudioAttributes(attrs)
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufSamples * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setSessionId(0)
                .build()
            t.write(pcm, 0, pcm.size)
            t.setLoopPoints(0, pcm.size, -1) // nieskończona pętla
            t.setVolume(0.01f) // ekstra wyciszenie systemowe
            t.play()
            track = t
            dziala = true
            Log.i(TAG, "keepalive ON tryb=$tryb")
        } catch (e: Exception) {
            Log.w(TAG, "keepalive start fail", e)
            stop()
        }
    }

    fun stop() {
        dziala = false
        val t = track
        track = null
        runCatching {
            t?.stop()
            t?.release()
        }
        Log.i(TAG, "keepalive OFF")
    }

    companion object {
        private const val TAG = "NagrywanieKeepalive"
    }
}
