package dialer.dominik.pl.nagranie

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin

class AnulatorEchaTest {

    @Test
    fun aecRedukujeEchoGdyTylkoOni() {
        val sr = 16_000
        val delay = sr * 8 / 1000 // jak cepstrum z nowych WAV
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
        assertTrue("residual=$stosunek", stosunek < 0.22)
        assertTrue(
            "delayEst=${aec.opoznienieProbek()} vs $delay",
            abs(aec.opoznienieProbek() - delay) < sr * 6 / 1000,
        )
    }

    @Test
    fun hangoverChroniMojaMowe() {
        val aec = AnulatorEcha(16_000)
        // Ja mówię
        repeat(400) { aec.oczysc(9000, 800) }
        // Potem oni głośno, ja cicho — hangover powinien trzymać gain chwilę
        var minWczesny = 99f
        repeat(200) { // ~12 ms — w hangover
            aec.oczysc(100, 5000)
            minWczesny = minOf(minWczesny, aec.micGainPrzyDownlink(AnulatorEcha.Tryb.Mic))
        }
        assertTrue("w hangover gain=$minWczesny", minWczesny >= 1.0f)
        // Po hangover + milczeniu — duck
        repeat(6000) { aec.oczysc(80, 5000) }
        val g = aec.micGainPrzyDownlink(AnulatorEcha.Tryb.Mic)
        assertTrue("po hangover przy samych nich gain=$g", g < 0.25f)
    }

    @Test
    fun duckNieZerujeGdyJaMowie() {
        val aec = AnulatorEcha(16_000)
        repeat(800) { aec.oczysc(8000, 1500) }
        val g = aec.micGainPrzyDownlink(AnulatorEcha.Tryb.Mic)
        assertTrue("gdy ja mówię gain=$g", g >= 1.0f)
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
        assertTrue(energia > 16_000 * 500L)
    }

    @Test
    fun mixXorGdyOniTylkoDownlink() {
        val aec = AnulatorEcha(16_000)
        // Samo echo (near = α·far) — po zbieżności mix = DN
        fun farAt(i: Int): Short =
            (sin(2.0 * Math.PI * 400.0 * i / 16_000) * 10_000).toInt().toShort()
        val delay = 16_000 * 8 / 1000
        var sameAsDn = 0
        for (i in 0 until 16_000 * 3) {
            val far = farAt(i)
            val delayed = if (i >= delay) farAt(i - delay).toInt() else 0
            val near = (delayed * 0.55).toInt().toShort()
            val clean = aec.oczysc(near, far)
            val mix = aec.mixXor(clean, far, AnulatorEcha.Tryb.Mic, { a, b ->
                (a.toInt() + b.toInt()).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }, { s, g -> (s.toInt() * g).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort() })
            if (i > 16_000 * 2 && mix == far) sameAsDn++
        }
        assertTrue("mix XOR powinien oddawać sam DN ($sameAsDn)", sameAsDn > 8_000)
    }

    @Test
    fun kolejkaPcmNieGubiPrzyNierownychReadach() {
        val q = KolejkaPcm(1024)
        val a = byteArrayOf(1, 0, 2, 0, 3, 0, 4, 0)
        q.dodajBajty(a, 4)
        q.dodajBajty(a, 8)
        assertTrue(q.size == 6)
        assertTrue(q.pobierz().toInt() == 1)
    }
}
