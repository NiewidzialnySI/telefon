package dialer.dominik.pl.nagranie

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin

class AnulatorEchaTest {

    @Test
    fun aecRedukujeEchoGdyTylkoOni() {
        val sr = 16_000
        val delay = sr * 12 / 1000
        val aec = AnulatorEcha(sr)
        val alpha = 0.55f
        fun farAt(i: Int): Short {
            val v = sin(2.0 * Math.PI * 380.0 * i / sr) * 7_000 +
                sin(2.0 * Math.PI * 710.0 * i / sr) * 4_500 +
                sin(2.0 * Math.PI * 120.0 * i / sr) * 2_500 +
                ((i * 17) % 97 - 48) * 40.0
            return v.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        var echoPrzed = 0.0
        var echoPo = 0.0
        val n = sr * 4
        for (i in 0 until n) {
            val far = farAt(i)
            val delayed = if (i >= delay) farAt(i - delay).toInt() else 0
            val near = (delayed * alpha).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            val clean = aec.oczysc(near, far)
            val gain = aec.micGainPrzyDownlink(AnulatorEcha.Tryb.Mic)
            val poDuck = (clean.toInt() * gain).toInt()
            if (i > sr * 2) {
                echoPrzed += abs(near.toInt()).toDouble()
                echoPo += abs(poDuck).toDouble()
            }
        }
        val stosunek = echoPo / echoPrzed.coerceAtLeast(1.0)
        println("AEC residual=$stosunek delayEst=${aec.opoznienieProbek()}")
        assertTrue("residual=$stosunek", stosunek < 0.35)
    }

    @Test
    fun duckNieZerujeGdyJaMowie() {
        val aec = AnulatorEcha(16_000)
        // Ja głośno, oni ciszej
        repeat(800) {
            aec.oczysc(8000, 1500)
        }
        val g = aec.micGainPrzyDownlink(AnulatorEcha.Tryb.Mic)
        assertTrue("gdy ja mówię gain=$g powinien zostać wysoki", g >= 1.0f)
    }

    @Test
    fun duckSciszaGdyTylkoOni() {
        val aec = AnulatorEcha(16_000)
        repeat(800) { aec.oczysc(200, 5000) }
        val g = aec.micGainPrzyDownlink(AnulatorEcha.Tryb.Mic)
        assertTrue("przy samych nich gain=$g < 0.4", g < 0.40f)
        assertTrue("nigdy nie zeruj całkowicie gain=$g", g >= 0.10f)
    }

    @Test
    fun aecNieZjadaNearGdyFarCichy() {
        val aec = AnulatorEcha(16_000)
        var energia = 0L
        for (i in 0 until 16_000) {
            val near = (sin(2.0 * Math.PI * 220.0 * i / 16_000) * 8_000).toInt().toShort()
            val clean = aec.oczysc(near, 0)
            val g = aec.micGainPrzyDownlink(AnulatorEcha.Tryb.Mic)
            energia += abs((clean.toInt() * g).toInt()).toLong()
        }
        assertTrue("przy ciszy far near powinien przejść", energia > 16_000 * 500L)
    }

    @Test
    fun kolejkaPcmNieGubiPrzyNierownychReadach() {
        val q = KolejkaPcm(1024)
        val a = byteArrayOf(1, 0, 2, 0, 3, 0, 4, 0)
        q.dodajBajty(a, 4)
        q.dodajBajty(a, 8)
        assertTrue(q.size == 6)
        assertTrue(q.pobierz().toInt() == 1)
        assertTrue(q.pobierz().toInt() == 2)
    }
}
