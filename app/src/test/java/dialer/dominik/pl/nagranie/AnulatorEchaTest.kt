package dialer.dominik.pl.nagranie

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin

/**
 * Symulacja echa ze słuchawki ≈ nagranie użytkownika:
 * near = α·far[t−delay] (podwójny DOWNLINK, ~8–16 ms).
 */
class AnulatorEchaTest {

    @Test
    fun aecRedukujeEchoPrzyStalymOpoznieniu() {
        val sr = 16_000
        val delay = sr * 12 / 1000 // 12 ms — jak w WAV (cepstrum ~16, xcorr 8–16)
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
        println("AEC+halfduplex residual=$stosunek delayEst=${aec.opoznienieProbek()} (cel≈$delay)")
        // Half-duplex przy głośnym far powinien zbić podwójny DN mocno.
        assertTrue("residual=$stosunek", stosunek < 0.20)
        assertTrue(
            "delayEst=${aec.opoznienieProbek()} vs $delay",
            abs(aec.opoznienieProbek() - delay) < sr * 8 / 1000,
        )
    }

    @Test
    fun halfDuplexWyciszaNearGdyDnGlosny() {
        val aec = AnulatorEcha(16_000)
        // Rozgrzej envelope DN
        repeat(800) { aec.oczysc(100, 4000) }
        val g = aec.micGainPrzyDownlink(AnulatorEcha.Tryb.Mic)
        assertTrue("przy głośnym DN gain=$g powinien ~0", g < 0.08f)
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
        val a = byteArrayOf(1, 0, 2, 0, 3, 0, 4, 0) // 1,2,3,4 little-endian shorts
        q.dodajBajty(a, 4) // tylko 1,2
        q.dodajBajty(a, 8) // 1,2,3,4
        assertTrue(q.size == 6)
        assertTrue(q.pobierz().toInt() == 1)
        assertTrue(q.pobierz().toInt() == 2)
    }
}
