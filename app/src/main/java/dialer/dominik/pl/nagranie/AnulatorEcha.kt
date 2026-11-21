package dialer.dominik.pl.nagranie

import kotlin.math.abs

/**
 * Własny AEC na nagrania rozmów (MIC/UPLINK + referencja DOWNLINK).
 *
 * Bez natywnych libów (WebRTC AEC3 / Speex) — czysty Kotlin:
 * 1. Estymacja opóźnienia: lag minimalizujący energię near − α·far[t−lag]
 * 2. Adaptacyjny tap na tym lag (szybka zbieżność earpiece→mic)
 * 3. NLMS na residuum z detekcją double-talk (jak aec-library / OmniVoice)
 *
 * Użycie: `oczysc(near=MIC/UP, far=DOWNLINK)` → near bez echa akustycznego,
 * potem mix z czystym DOWNLINK.
 */
internal class AnulatorEcha(
    private val sampleRate: Int,
    /** Max opóźnienie ścieżki earpiece→mic (ms). */
    maxDelayMs: Int = 90,
) {
    private val maxDelay = (sampleRate * maxDelayMs / 1000).coerceAtLeast(64)
    private val minDelay = (sampleRate * 6 / 1000).coerceAtLeast(1)

    // Ring far do tapu + historii
    private val ring = ShortArray(maxDelay)
    private var write = 0
    private var delayEst = (sampleRate * 20 / 1000).coerceIn(minDelay, maxDelay - 1)
    private var wTap = 0.35f

    // NLMS na residuum (~32 ms)
    private val nLms = (sampleRate * 32 / 1000).coerceIn(48, maxDelay)
    private val wNlms = FloatArray(nLms)
    private val xNlms = FloatArray(nLms)
    private var iNlms = 0
    private val mu = 0.08f
    private val eps = 1e-3f
    private val leakage = 0.9997f // anty-wybuch wag

    // Bufor do estymacji opóźnienia (~200 ms)
    private val corrWin = (sampleRate / 5).coerceAtLeast(200)
    private val farHist = ShortArray(maxDelay + corrWin)
    private val nearHist = ShortArray(corrWin)
    private var histWrite = 0
    private var samplesSinceCorr = 0
    private var delayZablokowane = false

    fun oczysc(near: Short, far: Short): Short {
        zapiszHistorie(near, far)
        samplesSinceCorr++
        if (samplesSinceCorr >= corrWin) {
            samplesSinceCorr = 0
            oszacujOpoznienie()
        }

        ring[write] = far
        write = (write + 1) % maxDelay

        // 1) Tap na estymowanym opóźnieniu
        val idx = (write - delayEst + maxDelay) % maxDelay
        val farD = ring[idx].toFloat()
        var err = near.toFloat() - wTap * farD
        val farAbs = abs(far.toInt())
        if (farAbs > 300 && abs(near.toInt()) < farAbs * 2.5f) {
            // NLMS 1-tap
            val x2 = farD * farD + eps
            wTap = (wTap + (0.15f / x2) * err * farD).coerceIn(0.02f, 1.15f)
            err = near.toFloat() - wTap * farD
        }

        // 2) NLMS residuum
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
        val nearAbs = abs(near.toInt())
        if (farAbs > 250 && nearAbs < farAbs * 2.6f) {
            val step = mu / xPow
            for (i in 0 until nLms) {
                val j = (iNlms - i + nLms) % nLms
                wNlms[i] = leakage * wNlms[i] + step * err * xNlms[j]
            }
        } else {
            for (i in 0 until nLms) wNlms[i] *= leakage
        }
        iNlms = (iNlms + 1) % nLms

        return err.toInt()
            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            .toShort()
    }

    fun micGainPrzyDownlink(dn: Short, agresywny: Boolean): Float {
        val dnAbs = abs(dn.toInt())
        return if (agresywny) {
            when {
                dnAbs > 2000 -> 0.05f
                dnAbs > 1000 -> 0.14f
                dnAbs > 500 -> 0.42f
                dnAbs > 250 -> 0.85f
                else -> 1.50f
            }
        } else {
            when {
                dnAbs > 2500 -> 0.35f
                dnAbs > 1200 -> 0.55f
                dnAbs > 600 -> 0.75f
                else -> 1.0f
            }
        }
    }

    fun opoznienieProbek(): Int = delayEst

    private fun zapiszHistorie(near: Short, far: Short) {
        farHist[histWrite % farHist.size] = far
        nearHist[histWrite % nearHist.size] = near
        histWrite++
    }

    /**
     * Lag ∈ [minDelay, maxDelay) minimalizujący Σ|near − 0.45·far[t−lag]|.
     * Odporniejsze na tony (korelacja ma fałszywe maksima co okres).
     */
    private fun oszacujOpoznienie() {
        if (histWrite < maxDelay + corrWin) return
        var farEnergy = 0L
        for (i in 0 until corrWin step 2) {
            val f = odczytFar(i).toInt()
            farEnergy += abs(f).toLong()
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
                errSum += abs(n - (f * 45) / 100).toLong()
                i += 3
            }
            if (errSum < bestErr) {
                bestErr = errSum
                bestLag = lag
            }
            lag += 2
        }
        val maxSkok = if (delayZablokowane) {
            (sampleRate * 4 / 1000).coerceAtLeast(1)
        } else {
            (sampleRate * 12 / 1000).coerceAtLeast(1)
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
