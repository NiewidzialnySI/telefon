package dialer.dominik.pl.nagranie

import kotlin.math.abs

/**
 * AEC na nagrania (MIC/UPLINK + referencja DOWNLINK).
 *
 * Z analizy WAV (13 s, mono 16 kHz): echo = **podwójny DOWNLINK** z opóźnieniem
 * ~8–16 ms (cepstrum ~16 ms, xcorr do 0.9) — MIC łapie słuchawkę, a mix
 * dokłada jeszcze czysty DN. Miękki NLMS tego nie ubija.
 *
 * Strategia:
 * 1. Estymacja opóźnienia (min residual)
 * 2. Tap + NLMS
 * 3. **Residual suppressor** gdy far głośny a near wygląda jak echo
 * 4. **Half-duplex / twarde duck** na ścieżce MIC (envelope DN)
 */
internal class AnulatorEcha(
    private val sampleRate: Int,
    /** Max opóźnienie ścieżki earpiece→mic (ms). */
    maxDelayMs: Int = 90,
) {
    private val maxDelay = (sampleRate * maxDelayMs / 1000).coerceAtLeast(64)
    private val minDelay = (sampleRate * 5 / 1000).coerceAtLeast(1)

    private val ring = ShortArray(maxDelay)
    private var write = 0
    private var delayEst = (sampleRate * 12 / 1000).coerceIn(minDelay, maxDelay - 1)
    private var wTap = 0.55f

    private val nLms = (sampleRate * 40 / 1000).coerceIn(64, maxDelay)
    private val wNlms = FloatArray(nLms)
    private val xNlms = FloatArray(nLms)
    private var iNlms = 0
    private val mu = 0.10f
    private val eps = 1e-3f
    private val leakage = 0.9995f

    private val corrWin = (sampleRate / 5).coerceAtLeast(200)
    private val farHist = ShortArray(maxDelay + corrWin)
    private val nearHist = ShortArray(corrWin)
    private var histWrite = 0
    private var samplesSinceCorr = 0
    private var delayZablokowane = false

    // Envelope DN (~15 ms) do half-duplex
    private var envDn = 0f
    private val envAlpha = 1f - 1f / (sampleRate * 0.015f).coerceAtLeast(1f)
    private var envNear = 0f

    fun oczysc(near: Short, far: Short): Short {
        zapiszHistorie(near, far)
        samplesSinceCorr++
        if (samplesSinceCorr >= corrWin) {
            samplesSinceCorr = 0
            oszacujOpoznienie()
        }

        val farAbs = abs(far.toInt()).toFloat()
        val nearAbs = abs(near.toInt()).toFloat()
        envDn = envAlpha * envDn + (1f - envAlpha) * farAbs
        envNear = envAlpha * envNear + (1f - envAlpha) * nearAbs

        ring[write] = far
        write = (write + 1) % maxDelay

        val idx = (write - delayEst + maxDelay) % maxDelay
        val farD = ring[idx].toFloat()
        var err = near.toFloat() - wTap * farD
        if (farAbs > 250f && nearAbs < farAbs * 2.8f) {
            val x2 = farD * farD + eps
            wTap = (wTap + (0.22f / x2) * err * farD).coerceIn(0.05f, 1.35f)
            err = near.toFloat() - wTap * farD
        }

        xNlms[iNlms] = far.toFloat()
        var y = 0f
        var xPow = eps
        for (i in 0 until nLms) {
            val j = (iNlms - i + nLms) % nLms
            val x = xNlms[j]
            y += wNlms[i] * x
            xPow += x * x
        }
        err -= y
        if (farAbs > 200f && nearAbs < farAbs * 2.8f) {
            val step = mu / xPow
            for (i in 0 until nLms) {
                val j = (iNlms - i + nLms) % nLms
                wNlms[i] = leakage * wNlms[i] + step * err * xNlms[j]
            }
        } else {
            for (i in 0 until nLms) wNlms[i] *= leakage
        }
        iNlms = (iNlms + 1) % nLms

        // Residual suppressor: gdy oni mówią i residuum nadal „brzmi” jak far.
        err = residualSuppressor(err, farAbs)

        return err.toInt()
            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            .toShort()
    }

    /**
     * Gain near przed mixem.
     * [Tryb.Mic]: half-duplex — przy głośnym DN near ≈ 0 (koniec podwójnego głosu).
     * [Tryb.Uplink]: delikatniej (UP zwykle bez echa akustycznego).
     */
    fun micGainPrzyDownlink(tryb: Tryb): Float {
        return when (tryb) {
            Tryb.Mic -> when {
                envDn > 1800f -> 0.00f
                envDn > 900f -> 0.04f
                envDn > 450f -> 0.12f
                envDn > 220f -> 0.35f
                envDn > 100f -> 0.70f
                else -> 1.45f // DN cichy → podbij „mnie”
            }
            Tryb.Uplink -> when {
                envDn > 2500f -> 0.45f
                envDn > 1200f -> 0.70f
                envDn > 600f -> 0.85f
                else -> 1.0f
            }
        }
    }

    /** Kompatybilność ze starym API. */
    fun micGainPrzyDownlink(dn: Short, agresywny: Boolean): Float {
        // Odśwież env z bieżącej próbki (gdy wywołane bez oczysc w tej chwili).
        val farAbs = abs(dn.toInt()).toFloat()
        envDn = envAlpha * envDn + (1f - envAlpha) * farAbs
        return micGainPrzyDownlink(if (agresywny) Tryb.Mic else Tryb.Uplink)
    }

    fun opoznienieProbek(): Int = delayEst
    fun envDownlink(): Float = envDn

    enum class Tryb { Mic, Uplink }

    private fun residualSuppressor(err: Float, farAbs: Float): Float {
        if (envDn < 400f) return err
        val eAbs = abs(err)
        // Residuum małe względem far → to echo, nie „ja”.
        if (eAbs < envDn * 0.85f) {
            val atten = when {
                envDn > 1500f -> 0.06f
                envDn > 800f -> 0.15f
                else -> 0.35f
            }
            return err * atten
        }
        // Double-talk: near głośniejszy niż far — nie tłumić.
        if (envNear > envDn * 1.1f) return err
        // Borderline: częściowe tłumenie
        if (farAbs > 500f && eAbs < envDn * 1.3f) {
            return err * 0.45f
        }
        return err
    }

    private fun zapiszHistorie(near: Short, far: Short) {
        farHist[histWrite % farHist.size] = far
        nearHist[histWrite % nearHist.size] = near
        histWrite++
    }

    private fun oszacujOpoznienie() {
        if (histWrite < maxDelay + corrWin) return
        var farEnergy = 0L
        for (i in 0 until corrWin step 2) {
            farEnergy += abs(odczytFar(i).toInt()).toLong()
        }
        if (farEnergy < corrWin * 40L) return

        var bestLag = delayEst
        var bestErr = Long.MAX_VALUE
        var lag = minDelay
        while (lag < maxDelay) {
            var errSum = 0L
            var i = 0
            while (i < corrWin) {
                val n = odczytNear(i).toInt()
                val f = odczytFar(i + lag).toInt()
                // α≈0.5–0.7 typowe dla earpiece→mic (z WAV: g≈0.35–0.63)
                errSum += abs(n - (f * 55) / 100).toLong()
                i += 3
            }
            if (errSum < bestErr) {
                bestErr = errSum
                bestLag = lag
            }
            lag += 2
        }
        val maxSkok = if (delayZablokowane) {
            (sampleRate * 3 / 1000).coerceAtLeast(1)
        } else {
            (sampleRate * 10 / 1000).coerceAtLeast(1)
        }
        val nowy = (delayEst + (bestLag - delayEst).coerceIn(-maxSkok, maxSkok))
            .coerceIn(minDelay, maxDelay - 1)
        if (abs(nowy - delayEst) <= 2) delayZablokowane = true
        delayEst = nowy
    }

    private fun odczytNear(ileWstecz: Int): Short {
        val idx = (histWrite - 1 - ileWstecz + nearHist.size * 8) % nearHist.size
        return nearHist[idx]
    }

    private fun odczytFar(ileWstecz: Int): Short {
        val idx = (histWrite - 1 - ileWstecz + farHist.size * 8) % farHist.size
        return farHist[idx]
    }
}

/**
 * Kolejka PCM shortów — wyrównuje dwa potoki (UP/DN), żeby nie gubić próbek
 * gdy jeden read() zwróci mniej bajtów (desync = „straszne echo” nawet z AEC).
 */
internal class KolejkaPcm(pojemnosc: Int = 16_000) {
    private val buf = ShortArray(pojemnosc)
    private var r = 0
    private var w = 0
    private var n = 0
    val size: Int get() = n
    val isEmpty: Boolean get() = n == 0

    fun dodajBajty(data: ByteArray, len: Int) {
        var i = 0
        while (i + 1 < len) {
            if (n >= buf.size) {
                // Overflow: wyrzuć najstarsze (lepiej niż desync).
                r = (r + 1) % buf.size
                n--
            }
            val s = ((data[i].toInt() and 0xff) or (data[i + 1].toInt() shl 8)).toShort()
            buf[w] = s
            w = (w + 1) % buf.size
            n++
            i += 2
        }
    }

    fun pobierz(): Short {
        check(n > 0)
        val s = buf[r]
        r = (r + 1) % buf.size
        n--
        return s
    }
}
