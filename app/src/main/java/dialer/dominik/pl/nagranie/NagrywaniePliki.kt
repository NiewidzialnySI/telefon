package dialer.dominik.pl.nagranie

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object NagrywaniePliki {
    private val FORMAT = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

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

    fun lista(ctx: Context): List<File> =
        katalog(ctx).listFiles()
            ?.filter { it.isFile && (it.extension == "wav" || it.extension == "m4a") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
}
