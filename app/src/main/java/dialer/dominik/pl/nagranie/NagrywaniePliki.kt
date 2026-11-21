package dialer.dominik.pl.nagranie

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object NagrywaniePliki {
    private val FORMAT = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    /** Sidecar L=near/UP, R=DOWNLINK — do offline AEC / ponownego czyszczenia. */
    const val STEREO_SUFFIX = ".stereo.wav"

    fun katalog(ctx: Context): File {
        val dir = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "nagrania")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun nowyPlik(ctx: Context, numer: String, ext: String = "wav"): File {
        val bezpieczny = numer.filter { it.isDigit() || it == '+' }.ifBlank { "nieznany" }
        val nazwa = "rozmowa_${FORMAT.format(Date())}_$bezpieczny.$ext"
        return File(katalog(ctx), nazwa)
    }

    fun plikStereoObok(mono: File): File =
        File(mono.parentFile, mono.nameWithoutExtension + STEREO_SUFFIX)

    fun jestSidecarStereo(f: File): Boolean =
        f.name.endsWith(STEREO_SUFFIX, ignoreCase = true)

    /** Tory pomocnicze / sidecar — nie pokazuj w liście nagrań. */
    fun jestPlikPomocniczy(f: File): Boolean {
        if (jestSidecarStereo(f)) return true
        val n = f.nameWithoutExtension.lowercase()
        return n.endsWith("_ja") || n.endsWith("_oni") || n.endsWith("_czyste") ||
            n.contains(".czyste.tmp")
    }

    /** Kasuje mono + sidecar stereo (jeśli jest). */
    fun usunZSidecar(mono: File) {
        runCatching { plikStereoObok(mono).delete() }
        runCatching { mono.delete() }
    }

    fun lista(ctx: Context): List<File> =
        katalog(ctx).listFiles()
            ?.filter {
                it.isFile &&
                    (it.extension == "wav" || it.extension == "m4a") &&
                    !jestPlikPomocniczy(it)
            }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
}
