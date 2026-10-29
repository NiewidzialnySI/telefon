package dialer.dominik.pl.sms

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log

/**
 * Raport dostarczenia SMS do telefonu odbiorcy.
 *
 * Ważne: NIE wolno polegać na samym [getResultCode].
 * Prawdziwy wynik jest w PDU (`SmsMessage.status`) — wiele OEM/operatorów
 * odpala broadcast z RESULT_CANCELED / pustym PDU zanim raport dotrze
 * (albo wcale go nie ma). Stąd „raz Wysłano, raz Błąd / Dostarczono”.
 *
 * Wzorzec: Fossify / AOSP Messaging — brak PDU = ignoruj; mapuj status z PDU.
 */
class SmsStatusDeliveredReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val uri = intent.data
        val lokalny = intent.getLongExtra(EXTRA_LOKALNY_KLUCZ, -1L).takeIf { it >= 0 }
        val messageId = uri?.lastPathSegment?.toLongOrNull()
            ?: intent.getLongExtra(EXTRA_MESSAGE_ID, -1L).takeIf { it >= 0 }

        val statusZPdu = odczytajStatusZRaportu(intent)
        if (statusZPdu == null) {
            // Brak sensownego raportu — zostaw „Wysłano” (STATUS_PENDING), nie krzycz o błędzie.
            Log.i(TAG, "Delivery broadcast bez PDU/statusu — ignoruję (uri=$uri)")
            return
        }

        val stan = when (statusZPdu) {
            Telephony.Sms.STATUS_COMPLETE -> StanWysylki.DOSTARCZONO
            Telephony.Sms.STATUS_FAILED -> StanWysylki.BLAD
            Telephony.Sms.STATUS_PENDING -> StanWysylki.WYSŁANO
            else -> {
                Log.i(TAG, "Delivery status=$statusZPdu — bez zmiany UI")
                return
            }
        }

        val values = ContentValues().apply {
            put(Telephony.Sms.STATUS, statusZPdu)
        }
        try {
            when {
                uri != null -> context.contentResolver.update(uri, values, null, null)
                messageId != null -> context.contentResolver.update(
                    Telephony.Sms.CONTENT_URI, values, "${Telephony.Sms._ID}=?", arrayOf(messageId.toString())
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Nie udało się zapisać raportu dostarczenia", e)
        }

        // STATUS_PENDING z raportu = nadal czekamy — nie emituj (zostaje „Wysłano”).
        if (stan == StanWysylki.WYSŁANO) return

        StatusWiadomosciBus.emituj(
            AktualizacjaStatusuWiadomosci(
                messageUri = uri?.toString(),
                messageId = messageId,
                lokalnyKlucz = lokalny,
                stan = stan,
                type = Telephony.Sms.MESSAGE_TYPE_SENT,
                status = statusZPdu
            )
        )
        Log.i(TAG, "Delivery → $stan status=$statusZPdu uri=$uri")
    }

    companion object {
        private const val TAG = "SmsStatusDelivered"
        const val ACTION = "dialer.dominik.pl.SMS_DELIVERED"
        const val EXTRA_MESSAGE_ID = "message_id"
        const val EXTRA_LOKALNY_KLUCZ = "lokalny_klucz"

        /**
         * @return STATUS_COMPLETE / PENDING / FAILED albo null gdy brak danych.
         */
        fun odczytajStatusZRaportu(intent: Intent): Int? {
            val pdu = intent.getByteArrayExtra("pdu") ?: return null
            val format = intent.getStringExtra("format")
            val sms = try {
                if (format != null) SmsMessage.createFromPdu(pdu, format)
                else @Suppress("DEPRECATION") SmsMessage.createFromPdu(pdu)
            } catch (e: Exception) {
                Log.w(TAG, "createFromPdu: ${e.message}")
                return null
            } ?: return null

            var status = try {
                sms.status
            } catch (e: NullPointerException) {
                // SmsMessage.mWrappedSmsMessage bywa null (OEM).
                return null
            }

            // CDMA (3GPP2): surowy status w innych bitach niż GSM.
            if (format == "3gpp2") {
                val errorClass = (status shr 24) and 0x03
                val statusCode = (status shr 16) and 0x3f
                status = when (errorClass) {
                    0 -> if (statusCode == 0x02) Telephony.Sms.STATUS_COMPLETE
                    else Telephony.Sms.STATUS_PENDING
                    2 -> Telephony.Sms.STATUS_PENDING
                    3 -> Telephony.Sms.STATUS_FAILED
                    else -> Telephony.Sms.STATUS_PENDING
                }
            }
            return status
        }
    }
}
