package dialer.dominik.pl.sms

import android.provider.Telephony
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Statusy wysyłki SMS/MMS widoczne w UI (wzorzec Fossify Messages).
 *
 * Telephony.Sms.STATUS_*:
 * - STATUS_NONE (-1) — brak raportu / jeszcze nie dotyczy
 * - STATUS_COMPLETE (0) — dostarczono do odbiorcy
 * - STATUS_PENDING (32) — oczekuje na raport dostarczenia
 * - STATUS_FAILED (64) — niedostarczono / błąd
 */
enum class StanWysylki {
    /** W trakcie wysyłki (OUTBOX / MMS budowane) — pokaż logo. */
    WYSYLANIE,
    /** Odebrane przez SMSC (wysłane z telefonu). */
    WYSŁANO,
    /** Raport dostarczenia OK. */
    DOSTARCZONO,
    /** Błąd wysyłki lub niedostarczenia. */
    BLAD
}

data class AktualizacjaStatusuWiadomosci(
    val messageUri: String?,
    val messageId: Long?,
    val lokalnyKlucz: Long?,
    val stan: StanWysylki,
    val type: Int? = null,
    val status: Int? = null
)

/** Bus do odświeżania dymków po broadcastach SENT / DELIVERED. */
object StatusWiadomosciBus {
    private val _aktualizacje = MutableSharedFlow<AktualizacjaStatusuWiadomosci>(extraBufferCapacity = 64)
    val aktualizacje: SharedFlow<AktualizacjaStatusuWiadomosci> = _aktualizacje.asSharedFlow()

    fun emituj(aktualizacja: AktualizacjaStatusuWiadomosci) {
        _aktualizacje.tryEmit(aktualizacja)
    }
}

/**
 * Mapowanie kolumn Telephony.Sms → UI.
 *
 * Kolejność sensu:
 * 1) OUTBOX/QUEUED → Wysyłanie…
 * 2) FAILED (type lub status) → Błąd
 * 3) STATUS_COMPLETE → Dostarczono (raport od odbiorcy)
 * 4) SENT + PENDING/NONE → Wysłano (SMSC przyjął; raport może nigdy nie przyjść)
 */
fun stanZKolumnSms(type: Int, status: Int): StanWysylki = when {
    type == Telephony.Sms.MESSAGE_TYPE_OUTBOX ||
        type == Telephony.Sms.MESSAGE_TYPE_QUEUED -> StanWysylki.WYSYLANIE
    type == Telephony.Sms.MESSAGE_TYPE_FAILED ||
        status == Telephony.Sms.STATUS_FAILED -> StanWysylki.BLAD
    status == Telephony.Sms.STATUS_COMPLETE -> StanWysylki.DOSTARCZONO
    // PENDING albo brak raportu (−1) przy SENT = wysłane z telefonu.
    type == Telephony.Sms.MESSAGE_TYPE_SENT -> StanWysylki.WYSŁANO
    else -> StanWysylki.WYSŁANO
}

/** Priorytet statusu przy scalaniu live + reload z bazy (wyższy = ostateczniejszy). */
fun priorytetStanu(stan: StanWysylki): Int = when (stan) {
    StanWysylki.WYSYLANIE -> 0
    StanWysylki.WYSŁANO -> 1
    StanWysylki.DOSTARCZONO -> 3
    StanWysylki.BLAD -> 2
}
