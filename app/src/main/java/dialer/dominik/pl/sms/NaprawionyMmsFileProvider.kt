package dialer.dominik.pl.sms

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException

/**
 * Zamiennik [com.klinker.android.send_message.MmsFileProvider].
 *
 * W mmslib 1.0.1 openFile robi `File(cacheDir, uri.path)`, a path ma wiodący `/`
 * (np. `/send.123.dat`) — wtedy [File] traktuje child jako ścieżkę bezwzględną
 * i szuka pliku w `/`, zamiast w cache. SmsManager dostaje FileNotFound → MMS nie wychodzi.
 */
class NaprawionyMmsFileProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val ctx = context ?: throw FileNotFoundException("brak context")
        val nazwa = uri.lastPathSegment?.takeIf { it.isNotBlank() && !it.contains("..") }
            ?: throw FileNotFoundException("brak nazwy pliku: $uri")
        val plik = File(ctx.cacheDir, nazwa)
        if (!plik.exists()) {
            throw FileNotFoundException("brak PDU: ${plik.absolutePath}")
        }
        val flags = if (mode.contains("w")) {
            ParcelFileDescriptor.MODE_READ_WRITE
        } else {
            ParcelFileDescriptor.MODE_READ_ONLY
        }
        return ParcelFileDescriptor.open(plik, flags)
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = "application/vnd.wap.mms-message"

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0
}
