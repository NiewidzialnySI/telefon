package dialer.dominik.pl.nagranie

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.sin

class OczyszczanieEchaNagranTest {

    @Test
    fun offlineXorUsuwaEchoZSidecar() {
        val dir = createTempDir("oczyszczanie")
        try {
            val mono = File(dir, "rozmowa_test_123.wav")
            val stereo = File(dir, "rozmowa_test_123.stereo.wav")
            val sr = 16_000
            val delay = sr * 8 / 1000
            val n = sr * 2
            // Stereo: L = echo far, R = far
            val stereoWav = WavPisarz(stereo, sr, 2)
            val buf = ByteArray(8)
            fun farAt(i: Int): Short =
                (sin(2.0 * Math.PI * 440.0 * i / sr) * 10_000).toInt().toShort()
            for (i in 0 until n) {
                val far = farAt(i)
                val near = if (i >= delay) (farAt(i - delay) * 0.55).toInt().toShort() else 0
                buf[0] = (near.toInt() and 0xff).toByte()
                buf[1] = ((near.toInt() shr 8) and 0xff).toByte()
                buf[2] = (far.toInt() and 0xff).toByte()
                buf[3] = ((far.toInt() shr 8) and 0xff).toByte()
                stereoWav.write(buf, 0, 4)
            }
            stereoWav.close()
            // Pusty mono placeholder
            WavPisarz(mono, sr, 1).close()

            val w = OczyszczanieEchaNagran.przetworzPoNagraniu(mono)
            assertNotNull(w)
            assertTrue(mono.exists() && mono.length() > 44)

            // Odczytaj mono — powinien być bliski samemu DN (mało echa)
            val bytes = mono.readBytes()
            val pcm = bytes.copyOfRange(44, bytes.size)
            var energyDiff = 0.0
            var energyFar = 0.0
            var i = 0
            var si = 0
            while (i + 1 < pcm.size && si < n) {
                val mix = ((pcm[i].toInt() and 0xff) or (pcm[i + 1].toInt() shl 8)).toShort()
                val far = farAt(si)
                if (si > sr / 2) {
                    energyDiff += abs(mix.toInt() - far.toInt()).toDouble()
                    energyFar += abs(far.toInt()).toDouble()
                }
                i += 2
                si++
            }
            val ratio = energyDiff / energyFar.coerceAtLeast(1.0)
            println("offline XOR residual vs DN=$ratio")
            assertTrue("mix powinien ≈ DN residual=$ratio", ratio < 0.15)
        } finally {
            dir.deleteRecursively()
        }
    }
}
