package dialer.dominik.pl.nagranie

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Offline: stereo sidecar (L=near/UP, R=DOWNLINK) → mono WAV z AEC.
 * Pozwala ponownie wyczyścić nagranie bez ponownego rozmowy.
 */
object OczyszczanieEchaNagran {

    /**
     * @return plik mono po AEC, lub null gdy brak stereo / błąd.
     */
    fun zStereoDoMono(stereo: File, monoDocelowy: File? = null): File? {
        if (!stereo.exists() || stereo.length() <= 44L) return null
        return runCatching {
            val raf = RandomAccessFile(stereo, "r")
            try {
                val hdr = ByteArray(44)
                raf.readFully(hdr)
                val bb = ByteBuffer.wrap(hdr).order(ByteOrder.LITTLE_ENDIAN)
                bb.position(22)
                val kanaly = bb.short.toInt()
                bb.position(24)
                val rate = bb.int
                bb.position(40)
                val dataLen = bb.int.coerceAtLeast(0)
                if (kanaly != 2 || rate <= 0) return@runCatching null

                val out = monoDocelowy
                    ?: File(
                        stereo.parentFile,
                        stereo.name.removeSuffix(NagrywaniePliki.STEREO_SUFFIX) + ".wav",
                    )
                val wav = WavPisarz(out, rate, 1)
                val aec = AnulatorEcha(rate)
                val chunk = ByteArray(8192)
                var left = dataLen.toLong()
                val mono = ByteArray(4096)
                while (left > 0) {
                    val n = raf.read(chunk, 0, minOf(chunk.size.toLong(), left).toInt())
                    if (n <= 0) break
                    left -= n
                    val frames = n / 4
                    var o = 0
                    for (i in 0 until frames) {
                        val base = i * 4
                        if (base + 3 >= n) break
                        val near = ((chunk[base].toInt() and 0xff) or (chunk[base + 1].toInt() shl 8)).toShort()
                        val far = ((chunk[base + 2].toInt() and 0xff) or (chunk[base + 3].toInt() shl 8)).toShort()
                        val clean = aec.oczysc(near, far)
                        val gain = aec.micGainPrzyDownlink(far, agresywny = true)
                        val lu = wzmocnij(clean, gain)
                        val mix = mieszaj(lu, far)
                        mono[o++] = (mix.toInt() and 0xff).toByte()
                        mono[o++] = ((mix.toInt() shr 8) and 0xff).toByte()
                    }
                    if (o > 0) wav.write(mono, 0, o)
                }
                wav.close()
                out
            } finally {
                raf.close()
            }
        }.getOrNull()
    }

    private fun mieszaj(a: Short, b: Short): Short {
        val sum = a.toInt() + b.toInt()
        return sum.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
    }

    private fun wzmocnij(s: Short, razy: Float): Short {
        val v = (s.toInt() * razy).toInt()
        return v.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
    }
}
