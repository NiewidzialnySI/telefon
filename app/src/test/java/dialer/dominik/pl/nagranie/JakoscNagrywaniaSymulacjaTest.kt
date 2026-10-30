package dialer.dominik.pl.nagranie

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Symulacja: ucinanie początku rozmowy — stary vs nowy algorytm.
 * Uruchom: `./gradlew :app:testDebugUnitTest --tests JakoscNagrywaniaSymulacjaTest`
 */
class JakoscNagrywaniaSymulacjaTest {

    @Test
    fun keepaliveJestNieslyszalnyWzoremRms() {
        assertTrue(JakoscNagrywania.czyKeepaliveNieslyszalny())
    }

    @Test
    fun graceNieOdrzucaWWarmupGdyMowaPrzychodziPozniej() {
        // W 900 ms cisza, mowa od 900 — stary przy pechu mógłby odrzucić za wcześnie
        // przy krótkim oknie; z grace=1400 czekamy i łapiemy mowę.
        assertEquals(
            JakoscNagrywania.Werdykt.Czekaj,
            JakoscNagrywania.werdyktProbe(800, slychac = false, graceMs = 1400, verifyMs = 2000),
        )
        assertEquals(
            JakoscNagrywania.Werdykt.Slychac,
            JakoscNagrywania.werdyktProbe(900, slychac = true, graceMs = 1400, verifyMs = 2000),
        )
    }

    @Test
    fun poGraceIVerifyOdrzucCichySilnik() {
        assertEquals(
            JakoscNagrywania.Werdykt.Ciche,
            JakoscNagrywania.werdyktProbe(3400, slychac = false, graceMs = 1400, verifyMs = 2000),
        )
    }

    @Test
    fun symulacjaTypowegoHalWarmup_zyskDodatni() {
        val w = JakoscNagrywania.symulujUcinaniePoczatku(
            warmupCiszaMs = 900,
            mowaStartMs = 900,
            mowaCzasMs = 4000,
        )
        println(w.raport())
        assertTrue(
            "nowyUciete=${w.nowyUcieteMs} staryUciete=${w.staryUcieteMs}\n${w.raport()}",
            w.nowyUcieteMs <= w.staryUcieteMs,
        )
        assertTrue(w.warmupPoKeepaliveMs < w.warmupCiszaMs)
        assertTrue(w.nowyUslyszalMs >= 0)
    }

    /**
     * Klasyczny fail: agresywne okno 1.5 s odrzuca silnik zanim HAL odda mowę (2.0 s).
     * Grace 1.4 s + verify ratuje nagranie → 0 ms ucięcia vs utrata całego startu.
     */
    @Test
    fun symulacjaAgresywnegoOkna_graceRatujePoczatek() {
        val w = JakoscNagrywania.symulujUcinaniePoczatku(
            warmupCiszaMs = 2000,
            mowaStartMs = 2000,
            mowaCzasMs = 3000,
            graceMs = 1400,
            verifyMs = 2000,
            prerollMs = 1500,
            staryOknoMs = 1500, // stare „szybkie” okno
        )
        println(w.raport())
        assertTrue("stary powinien odrzucić przed mową", w.staryOdrzucilMs in 1..1999)
        assertTrue("nowy powinien usłyszeć mowę", w.nowyUslyszalMs >= 2000)
        assertTrue(
            "zysk: staryUciete=${w.staryUcieteMs} nowy=${w.nowyUcieteMs}\n${w.raport()}",
            w.nowyUcieteMs < w.staryUcieteMs,
        )
    }

    @Test
    fun symulacjaMartwegoSilnika_softSwitchZPreroll() {
        // Mowa nigdy nie przychodzi w oknie probe — oba odrzucają.
        val w = JakoscNagrywania.symulujUcinaniePoczatku(
            warmupCiszaMs = 5000,
            mowaStartMs = 50_000,
            mowaCzasMs = 100,
            graceMs = 1400,
            verifyMs = 2000,
            prerollMs = 1500,
            staryOknoMs = 3200,
        )
        println(w.raport())
        assertTrue(w.staryOdrzucilMs >= 0)
        assertTrue(w.nowyOdrzucilMs >= 0)
        assertTrue(w.nowyUcieteMs <= w.staryUcieteMs)
    }

    @Test
    fun prerollBuforZachowujeKolejnosc() {
        val b = PrerollBufor(sampleRate = 1000, kanaly = 1, pojemnoscMs = 100) // 200 B
        b.dodaj(byteArrayOf(1, 2, 3, 4), 0, 4)
        b.dodaj(byteArrayOf(5, 6), 0, 2)
        val z = b.zrzut()
        assertEquals(6, z.size)
        assertEquals(1.toByte(), z[0])
        assertEquals(6.toByte(), z[5])
    }
}
