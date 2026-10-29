package dialer.dominik.pl.ui.screens
import androidx.compose.runtime.Immutable

import android.Manifest
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.SmsManager
import android.widget.Toast
import dialer.dominik.pl.sms.KanalWiadomosci
import dialer.dominik.pl.sms.WykrywanieOtp
import dialer.dominik.pl.sms.wyslijPrzezKomunikator
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
@Immutable
data class SmsWpis(
    val id: Long,
    val address: String,
    val body: String,
    val date: Long,
    val type: Int,
    /** Czy wiadomość została przeczytana (kolumna READ w skrzynce). */
    val read: Boolean = true,
    /** Opcjonalna bitmapa załącznika (MMS). */
    val bitmap: android.graphics.Bitmap? = null,
    /** Opcjonalne ID wiadomości MMS do leniwego ładowania zdjęcia w UI. */
    val mmsMsgId: Long? = null,
    /**
     * Kolumna Telephony.Sms.STATUS (raport dostarczenia).
     * STATUS_NONE / PENDING / COMPLETE / FAILED.
     */
    val status: Int = Telephony.Sms.STATUS_NONE,
    /**
     * Klucz lokalny dla dymka optymistycznego (zanim/niezależnie od id w providerze).
     * Używany przy aktualizacjach z BroadcastReceiverów SENT/DELIVERED.
     */
    val lokalnyKlucz: Long? = null
)

/** Normalizuje adres SMS / nadawcę do celów grupowania i dopasowywania wątków. */
internal fun normalizujAdresSms(adres: String): String {
    if (adres.isBlank()) return ""
    val cyfry = adres.filter { it.isDigit() }
    if (cyfry.length >= 7) {
        if (cyfry.startsWith("48") && cyfry.length == 11) {
            return cyfry.substring(2)
        }
        if (cyfry.startsWith("0048") && cyfry.length == 13) {
            return cyfry.substring(4)
        }
        if (cyfry.length > 9) {
            return cyfry.takeLast(9)
        }
        return cyfry
    } else {
        return adres.lowercase().replace(Regex("[^a-z0-9]"), "")
    }
}

@Immutable
data class SmsWatek(
    val address: String,
    val contactName: String?,
    val lastMessage: String,
    val lastDate: Long,
    val messages: List<SmsWpis>,
    /** Czy numer ma swój kontakt w książce (pozwala ciemniej/jaśniej tło wątku). */
    val isKontakt: Boolean = false,
    /** Liczba nieprzeczytanych wiadomości w wątku (0 = wszystko przeczytane). */
    val nieprzeczytane: Int = 0,
    /** Pseudonim ustawiony w tej aplikacji (SharedPreferences) — wyświetlany nad numerem. */
    val pseudonim: String? = null,
    /** URI zdjęcia kontaktu z książki (do awatara w wątku). */
    val fotoUri: String? = null,
    /** ID pasującej wiadomości przy wyszukiwaniu (do skoku w czacie). */
    val searchMatchMsgId: Long? = null
)
