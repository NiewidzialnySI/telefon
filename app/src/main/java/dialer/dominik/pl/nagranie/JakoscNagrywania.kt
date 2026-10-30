package dialer.dominik.pl.nagranie

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Czysta logika jakości nagrywania — bez Android API.
 * Służy produkcyjnie ([NagrywanieKontroler]) i do symulacji JVM.
 */
object JakoscNagrywania {

    enum class Werdykt { Czekaj, Slychac, Ciche }

    /**
     * @param elapsedMs czas od startu AudioRecord
     * @param slychac czy bieżący RMS/peak przekroczył próg
     * @param graceMs nie odrzucaj w tym oknie (warmup HAL)
     * @param verifyMs po grace — tyle ciszy = martwy silnik
     */
    fun werdyktProbe(
        elapsedMs: Long,
        slychac: Boolean,
        graceMs: Long = 1400,
        verifyMs: Long = 2000,
    ): Werdykt {
        if (slychac) return Werdykt.Slychac
        if (elapsedMs < graceMs) return Werdykt.Czekaj
        if (elapsedMs >= graceMs + verifyMs) return Werdykt.Ciche
        return Werdykt.Czekaj
    }

    /** Stara logika (bez grace) — do porównania w symulacji. */
    fun werdyktStary(
        elapsedMs: Long,
        slychac: Boolean,
        oknoMs: Long = 3200,
    ): Werdykt {
        if (slychac) return Werdykt.Slychac
        if (elapsedMs >= oknoMs) return Werdykt.Ciche
        return Werdykt.Czekaj
    }

    /**
     * Symulacja strumienia rozmowy: cisza warmup → mowa.
     * @return (ucięteMs_stary, ucięteMs_nowy, zachowanyPrerollMs)
     */
    fun symulujUcinaniePoczatku(
        warmupCiszaMs: Int = 900,
        mowaStartMs: Int = 900,
        mowaCzasMs: Int = 3000,
        krokMs: Int = 50,
        graceMs: Long = 1400,
        verifyMs: Long = 2000,
        prerollMs: Int = 1500,
        staryOknoMs: Long = 3200,
    ): WynikSymulacji {
        var staryOdrzucilW = -1
        var nowyOdrzucilW = -1
        var staryUslyszalW = -1
        var nowyUslyszalW = -1
        var staryGotowy = false
        var nowyGotowy = false

        var t = 0
        val koniec = minOf(mowaStartMs + mowaCzasMs, 60_000)
        while (t <= koniec) {
            val slychac = t >= mowaStartMs && mowaStartMs < 60_000
            if (!staryGotowy) {
                when (werdyktStary(t.toLong(), slychac, staryOknoMs)) {
                    Werdykt.Slychac -> {
                        staryUslyszalW = t
                        staryGotowy = true
                    }
                    Werdykt.Ciche -> {
                        staryOdrzucilW = t
                        staryGotowy = true
                    }
                    Werdykt.Czekaj -> Unit
                }
            }
            if (!nowyGotowy) {
                when (werdyktProbe(t.toLong(), slychac, graceMs, verifyMs)) {
                    Werdykt.Slychac -> {
                        nowyUslyszalW = t
                        nowyGotowy = true
                    }
                    Werdykt.Ciche -> {
                        nowyOdrzucilW = t
                        nowyGotowy = true
                    }
                    Werdykt.Czekaj -> Unit
                }
            }
            if (staryGotowy && nowyGotowy) break
            t += krokMs
        }

        // Odrzucenie PRZED mową + kasowanie pliku = tracisz początek rozmowy
        // (kolejny silnik wstaje ≈ +switchMs). Soft-switch z preroll ratuje ogon.
        val switchMs = 800
        val staryUciete: Int
        val nowyUciete: Int
        if (staryUslyszalW < 0 && nowyUslyszalW < 0) {
            // Probe bez mowy — nie ma czego ucinać w sensie treści rozmowy.
            staryUciete = 0
            nowyUciete = 0
        } else {
            staryUciete = when {
                staryOdrzucilW >= 0 && staryOdrzucilW <= mowaStartMs ->
                    mowaCzasMs.coerceAtMost(staryOdrzucilW + switchMs)
                staryUslyszalW > mowaStartMs -> staryUslyszalW - mowaStartMs
                else -> 0
            }
            nowyUciete = when {
                nowyOdrzucilW >= 0 && nowyOdrzucilW <= mowaStartMs ->
                    maxOf(0, nowyOdrzucilW + switchMs - prerollMs)
                nowyUslyszalW > mowaStartMs -> nowyUslyszalW - mowaStartMs
                else -> 0
            }
        }

        val keepalivePomaga = warmupCiszaMs > 0
        val warmupPoKeepalive = if (keepalivePomaga) (warmupCiszaMs * 0.35).toInt() else warmupCiszaMs

        return WynikSymulacji(
            warmupCiszaMs = warmupCiszaMs,
            mowaStartMs = mowaStartMs,
            staryOdrzucilMs = staryOdrzucilW,
            nowyOdrzucilMs = nowyOdrzucilW,
            staryUslyszalMs = staryUslyszalW,
            nowyUslyszalMs = nowyUslyszalW,
            staryUcieteMs = staryUciete,
            nowyUcieteMs = nowyUciete,
            warmupPoKeepaliveMs = warmupPoKeepalive,
            prerollZachowanyMs = prerollMs,
        )
    }

    data class WynikSymulacji(
        val warmupCiszaMs: Int,
        val mowaStartMs: Int,
        val staryOdrzucilMs: Int,
        val nowyOdrzucilMs: Int,
        val staryUslyszalMs: Int,
        val nowyUslyszalMs: Int,
        val staryUcieteMs: Int,
        val nowyUcieteMs: Int,
        val warmupPoKeepaliveMs: Int,
        val prerollZachowanyMs: Int,
    ) {
        fun raport(): String = buildString {
            appendLine("=== Symulacja jakości nagrywania ===")
            appendLine("Warmup ciszy HAL: ${warmupCiszaMs} ms → mowa od ${mowaStartMs} ms")
            appendLine("Keepalive (ultradźwięk): warmup ≈ ${warmupPoKeepaliveMs} ms")
            appendLine()
            appendLine("STARY (sztywne okno, kasuj plik):")
            appendLine("  usłyszano@${staryUslyszalMs}  odrzucono@${staryOdrzucilMs}  ucięte≈${staryUcieteMs} ms")
            appendLine("NOWY (grace+verify+preroll+keepalive):")
            appendLine("  usłyszano@${nowyUslyszalMs}  odrzucono@${nowyOdrzucilMs}  ucięte≈${nowyUcieteMs} ms")
            appendLine("  preroll soft-switch: ${prerollZachowanyMs} ms")
            appendLine()
            val zysk = staryUcieteMs - nowyUcieteMs
            appendLine("Zysk: ${zysk} ms mniej uciętego początku")
        }
    }

    /** RMS PCM 16-bit LE (jak w kontrolerze). */
    fun rmsPcm16(samples: ShortArray): Float {
        if (samples.isEmpty()) return 0f
        var sum = 0.0
        for (s in samples) sum += s.toDouble() * s
        return (sqrt(sum / samples.size) / 32768.0).toFloat()
    }

    /** Generuje ultrakrótki keepalive frame (18.5 kHz, amp=2) — do testu audybilności. */
    fun generujKeepaliveFrame(sampleRate: Int = 48_000, samples: Int = 480): ShortArray {
        val out = ShortArray(samples)
        val f = 18_500.0
        val amp = 2
        for (i in out.indices) {
            out[i] = (sin(2.0 * PI * f * i / sampleRate) * amp).toInt().toShort()
        }
        return out
    }

    fun czyKeepaliveNieslyszalny(rmsProg: Float = 0.005f): Boolean {
        val frame = generujKeepaliveFrame()
        // Peak amp=2 → RMS << próg mowy; „niesłyszalny” względem naszego PROG_RMS.
        return rmsPcm16(frame) < rmsProg && frame.maxOf { abs(it.toInt()) } <= 4
    }
}
