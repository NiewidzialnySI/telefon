package dialer.dominik.pl.sms

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast

/**
 * Wynik wysyłki MMS (SmsManager.sendMultimediaMessage / mmslib system sending).
 */
class MmsStatusSentReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val lokalny = intent.getLongExtra(EXTRA_LOKALNY_KLUCZ, -1L).takeIf { it >= 0 }
        val messageId = intent.getLongExtra(EXTRA_MESSAGE_ID, -1L).takeIf { it >= 0 }

        if (resultCode == Activity.RESULT_OK) {
            StatusWiadomosciBus.emituj(
                AktualizacjaStatusuWiadomosci(
                    messageUri = intent.getStringExtra(EXTRA_CONTENT_URI),
                    messageId = messageId,
                    lokalnyKlucz = lokalny,
                    stan = StanWysylki.WYSŁANO
                )
            )
        } else {
            StatusWiadomosciBus.emituj(
                AktualizacjaStatusuWiadomosci(
                    messageUri = intent.getStringExtra(EXTRA_CONTENT_URI),
                    messageId = messageId,
                    lokalnyKlucz = lokalny,
                    stan = StanWysylki.BLAD
                )
            )
            val opis = when (resultCode) {
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
                else -> "Nie udało się wysłać MMS ($resultCode)"
            }
            Toast.makeText(context, opis, Toast.LENGTH_LONG).show()
            Log.w(TAG, "MMS send failed code=$resultCode")
        }
    }

    companion object {
        private const val TAG = "MmsStatusSent"
        const val ACTION = "dialer.dominik.pl.MMS_SENT"
        const val EXTRA_MESSAGE_ID = "message_id"
        const val EXTRA_LOKALNY_KLUCZ = "lokalny_klucz"
        const val EXTRA_CONTENT_URI = "content_uri"
    }
}
