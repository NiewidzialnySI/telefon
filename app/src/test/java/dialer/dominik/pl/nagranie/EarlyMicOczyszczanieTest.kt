package dialer.dominik.pl.nagranie

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EarlyMicOczyszczanieTest {

    @Test
    fun krotkiBuforOdrzucony() {
        val sr = 16_000
        // 200 ms mono PCM < MIN_MS 350
        val pcm = ByteArray(sr * 2 * 200 / 1000)
        assertEquals(0, EarlyMicOczyszczanie.oczysc(pcm, sr).size)
    }

    @Test
    fun cicheRamkiWyzerowaneGlosneZostaja() {
        val sr = 16_000
        val n = sr // 1 s
        val pcm = ByteArray(n * 2)
        // 0–200 ms cisza, 200–600 ms głośno, reszta cisza
        fun put(i: Int, v: Short) {
            pcm[i * 2] = (v.toInt() and 0xff).toByte()
            pcm[i * 2 + 1] = ((v.toInt() shr 8) and 0xff).toByte()
        }
        for (i in 0 until n) {
            val v = when {
                i in (sr * 200 / 1000) until (sr * 600 / 1000) -> 5000.toShort()
                else -> 50.toShort()
            }
            put(i, v)
        }
        val out = EarlyMicOczyszczanie.oczysc(pcm, sr)
        assertTrue(out.size == pcm.size)
        var loud = 0
        var quiet = 0
        var i = 0
        while (i + 1 < out.size) {
            val s = ((out[i].toInt() and 0xff) or (out[i + 1].toInt() shl 8)).toShort()
            if (kotlin.math.abs(s.toInt()) > 1000) loud++ else quiet++
            i += 2
        }
        assertTrue("loud=$loud", loud > sr * 200 / 1000)
        assertTrue("quiet=$quiet", quiet > sr * 300 / 1000)
    }
}
