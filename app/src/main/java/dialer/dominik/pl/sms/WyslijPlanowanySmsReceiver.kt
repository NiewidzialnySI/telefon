package dialer.dominik.pl.sms

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

/**
 * Odbiera alarm zaplanowanej wiadomości SMS (scheduled send) i wysyła ją.
 * Wiadomość odczytujemy z SharedPreferences "sms_planowane" po id, wysyłamy
 * przez SmsManager (domyślna subskrypcja + SENT/DELIVERED), a następnie usuwamy wpis.
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
        val ok = try {
            val sms = smsManagerDlaDomyslnejSubskrypcji(context)
            val czesci = sms.divideMessage(tresc)
            if (czesci.isNullOrEmpty()) {
                false
            } else {
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                val sentIntents = ArrayList<PendingIntent>(czesci.size)
                val deliveredIntents = ArrayList<PendingIntent>(czesci.size)
                for (i in czesci.indices) {
                    val sent = Intent(context, SmsStatusSentReceiver::class.java).apply {
                        action = SmsStatusSentReceiver.ACTION
                        putExtra("plan_id", id)
                        putExtra("part", i)
                    }
                    val del = Intent(context, SmsStatusDeliveredReceiver::class.java).apply {
                        action = SmsStatusDeliveredReceiver.ACTION
                        putExtra("plan_id", id)
                        putExtra("part", i)
                    }
                    sentIntents.add(
                        PendingIntent.getBroadcast(context, (id.hashCode() xor i), sent, flags)
                    )
                    deliveredIntents.add(
                        PendingIntent.getBroadcast(context, (id.hashCode() xor (i + 1000)), del, flags)
                    )
                }
                if (czesci.size == 1) {
                    sms.sendTextMessage(numer, null, czesci[0], sentIntents[0], deliveredIntents[0])
                } else {
                    sms.sendMultipartTextMessage(numer, null, czesci, sentIntents, deliveredIntents)
                }
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Błąd wysyłki zaplanowanego SMS", e)
            false
        }
        if (!ok) {
            // Zostaw wpis — kolejny alarm / ręczna ponowna próba nie zgubi treści.
            Log.w(TAG, "Planowany SMS nie wysłany — zostawiam prefs id=$id")
            return
        }
        try {
            val values = ContentValues().apply {
                put(Telephony.Sms.Sent.ADDRESS, numer)
                put(Telephony.Sms.Sent.BODY, tresc)
                put(Telephony.Sms.Sent.DATE, System.currentTimeMillis())
                put(Telephony.Sms.Sent.READ, 1)
            }
            context.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
        } catch (e: Exception) {
            Log.e(TAG, "Błąd zapisu planowanego SMS do Sent", e)
        }
        prefs.edit().remove(id).apply()
        Log.i(TAG, "Wysłano zaplanowany SMS na $numer (${tresc.length} znaków)")
    }

    companion object {
        private const val TAG = "PlanowanySms"
    }
}
