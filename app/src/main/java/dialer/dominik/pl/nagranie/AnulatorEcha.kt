package dialer.dominik.pl.nagranie

import kotlin.math.abs

/**
 * AEC na nagrania (MIC/UPLINK + referencja DOWNLINK).
 *
 * Po #16 echo wróciło: duck wymagał `envNear < 0.40·DN`, a wyciek ze słuchawki
 * to ~0.55·DN — warunek prawie nigdy nie padał. Duck liczymy z **envErr**
 * (residuum po AEC): po anulowaniu echo małe → ścisz; Twoja mowa zostaje w err.
 */
internal class AnulatorEcha(
    private val sampleRate: Int,
    maxDelayMs: Int = 90,
) {
    private val maxDelay = (sampleRate * maxDelayMs / 1000).coerceAtLeast(64)
    private val minDelay = (sampleRate * 5 / 1000).coerceAtLeast(1)

    private val ring = ShortArray(maxDelay)
    private var write = 0
    private var delayEst = (sampleRate * 8 / 1000).coerceIn(minDelay, maxDelay - 1)
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

    private var envDn = 0f
    private var envNear = 0f
    private var envErr = 0f
    private val envAlpha = 1f - 1f / (sampleRate * 0.015f).coerceAtLeast(1f)

    private var hangover = 0
    private val hangoverMax = (sampleRate * 0.30f).toInt() // ~300 ms — chroni Twoje zdania

    fun hangoverAktywny(): Boolean = hangover > 0

    /**
     * Mix XOR (własny pomysł po analizie WAV + GH cally/BCR):
     * Sumowanie MIC+DN **zawsze** zostawia podwójny głos przy niedoskonałym AEC.
     * Gdy oni mówią → **sam DOWNLINK**. Gdy Ty (hangover/envErr) → near + cichszy DN.
     */
    fun mixXor(
        nearClean: Short,
        dn: Short,
        tryb: Tryb,
        mieszaj: (Short, Short) -> Short,
        wzmocnij: (Short, Float) -> Short,
    ): Short {
        val ja =
            hangover > 0 || (envErr > 500f && envErr > envDn * 0.32f)
        if (tryb == Tryb.Mic) {
            if (ja) {
                return mieszaj(wzmocnij(nearClean, 1.30f), wzmocnij(dn, 0.50f))
            }
            // Oni / echo: zero MIC w mixie — jedyne pewne zabicie podwójnego DN.
            if (envDn > 350f) {
                return dn
            }
        }
        val g = micGainPrzyDownlink(tryb)
        return mieszaj(wzmocnij(nearClean, g), dn)
    }

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
        // Adaptuj tylko gdy near nie jest wyraźnie głośniejszy od far (double-talk).
        // Echo α~0.55: nearAbs < farAbs*1.6 → OK. Moja mowa: nearAbs ≫ farAbs → stop.
        val moznaAdaptowac = farAbs > 250f && nearAbs < farAbs * 1.6f
        if (moznaAdaptowac) {
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
        if (moznaAdaptowac) {
            val step = mu / xPow
            for (i in 0 until nLms) {
                val j = (iNlms - i + nLms) % nLms
                wNlms[i] = leakage * wNlms[i] + step * err * xNlms[j]
            }
        } else {
            for (i in 0 until nLms) wNlms[i] *= leakage
        }
        iNlms = (iNlms + 1) % nLms

        val errAbs = abs(err)
        envErr = envAlpha * envErr + (1f - envAlpha) * errAbs

        // Hangover gdy residuum = mowa (nie zgaszone echo).
        if (envErr > 550f && envErr > envDn * 0.35f) {
            hangover = hangoverMax
        } else if (hangover > 0) {
            hangover--
        }

        err = residualSuppressor(err)

        return err.toInt()
            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            .toShort()
    }

    /**
     * Duck wg envErr (po AEC), nie surowego near — echo α·DN nie blokuje ściszenia.
     * Hangover chroni końcówki Twoich zdań.
     */
    fun micGainPrzyDownlink(tryb: Tryb): Float {
        if (hangover > 0 || (envErr > 550f && envErr > envDn * 0.35f)) {
            return if (tryb == Tryb.Mic) 1.35f else 1.10f
        }
        return when (tryb) {
            Tryb.Mic -> when {
                envDn > 1600f && envErr < 300f -> 0.05f
                envDn > 900f && envErr < 380f -> 0.12f
                envDn > 500f && envErr < 450f -> 0.28f
                envDn > 280f && envErr < envDn * 0.35f -> 0.50f
                else -> 1.15f
            }
            Tryb.Uplink -> when {
                envDn > 2500f -> 0.70f
                envDn > 1200f -> 0.85f
                else -> 1.0f
            }
        }
    }

    fun micGainPrzyDownlink(dn: Short, agresywny: Boolean): Float {
        val farAbs = abs(dn.toInt()).toFloat()
        envDn = envAlpha * envDn + (1f - envAlpha) * farAbs
        return micGainPrzyDownlink(if (agresywny) Tryb.Mic else Tryb.Uplink)
    }

    fun opoznienieProbek(): Int = delayEst
    fun envDownlink(): Float = envDn
    fun envNear(): Float = envNear

    enum class Tryb { Mic, Uplink }

    private fun residualSuppressor(err: Float): Float {
        if (hangover > 0) return err
        if (envDn < 500f) return err
        val eAbs = abs(err)
        if (eAbs < envDn * 0.55f) {
            val atten = when {
                envDn > 1600f -> 0.12f
                envDn > 900f -> 0.25f
                else -> 0.45f
            }
            return err * atten
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
        val lo = (sampleRate * 5 / 1000).coerceAtLeast(minDelay)
        val hi = (sampleRate * 35 / 1000).coerceAtMost(maxDelay - 1)
        var lag = lo
        while (lag <= hi) {
            var errSum = 0L
            var i = 0
            while (i < corrWin) {
                val n = odczytNear(i).toInt()
                val f = odczytFar(i + lag).toInt()
                errSum += abs(n - (f * 55) / 100).toLong()
                i += 3
            }
            if (errSum < bestErr) {
                bestErr = errSum
                bestLag = lag
            }
            lag += 1
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
