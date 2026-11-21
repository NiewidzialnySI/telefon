package dialer.dominik.pl.nagranie

import kotlin.math.abs

/**
 * Early-MIC (bufor podczas czekania na ADB) nie ma toru DOWNLINK —
 * nie da się zrobić pełnego AEC. Czyścimy jak się da:
 * - odrzuć bardzo krótki bufor (shell wstawał szybko → Dual i tak łapie początek)
 * - bramka ramek: ucina ciche ogony echa ze słuchawki
 *
 * Źródło AudioRecord: preferuj VOICE_COMMUNICATION (OEM AEC).
 */
internal object EarlyMicOczyszczanie {

    /** Poniżej tego (~350 ms) nie doklejaj — unikaj „echa na starcie” przy szybkim shellu. */
    private const val MIN_MS = 350

    /** Średnie |sample| w ramce 20 ms poniżej → cisza (echo/leak). */
    private const val GATE_MEAN = 900

    fun oczysc(pcm: ByteArray, sampleRate: Int): ByteArray {
        val minB = (sampleRate * 2L * MIN_MS) / 1000L
        if (pcm.size < minB) return ByteArray(0)
        val frameB = ((sampleRate * 20) / 1000).coerceAtLeast(1) * 2
        val out = pcm.copyOf()
        var i = 0
        while (i + frameB <= out.size) {
            var energy = 0L
            var j = i
            while (j + 1 < i + frameB) {
                val s = ((out[j].toInt() and 0xff) or (out[j + 1].toInt() shl 8)).toShort()
                energy += abs(s.toInt()).toLong()
                j += 2
            }
            val mean = energy / (frameB / 2)
            if (mean < GATE_MEAN) {
                out.fill(0, i, i + frameB)
            }
            i += frameB
        }
        return out
    }
}
