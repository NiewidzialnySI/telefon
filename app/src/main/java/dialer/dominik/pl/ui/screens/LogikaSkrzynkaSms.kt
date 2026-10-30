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
internal fun pobierzPasujacaWiadomosc(context: Context, adres: String, zapytanie: String): SmsWpis? {
    if (zapytanie.isBlank()) return null
    val q = normalizujPolski(zapytanie).trim()
    val wpisy = pobierzWiadomosciDlaAdresu(context, adres)
    return wpisy.find { normalizujPolski(it.body).contains(q) }
}

/**
 * Efektywne wyszukiwanie „w wiadomościach”.
 * Zamiast dla KAŻDEGO wątku osobno pobierać CAŁĄ skrzynkę i filtrować w Kotlinie
 * (pobierzWiadomosciDlaAdresu → query całego Telephony.Sms dla każdego adresu — sekundy
 * przy większej skrzynce), robimy JEDEN query z LIKE na treści. Zwracamy mapę
 * znormalizowanyAdres → pasująca wiadomość (najnowsza), więc wynik podpinamy pod wątki.
 */
internal fun pobierzPasujaceSmsy(context: Context, zapytanie: String): Map<String, SmsWpis> {
    val q = zapytanie.trim()
    if (q.length < 2) return emptyMap()
    // LIKE z escapem znaków specjalnych % i _ (żeby user szukający np. "100%" nie podważył wzorca)
    val esq = q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
    val wzorzec = "%$esq%"
    val wynik = LinkedHashMap<String, SmsWpis>() // zachowuje kolejność, usuwa duplikaty adresów
    try {
        val cursor = context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE
            ),
            "${Telephony.Sms.BODY} LIKE ?",
            arrayOf(wzorzec),
            "${Telephony.Sms.DATE} DESC"
        )
        cursor?.use { c ->
            val idIdx = c.getColumnIndex(Telephony.Sms._ID)
            val addrIdx = c.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyIdx = c.getColumnIndex(Telephony.Sms.BODY)
            val dateIdx = c.getColumnIndex(Telephony.Sms.DATE)
            while (c.moveToNext()) {
                val id = if (idIdx != -1) c.getLong(idIdx) else 0L
                val address = if (addrIdx != -1) c.getString(addrIdx) ?: "" else ""
                val body = if (bodyIdx != -1) c.getString(bodyIdx) ?: "" else ""
                val date = if (dateIdx != -1) c.getLong(dateIdx) else 0L
                val norm = normalizujAdresSms(address)
                // Pierwsze trafienie na adres = najnowsze (sort DESC) — pomiń resztę
                if (!wynik.containsKey(norm)) {
                    wynik[norm] = SmsWpis(id, address, body, date, Telephony.Sms.MESSAGE_TYPE_INBOX, true, null)
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return wynik
}

data class ContactCacheInfo(
    val name: String,
    val photoUri: String?
)

object KontaktyCache {
    @Volatile var mapaNumerow: Map<String, ContactCacheInfo>? = null

    fun get(context: Context, numer: String): ContactCacheInfo? {
        val mapa = mapaNumerow ?: synchronized(this) {
            mapaNumerow ?: wczytajWszystkieKontakty(context).also { mapaNumerow = it }
        }
        val norm = normalizujAdresSms(numer)
        return mapa[norm] ?: mapa[numer]
    }

    fun invalidate() {
        mapaNumerow = null
    }
}

internal fun wczytajWszystkieKontakty(context: Context): Map<String, ContactCacheInfo> {
    val mapa = mutableMapOf<String, ContactCacheInfo>()
    try {
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.PHOTO_URI
        )
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val photoIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)
            while (cursor.moveToNext()) {
                val number = if (numIdx != -1) cursor.getString(numIdx) ?: "" else ""
                val name = if (nameIdx != -1) cursor.getString(nameIdx) ?: "" else ""
                val photoUri = if (photoIdx != -1) cursor.getString(photoIdx) else null
                if (number.isNotBlank() && name.isNotBlank()) {
                    val norm = normalizujAdresSms(number)
                    mapa[norm] = ContactCacheInfo(name, photoUri)
                    mapa[number] = ContactCacheInfo(name, photoUri)
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return mapa
}

object WatekCache {
    @Volatile var watki: List<SmsWatek>? = null
}

/** Pełne unieważnienie (lista wątków + cache czatów + kontakty). */
internal fun uniewaznijCacheSkrzynki() {
    SkrzynkaCache.wpisy = null
    WatekCache.watki = null
    CzatWatekCache.invalidate()
    KontaktyCache.invalidate()
}

/** Tylko lista wątków / skrzynka — bez czyszczenia cache otwartego czatu. */
internal fun uniewaznijCacheListyWatkow() {
    SkrzynkaCache.wpisy = null
    WatekCache.watki = null
}

/**
 * Usuwa całą konwersację (SMS + MMS) dla numeru/nadawcy.
 * Najpierw po thread_id (`content://mms-sms/conversations/#`), potem fallback po wariantach ADDRESS.
 * Zwraca true gdy provider zgłosił usunięcie ≥1 wiersza (wymaga roli domyślnej SMS).
 */
internal fun usunKonwersacje(context: Context, numer: String): Boolean {
    if (numer.isBlank()) return false
    var usunieto = false
    try {
        val warianty = wariantyAdresuDoQuery(numer)
        var threadId: Long? = null
        // Szukaj istniejącego THREAD_ID — bez getOrCreate (nie twórz pustego wątku).
        for (w in warianty) {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms.THREAD_ID),
                "${Telephony.Sms.ADDRESS} = ?",
                arrayOf(w),
                null
            )?.use { c ->
                if (c.moveToFirst()) {
                    val tid = c.getLong(0)
                    if (tid > 0L) threadId = tid
                }
            }
            if (threadId != null) break
        }
        if (threadId == null) {
            threadId = threadIdDlaAdresu(context, numer)
        }
        val tid = threadId
        if (tid != null && tid > 0L) {
            try {
                val n = context.contentResolver.delete(
                    Uri.parse("content://mms-sms/conversations/$tid"),
                    null,
                    null
                )
                if (n > 0) usunieto = true
            } catch (e: Exception) {
                android.util.Log.w("SmsSkrzynka", "delete mms-sms/conversations: ${e.message}")
            }
            if (!usunieto) {
                try {
                    val n = context.contentResolver.delete(
                        Uri.parse("content://sms/conversations/$tid"),
                        null,
                        null
                    )
                    if (n > 0) usunieto = true
                } catch (e: Exception) {
                    android.util.Log.w("SmsSkrzynka", "delete sms/conversations: ${e.message}")
                }
            }
            try {
                context.contentResolver.delete(
                    Telephony.Mms.CONTENT_URI,
                    "${Telephony.Mms.THREAD_ID} = ?",
                    arrayOf(tid.toString())
                )
            } catch (_: Exception) { /* OEM bez kasowania MMS po thread */ }
        }
        if (warianty.isNotEmpty()) {
            val placeholders = warianty.joinToString(",") { "?" }
            try {
                val n = context.contentResolver.delete(
                    Telephony.Sms.CONTENT_URI,
                    "${Telephony.Sms.ADDRESS} IN ($placeholders)",
                    warianty.toTypedArray()
                )
                if (n > 0) usunieto = true
            } catch (e: Exception) {
                android.util.Log.w("SmsSkrzynka", "delete sms IN: ${e.message}")
            }
        }
        uniewaznijCacheListyWatkow()
        uniewaznijCacheWatku(numer)
    } catch (e: Exception) {
        android.util.Log.e("SmsSkrzynka", "Usuwanie konwersacji nieudane", e)
    }
    return usunieto
}

internal fun pobierzAdresDlaRecipientId(context: Context, recipientIdStr: String): String {
    if (recipientIdStr.isBlank()) return ""
    val firstId = recipientIdStr.trim().split(Regex("\\s+")).firstOrNull() ?: return ""
    return try {
        val uri = Uri.parse("content://mms-sms/canonical-address/$firstId")
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getString(0) ?: ""
            } else ""
        } ?: ""
    } catch (_: Exception) {
        ""
    }
}

/** Pobiera wątki SMS bezpośrednio z pre-indeksowanego natywnego widoku Telephony.Threads (content://mms-sms/conversations).
 *  Zwraca gotowe kafelki czatów w kilkanaście milisekund, niezależnie od liczby wiadomości w bazie! */
internal fun pobierzWątkiSms(context: Context, odswiez: Boolean = false): List<SmsWatek> {
    if (!odswiez) {
        WatekCache.watki?.let { return it }
    }
    if (odswiez) {
        KontaktyCache.invalidate()
    }

    val wynik = mutableListOf<SmsWatek>()
    try {
        val uri = Uri.parse("content://mms-sms/conversations?simple=true")
        val projection = arrayOf("_id", "date", "message_count", "snippet", "read", "recipient_ids")
        context.contentResolver.query(uri, projection, null, null, "date DESC")?.use { cursor ->
            val dateIdx = cursor.getColumnIndex("date")
            val snippetIdx = cursor.getColumnIndex("snippet")
            val readIdx = cursor.getColumnIndex("read")
            val recipientIdsIdx = cursor.getColumnIndex("recipient_ids")

            val countIdx = cursor.getColumnIndex("message_count")
            // Jeden odczyt prefs zamiast N× getSharedPreferences w pętli.
            val mapaPseudo = context.getSharedPreferences("kontakty_pseudonimy", Context.MODE_PRIVATE)
                .all
                .mapNotNull { (k, v) ->
                    val p = (v as? String)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    k to p
                }
                .toMap()
            while (cursor.moveToNext()) {
                var date = if (dateIdx != -1) cursor.getLong(dateIdx) else 0L
                if (date < 10000000000L) date *= 1000L
                val snippetRaw = if (snippetIdx != -1) cursor.getString(snippetIdx)?.trim().orEmpty() else ""
                val msgCount = if (countIdx != -1) cursor.getInt(countIdx) else 1
                // Pusty snippet + są wiadomości ≈ samo zdjęcie MMS (bez N+1 query na wątek).
                val snippet = snippetRaw.ifEmpty {
                    if (msgCount > 0) "MMS" else ""
                }
                val read = if (readIdx != -1) cursor.getInt(readIdx) == 1 else true
                val recipientIds = if (recipientIdsIdx != -1) cursor.getString(recipientIdsIdx) ?: "" else ""

                val address = pobierzAdresDlaRecipientId(context, recipientIds)
                if (address.isNotBlank()) {
                    val kontaktInfo = KontaktyCache.get(context, address)
                    val nazwa = kontaktInfo?.name
                    val foto = kontaktInfo?.photoUri

                    wynik.add(
                        SmsWatek(
                            address = address,
                            contactName = nazwa,
                            lastMessage = snippet,
                            lastDate = date,
                            messages = emptyList(),
                            isKontakt = nazwa != null,
                            nieprzeczytane = if (!read) 1 else 0,
                            pseudonim = nazwa?.let { mapaPseudo[it.trim()] },
                            fotoUri = foto
                        )
                    )
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    val ostateczneWiersze = if (wynik.isNotEmpty()) wynik else pobierzWątkiSmsFallback(context)

    // content://mms-sms/conversations potrafi zwrócić dwa wątki o tym samym adresie
    // (np. wiadomość grupowa + MMS, lub ten sam numer pod dwoma zapisami). Bez dedupe
    // LazyColumn z key={address} rzucał IllegalArgument: Key "..." already used — dokładnie
    // tak wywalało się przy wyszukiwaniu. Dedupe po znormalizowanym numerze.
    val zdeduplikowane = ostateczneWiersze.distinctBy { normalizujAdresSms(it.address) }

    WatekCache.watki = zdeduplikowane
    return zdeduplikowane
}

internal fun pobierzWątkiSmsFallback(context: Context): List<SmsWatek> {
    val wpisy = pobierzSkrzynkeSms(context, odswiez = true)
    val grupy = wpisy.groupBy { normalizujAdresSms(it.address) }
    return grupy.map { (normAddress, listaWpisow) ->
        val posortowane = listaWpisow.sortedBy { it.date }
        val ostatnia = posortowane.lastOrNull()
        val address = ostatnia?.address ?: normAddress
        val kontaktInfo = KontaktyCache.get(context, address)
        val nazwa = kontaktInfo?.name
        val foto = kontaktInfo?.photoUri
        val nieprzeczytane = listaWpisow.count { it.type == Telephony.Sms.MESSAGE_TYPE_INBOX && !it.read }
        val body = ostatnia?.body?.trim().orEmpty()
        SmsWatek(
            address = address,
            contactName = nazwa,
            lastMessage = when {
                body.isNotBlank() && body != "📷 [MMS]" -> body
                body == "📷 [MMS]" || body == "MMS" -> "MMS"
                else -> body
            },
            lastDate = ostatnia?.date ?: 0L,
            messages = emptyList(),
            isKontakt = nazwa != null,
            nieprzeczytane = nieprzeczytane,
            pseudonim = nazwa?.let { pobierzPseudonim(context, it) },
            fotoUri = foto
        )
    }.sortedByDescending { it.lastDate }
}

/** URI zdjęcia kontaktu z książki (PhoneLookup) albo null, gdy nie ma. */
internal fun pobierzFotoUriKontaktu(context: Context, numer: String): String? {
    if (numer.isBlank()) return null
    return try {
        val uri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI.buildUpon()
            .appendPath(Uri.encode(numer)).appendQueryParameter(ContactsContract.PhoneLookup.PHOTO_URI, "1").build()
        context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.PHOTO_URI), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (e: Exception) { null }
}

/**
 * Oznacza wszystkie nieprzeczytane wiadomości danego nadawcy jako przeczytane
 * (Telephony.Sms.READ = 1). Wywoływane przy otwarciu czatu — dzięki temu
 * karta wątku przestaje być wyróżniona jako „nieprzeczytana".
 */
internal fun oznaczWatekJakoPrzeczytany(context: Context, address: String) {
    try {
        val values = ContentValues().apply { put(Telephony.Sms.READ, 1) }
        // Exact address + warianty normalizacji (np. +48… vs 9 cyfr).
        val kandydaci = linkedSetOf(address, normalizujAdresSms(address)).filter { it.isNotBlank() }
        for (adr in kandydaci) {
            context.contentResolver.update(
                Telephony.Sms.CONTENT_URI,
                values,
                "${Telephony.Sms.ADDRESS} = ? AND ${Telephony.Sms.READ} = 0",
                arrayOf(adr)
            )
        }
        // MMS też ma kolumnę READ — bez tego licznik nieprzeczytanych zostaje po otwarciu czatu ze zdjęciem.
        oznaczMmsWatkuJakoPrzeczytane(context, address)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

/** Oznacza nieprzeczytane MMS-y wątku (po znormalizowanym adresie w addr). */
private fun oznaczMmsWatkuJakoPrzeczytane(context: Context, address: String) {
    try {
        val normCel = normalizujAdresSms(address)
        val cursor = context.contentResolver.query(
            Telephony.Mms.CONTENT_URI,
            arrayOf(Telephony.Mms._ID, Telephony.Mms.MESSAGE_BOX, Telephony.Mms.READ),
            "${Telephony.Mms.READ} = 0",
            null,
            null
        ) ?: return
        cursor.use { c ->
            val idIdx = c.getColumnIndex(Telephony.Mms._ID)
            val boxIdx = c.getColumnIndex(Telephony.Mms.MESSAGE_BOX)
            if (idIdx < 0) return
            val values = ContentValues().apply {
                put(Telephony.Mms.READ, 1)
                put(Telephony.Mms.SEEN, 1)
            }
            while (c.moveToNext()) {
                val id = c.getLong(idIdx)
                val box = if (boxIdx >= 0) c.getInt(boxIdx) else Telephony.Mms.MESSAGE_BOX_INBOX
                val mmsAddr = pobierzAdresMms(context, id, box) ?: continue
                if (normalizujAdresSms(mmsAddr) == normCel || mmsAddr == address) {
                    context.contentResolver.update(
                        Telephony.Mms.CONTENT_URI,
                        values,
                        "${Telephony.Mms._ID}=?",
                        arrayOf(id.toString())
                    )
                }
            }
        }
    } catch (e: Exception) {
        android.util.Log.w("LogikaSms", "oznacz MMS read: ${e.message}")
    }
}

/** Rozwiązuje nazwę kontaktu dla numeru telefonu. */
internal fun resolveContactName(context: Context, phoneNumber: String): String? {
    try {
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber))
        context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                if (nameIdx != -1) return cursor.getString(nameIdx)
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null
}
