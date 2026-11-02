package dialer.dominik.pl.sms

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import android.widget.Toast
import java.io.File

/**
 * Wynik wysyłki MMS (SmsManager.sendMultimediaMessage / mmslib system sending).
 *
 * Musi robić to samo co [com.klinker.android.send_message.MmsSentReceiver]:
 * po sukcesie przenieść wiadomość z outbox → sent i skasować tymczasowy PDU.
 * Bez tego ColorOS/OPPO czyści „wiszące” outboxy i MMS znika z czatu / nie dochodzi.
 */
class MmsStatusSentReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val pending = goAsync()
        val appCtx = context.applicationContext
        val result = resultCode
        val lokalny = intent.getLongExtra(EXTRA_LOKALNY_KLUCZ, -1L).takeIf { it >= 0 }
        val messageId = intent.getLongExtra(EXTRA_MESSAGE_ID, -1L).takeIf { it >= 0 }
        val contentUriStr = intent.getStringExtra(EXTRA_CONTENT_URI)
            ?: intent.dataString
        val filePath = intent.getStringExtra(EXTRA_FILE_PATH)

        Thread {
            try {
                if (result == Activity.RESULT_OK) {
                    oznaczJakoWyslane(appCtx, contentUriStr)
                    Log.i(TAG, "MMS wysłany OK klucz=$lokalny uri=$contentUriStr")
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
                        android.telephony.SmsManager.MMS_ERROR_IO_ERROR ->
                            "MMS za duży lub błąd odczytu (spróbuj mniejsze zdjęcie)"
                        android.telephony.SmsManager.MMS_ERROR_NO_DATA_NETWORK ->
                            "Brak danych mobilnych do MMS"
                        android.telephony.SmsManager.MMS_ERROR_UNABLE_CONNECT_MMS ->
                            "Brak połączenia z MMSC"
                        android.telephony.SmsManager.MMS_ERROR_INVALID_APN ->
                            "Błędna konfiguracja APN MMS"
                        android.telephony.SmsManager.MMS_ERROR_CONFIGURATION_ERROR ->
                            "Błąd konfiguracji MMS"
                        android.telephony.SmsManager.MMS_ERROR_HTTP_FAILURE ->
                            "Błąd sieci przy MMS"
                        else -> "Nie udało się wysłać MMS ($result)"
                    }
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        Toast.makeText(appCtx, opis, Toast.LENGTH_LONG).show()
                    }
                    Log.w(TAG, "MMS send failed code=$result uri=$contentUriStr")
                }
            } finally {
                // PDU w cache — kasuj po wyniku (sukces albo błąd), jak mmslib.
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
            val values = ContentValues(1).apply {
                put(Telephony.Mms.MESSAGE_BOX, Telephony.Mms.MESSAGE_BOX_SENT)
            }
            val n = context.contentResolver.update(Uri.parse(contentUriStr), values, null, null)
            Log.i(TAG, "outbox→sent rows=$n uri=$contentUriStr")
        } catch (e: Exception) {
            Log.e(TAG, "nie udało się przenieść do sent: ${e.message}", e)
        }
    }

    private fun oznaczJakoBlad(context: Context, contentUriStr: String?) {
        if (contentUriStr.isNullOrBlank()) return
        try {
            val values = ContentValues(1).apply {
                put(Telephony.Mms.MESSAGE_BOX, Telephony.Mms.MESSAGE_BOX_FAILED)
            }
            context.contentResolver.update(Uri.parse(contentUriStr), values, null, null)
        } catch (e: Exception) {
            Log.e(TAG, "nie udało się oznaczyć failed: ${e.message}", e)
        }
    }

    companion object {
        private const val TAG = "MmsStatusSent"
        const val ACTION = "dialer.dominik.pl.MMS_SENT"
        const val EXTRA_MESSAGE_ID = "message_id"
        const val EXTRA_LOKALNY_KLUCZ = "lokalny_klucz"
        /** Zgodne z mmslib Transaction.sendMmsThroughSystem. */
        const val EXTRA_CONTENT_URI = "content_uri"
        const val EXTRA_FILE_PATH = "file_path"
    }
}
