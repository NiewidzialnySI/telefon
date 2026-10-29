package dialer.dominik.pl.sms

import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import android.telephony.SmsManager
import android.util.Log

/**
 * Umożliwia szybką odpowiedź SMS-em na nieodebrane połączenie
 * („Odrzuć i wyślij wiadomość”).
 *
 * WYMAGANE przez system, żeby aplikacja pojawiła się na liście domyślnych
 * aplikacji SMS. Bez tej usługi Android nie pokazuje aplikacji w wyborze.
 */
class UslugaOdpowiedziSms : Service() {

    companion object {
        private const val TAG = "UslugaOdpowiedziSms"

        // Klucz pod którym Android zwykle przekazuje treść odpowiedzi
        private const val EXTRA_TEXT = "android.intent.extra.TEXT"
    }

    override fun onBind(intencja: Intent?): IBinder? = null

    override fun onStartCommand(intencja: Intent?, flagi: Int, idStartu: Int): Int {
        // Numer odbiorcy: systemowe RESPOND_VIA_MESSAGE daje go w intent.data
        // (np. sms:+48123456789). Treść odpowiedzi jest w extras.
        val numer = intencja?.data?.schemeSpecificPart ?: intencja?.dataString?.substringAfter("sms:")
        val tresc = intencja?.getStringExtra(Intent.EXTRA_TEXT)
            ?: intencja?.getStringExtra(EXTRA_TEXT)
            ?: ""

        Log.i(TAG, "Odpowiedź SMS na połączenie do $numer")

        if (!numer.isNullOrBlank() && tresc.isNotBlank()) {
            val maUprawnienie = checkSelfPermission(android.Manifest.permission.SEND_SMS) ==
                PackageManager.PERMISSION_GRANTED
            if (maUprawnienie) {
                try {
                    val smsManager = getSystemService(SmsManager::class.java)
                    val czesci = smsManager.divideMessage(tresc)
                    if (czesci.isNotEmpty()) {
                        smsManager.sendMultipartTextMessage(numer, null, czesci, null, null)
                        Log.i(TAG, "Wysłano odpowiedź SMS do $numer")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Błąd wysyłania odpowiedzi SMS", e)
                }
            } else {
                Log.w(TAG, "Brak uprawnienia SEND_SMS do wysłania odpowiedzi")
            }
        } else {
            Log.w(TAG, "Brak numeru lub treści w intencji odpowiedzi")
        }

        stopSelf()
        return START_NOT_STICKY
    }
}
