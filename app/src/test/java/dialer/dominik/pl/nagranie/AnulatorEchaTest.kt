package dialer.dominik.pl.nagranie

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin

class AnulatorEchaTest {

    private fun mieszaj(a: Short, b: Short): Short =
        (a.toInt() + b.toInt()).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()

    private fun wzmocnij(s: Short, g: Float): Short =
        (s.toInt() * g).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()

    @Test
    fun mixXorPrzyEchuOddajeSamDn() {
        val aec = AnulatorEcha(16_000)
        fun farAt(i: Int): Short =
            (sin(2.0 * Math.PI * 400.0 * i / 16_000) * 10_000).toInt().toShort()
        val delay = 16_000 * 8 / 1000
        var bliskoDn = 0
        for (i in 0 until 16_000 * 3) {
            val far = farAt(i)
            val delayed = if (i >= delay) farAt(i - delay).toInt() else 0
            val near = (delayed * 0.55).toInt().toShort()
            val clean = aec.oczysc(near, far)
            val mix = aec.mixXor(clean, far, AnulatorEcha.Tryb.Mic, ::mieszaj, ::wzmocnij)
            // Soft crossfade — po rozgrzaniu mix ≈ DN (niekoniecznie bitowo ==).
            if (i > 16_000 && abs(mix.toInt() - far.toInt()) < 400) bliskoDn++
        }
        assertTrue("przy echu mix≈DN ($bliskoDn)", bliskoDn > 20_000)
    }

    @Test
    fun mixXorGdyJaMowieWCiszyDnOddajeNear() {
        val aec = AnulatorEcha(16_000)
        // DN cichy, ja głośno
        repeat(2000) {
            val near = 9000.toShort()
            val dn = 50.toShort()
            aec.oczysc(near, dn)
            val mix = aec.mixXor(near, dn, AnulatorEcha.Tryb.Mic, ::mieszaj, ::wzmocnij)
            // Po rozgrzaniu hangover/envErr — near wzmocniony, nie DN
            if (it > 800) {
                assertTrue("mix=${mix.toInt()} nie powinien być samym DN", abs(mix.toInt()) > 1000)
                assertTrue(mix != dn)
            }
        }
    }

    @Test
    fun mixXorAtakDnBezMicPoFade() {
        val aec = AnulatorEcha(16_000)
        // Cisza, potem DN — po ~15 ms crossfade mix ≈ DN (bez MIC).
        repeat(500) { aec.oczysc(0, 20) }
        var last = 0.toShort()
        for (i in 0 until 400) {
            val nearEcho = 5000.toShort()
            val dn = 8000.toShort()
            aec.oczysc(nearEcho, dn)
            last = aec.mixXor(nearEcho, dn, AnulatorEcha.Tryb.Mic, ::mieszaj, ::wzmocnij)
        }
        assertTrue("po fade mix≈DN (${last.toInt()})", abs(last.toInt() - 8000) < 600)
    }

    @Test
    fun mixXorBezTrzaskowNaZerachDn() {
        val aec = AnulatorEcha(16_000)
        fun farAt(i: Int): Short =
            (sin(2.0 * Math.PI * 400.0 * i / 16_000) * 10_000).toInt().toShort()
        var prev = 0
        var bigJumps = 0
        for (i in 0 until 16_000 * 2) {
            val far = farAt(i)
            val near = (farAt(i) * 0.55).toInt().toShort()
            val clean = aec.oczysc(near, far)
            val mix = aec.mixXor(clean, far, AnulatorEcha.Tryb.Mic, ::mieszaj, ::wzmocnij)
            if (i > 2000 && abs(mix.toInt() - prev) > 12_000) bigJumps++
            prev = mix.toInt()
        }
        assertTrue("trzaski jumps>12k=$bigJumps", bigJumps < 30)
    }

    @Test
    fun hangoverNieZEchaGdyDnGlosny() {
        val aec = AnulatorEcha(16_000)
        fun farAt(i: Int): Short =
            (sin(2.0 * Math.PI * 380.0 * i / 16_000) * 12_000).toInt().toShort()
        val delay = 128
        for (i in 0 until 16_000) {
            val far = farAt(i)
            val near = if (i >= delay) (farAt(i - delay) * 0.55f).toInt().toShort() else 0
            aec.oczysc(near, far)
        }
        // Przy głośnym DN hangover nie powinien trzymać „ja”
        assertTrue("hangover nie z echa", !aec.hangoverAktywny())
    }

    @Test
    fun aecRedukujeEchoGdyTylkoOni() {
        val sr = 16_000
        val delay = sr * 8 / 1000
        val aec = AnulatorEcha(sr)
        fun farAt(i: Int): Short {
            val v = sin(2.0 * Math.PI * 380.0 * i / sr) * 7_000 +
                sin(2.0 * Math.PI * 710.0 * i / sr) * 4_500 +
                ((i * 17) % 97 - 48) * 40.0
            return v.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        var echoPrzed = 0.0
        var echoPo = 0.0
        for (i in 0 until sr * 3) {
            val far = farAt(i)
            val delayed = if (i >= delay) farAt(i - delay).toInt() else 0
            val near = (delayed * 0.55).toInt().toShort()
            val clean = aec.oczysc(near, far)
            val mix = aec.mixXor(clean, far, AnulatorEcha.Tryb.Mic, ::mieszaj, ::wzmocnij)
            if (i > sr) {
                echoPrzed += abs(near.toInt() + far.toInt()).toDouble()
                // Po XOR mix≈far — „echo near” nie wchodzi
                echoPo += abs(mix.toInt() - far.toInt()).toDouble()
            }
        }
        assertTrue("XOR residual near-in-mix=${echoPo / echoPrzed}", echoPo / echoPrzed < 0.05)
    }

    @Test
    fun kolejkaPcmNieGubiPrzyNierownychReadach() {
        val q = KolejkaPcm(1024)
        val a = byteArrayOf(1, 0, 2, 0, 3, 0, 4, 0)
        q.dodajBajty(a, 4)
        q.dodajBajty(a, 8)
        assertEquals(6, q.size)
        assertEquals(1, q.pobierz().toInt())
    }
}
