package dialer.dominik.pl.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.RemoteInput

/**
 * Obsługuje akcje z powiadomień o SMS-ach:
 * - [AKCJA_KOPIUJ_OTP]: kopiuje kod weryfikacyjny do schowka.
 * - [AKCJA_ODPOWIEDZ]: wysyła szybką odpowiedź wpisaną w powiadomieniu (RemoteInput).
 */
class AkcjaSmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AkcjaSmsReceiver"
        private const val KLAWISZ_ODPOWIEDZ = "KLAWISZ_ODPOWIEDZ"

        const val AKCJA_KOPIUJ_OTP = "AKCJA_KOPIUJ_OTP"
        const val AKCJA_ODPOWIEDZ = "AKCJA_ODPOWIEDZ"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AKCJA_KOPIUJ_OTP -> {
                val kod = intent.getStringExtra("KOD")
                if (!kod.isNullOrBlank()) {
                    OdbiorcaSms.kopiujDoSchowka(context, kod)
                    Log.i(TAG, "Skopiowano OTP: $kod")
                }
            }

            AKCJA_ODPOWIEDZ -> {
                val nadawca = intent.getStringExtra("NADAWCA") ?: return
                val odpowiedz = RemoteInput.getResultsFromIntent(intent)
                    ?.getCharSequence(KLAWISZ_ODPOWIEDZ)
                    ?.toString()
                    ?.trim()
                if (odpowiedz.isNullOrBlank()) return
                wyslijSms(context, nadawca, odpowiedz)
            }
        }
    }

    private fun wyslijSms(context: Context, numer: String, tresc: String) {
        try {
            val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            val czesci = smsManager.divideMessage(tresc)
            if (czesci.isNullOrEmpty()) return
            if (czesci.size == 1) {
                smsManager.sendTextMessage(numer, null, czesci[0], null, null)
            } else {
                smsManager.sendMultipartTextMessage(numer, null, czesci, null, null)
            }

            // Dodaj wysłaną wiadomość do systemowej skrzynki nadawczej (Sent),
            // żeby aplikacja SMS zachowywała się jak prawdziwa domyślna apka.
            try {
                val values = android.content.ContentValues().apply {
                    put(Telephony.Sms.Sent.ADDRESS, numer)
                    put(Telephony.Sms.Sent.BODY, tresc)
                    put(Telephony.Sms.Sent.DATE, System.currentTimeMillis())
                    put(Telephony.Sms.Sent.READ, 1)
                }
                context.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
            } catch (e: Exception) {
                Log.e(TAG, "Błąd zapisu wysłanego SMS", e)
            }

            Log.i(TAG, "Wysłano SMS z powiadomienia do $numer")
        } catch (e: Exception) {
            Log.e(TAG, "Nie udało się wysłać SMS z powiadomienia", e)
        }
    }
}
