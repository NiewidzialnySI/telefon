package dialer.dominik.pl.call

import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import dialer.dominik.pl.data.BazaSpamu

/**
 * Identyfikacja rozmów i blokowanie spamu.
 *
 * WYMAGANE przez system, żeby aplikacja pojawiła się na liście
 * „Identyfikacja rozmów i spamu". Rola to ROLE_CALL_SCREENING.
 *
 * Usługa dostaje KAŻDE przychodzące połączenie, także spoza kontaktów.
 * Można je przepuścić, odrzucić, oznaczyć jako spam albo wyciszyć.
 *
 * Lista zablokowanych numerów jest trwała w [BazaSpamu].
 */
class UslugaIdentyfikacjiPolaczen : CallScreeningService() {

    companion object {
        private const val TAG = "IdentyfikacjaPolaczen"
    }

    override fun onScreenCall(callDetails: Call.Details) {
        val numer = callDetails.handle?.schemeSpecificPart ?: ""
        Log.i(TAG, "Sprawdzam połączenie od: $numer")

        val czySpam = BazaSpamu.czySpam(this, numer)

        val odpowiedz = CallResponse.Builder()
            .setDisallowCall(czySpam)
            .setRejectCall(czySpam)
            .setSkipCallLog(false)
            .setSkipNotification(false)
            .build()

        respondToCall(callDetails, odpowiedz)
    }
}
