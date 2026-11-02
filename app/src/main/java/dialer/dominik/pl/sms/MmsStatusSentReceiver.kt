package dialer.dominik.pl.sms

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import android.widget.Toast
import java.io.File

/**
 * Wynik wysyłki MMS (SmsManager.sendMultimediaMessage / mmslib system sending).
 *
 * Po sukcesie: outbox → sent + kasuj PDU (jak mmslib MmsSentReceiver).
 *
 * ColorOS bywa zwraca resultCode=0 (RESULT_CANCELED) mimo udanego przyjęcia MMS —
 * traktujemy tylko dodatnie [SmsManager.MMS_ERROR_*] jako realny błąd. Fałszywy
 * „błąd 0” + MESSAGE_BOX_FAILED sprawiał, że UI rysowało MMS jako przychodzący.
 */
class MmsStatusSentReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val pending = goAsync()
        val appCtx = context.applicationContext
        // RESULT_OK == -1; 0 = CANCELED (często fałszywy na OEM); >0 = MMS_ERROR_*.
        val result = resultCode
        val lokalny = intent.getLongExtra(EXTRA_LOKALNY_KLUCZ, -1L).takeIf { it >= 0 }
        val messageId = intent.getLongExtra(EXTRA_MESSAGE_ID, -1L).takeIf { it >= 0 }
        val contentUriStr = intent.getStringExtra(EXTRA_CONTENT_URI)
            ?: intent.dataString
        val filePath = intent.getStringExtra(EXTRA_FILE_PATH)

        Thread {
            try {
                val sukces = result == Activity.RESULT_OK || result == 0
                if (sukces) {
                    if (result == 0) {
                        Log.w(TAG, "resultCode=0 (CANCELED) — traktuję jako sukces (OEM quirk) uri=$contentUriStr")
                    }
                    oznaczJakoWyslane(appCtx, contentUriStr)
                    Log.i(TAG, "MMS wysłany OK klucz=$lokalny code=$result uri=$contentUriStr")
                    StatusWiadomosciBus.emituj(
                        AktualizacjaStatusuWiadomosci(
                            messageUri = contentUriStr,
                            messageId = messageId,
                            lokalnyKlucz = lokalny,
                            stan = StanWysylki.WYSŁANO
                        )
                    )
                } else {
                    oznaczJakoBlad(appCtx, contentUriStr)
                    StatusWiadomosciBus.emituj(
                        AktualizacjaStatusuWiadomosci(
                            messageUri = contentUriStr,
                            messageId = messageId,
                            lokalnyKlucz = lokalny,
                            stan = StanWysylki.BLAD
                        )
                    )
                    val opis = when (result) {
                        SmsManager.MMS_ERROR_IO_ERROR ->
                            "MMS za duży lub błąd odczytu (spróbuj mniejsze zdjęcie)"
                        SmsManager.MMS_ERROR_NO_DATA_NETWORK ->
                            "Brak danych mobilnych do MMS"
                        SmsManager.MMS_ERROR_UNABLE_CONNECT_MMS ->
                            "Brak połączenia z MMSC"
                        SmsManager.MMS_ERROR_INVALID_APN ->
                            "Błędna konfiguracja APN MMS"
                        SmsManager.MMS_ERROR_CONFIGURATION_ERROR ->
                            "Błąd konfiguracji MMS"
                        SmsManager.MMS_ERROR_HTTP_FAILURE ->
                            "Błąd sieci przy MMS"
                        else -> "Nie udało się wysłać MMS ($result)"
                    }
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        Toast.makeText(appCtx, opis, Toast.LENGTH_LONG).show()
                    }
                    Log.w(TAG, "MMS send failed code=$result uri=$contentUriStr")
                }
            } finally {
                if (!filePath.isNullOrBlank()) {
                    runCatching { File(filePath).delete() }
                }
                pending.finish()
            }
        }.start()
    }

    private fun oznaczJakoWyslane(context: Context, contentUriStr: String?) {
        if (contentUriStr.isNullOrBlank()) return
        try {
            val uri = Uri.parse(contentUriStr)
            val values = ContentValues(1).apply {
                put(Telephony.Mms.MESSAGE_BOX, Telephony.Mms.MESSAGE_BOX_SENT)
            }
            var n = context.contentResolver.update(uri, values, null, null)
            // Po update URI /outbox/N bywa nieważny — fallback po _id.
            if (n <= 0) {
                val id = uri.lastPathSegment?.toLongOrNull()
                if (id != null && id > 0L) {
                    n = context.contentResolver.update(
                        Telephony.Mms.CONTENT_URI,
                        values,
                        "${Telephony.Mms._ID}=?",
                        arrayOf(id.toString())
                    )
                }
            }
            Log.i(TAG, "outbox→sent rows=$n uri=$contentUriStr")
        } catch (e: Exception) {
            Log.e(TAG, "nie udało się przenieść do sent: ${e.message}", e)
        }
    }

    private fun oznaczJakoBlad(context: Context, contentUriStr: String?) {
        if (contentUriStr.isNullOrBlank()) return
        try {
            val uri = Uri.parse(contentUriStr)
            val values = ContentValues(1).apply {
                put(Telephony.Mms.MESSAGE_BOX, Telephony.Mms.MESSAGE_BOX_FAILED)
            }
            var n = context.contentResolver.update(uri, values, null, null)
            if (n <= 0) {
                val id = uri.lastPathSegment?.toLongOrNull()
                if (id != null && id > 0L) {
                    n = context.contentResolver.update(
                        Telephony.Mms.CONTENT_URI,
                        values,
                        "${Telephony.Mms._ID}=?",
                        arrayOf(id.toString())
                    )
                }
            }
            Log.i(TAG, "→failed rows=$n uri=$contentUriStr")
        } catch (e: Exception) {
            Log.e(TAG, "nie udało się oznaczyć failed: ${e.message}", e)
        }
    }

    companion object {
        private const val TAG = "MmsStatusSent"
        const val ACTION = "dialer.dominik.pl.MMS_SENT"
        const val EXTRA_MESSAGE_ID = "message_id"
        const val EXTRA_LOKALNY_KLUCZ = "lokalny_klucz"
        const val EXTRA_CONTENT_URI = "content_uri"
        const val EXTRA_FILE_PATH = "file_path"
    }
}
