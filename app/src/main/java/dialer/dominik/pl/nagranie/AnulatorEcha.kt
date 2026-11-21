package dialer.dominik.pl.nagranie

import kotlin.math.abs

/**
 * AEC na nagrania (MIC/UPLINK + referencja DOWNLINK).
 *
 * WAV użytkownika: echo = podwójny DOWNLINK ~8–16 ms (MIC + czysty DN).
 * #15 half-duplex (near≈0 przy DN) zjadał „mnie” w całej rozmowie —
 * teraz: AEC + **łagodny duck tylko gdy oni wyraźnie głośniejsi**,
 * z ochroną double-talk (gdy mówię, near zostaje).
 */
internal class AnulatorEcha(
    private val sampleRate: Int,
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

    private var envDn = 0f
    private var envNear = 0f
    private val envAlpha = 1f - 1f / (sampleRate * 0.015f).coerceAtLeast(1f)

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
        // Adaptuj tylko gdy oni mówią, a near nie wygląda na moją mowę.
        if (farAbs > 250f && nearAbs < farAbs * 1.6f) {
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
        if (farAbs > 200f && nearAbs < farAbs * 1.6f) {
            val step = mu / xPow
            for (i in 0 until nLms) {
                val j = (iNlms - i + nLms) % nLms
                wNlms[i] = leakage * wNlms[i] + step * err * xNlms[j]
            }
        } else {
            for (i in 0 until nLms) wNlms[i] *= leakage
        }
        iNlms = (iNlms + 1) % nLms

        err = residualSuppressor(err)

        return err.toInt()
            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            .toShort()
    }

    /**
     * Gain near przed mixem.
     * Nigdy nie zeruj „mnie” — tylko ścisz, gdy oni wyraźnie dominują.
     */
    fun micGainPrzyDownlink(tryb: Tryb): Float {
        // Double-talk / ja mówię → pełny near (albo lekki boost).
        if (envNear > 350f && envNear >= envDn * 0.65f) {
            return if (tryb == Tryb.Mic) 1.35f else 1.1f
        }
        return when (tryb) {
            Tryb.Mic -> when {
                // Tylko oni (near cichy względem DN) — ścisz echo ze słuchawki.
                envDn > 2200f && envNear < envDn * 0.40f -> 0.18f
                envDn > 1200f && envNear < envDn * 0.45f -> 0.35f
                envDn > 600f && envNear < envDn * 0.50f -> 0.55f
                envDn > 300f && envNear < envDn * 0.55f -> 0.75f
                else -> 1.25f
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
        // Nie tykaj, gdy ja mówię.
        if (envNear > 350f && envNear >= envDn * 0.65f) return err
        if (envDn < 600f) return err
        val eAbs = abs(err)
        // Residuum wyraźnie mniejsze niż far → echo, nie mowa.
        if (eAbs < envDn * 0.55f) {
            val atten = when {
                envDn > 1800f -> 0.25f
                envDn > 900f -> 0.40f
                else -> 0.60f
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
        var lag = minDelay
        while (lag < maxDelay) {
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
 * gdy jeden read() zwróci mniej bajtów.
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
