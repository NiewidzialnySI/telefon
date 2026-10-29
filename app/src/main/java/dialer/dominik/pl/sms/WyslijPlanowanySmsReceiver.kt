package dialer.dominik.pl.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.util.Log

/**
 * Odbiera alarm zaplanowanej wiadomości SMS (scheduled send) i wysyła ją.
 * Wiadomość odczytujemy z SharedPreferences "sms_planowane" po id, wysyłamy
 * przez SmsManager, a następnie usuwamy wpis (żeby nie wysłać powtórnie).
 */
class WyslijPlanowanySmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("PLAN_ID") ?: run {
            Log.w(TAG, "Brak PLAN_ID w alarmie planowanej wiadomości")
            return
        }
        val prefs = context.getSharedPreferences("sms_planowane", Context.MODE_PRIVATE)
        val wpis = prefs.getString(id, null) ?: run {
            Log.w(TAG, "Brak wpisu planowanej wiadomości o id=$id")
            return
        }
        // Format wpisu: "numer\t<czasMs>\t<tresc>" (człony rozdzielone tabulatorem).
        val parts = wpis.split("\t")
        if (parts.size < 3) return
        val numer = parts[0]
        val tresc = parts.subList(2, parts.size).joinToString("\t")
        try {
            val sms = SmsManager.getDefault()
            sms.sendTextMessage(numer, null, tresc, null, null)
            Log.i(TAG, "Wysłano zaplanowany SMS na $numer: ${tresc.take(30)}")
        } catch (e: Exception) {
            Log.e(TAG, "Błąd wysyłki zaplanowanego SMS", e)
        }
        // Usuń wpis — wiadomość wysłana.
        prefs.edit().remove(id).apply()
    }

    companion object {
        private const val TAG = "PlanowanySms"
    }
}