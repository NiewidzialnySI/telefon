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

/**
 * Broadcast po przekazaniu SMS do SMSC (wzorzec Fossify SmsStatusSentReceiver).
 * Intent.data = content://sms/{id}
 */
class SmsStatusSentReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val uri = intent.data
        val lokalny = intent.getLongExtra(EXTRA_LOKALNY_KLUCZ, -1L).takeIf { it >= 0 }
        val messageId = uri?.lastPathSegment?.toLongOrNull()
            ?: intent.getLongExtra(EXTRA_MESSAGE_ID, -1L).takeIf { it >= 0 }

        when (resultCode) {
            Activity.RESULT_OK -> {
                // Nie nadpisuj STATUS_COMPLETE, jeśli raport dostarczenia zdążył wcześniej.
                aktualizujBaze(
                    context, uri, messageId,
                    type = Telephony.Sms.MESSAGE_TYPE_SENT,
                    status = Telephony.Sms.STATUS_PENDING,
                    nieNadpisujJesliDostarczono = true
                )
                StatusWiadomosciBus.emituj(
                    AktualizacjaStatusuWiadomosci(
                        messageUri = uri?.toString(),
                        messageId = messageId,
                        lokalnyKlucz = lokalny,
                        stan = StanWysylki.WYSŁANO,
                        type = Telephony.Sms.MESSAGE_TYPE_SENT,
                        status = Telephony.Sms.STATUS_PENDING
                    )
                )
            }
            else -> {
                aktualizujBaze(
                    context, uri, messageId,
                    type = Telephony.Sms.MESSAGE_TYPE_FAILED,
                    status = Telephony.Sms.STATUS_FAILED,
                    nieNadpisujJesliDostarczono = false
                )
                StatusWiadomosciBus.emituj(
                    AktualizacjaStatusuWiadomosci(
                        messageUri = uri?.toString(),
                        messageId = messageId,
                        lokalnyKlucz = lokalny,
                        stan = StanWysylki.BLAD,
                        type = Telephony.Sms.MESSAGE_TYPE_FAILED,
                        status = Telephony.Sms.STATUS_FAILED
                    )
                )
                val opis = when (resultCode) {
                    SmsManager.RESULT_ERROR_NO_SERVICE -> "Brak zasięgu"
                    SmsManager.RESULT_ERROR_RADIO_OFF -> "Radio wyłączone"
                    SmsManager.RESULT_ERROR_NULL_PDU -> "Błąd SMS (PDU)"
                    SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "Błąd wysyłania SMS"
                    else -> "Nie udało się wysłać SMS ($resultCode)"
                }
                Toast.makeText(context, opis, Toast.LENGTH_SHORT).show()
                Log.w(TAG, "SMS send failed code=$resultCode uri=$uri")
            }
        }
    }

    private fun aktualizujBaze(
        context: Context,
        uri: Uri?,
        messageId: Long?,
        type: Int,
        status: Int,
        nieNadpisujJesliDostarczono: Boolean
    ) {
        val values = ContentValues().apply {
            put(Telephony.Sms.TYPE, type)
            put(Telephony.Sms.STATUS, status)
            put(Telephony.Sms.DATE_SENT, System.currentTimeMillis())
        }
        val whereExtra = if (nieNadpisujJesliDostarczono) {
            " AND (${Telephony.Sms.STATUS} IS NULL OR ${Telephony.Sms.STATUS} != ${Telephony.Sms.STATUS_COMPLETE})"
        } else ""
        try {
            when {
                uri != null -> {
                    // Uri wskazuje konkretny wiersz — WHERE na status nadal działa.
                    if (nieNadpisujJesliDostarczono) {
                        context.contentResolver.update(
                            uri, values,
                            "${Telephony.Sms.STATUS} IS NULL OR ${Telephony.Sms.STATUS} != ?",
                            arrayOf(Telephony.Sms.STATUS_COMPLETE.toString())
                        )
                    } else {
                        context.contentResolver.update(uri, values, null, null)
                    }
                }
                messageId != null -> context.contentResolver.update(
                    Telephony.Sms.CONTENT_URI,
                    values,
                    "${Telephony.Sms._ID}=?$whereExtra",
                    arrayOf(messageId.toString())
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Nie udało się zaktualizować statusu SMS", e)
        }
    }

    companion object {
        private const val TAG = "SmsStatusSent"
        const val ACTION = "dialer.dominik.pl.SMS_SENT"
        const val EXTRA_MESSAGE_ID = "message_id"
        const val EXTRA_LOKALNY_KLUCZ = "lokalny_klucz"
    }
}
