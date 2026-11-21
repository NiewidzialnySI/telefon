package dialer.dominik.pl.nagranie

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin

/**
 * Symulacja echa słuchawki: near = α·far[t−delay] (+ opcjonalnie mowa).
 * Po AEC energia echa w near powinna spaść.
 */
class AnulatorEchaTest {

    @Test
    fun aecRedukujeEchoPrzyStalymOpoznieniu() {
        val sr = 16_000
        val delay = sr * 22 / 1000 // 22 ms
        val aec = AnulatorEcha(sr)
        val alpha = 0.55f
        // Far = mowa-podobny sygnał (kilka tonów + „szum”), nie czysty sinus —
        // inaczej korelacja ma fałszywe maksima co okres.
        fun farAt(i: Int): Short {
            val v = sin(2.0 * Math.PI * 380.0 * i / sr) * 7_000 +
                sin(2.0 * Math.PI * 710.0 * i / sr) * 4_500 +
                sin(2.0 * Math.PI * 120.0 * i / sr) * 2_500 +
                ((i * 17) % 97 - 48) * 40.0
            return v.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        var echoPrzed = 0.0
        var echoPo = 0.0
        val n = sr * 4 // 4 s — czas na estymację + zbieżność
        for (i in 0 until n) {
            val far = farAt(i)
            val delayed = if (i >= delay) farAt(i - delay).toInt() else 0
            val near = (delayed * alpha).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            val clean = aec.oczysc(near, far)
            if (i > sr * 2) {
                echoPrzed += abs(near.toInt()).toDouble()
                echoPo += abs(clean.toInt()).toDouble()
            }
        }
        val stosunek = echoPo / echoPrzed.coerceAtLeast(1.0)
        println("AEC residual=$stosunek delayEst=${aec.opoznienieProbek()} (cel≈$delay)")
        assertTrue("AEC powinien zbić echo (residual=$stosunek)", stosunek < 0.40)
        assertTrue(
            "delayEst=${aec.opoznienieProbek()} vs $delay",
            abs(aec.opoznienieProbek() - delay) < sr * 8 / 1000,
        )
    }

    @Test
    fun aecNieZjadaNearGdyFarCichy() {
        val aec = AnulatorEcha(16_000)
        var energia = 0L
        for (i in 0 until 16_000) {
            val near = (sin(2.0 * Math.PI * 220.0 * i / 16_000) * 8_000).toInt().toShort()
            val clean = aec.oczysc(near, 0)
            energia += abs(clean.toInt()).toLong()
        }
        assertTrue("przy ciszy far near powinien przejść", energia > 16_000 * 500L)
    }
}
