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
import android.os.Bundle
import android.os.SystemClock
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import android.widget.Toast
import dialer.dominik.pl.sms.KanalWiadomosci
import dialer.dominik.pl.sms.WykrywanieOtp
import dialer.dominik.pl.sms.wyslijPrzezKomunikator
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Ile wiadomości ładujemy przy otwarciu czatu / stronie historii (Fossify-style). */
internal const val CZAT_PAGE_SIZE = 80

private const val TAG_CZAT_PERF = "CzatPerf"

/** Cache wiadomości per znormalizowany adres — unika ponownego skanu Telephony. */
object CzatWatekCache {
    private val mapa = ConcurrentHashMap<String, List<SmsWpis>>()

    fun get(normAdres: String): List<SmsWpis>? = mapa[normAdres]

    fun put(normAdres: String, wpisy: List<SmsWpis>) {
        mapa[normAdres] = wpisy
    }

    fun invalidate(normAdres: String? = null) {
        if (normAdres == null) mapa.clear() else mapa.remove(normAdres)
    }
}

internal fun uniewaznijCacheWatku(adres: String) {
    CzatWatekCache.invalidate(normalizujAdresSms(adres))
}

/** Warianty numeru do SQL ADDRESS=? (PL +48 / lokalny). */
internal fun wariantyAdresuDoQuery(adres: String): List<String> {
    val out = linkedSetOf<String>()
    val trim = adres.trim()
    if (trim.isNotBlank()) out += trim
    val norm = normalizujAdresSms(adres)
    if (norm.isNotBlank()) {
        out += norm
        if (norm.all { it.isDigit() } && norm.length >= 7) {
            out += "+48$norm"
            out += "48$norm"
            out += "0048$norm"
        }
    }
    return out.toList()
}

internal fun threadIdDlaAdresu(context: Context, adres: String): Long? {
    return try {
        @Suppress("DEPRECATION")
        Telephony.Threads.getOrCreateThreadId(context, adres).takeIf { it > 0L }
    } catch (e: Exception) {
        try {
            @Suppress("DEPRECATION")
            Telephony.Threads.getOrCreateThreadId(context, setOf(adres)).takeIf { it > 0L }
        } catch (_: Exception) {
            Log.w(TAG_CZAT_PERF, "threadId: ${e.message}")
            null
        }
    }
}

/** Wynik optymistycznej wysyłki SMS — id w providerze + lokalny klucz do UI. */
data class WynikWysylkiSms(
    val messageId: Long,
    val messageUri: Uri,
    val lokalnyKlucz: Long
)

/**
 * Wysyła SMS (wzorzec Fossify MessagingUtils + SmsSender):
 * 1) zapis do OUTBOX ze STATUS_PENDING
 * 2) sendMultipartTextMessage z PendingIntent SENT/DELIVERED (URI wiadomości)
 * 3) BroadcastReceivery aktualizują TYPE/STATUS i emitują [StatusWiadomosciBus]
 *
 * Zwraca dane dymka albo null przy błędzie / kanale innym niż SMS.
 */
internal fun wyslijWiadomosc(
    context: Context,
    numer: String,
    tresc: String,
    kanal: KanalWiadomosci,
    lokalnyKlucz: Long = System.nanoTime()
): WynikWysylkiSms? {
    if (kanal != KanalWiadomosci.Sms) {
        wyslijPrzezKomunikator(context, kanal, numer, tresc)
        return null
    }

    val maSendSms = context.checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
    if (!maSendSms) {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$numer")).apply {
            putExtra("sms_body", tresc)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Błąd wysyłania", Toast.LENGTH_SHORT).show()
        }
        return null
    }

    return try {
        // Dual-SIM: SmsManager.getDefault() bywa bez subskrypcji → błąd wysyłki/MMS.
        val smsManager = dialer.dominik.pl.sms.smsManagerDlaDomyslnejSubskrypcji(context)

        val czesci = smsManager.divideMessage(tresc)
        if (czesci.isNullOrEmpty()) return null

        val teraz = System.currentTimeMillis()
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, numer)
            put(Telephony.Sms.BODY, tresc)
            put(Telephony.Sms.DATE, teraz)
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_OUTBOX)
            put(Telephony.Sms.STATUS, Telephony.Sms.STATUS_PENDING)
        }
        val messageUri = context.contentResolver.insert(Telephony.Sms.CONTENT_URI, values)
            ?: context.contentResolver.insert(Telephony.Sms.Outbox.CONTENT_URI, values)
            ?: run {
                Toast.makeText(context, "Nie można zapisać SMS", Toast.LENGTH_SHORT).show()
                return null
            }
        val messageId = messageUri.lastPathSegment?.toLongOrNull() ?: teraz

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        val sentIntenty = java.util.ArrayList<PendingIntent>(czesci.size)
        val deliveryIntenty = java.util.ArrayList<PendingIntent?>(czesci.size)

        for (i in czesci.indices) {
            val partReq = (messageId.toInt() xor (i shl 8))
            val sentIntent = Intent(context, dialer.dominik.pl.sms.SmsStatusSentReceiver::class.java).apply {
                action = dialer.dominik.pl.sms.SmsStatusSentReceiver.ACTION
                data = messageUri
                putExtra(dialer.dominik.pl.sms.SmsStatusSentReceiver.EXTRA_MESSAGE_ID, messageId)
                putExtra(dialer.dominik.pl.sms.SmsStatusSentReceiver.EXTRA_LOKALNY_KLUCZ, lokalnyKlucz)
            }
            sentIntenty.add(PendingIntent.getBroadcast(context, partReq, sentIntent, flags))

            // Raport dostarczenia tylko dla ostatniej części (jak Fossify).
            if (i == czesci.lastIndex) {
                val delIntent = Intent(context, dialer.dominik.pl.sms.SmsStatusDeliveredReceiver::class.java).apply {
                    action = dialer.dominik.pl.sms.SmsStatusDeliveredReceiver.ACTION
                    data = messageUri
                    putExtra(dialer.dominik.pl.sms.SmsStatusDeliveredReceiver.EXTRA_MESSAGE_ID, messageId)
                    putExtra(dialer.dominik.pl.sms.SmsStatusDeliveredReceiver.EXTRA_LOKALNY_KLUCZ, lokalnyKlucz)
                }
                deliveryIntenty.add(PendingIntent.getBroadcast(context, partReq + 0x100000, delIntent, flags))
            } else {
                deliveryIntenty.add(null)
            }
        }

        @Suppress("UNCHECKED_CAST")
        smsManager.sendMultipartTextMessage(
            numer, null, czesci, sentIntenty, deliveryIntenty as java.util.ArrayList<PendingIntent>
        )
        WynikWysylkiSms(messageId = messageId, messageUri = messageUri, lokalnyKlucz = lokalnyKlucz)
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Błąd wysyłania", Toast.LENGTH_SHORT).show()
        null
    }
}

/** Globalny cache skrzynki w pamięci — żeby nie czytać całej skrzynki przy każdym otwarciu czatu. */
object SkrzynkaCache {
    @Volatile var wpisy: List<SmsWpis>? = null
}

/**
 * Pobiera wiadomości SMS oraz MMS z systemowej skrzynki. Gdy [odswiez] = false i cache już jest,
 * zwraca zapamiętaną listę NATYCHMIAST (bez ponownego czytania całej skrzynki).
 * Przy [odswiez] = true (np. po wysłaniu / pull-to-refresh) przeładowuje z dysku i aktualizuje cache.
 */
internal fun pobierzSkrzynkeSms(context: Context, odswiez: Boolean = false): List<SmsWpis> {
    if (!odswiez) {
        SkrzynkaCache.wpisy?.let { return it }
    }
    val lista = mutableListOf<SmsWpis>()
    
    // 1. Pobierz SMS-y (limit 300 najnowszych — Bundle QUERY_ARG_*, nie „LIMIT” w sortOrder).
    try {
        val projekcja = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE,
            Telephony.Sms.READ,
            Telephony.Sms.STATUS
        )
        val queryArgs = android.os.Bundle().apply {
            putInt(android.content.ContentResolver.QUERY_ARG_LIMIT, 300)
            putStringArray(
                android.content.ContentResolver.QUERY_ARG_SORT_COLUMNS,
                arrayOf(Telephony.Sms.DATE)
            )
            putInt(
                android.content.ContentResolver.QUERY_ARG_SORT_DIRECTION,
                android.content.ContentResolver.QUERY_SORT_DIRECTION_DESCENDING
            )
        }
        val cursor = context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            projekcja,
            queryArgs,
            null
        )
        cursor?.use {
            val idIdx = cursor.getColumnIndex(Telephony.Sms._ID)
            val addrIdx = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyIdx = cursor.getColumnIndex(Telephony.Sms.BODY)
            val dateIdx = cursor.getColumnIndex(Telephony.Sms.DATE)
            val typeIdx = cursor.getColumnIndex(Telephony.Sms.TYPE)
            val readIdx = cursor.getColumnIndex(Telephony.Sms.READ)
            val statusIdx = cursor.getColumnIndex(Telephony.Sms.STATUS)

            while (cursor.moveToNext()) {
                val id = if (idIdx != -1) cursor.getLong(idIdx) else 0L
                val address = if (addrIdx != -1) cursor.getString(addrIdx) ?: "" else ""
                val body = if (bodyIdx != -1) cursor.getString(bodyIdx) ?: "" else ""
                val date = if (dateIdx != -1) cursor.getLong(dateIdx) else 0L
                val type = if (typeIdx != -1) cursor.getInt(typeIdx) else Telephony.Sms.MESSAGE_TYPE_INBOX
                val read = if (readIdx != -1) cursor.getInt(readIdx) == 1 else true
                val status = if (statusIdx != -1) cursor.getInt(statusIdx) else Telephony.Sms.STATUS_NONE

                if (address.isNotBlank()) {
                    lista.add(SmsWpis(id, address, body, date, type, read, null, status = status))
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    // 2. Pobierz MMS-y (Inbox/Sent — limit 100 przez queryArgs).
    try {
        val mmsArgs = android.os.Bundle().apply {
            putInt(android.content.ContentResolver.QUERY_ARG_LIMIT, 100)
            putStringArray(
                android.content.ContentResolver.QUERY_ARG_SORT_COLUMNS,
                arrayOf(android.provider.Telephony.Mms.DATE)
            )
            putInt(
                android.content.ContentResolver.QUERY_ARG_SORT_DIRECTION,
                android.content.ContentResolver.QUERY_SORT_DIRECTION_DESCENDING
            )
        }
        val mmsCursor = context.contentResolver.query(
            android.provider.Telephony.Mms.CONTENT_URI,
            arrayOf("_id", "date", "msg_box", "read"),
            mmsArgs,
            null
        )
        mmsCursor?.use {
            val idIdx = mmsCursor.getColumnIndex("_id")
            val dateIdx = mmsCursor.getColumnIndex("date")
            val boxIdx = mmsCursor.getColumnIndex("msg_box")
            val readIdx = mmsCursor.getColumnIndex("read")

            while (mmsCursor.moveToNext()) {
                val id = if (idIdx != -1) mmsCursor.getLong(idIdx) else 0L
                var date = if (dateIdx != -1) mmsCursor.getLong(dateIdx) else 0L
                if (date < 10000000000L) date *= 1000L // konwersja sekund na ms
                val box = if (boxIdx != -1) mmsCursor.getInt(boxIdx) else 1
                val read = if (readIdx != -1) mmsCursor.getInt(readIdx) == 1 else true
                val type = if (box == 2 || box == 4) Telephony.Sms.MESSAGE_TYPE_SENT else Telephony.Sms.MESSAGE_TYPE_INBOX

                val address = pobierzAdresMms(context, id, box) ?: continue
                val textBody = pobierzTekstMms(context, id)
                // Bitmapy ładujemy leniwie w UI (mmsMsgId) — pełne dekodowanie 100 MMS
                // przy każdym odświeżeniu skrzynki blokowało wątek i często wywalało OOM.

                val finalBody = if (textBody.isNotBlank()) textBody else "📷 [MMS]"

                lista.add(
                    SmsWpis(
                        id = 1_000_000_000L + id,
                        address = address,
                        body = finalBody,
                        date = date,
                        type = type,
                        read = read,
                        bitmap = null,
                        mmsMsgId = id
                    )
                )
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    val posortowane = lista.sortedByDescending { it.date }
    SkrzynkaCache.wpisy = posortowane
    return posortowane
}

/**
 * Wiadomości czatu dla adresu — po [threadId] / wariantach ADDRESS, z limitem stron.
 * @param starszeNizMs gdy nie-null: kolejna strona historii starsza niż ta data (ms).
 * @param uzyjCache true = zwróć cache jeśli jest (tylko pierwsza strona).
 */
internal fun pobierzWiadomosciDlaAdresu(
    context: Context,
    adres: String,
    limit: Int = CZAT_PAGE_SIZE,
    starszeNizMs: Long? = null,
    uzyjCache: Boolean = true
): List<SmsWpis> {
    val t0 = SystemClock.elapsedRealtime()
    val normTarget = normalizujAdresSms(adres)

    if (uzyjCache && starszeNizMs == null) {
        CzatWatekCache.get(normTarget)?.let { cached ->
            val out = if (limit < Int.MAX_VALUE) cached.take(limit) else cached
            Log.i(TAG_CZAT_PERF, "cache hit norm=$normTarget n=${out.size} ${SystemClock.elapsedRealtime() - t0}ms")
            return out
        }
    }

    val lista = mutableListOf<SmsWpis>()
    val threadId = threadIdDlaAdresu(context, adres)

    try {
        lista.addAll(
            pobierzSmsDlaWatku(
                context = context,
                adres = adres,
                threadId = threadId,
                limit = limit,
                starszeNizMs = starszeNizMs
            )
        )
        lista.addAll(
            pobierzMmsZdjeciaDlaAdresu(
                context = context,
                adres = adres,
                threadId = threadId,
                limit = limit,
                starszeNizMs = starszeNizMs
            )
        )
    } catch (e: Exception) {
        Log.e(TAG_CZAT_PERF, "pobierzWiadomosciDlaAdresu", e)
    }

    val posortowane = lista
        .distinctBy { if (it.mmsMsgId != null) "m${it.mmsMsgId}" else "s${it.id}" }
        .sortedByDescending { it.date }
        .let { if (limit < Int.MAX_VALUE) it.take(limit) else it }

    if (starszeNizMs == null) {
        CzatWatekCache.put(normTarget, posortowane)
    } else if (posortowane.isNotEmpty()) {
        val merged = ((CzatWatekCache.get(normTarget).orEmpty()) + posortowane)
            .distinctBy { if (it.mmsMsgId != null) "m${it.mmsMsgId}" else "s${it.id}" }
            .sortedByDescending { it.date }
        CzatWatekCache.put(normTarget, merged)
    }

    Log.i(
        TAG_CZAT_PERF,
        "load norm=$normTarget thread=$threadId n=${posortowane.size} olderThan=$starszeNizMs ${SystemClock.elapsedRealtime() - t0}ms"
    )
    return posortowane
}

private fun pobierzSmsDlaWatku(
    context: Context,
    adres: String,
    threadId: Long?,
    limit: Int,
    starszeNizMs: Long?
): List<SmsWpis> {
    val lista = mutableListOf<SmsWpis>()
    val sel = mutableListOf<String>()
    val args = mutableListOf<String>()

    if (threadId != null && threadId > 0L) {
        sel += "${Telephony.Sms.THREAD_ID}=?"
        args += threadId.toString()
    } else {
        val warianty = wariantyAdresuDoQuery(adres)
        if (warianty.isEmpty()) return emptyList()
        sel += "(" + warianty.joinToString(" OR ") { "${Telephony.Sms.ADDRESS}=?" } + ")"
        args.addAll(warianty)
    }
    if (starszeNizMs != null) {
        sel += "${Telephony.Sms.DATE}<?"
        args += starszeNizMs.toString()
    }

    val queryArgs = Bundle().apply {
        if (limit < Int.MAX_VALUE) putInt(android.content.ContentResolver.QUERY_ARG_LIMIT, limit)
        putStringArray(
            android.content.ContentResolver.QUERY_ARG_SORT_COLUMNS,
            arrayOf(Telephony.Sms.DATE)
        )
        putInt(
            android.content.ContentResolver.QUERY_ARG_SORT_DIRECTION,
            android.content.ContentResolver.QUERY_SORT_DIRECTION_DESCENDING
        )
        putString(
            android.content.ContentResolver.QUERY_ARG_SQL_SELECTION,
            sel.joinToString(" AND ")
        )
        putStringArray(
            android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
            args.toTypedArray()
        )
    }

    context.contentResolver.query(
        Telephony.Sms.CONTENT_URI,
        arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE,
            Telephony.Sms.READ,
            Telephony.Sms.STATUS
        ),
        queryArgs,
        null
    )?.use { cursor ->
        val idIdx = cursor.getColumnIndex(Telephony.Sms._ID)
        val addrIdx = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
        val bodyIdx = cursor.getColumnIndex(Telephony.Sms.BODY)
        val dateIdx = cursor.getColumnIndex(Telephony.Sms.DATE)
        val typeIdx = cursor.getColumnIndex(Telephony.Sms.TYPE)
        val readIdx = cursor.getColumnIndex(Telephony.Sms.READ)
        val statusIdx = cursor.getColumnIndex(Telephony.Sms.STATUS)
        val normTarget = normalizujAdresSms(adres)
        while (cursor.moveToNext()) {
            val address = if (addrIdx != -1) cursor.getString(addrIdx) ?: "" else ""
            // Gdy query po ADDRESS — i tak filtr norm (różne zapisy tego samego numeru).
            if (threadId == null && normalizujAdresSms(address) != normTarget) continue
            lista.add(
                SmsWpis(
                    id = if (idIdx != -1) cursor.getLong(idIdx) else 0L,
                    address = address,
                    body = if (bodyIdx != -1) cursor.getString(bodyIdx) ?: "" else "",
                    date = if (dateIdx != -1) cursor.getLong(dateIdx) else 0L,
                    type = if (typeIdx != -1) cursor.getInt(typeIdx) else Telephony.Sms.MESSAGE_TYPE_INBOX,
                    read = if (readIdx != -1) cursor.getInt(readIdx) == 1 else true,
                    bitmap = null,
                    status = if (statusIdx != -1) cursor.getInt(statusIdx) else Telephony.Sms.STATUS_NONE
                )
            )
        }
    }
    return lista
}

/**
 * MMS dla wątku — po THREAD_ID (szybko) lub skan z limitem + filtr adresu (fallback).
 * Pomija NotificationInd (m_type=130).
 */
internal fun pobierzMmsZdjeciaDlaAdresu(
    context: Context,
    adres: String,
    threadId: Long? = null,
    limit: Int = CZAT_PAGE_SIZE,
    starszeNizMs: Long? = null
): List<SmsWpis> {
    val wynik = mutableListOf<SmsWpis>()
    val normTarget = normalizujAdresSms(adres)
    val tid = threadId ?: threadIdDlaAdresu(context, adres)
    try {
        val sel = mutableListOf<String>()
        val args = mutableListOf<String>()
        // 130 = NOTIFICATION_IND — bez treści.
        sel += "${Telephony.Mms.MESSAGE_TYPE}!=?"
        args += "130"
        if (tid != null && tid > 0L) {
            sel += "${Telephony.Mms.THREAD_ID}=?"
            args += tid.toString()
        }
        if (starszeNizMs != null) {
            // Kolumna date MMS = sekundy.
            sel += "${Telephony.Mms.DATE}<?"
            args += (starszeNizMs / 1000L).toString()
        }

        val queryArgs = Bundle().apply {
            // Bez threadId i tak nie skanujemy nieskończenie — limit × 3 zapas na filtr adresu.
            val lim = if (tid != null) limit else (limit * 3).coerceAtMost(300)
            if (lim < Int.MAX_VALUE) putInt(android.content.ContentResolver.QUERY_ARG_LIMIT, lim)
            putStringArray(
                android.content.ContentResolver.QUERY_ARG_SORT_COLUMNS,
                arrayOf(Telephony.Mms.DATE)
            )
            putInt(
                android.content.ContentResolver.QUERY_ARG_SORT_DIRECTION,
                android.content.ContentResolver.QUERY_SORT_DIRECTION_DESCENDING
            )
            putString(
                android.content.ContentResolver.QUERY_ARG_SQL_SELECTION,
                sel.joinToString(" AND ")
            )
            putStringArray(
                android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                args.toTypedArray()
            )
        }

        context.contentResolver.query(
            Telephony.Mms.CONTENT_URI,
            arrayOf("_id", "date", "msg_box", "read", "thread_id"),
            queryArgs,
            null
        )?.use { mms ->
            val idIdx = mms.getColumnIndex("_id")
            val dateIdx = mms.getColumnIndex("date")
            val boxIdx = mms.getColumnIndex("msg_box")
            val readIdx = mms.getColumnIndex("read")
            while (mms.moveToNext() && wynik.size < limit) {
                val id = if (idIdx != -1) mms.getLong(idIdx) else 0L
                var date = if (dateIdx != -1) mms.getLong(dateIdx) else 0L
                if (date < 10000000000L) date *= 1000L
                val box = if (boxIdx != -1) mms.getInt(boxIdx) else 1
                val read = if (readIdx != -1) mms.getInt(readIdx) == 1 else true
                val type = if (box == 2 || box == 4) Telephony.Sms.MESSAGE_TYPE_SENT else Telephony.Sms.MESSAGE_TYPE_INBOX

                val mmsAddress = pobierzAdresMms(context, id, box) ?: continue
                if (tid == null && normalizujAdresSms(mmsAddress) != normTarget) continue
                // Przy threadId i tak weryfikuj norm — grupowe wątki rzadkie, ale bezpieczniej.
                if (tid != null && normalizujAdresSms(mmsAddress) != normTarget &&
                    normalizujAdresSms(mmsAddress).isNotEmpty()
                ) {
                    // Wątek 1:1 — zwykle match; przy mismatch zostaw (OEM czasem myli addr).
                }
                val textBody = pobierzTekstMms(context, id)
                val finalBody = if (textBody.isNotBlank()) textBody else "📷 [MMS]"
                wynik.add(
                    SmsWpis(
                        id = 1_000_000_000L + id,
                        address = mmsAddress.ifBlank { adres },
                        body = finalBody,
                        date = date,
                        type = type,
                        read = read,
                        bitmap = null,
                        mmsMsgId = id
                    )
                )
            }
        }
    } catch (e: Exception) {
        Log.e(TAG_CZAT_PERF, "pobierzMmsZdjeciaDlaAdresu", e)
    }
    return wynik
}

/** Wyszukuje kontakty po nazwie i numerze (max 5 wyników), grupując numery per kontakt. */
internal fun wyszukajKontaktyDlaSms(context: Context, query: String): List<ContactSuggestion> {
    val q = query.trim().lowercase()
    val qNum = q.filter { it.isDigit() }
    val dane = mutableMapOf<Long, Pair<String, MutableList<String>>>() // contactId -> (nazwa, numery)
    try {
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone._ID,
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        context.contentResolver.query(uri, projection, null, null, "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC")?.use { cursor ->
            val contactIdIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext()) {
                val contactId = if (contactIdIdx != -1) cursor.getLong(contactIdIdx) else 0L
                val number = if (numIdx != -1) cursor.getString(numIdx) ?: "" else ""
                val name = if (nameIdx != -1) cursor.getString(nameIdx) ?: "" else ""
                if (name.isBlank() || number.isBlank()) continue
                // Szukamy PO NAZWIE (imię/nazwisko/firma/pseudonim) ORAZ po numerze.
                val pasujeNazwa = q.isBlank() || name.lowercase().contains(q) ||
                    (pobierzPseudonim(context, name)?.lowercase()?.contains(q) == true)
                val pasujeNumer = qNum.isNotBlank() && normalizujKontakt(number).contains(qNum)
                if (pasujeNazwa || pasujeNumer) {
                    val para = dane.getOrPut(contactId) { name to mutableListOf() }
                    if (normalizujKontakt(number) !in para.second.map { normalizujKontakt(it) }) {
                        para.second.add(number)
                    }
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    val lista = dane.entries
        .map { (_, para) ->
            ContactSuggestion(
                name = para.first,
                number = para.second.firstOrNull(),
                numery = para.second
            )
        }
    // Pusty query -> cała książka (limit dla czytelności); inaczej -> wyniki wyszukiwania.
    return if (q.isEmpty()) lista.take(60) else lista.take(12)
}

/** Czy nadawca to znana firma (alfa-nadawca, np. Microsoft, MarketUL) — adres NIE jest
 *  numerem telefonu, tylko nazwą. Taki wątek pokazujemy z logiem zamiast awatara/inicjału. */
internal fun czyZnanaFirma(adres: String?): Boolean {
    if (adres.isNullOrBlank()) return false
    val cyfry = adres.count { it.isDigit() }
    val litery = adres.count { it.isLetter() }
    val tylkoNumer = cyfry > 0 && litery == 0
    return !tylkoNumer && litery >= 3 && !adres.contains("@")
}

/** Czy ta aplikacja jest domyślną do SMS (rola ROLE_SMS / pakiet systemowy). */
internal fun czyJestDomyslnaAplikacjaSms(context: Context): Boolean {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
        val roleManager = context.getSystemService(android.app.role.RoleManager::class.java)
        if (roleManager != null && roleManager.isRoleAvailable(android.app.role.RoleManager.ROLE_SMS)) {
            return roleManager.isRoleHeld(android.app.role.RoleManager.ROLE_SMS)
        }
    }
    return Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
}

/**
 * Intencja systemowego wyboru domyślnej aplikacji SMS
 * ([RoleManager.createRequestRoleIntent] / starsze ACTION_CHANGE_DEFAULT).
 */
internal fun intencjaUstawDomyslnaSms(context: Context): Intent {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
        val roleManager = context.getSystemService(android.app.role.RoleManager::class.java)
        if (roleManager != null && roleManager.isRoleAvailable(android.app.role.RoleManager.ROLE_SMS)) {
            return roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_SMS)
        }
    }
    @Suppress("DEPRECATION")
    return Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
        putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
    }
}

/** Normalizuje tekst: lowercase + foldowanie polskich znaków diakrytycznych (ą→a itd.). */
internal fun normalizujPolski(s: String): String {
    val mapa = mapOf(
        'ą' to 'a', 'ć' to 'c', 'ę' to 'e', 'ł' to 'l', 'ń' to 'n',
        'ó' to 'o', 'ś' to 's', 'ź' to 'z', 'ż' to 'z'
    )
    val sb = StringBuilder(s.length)
    for (ch in s) {
        val c = ch.lowercaseChar()
        sb.append(mapa[c] ?: c)
    }
    return sb.toString()
}

/** Odległość Levenshteina (edycyjna) między dwoma napisami. */
internal fun lewenshtein(a: String, b: String): Int {
    val m = a.length; val n = b.length
    if (m == 0) return n
    if (n == 0) return m
    var poprzedni = IntArray(n + 1) { it }
    var biezacy = IntArray(n + 1)
    for (i in 1..m) {
        biezacy[0] = i
        for (j in 1..n) {
            val koszt = if (a[i - 1] == b[j - 1]) 0 else 1
            biezacy[j] = minOf(
                poprzedni[j] + 1,      // usunięcie
                biezacy[j - 1] + 1,     // wstawienie
                poprzedni[j - 1] + koszt // zamiana
            )
        }
        val tmp = poprzedni; poprzedni = biezacy; biezacy = tmp
    }
    return poprzedni[n]
}

/** Czy watek pasuje do zapytania: szuka w nazwie, numerze i PEŁNEJ treści czatu,
 *  z obsługą polskich znaków i literówek (fuzzy Levenshtein ratio ≥ 0.7). */
internal fun szukajWWatku(watek: SmsWatek, zapytanie: String): Boolean {
    val q = normalizujPolski(zapytanie).trim()
    if (q.isBlank()) return false
    val qz = q.filter { it.isLetterOrDigit() }

    // 1. Szybkie trafienie: nazwa / numer / ostatnia treść zawierają zapytanie (znormalizowane).
    val nazwa = normalizujPolski(watek.contactName ?: "")
    val adres = normalizujPolski(watek.address)
    if (adres.contains(q, ignoreCase = true) || nazwa.contains(q, ignoreCase = true)) return true

    // 2. Pełna treść czatu — połącz wiadomości.
    val tresc = watek.messages.joinToString(" ") { normalizujPolski(it.body) }
    if (tresc.contains(q, ignoreCase = true)) return true

    // 3. Fuzzy: któreś słowo z zapytania (≥3 znaki) pasuje do słowa w treści/nazwie
    //    na co najmniej 70% (Levenshtein) — wykrywa literówki.
    val slowaZapytania = q.split(Regex("\\s+")).map { it.filter { c -> c.isLetterOrDigit() } }
        .filter { it.length >= 3 }
    val corpus = (nazwa + " " + tresc).split(Regex("\\s+")).map { it.filter { c -> c.isLetterOrDigit() } }
    for (zw in slowaZapytania) {
        for (sw in corpus) {
            if (sw.length >= 3) {
                val dl = maxOf(zw.length, sw.length)
                val ratio = 1.0 - lewenshtein(zw, sw).toDouble() / dl
                if (ratio >= 0.7) return true
            }
        }
    }
    return false
}

/**
 * Zwraca zaplanowane SMS-y wątku (format wpisu: "numer\t<czasMs>\t<tresc>")
 * jako listę (id, czasMs, treść) posortowaną wg czasu planowanej wysyłki.
 */
internal fun pobierzPlanowaneSms(context: Context, numer: String): List<Triple<String, Long, String>> {
    val prefs = context.getSharedPreferences("sms_planowane", Context.MODE_PRIVATE)
    val out = ArrayList<Triple<String, Long, String>>()
    val teraz = System.currentTimeMillis()
    prefs.all.forEach { (id, v) ->
        val wpis = v as? String ?: return@forEach
        val parts = wpis.split("\t")
        if (parts.size < 3) return@forEach
        if (parts[0] != numer) return@forEach
        val czas = parts[1].toLongOrNull() ?: return@forEach
        // Pokaż tylko jeszcze niewysłane (alarm w przyszłości).
        if (czas <= teraz) return@forEach
        out.add(Triple(id, czas, parts.subList(2, parts.size).joinToString("\t")))
    }
    return out.sortedBy { it.second }
}

/** Odwołuje zaplanowaną wiadomość: usuwa wpis z prefs i anuluje alarm. */
internal fun odwolajPlanowanySms(context: Context, id: String) {
    val prefs = context.getSharedPreferences("sms_planowane", Context.MODE_PRIVATE)
    val intent = android.content.Intent(context, dialer.dominik.pl.sms.WyslijPlanowanySmsReceiver::class.java)
        .putExtra("PLAN_ID", id)
    val pending = android.app.PendingIntent.getBroadcast(
        context, id.hashCode(), intent,
        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
    )
    val alarm = context.getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
    alarm.cancel(pending)
    prefs.edit().remove(id).apply()
}

/** Oblicza licznik znaków i części SMS (np. "140/160 (1)"). */
internal fun obliczLicznikSms(tekst: String): String {
    if (tekst.isEmpty()) return ""
    val czyUnicode = tekst.any { c -> c !in "\n\r !\"#\$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~" }
    val maxSingle = if (czyUnicode) 70 else 160
    val maxMulti = if (czyUnicode) 67 else 153
    
    val len = tekst.length
    return if (len <= maxSingle) {
        "${maxSingle - len}/$maxSingle (1)"
    } else {
        val czesci = (len + maxMulti - 1) / maxMulti
        val pozostalo = czesci * maxMulti - len
        "$pozostalo/$maxMulti ($czesci)"
    }
}

/**
 * Usuwa przychodzące SMS-y z kodami 2FA/OTP starsze niż 24 h.
 *
 * Wykrywanie jak w [dialer.dominik.pl.sms.WykrywanieOtp] / projekcie SmsCode na GitHubie:
 * słowo kluczowe + krótki kod cyfrowy w pobliżu (nie samo `contains("kod")`).
 * Tylko skrzynka odbiorcza (`MESSAGE_TYPE_INBOX`).
 */
internal fun usunStareKodyOtp(context: Context): Int {
    var usuniete = 0
    try {
        val dobamMs = 24 * 3600 * 1000L
        val limitCzasu = System.currentTimeMillis() - dobamMs
        val selection = "${Telephony.Sms.DATE} < ? AND ${Telephony.Sms.TYPE} = ?"
        val args = arrayOf(
            limitCzasu.toString(),
            Telephony.Sms.MESSAGE_TYPE_INBOX.toString()
        )
        val cursor = context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms._ID, Telephony.Sms.BODY),
            selection,
            args,
            null
        )
        val doUsuniecia = ArrayList<Long>()
        cursor?.use { c ->
            val idIdx = c.getColumnIndex(Telephony.Sms._ID)
            val bodyIdx = c.getColumnIndex(Telephony.Sms.BODY)
            if (idIdx == -1 || bodyIdx == -1) return@use
            while (c.moveToNext()) {
                val id = c.getLong(idIdx)
                val body = c.getString(bodyIdx) ?: ""
                if (id != 0L && WykrywanieOtp.czyWiadomoscOtp(body)) {
                    doUsuniecia.add(id)
                }
            }
        }
        for (id in doUsuniecia) {
            val count = context.contentResolver.delete(
                Telephony.Sms.CONTENT_URI,
                "${Telephony.Sms._ID} = ?",
                arrayOf(id.toString())
            )
            if (count > 0) usuniete++
        }
        if (usuniete > 0) {
            uniewaznijCacheSkrzynki()
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return usuniete
}

// ----- ROBOZY CZAT (draft) -----
// "Roboczy" czat: numer bez systemowego wątku, ale z zapisanym szkicem (draft).
// Gdy zaczniemy pisać i wyjdziemy, draft trwa (nie usuwa się), a numer pojawia się
// w skrzynce jako wątek roboczy. Wysyłka czyści draft; usunięcie usuwa go.

internal fun zapiszDraftCzatu(context: Context, numer: String, tresc: String) {
    val prefs = context.getSharedPreferences("sms_drafty_robocze", Context.MODE_PRIVATE)
    android.util.Log.i("DraftCzat", "zapiszDraftCzatu numer=$numer tresc='$tresc'")
    if (tresc.isBlank()) {
        prefs.edit().remove(numer).apply()
    } else {
        prefs.edit().putString(numer, tresc).apply()
    }
}

internal fun pobierzDraftCzatu(context: Context, numer: String): String? {
    val prefs = context.getSharedPreferences("sms_drafty_robocze", Context.MODE_PRIVATE)
    return prefs.getString(numer, null)?.takeIf { it.isNotBlank() }
}

internal fun usunDraftCzatu(context: Context, numer: String) {
    val prefs = context.getSharedPreferences("sms_drafty_robocze", Context.MODE_PRIVATE)
    prefs.edit().remove(numer).apply()
}

// ----- AUTO-EMOJI z emotikonów tekstowych -----
// Mapowanie typowych emotikonów tekstowych na emoji. Zamieniane NA KOŃCU wpisywanego tekstu
// (gdy użytkownik właśnie "dokończył" emotikon spacją/enterem lub po prostu go wpisał).

private val mapaEmotikon = listOf(
    ":-)" to "😊", ":)" to "😊", ":-)" to "🙂",
    ":D" to "😄", ":-D" to "😄", "XD" to "😆", "xD" to "😆",
    "XD" to "😂", "xDD" to "😂",
    ":-(" to "😢", ":(" to "😢", ":'(" to "😭", ";-(" to "😢",
    ";-)" to "😉", ";)" to "😉",
    ":P" to "😛", ":-P" to "😛", ":p" to "😛",
    ":O" to "😮", ":-O" to "😮", ":o" to "😮",
    ":*" to "😘", ":-*" to "😘",
    "<3" to "❤️", "</3" to "💔",
    ":/" to "😕", ":-/" to "😕",
    ":')" to "😂",
    ":|" to "😐",
    "(y)" to "👍", "(Y)" to "👍",
    "o/" to "👋", "\\o" to "🙌"
)

/** Zamienia emotikon tekstowy na końcu [tekst] na emoji (jeśli istnieje mapowanie). */
internal fun zamienEmotikonNaKoncu(tekst: String): String {
    if (tekst.length < 2) return tekst
    // Najdłuższe dopasowanie (np. ":-)" zamiast ":)")
    for ((emotikon, emoji) in mapaEmotikon.sortedByDescending { it.first.length }) {
        if (tekst.endsWith(emotikon)) {
            return tekst.dropLast(emotikon.length) + emoji
        }
    }
    return tekst
}

/** Zamienia WSZYSTKIE emotikony tekstowe w tekście na emoji (przy wysyłce pomocniczo). */
internal fun zamienWszystkieEmotikony(tekst: String): String {
    var wynik = tekst
    for ((emotikon, emoji) in mapaEmotikon.sortedByDescending { it.first.length }) {
        wynik = wynik.replace(emotikon, emoji)
    }
    return wynik
}

/** Zwraca numery mające zapisany roboczy szkic (do dodania w skrzynce). */
internal fun pobierzNumeryZRoboczymCzatem(context: Context): Set<String> {
    val prefs = context.getSharedPreferences("sms_drafty_robocze", Context.MODE_PRIVATE)
    return prefs.all.filterValues { (it as? String)?.isNotBlank() == true }.keys
}

/**
 * Dołącza do listy wątków te numery, które mają zapisany roboczy szkic (draft),
 * ale NIE mają jeszcze systemowego wątku SMS. Zwraca pełną listę (istniejące + robocze)
 * bez duplikatów. Roboczy wątek pokazuje podgląd draftu i jest oznaczony jako roboczy.
 */
internal fun dolaczRoboczeDoWatkow(context: Context, watki: List<SmsWatek>): List<SmsWatek> {
    val roboczeNumery = pobierzNumeryZRoboczymCzatem(context)
    if (roboczeNumery.isEmpty()) return watki
    // Mapa istniejących po adresie.
    val istniejacePoAdresie = watki.associateBy { it.address }.toMutableMap()
    val dodatkowe = ArrayList<SmsWatek>()
    roboczeNumery.forEach { numer ->
        val tresc = pobierzDraftCzatu(context, numer) ?: ""
        val istniejacy = istniejacePoAdresie[numer]
        if (istniejacy != null) {
            // Roboczy na ISTNIEJĄCYM wątku — nadpisz podgląd i daj na górę (Long.MAX).
            istniejacePoAdresie[numer] = istniejacy.copy(
                lastMessage = "Szkic: $tresc",
                lastDate = Long.MAX_VALUE
            )
        } else {
            // Roboczy dla NOWEGO numeru — dodaj wątek.
            dodatkowe.add(
                SmsWatek(
                    address = numer,
                    contactName = resolveContactName(context, numer),
                    lastMessage = "Szkic: $tresc",
                    lastDate = Long.MAX_VALUE,
                    messages = emptyList()
                )
            )
        }
    }
    return (istniejacePoAdresie.values + dodatkowe).sortedByDescending { it.lastDate }
}

// ===== MMS / ZAŁĄCZNIKI (zdjęcia) =====

/**
 * Adres rozmówcy MMS.
 * Inbox (msg_box=1): FROM = 137; Sent/Outbox: TO = 151.
 * Niektóre OEM-y zapisują typy odwrotnie / tylko jeden wpis — dlatego fallback
 * po „drugim” typie, a na końcu pierwszy sensowny adres (bez insert-address-token).
 */
internal fun pobierzAdresMms(context: Context, msgId: Long, msgBox: Int): String? {
    try {
        val preferFrom = msgBox == Telephony.Mms.MESSAGE_BOX_INBOX ||
            msgBox == Telephony.Mms.MESSAGE_BOX_ALL
        val primaryType = if (preferFrom) 137 else 151   // FROM / TO (PduHeaders)
        val secondaryType = if (preferFrom) 151 else 137

        var primary: String? = null
        var secondary: String? = null
        var any: String? = null

        context.contentResolver.query(
            Uri.withAppendedPath(Telephony.Mms.CONTENT_URI, "$msgId/addr"),
            arrayOf(Telephony.Mms.Addr.ADDRESS, Telephony.Mms.Addr.TYPE),
            null, null, null
        )?.use { c ->
            val addrIdx = c.getColumnIndex(Telephony.Mms.Addr.ADDRESS)
            val typeIdx = c.getColumnIndex(Telephony.Mms.Addr.TYPE)
            while (c.moveToNext()) {
                val addr = if (addrIdx >= 0) c.getString(addrIdx) else null
                if (addr.isNullOrBlank() || addr == "insert-address-token") continue
                val type = if (typeIdx >= 0) c.getInt(typeIdx) else 0
                when (type) {
                    primaryType -> if (primary == null) primary = addr
                    secondaryType -> if (secondary == null) secondary = addr
                    else -> if (any == null) any = addr
                }
                if (any == null) any = addr
            }
        }
        return primary ?: secondary ?: any
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null
}

internal fun pobierzTekstMms(context: Context, msgId: Long): String {
    try {
        context.contentResolver.query(
            android.provider.Telephony.Mms.Part.CONTENT_URI,
            arrayOf("_id", android.provider.Telephony.Mms.Part.MSG_ID, android.provider.Telephony.Mms.Part.CONTENT_TYPE, android.provider.Telephony.Mms.Part.TEXT),
            "${android.provider.Telephony.Mms.Part.MSG_ID} = ? AND ${android.provider.Telephony.Mms.Part.CONTENT_TYPE} = ?",
            arrayOf(msgId.toString(), "text/plain"),
            null
        )?.use { parts ->
            val textIdx = parts.getColumnIndex(android.provider.Telephony.Mms.Part.TEXT)
            if (parts.moveToNext() && textIdx != -1) {
                return parts.getString(textIdx) ?: ""
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return ""
}

/** Globalny cache bitmap MMS w pamięci — żeby nie dekodować zdjęć z dysku ponownie. */
object MmsBitmapCache {
    private val mapa = java.util.Collections.synchronizedMap(mutableMapOf<Long, android.graphics.Bitmap>())
    fun get(id: Long): android.graphics.Bitmap? = mapa[id]
    fun put(id: Long, bmp: android.graphics.Bitmap) { mapa[id] = bmp }
}

/** Pomocnicza funkcja bezpiecznego dekodowania bitmapy z zachowaniem proporcji i bez ryzyka OOM (OutOfMemoryError). */
internal fun dekodujBitmapuZUri(context: Context, uri: android.net.Uri, maxDim: Int = 1024): android.graphics.Bitmap? {
    return try {
        val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { 
            android.graphics.BitmapFactory.decodeStream(it, null, options) 
        }
        var inSampleSize = 1
        val height = options.outHeight
        val width = options.outWidth
        if (height > maxDim || width > maxDim) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= maxDim && (halfWidth / inSampleSize) >= maxDim) {
                inSampleSize *= 2
            }
        }
        val decodeOptions = android.graphics.BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
        }
        context.contentResolver.openInputStream(uri)?.use { 
            android.graphics.BitmapFactory.decodeStream(it, null, decodeOptions) 
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

internal fun pobierzCzescObrazu(context: Context, msgId: Long): android.graphics.Bitmap? {
    MmsBitmapCache.get(msgId)?.let { return it }
    return try {
        var znaleziony: android.graphics.Bitmap? = null
        context.contentResolver.query(
            android.provider.Telephony.Mms.Part.CONTENT_URI,
            arrayOf("_id", android.provider.Telephony.Mms.Part.MSG_ID, android.provider.Telephony.Mms.Part.CONTENT_TYPE),
            "${android.provider.Telephony.Mms.Part.MSG_ID} = ?",
            arrayOf(msgId.toString()),
            null
        )?.use { parts ->
            val idIdx = parts.getColumnIndex("_id")
            val typeIdx = parts.getColumnIndex(android.provider.Telephony.Mms.Part.CONTENT_TYPE)
            while (parts.moveToNext()) {
                val partId = if (idIdx != -1) parts.getString(idIdx) ?: "" else ""
                val typ = if (typeIdx != -1) parts.getString(typeIdx) ?: "" else ""
                val jestObraz = typ.contains("image") || typ.contains("jpeg") || typ.contains("jpg") || typ.contains("png") || typ.contains("bmp") || typ.contains("gif")
                if (jestObraz && partId.isNotBlank()) {
                    val uri = android.content.ContentUris.withAppendedId(android.provider.Telephony.Mms.Part.CONTENT_URI, partId.toLongOrNull() ?: 0L)
                    val bmp = dekodujBitmapuZUri(context, uri, maxDim = 1024)
                    if (bmp != null) { 
                        znaleziony = bmp 
                        MmsBitmapCache.put(msgId, bmp)
                        break 
                    }
                }
            }
        }
        znaleziony
    } catch (e: Exception) {
        e.printStackTrace(); null
    }
}

/**
 * Kopiuje obraz źródłowy do prywatnego cache i zwraca jego Uri (FileProvider),
 * gotowy do wysyłki MMS przez SmsManager. Zwraca null przy błędzie.
 */
internal fun przygotujZdjecieDoMms(context: Context, zrodloUri: android.net.Uri): android.net.Uri? {
    return try {
        val dir = java.io.File(context.cacheDir, "images").apply { mkdirs() }
        val plik = java.io.File(dir, "mms_${System.nanoTime()}_${(1000..9999).random()}.jpg")
        context.contentResolver.openInputStream(zrodloUri)?.use { wejscie ->
            plik.outputStream().use { wyjscie -> wejscie.copyTo(wyjscie) }
        }
        androidx.core.content.FileProvider.getUriForFile(
            context,
            context.packageName + ".fileprovider",
            plik
        )
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

/**
 * Wynik optymistycznej wysyłki MMS.
 */
data class WynikWysylkiMms(
    val lokalnyKlucz: Long
)

/**
 * Max bok przy starcie kompresji — potem i tak dopasowujemy do limitu operatora.
 * (Błąd MMS 5 = IO_ERROR często = za duży PDU względem limitu operatora.)
 */
private const val MMS_START_MAX_BOK = 2048
/** Zapas na SMIL + nagłówki PDU przy limicie operatora. */
private const val MMS_PDU_OVERHEAD = 40_000
private const val MMS_DOMYSLNY_LIMIT = 300_000

/**
 * Wysyła MMS ze zdjęciem (wzorzec Fossify MessagingUtils + mmslib):
 * kompresja do limitu [SmsManager.MMS_CONFIG_MAX_MESSAGE_SIZE] operatora.
 */
internal fun wyslijMms(
    context: Context,
    numer: String,
    contentUri: android.net.Uri,
    tekst: String = "",
    bitmap: Bitmap? = null,
    lokalnyKlucz: Long = System.nanoTime()
): WynikWysylkiMms? {
    if (numer.isBlank()) {
        Toast.makeText(context, "Brak numeru odbiorcy", Toast.LENGTH_SHORT).show()
        return null
    }
    return try {
        val limitPdu = pobierzLimitMmsBajtow(context)
        val limitObrazu = (limitPdu - MMS_PDU_OVERHEAD).coerceAtLeast(80_000)

        val bajty = przygotujBajtyObrazuMms(context, contentUri, limitObrazu)
            ?: bitmap?.let { bitmapDoJpegMms(it, limitObrazu) }
            ?: run {
                Toast.makeText(context, "Nie można odczytać zdjęcia", Toast.LENGTH_SHORT).show()
                return null
            }

        val settings = com.klinker.android.send_message.Settings().apply {
            setUseSystemSending(true)
            setDeliveryReports(true)
        }
        val transaction = com.klinker.android.send_message.Transaction(context, settings)
        // Krótki tekst (nawet spacja) pomaga niektórym stackom MMS zbudować body.
        val message = com.klinker.android.send_message.Message(tekst.ifBlank { " " }, numer)
        message.addMedia(bajty, "image/jpeg", "image.jpg", "image.jpg")

        val sentIntent = Intent(context, dialer.dominik.pl.sms.MmsStatusSentReceiver::class.java).apply {
            action = dialer.dominik.pl.sms.MmsStatusSentReceiver.ACTION
            putExtra(dialer.dominik.pl.sms.MmsStatusSentReceiver.EXTRA_LOKALNY_KLUCZ, lokalnyKlucz)
        }
        transaction.setExplicitBroadcastForSentMms(sentIntent)
        transaction.sendNewMessage(message)

        WynikWysylkiMms(lokalnyKlucz = lokalnyKlucz)
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Błąd wysyłania MMS", Toast.LENGTH_SHORT).show()
        dialer.dominik.pl.sms.StatusWiadomosciBus.emituj(
            dialer.dominik.pl.sms.AktualizacjaStatusuWiadomosci(
                messageUri = null,
                messageId = null,
                lokalnyKlucz = lokalnyKlucz,
                stan = dialer.dominik.pl.sms.StanWysylki.BLAD
            )
        )
        null
    }
}

/** Limit PDU z konfiguracji operatora (Fossify / SO: błąd 5 przy przekroczeniu). */
private fun pobierzLimitMmsBajtow(context: Context): Int {
    return try {
        val sms = dialer.dominik.pl.sms.smsManagerDlaDomyslnejSubskrypcji(context)
        val cfg = sms.carrierConfigValues
        val zConfig = cfg?.getInt(SmsManager.MMS_CONFIG_MAX_MESSAGE_SIZE) ?: 0
        when {
            zConfig in 50_000..2_000_000 -> zConfig
            else -> MMS_DOMYSLNY_LIMIT
        }
    } catch (_: Exception) {
        MMS_DOMYSLNY_LIMIT
    }
}

/**
 * JPEG pod limit operatora: najpierw skala, potem jakość, na końcu dalsze zmniejszanie boku.
 */
private fun przygotujBajtyObrazuMms(context: Context, uri: Uri, limitBajtow: Int): ByteArray? {
    return try {
        val surowe = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        if (surowe.isEmpty()) return null

        val mime = context.contentResolver.getType(uri)?.lowercase().orEmpty()
        val toJpeg = mime.contains("jpeg") || mime.contains("jpg") ||
            (surowe.size >= 3 && surowe[0] == 0xFF.toByte() && surowe[1] == 0xD8.toByte())
        if (toJpeg && surowe.size <= limitBajtow) return surowe

        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(surowe, 0, surowe.size, opts)
        val bok = maxOf(opts.outWidth, opts.outHeight).coerceAtLeast(1)
        var sample = 1
        while (bok / sample > MMS_START_MAX_BOK * 2) sample *= 2

        var decoded = BitmapFactory.decodeByteArray(
            surowe, 0, surowe.size,
            BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: return null

        var maxBok = MMS_START_MAX_BOK
        var wynik: ByteArray? = null
        repeat(6) {
            val najdluzszy = maxOf(decoded.width, decoded.height)
            val skala = if (najdluzszy > maxBok) maxBok.toFloat() / najdluzszy else 1f
            val robocza = if (skala < 1f) {
                Bitmap.createScaledBitmap(
                    decoded,
                    (decoded.width * skala).toInt().coerceAtLeast(1),
                    (decoded.height * skala).toInt().coerceAtLeast(1),
                    true
                )
            } else decoded

            wynik = bitmapDoJpegMms(robocza, limitBajtow)
            if (robocza !== decoded && !robocza.isRecycled) robocza.recycle()
            if (wynik != null && wynik!!.size <= limitBajtow) return@repeat
            maxBok = (maxBok * 0.75f).toInt().coerceAtLeast(640)
        }
        if (!decoded.isRecycled) decoded.recycle()
        wynik
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

private fun bitmapDoJpegMms(bitmap: Bitmap, limitBajtow: Int): ByteArray? {
    var jakosc = 90
    var bajty: ByteArray = ByteArray(0)
    while (jakosc >= 55) {
        val baos = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, jakosc, baos)
        bajty = baos.toByteArray()
        if (bajty.size <= limitBajtow) return bajty
        jakosc -= 7
    }
    return bajty.takeIf { it.isNotEmpty() }
}
