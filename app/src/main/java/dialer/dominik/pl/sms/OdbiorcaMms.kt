package dialer.dominik.pl.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

/**
 * Odbiera powiadomienia o przychodzących MMS-ach.
 *
 * WYMAGANE przez system, żeby aplikacja pojawiła się na liście domyślnych
 * aplikacji SMS. Samą treść MMS pobiera się przez ContentResolver po
 * otrzymaniu powiadomienia — tutaj tylko je przyjmujemy.
 *
 * Uwaga: pełna obsługa MMS (pobieranie, wyświetlanie) to osobny etap.
 * Ten odbiornik istnieje po to, żeby system uznał aplikację za kandydata.
 */
class OdbiorcaMms : BroadcastReceiver() {

    companion object {
        private const val TAG = "OdbiorcaMms"

        /** Czy przyszło powiadomienie o MMS. */
        var odebranoPowiadomienie: Boolean = false
            private set
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) return

        val typ = intent.type
        if (typ != "application/vnd.wap.mms-message") return

        try {
            odebranoPowiadomienie = true
            Log.i(TAG, "Powiadomienie o MMS odebrane")
            // Pełne pobranie MMS: ContentResolver + Telephony.Mms — kolejny etap
        } catch (e: Exception) {
            Log.e(TAG, "Błąd odbioru MMS: ${e.javaClass.simpleName}", e)
        }
    }
}
