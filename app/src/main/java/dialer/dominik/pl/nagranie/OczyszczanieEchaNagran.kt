package dialer.dominik.pl.nagranie

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Offline narzędzie po nagraniu (sidecar `*.stereo.wav`: L=near, R=DOWNLINK).
 *
 * Inspiracje GH: omni-meeting-recorder (AEC post), BCR stereo split, speex wav_aec —
 * u nas bez natywu Kotlin 2.4: własny AEC + mix XOR na osobnych torach.
 *
 * Tryby:
 * - [Tryb.CzysteMono] — AEC+XOR → nadpisuje mono (domyślne po rozmowie)
 * - [Tryb.Rozdziel] — zapisuje `_ja.wav` + `_oni.wav`
 * - [Tryb.TylkoOni] / [Tryb.TylkoJa]
 */
object OczyszczanieEchaNagran {

    enum class Tryb { CzysteMono, Rozdziel, TylkoOni, TylkoJa }

    data class Wynik(
        val mono: File? = null,
        val ja: File? = null,
        val oni: File? = null,
    )

    fun maSidecar(mono: File): Boolean =
        NagrywaniePliki.plikStereoObok(mono).let { it.isFile && it.length() > 44L }

    /** Po zakończeniu rozmowy — wyczyść mono z sidecara (jeśli jest). */
    fun przetworzPoNagraniu(mono: File): Wynik? {
        if (!maSidecar(mono)) return null
        return uruchom(mono, Tryb.CzysteMono, nadpiszMono = true)
    }

    fun uruchom(
        mono: File,
        tryb: Tryb,
        nadpiszMono: Boolean = true,
    ): Wynik? {
        val stereo = NagrywaniePliki.plikStereoObok(mono)
        if (!stereo.isFile || stereo.length() <= 44L) return null
        return runCatching {
            when (tryb) {
                Tryb.CzysteMono -> {
                    val tmp = File(mono.parentFile, mono.nameWithoutExtension + ".czyste.tmp.wav")
                    val out = zStereo(stereo, tmp, Tryb.CzysteMono) ?: return@runCatching null
                    if (nadpiszMono) {
                        runCatching { mono.delete() }
                        if (!out.renameTo(mono)) {
                            out.copyTo(mono, overwrite = true)
                            out.delete()
                        }
                        Wynik(mono = mono)
                    } else {
                        val dest = File(mono.parentFile, mono.nameWithoutExtension + "_czyste.wav")
                        if (dest.exists()) dest.delete()
                        out.renameTo(dest)
                        Wynik(mono = dest)
                    }
                }
                Tryb.Rozdziel -> {
                    val ja = File(mono.parentFile, mono.nameWithoutExtension + "_ja.wav")
                    val oni = File(mono.parentFile, mono.nameWithoutExtension + "_oni.wav")
                    zStereo(stereo, ja, Tryb.TylkoJa) ?: return@runCatching null
                    zStereo(stereo, oni, Tryb.TylkoOni) ?: return@runCatching null
                    Wynik(ja = ja, oni = oni)
                }
                Tryb.TylkoOni, Tryb.TylkoJa -> {
                    val suf = if (tryb == Tryb.TylkoOni) "_oni.wav" else "_ja.wav"
                    val dest = File(mono.parentFile, mono.nameWithoutExtension + suf)
                    zStereo(stereo, dest, tryb) ?: return@runCatching null
                    Wynik(
                        ja = dest.takeIf { tryb == Tryb.TylkoJa },
                        oni = dest.takeIf { tryb == Tryb.TylkoOni },
                    )
                }
            }
        }.getOrNull()
    }

    /** Kompat: stary API. */
    fun zStereoDoMono(stereo: File, monoDocelowy: File? = null): File? {
        val out = monoDocelowy
            ?: File(
                stereo.parentFile,
                stereo.name.removeSuffix(NagrywaniePliki.STEREO_SUFFIX) + ".wav",
            )
        return zStereo(stereo, out, Tryb.CzysteMono)
    }

    private fun zStereo(stereo: File, out: File, tryb: Tryb): File? {
        if (!stereo.exists() || stereo.length() <= 44L) return null
        val raf = RandomAccessFile(stereo, "r")
        return try {
            val hdr = ByteArray(44)
            raf.readFully(hdr)
            val bb = ByteBuffer.wrap(hdr).order(ByteOrder.LITTLE_ENDIAN)
            bb.position(22)
            val kanaly = bb.short.toInt()
            bb.position(24)
            val rate = bb.int
            bb.position(40)
            val dataLen = bb.int.coerceAtLeast(0)
            if (kanaly != 2 || rate <= 0) return null

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
                    val sample = when (tryb) {
                        Tryb.TylkoOni -> far
                        Tryb.TylkoJa -> {
                            val clean = aec.oczysc(near, far)
                            // Offline: tylko residuum (bez echa), bez mixu z DN
                            if (aec.dnZywy(far) && !aec.hangoverAktywny()) {
                                0
                            } else {
                                wzmocnij(clean, 1.25f)
                            }
                        }
                        Tryb.CzysteMono, Tryb.Rozdziel -> {
                            val clean = aec.oczysc(near, far)
                            aec.mixXor(clean, far, AnulatorEcha.Tryb.Mic, ::mieszaj, ::wzmocnij)
                        }
                    }
                    // Bez twardej bramki |x|<60→0 — dawała klik na krawędziach ciszy.
                    mono[o++] = (sample.toInt() and 0xff).toByte()
                    mono[o++] = ((sample.toInt() shr 8) and 0xff).toByte()
                }
                if (o > 0) wav.write(mono, 0, o)
            }
            wav.close()
            out
        } catch (_: Throwable) {
            runCatching { out.delete() }
            null
        } finally {
            raf.close()
        }
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
