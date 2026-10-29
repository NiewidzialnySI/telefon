package dialer.dominik.pl.ui.screens
import androidx.compose.runtime.Immutable

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.SmsManager
import android.widget.Toast
import androidx.core.content.ContextCompat
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
    val mmsMsgId: Long? = null
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

internal fun uniewaznijCacheSkrzynki() {
    SkrzynkaCache.wpisy = null
    WatekCache.watki = null
    KontaktyCache.invalidate()
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

            while (cursor.moveToNext()) {
                var date = if (dateIdx != -1) cursor.getLong(dateIdx) else 0L
                if (date < 10000000000L) date *= 1000L
                val snippet = if (snippetIdx != -1) cursor.getString(snippetIdx) ?: "" else ""
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
                            pseudonim = nazwa?.let { pobierzPseudonim(context, it) },
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
        SmsWatek(
            address = address,
            contactName = nazwa,
            lastMessage = ostatnia?.body ?: "",
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
        context.contentResolver.update(
            Telephony.Sms.CONTENT_URI,
            values,
            "${Telephony.Sms.ADDRESS} = ? AND ${Telephony.Sms.READ} = 0",
            arrayOf(address)
        )
    } catch (e: Exception) {
        e.printStackTrace()
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

private const val SENT_SMS_ACTION = "dialer.dominik.pl.SENT_SMS"
private const val DELIVERY_SMS_ACTION = "dialer.dominik.pl.DELIVERY_SMS"

/** Wysyła SMS (lub otwiera komunikator) z potwierdzeniem statusu wysłania. */
internal fun wyslijWiadomosc(
    context: Context,
    numer: String,
    tresc: String,
    kanal: KanalWiadomosci
) {
    if (kanal != KanalWiadomosci.Sms) {
        wyslijPrzezKomunikator(context, kanal, numer, tresc)
        return
    }

    val maSendSms = context.checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
    if (!maSendSms) {
        // Fallback: otwarcie systemowej apki SMS z gotową treścią
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$numer")).apply {
            putExtra("sms_body", tresc)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Błąd wysyłania", Toast.LENGTH_SHORT).show()
        }
        return
    }

    try {
        val smsManager = context.getSystemService(SmsManager::class.java)
            ?: run { Toast.makeText(context, "Błąd wysyłania", Toast.LENGTH_SHORT).show(); return }

        // Dzielimy długą wiadomość na części (np. 160 znaków) — bez tego
        // SmsManager ucina treść. Każdą część wysyłamy z potwierdzeniem.
        val czesci = smsManager.divideMessage(tresc)
        if (czesci.isEmpty()) return

        val sentIntenty = ArrayList<PendingIntent>()
        val deliveryIntenty = ArrayList<PendingIntent>()

        var odliczanie = System.currentTimeMillis().toInt()
        for (i in czesci.indices) {
            val id = odliczanie + i
            sentIntenty.add(PendingIntent.getBroadcast(
                context, id, Intent(SENT_SMS_ACTION).apply { setPackage(context.packageName) },
                PendingIntent.FLAG_IMMUTABLE
            ))
            deliveryIntenty.add(PendingIntent.getBroadcast(
                context, id + 1_000_000, Intent(DELIVERY_SMS_ACTION).apply { setPackage(context.packageName) },
                PendingIntent.FLAG_IMMUTABLE
            ))
        }

        // Rejestrujemy chwilowy odbiornik statusu wysłania — po odebraniu
        // sam się wyrejestrowuje. (registerReceiver zwraca Intent, nie receiver,
        // więc nie przechowujemy referencji do celowego wyrejestrowania.)
        val filterOdpowiedz = IntentFilter().apply { addAction(SENT_SMS_ACTION) }
        context.registerReceiver(
            object : BroadcastReceiver() {
                override fun onReceive(rcvContext: Context, rcvIntent: Intent) {
                    val wynik = when (resultCode) {
                        android.app.Activity.RESULT_OK -> "SMS wysłany"
                        SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "Błąd wysyłania SMS"
                        SmsManager.RESULT_ERROR_NO_SERVICE -> "Brak zasięgu"
                        SmsManager.RESULT_ERROR_NULL_PDU -> "Błąd SMS (null PDU)"
                        SmsManager.RESULT_ERROR_RADIO_OFF -> "Radio wyłączone"
                        else -> "Błąd SMS"
                    }
                    Toast.makeText(context, wynik, Toast.LENGTH_SHORT).show()
                    rcvContext.unregisterReceiver(this)
                }
            },
            filterOdpowiedz,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        // Zapis do systemowej skrzynki "wysłane" — żeby wiadomość widniała
        // w historii. Status dostarczenia aktualizuje się przez deliveryIntent.
        try {
            val values = ContentValues().apply {
                put(Telephony.Sms.Sent.ADDRESS, numer)
                put(Telephony.Sms.Sent.BODY, tresc)
                put(Telephony.Sms.Sent.DATE, System.currentTimeMillis())
                put(Telephony.Sms.Sent.READ, 1)
            }
            context.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Wysyłka: sendMultipartTextMessage obsłuży też pojedyncze części
        try {
            smsManager.sendMultipartTextMessage(
                numer, null, czesci, sentIntenty, deliveryIntenty
            )
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Błąd wysyłania", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Błąd wysyłania", Toast.LENGTH_SHORT).show()
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
    
    // 1. Pobierz SMS-y (limit do 300 najnowszych dla natychmiastowego startu)
    try {
        val cursor = context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE,
                Telephony.Sms.READ
            ),
            null,
            null,
            "${Telephony.Sms.DATE} DESC LIMIT 300"
        )
        cursor?.use {
            val idIdx = cursor.getColumnIndex(Telephony.Sms._ID)
            val addrIdx = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyIdx = cursor.getColumnIndex(Telephony.Sms.BODY)
            val dateIdx = cursor.getColumnIndex(Telephony.Sms.DATE)
            val typeIdx = cursor.getColumnIndex(Telephony.Sms.TYPE)
            val readIdx = cursor.getColumnIndex(Telephony.Sms.READ)

            while (cursor.moveToNext()) {
                val id = if (idIdx != -1) cursor.getLong(idIdx) else 0L
                val address = if (addrIdx != -1) cursor.getString(addrIdx) ?: "" else ""
                val body = if (bodyIdx != -1) cursor.getString(bodyIdx) ?: "" else ""
                val date = if (dateIdx != -1) cursor.getLong(dateIdx) else 0L
                val type = if (typeIdx != -1) cursor.getInt(typeIdx) else Telephony.Sms.MESSAGE_TYPE_INBOX
                val read = if (readIdx != -1) cursor.getInt(readIdx) == 1 else true

                if (address.isNotBlank()) {
                    lista.add(SmsWpis(id, address, body, date, type, read, null))
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    // 2. Pobierz MMS-y (Inbox i Sent - limit do 100 najnowszych)
    try {
        val mmsCursor = context.contentResolver.query(
            android.provider.Telephony.Mms.CONTENT_URI,
            arrayOf("_id", "date", "msg_box", "read"),
            null,
            null,
            "date DESC LIMIT 100"
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
                val bitmap = pobierzCzescObrazu(context, id)

                val finalBody = if (textBody.isNotBlank()) textBody else if (bitmap != null) "📷 [MMS]" else "MMS"

                lista.add(
                    SmsWpis(
                        id = 1_000_000_000L + id,
                        address = address,
                        body = finalBody,
                        date = date,
                        type = type,
                        read = read,
                        bitmap = bitmap
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

internal fun pobierzWiadomosciDlaAdresu(context: Context, adres: String): List<SmsWpis> {
    val lista = mutableListOf<SmsWpis>()
    val normTarget = normalizujAdresSms(adres)
    try {
        val cursor = context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE,
                Telephony.Sms.READ
            ),
            null,
            null,
            "${Telephony.Sms.DATE} DESC"
        )
        cursor?.use {
            val idIdx = cursor.getColumnIndex(Telephony.Sms._ID)
            val addrIdx = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyIdx = cursor.getColumnIndex(Telephony.Sms.BODY)
            val dateIdx = cursor.getColumnIndex(Telephony.Sms.DATE)
            val typeIdx = cursor.getColumnIndex(Telephony.Sms.TYPE)
            val readIdx = cursor.getColumnIndex(Telephony.Sms.READ)

            while (cursor.moveToNext()) {
                val address = if (addrIdx != -1) cursor.getString(addrIdx) ?: "" else ""
                if (normalizujAdresSms(address) == normTarget) {
                    val id = if (idIdx != -1) cursor.getLong(idIdx) else 0L
                    val body = if (bodyIdx != -1) cursor.getString(bodyIdx) ?: "" else ""
                    val date = if (dateIdx != -1) cursor.getLong(dateIdx) else 0L
                    val type = if (typeIdx != -1) cursor.getInt(typeIdx) else Telephony.Sms.MESSAGE_TYPE_INBOX
                    val read = if (readIdx != -1) cursor.getInt(readIdx) == 1 else true

                    lista.add(SmsWpis(id, address, body, date, type, read, null))
                }
            }
        }
        val mmsyDlaAdresu = pobierzMmsZdjeciaDlaAdresu(context, adres)
        lista.addAll(mmsyDlaAdresu)
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return lista.sortedByDescending { it.date }
}

internal fun pobierzMmsZdjeciaDlaAdresu(context: Context, adres: String): List<SmsWpis> {
    val wynik = mutableListOf<SmsWpis>()
    val normTarget = normalizujAdresSms(adres)
    try {
        val mmsCursor = context.contentResolver.query(
            android.provider.Telephony.Mms.CONTENT_URI,
            arrayOf("_id", "date", "msg_box", "read"),
            null, null, "date DESC"
        )
        mmsCursor?.use { mms ->
            val idIdx = mms.getColumnIndex("_id")
            val dateIdx = mms.getColumnIndex("date")
            val boxIdx = mms.getColumnIndex("msg_box")
            val readIdx = mms.getColumnIndex("read")
            while (mms.moveToNext()) {
                val id = if (idIdx != -1) mms.getLong(idIdx) else 0L
                var date = if (dateIdx != -1) mms.getLong(dateIdx) else 0L
                if (date < 10000000000L) date *= 1000L
                val box = if (boxIdx != -1) mms.getInt(boxIdx) else 1
                val read = if (readIdx != -1) mms.getInt(readIdx) == 1 else true
                val type = if (box == 2 || box == 4) Telephony.Sms.MESSAGE_TYPE_SENT else Telephony.Sms.MESSAGE_TYPE_INBOX

                val mmsAddress = pobierzAdresMms(context, id, box) ?: continue
                if (normalizujAdresSms(mmsAddress) == normTarget) {
                    val textBody = pobierzTekstMms(context, id)
                    val finalBody = if (textBody.isNotBlank()) textBody else "📷 [MMS]"
                    wynik.add(
                        SmsWpis(
                            id = 1_000_000_000L + id,
                            address = mmsAddress,
                            body = finalBody,
                            date = date,
                            type = type,
                            read = read,
                            bitmap = null, // Ładowane leniwie w UI, żeby zapobiec wyciekom pamięci (OOM)!
                            mmsMsgId = id
                        )
                    )
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
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

/** Normalizuje nazwę firmy (alfa-nadawcy SMS) do domeny — mapa znanych polskich/firm +
 *  spadek na <nazwa>.com. Google favicons pobiera ikonę strony po domenie. */
internal fun domenaFirmy(nazwa: String): String {
    val klucz = nazwa.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "")
    val mapa = mapOf(
        "microsoft" to "microsoft.com",
        "tmobile" to "t-mobile.pl",
        "play" to "play.pl",
        "orange" to "orange.pl",
        "pluss" to "plus.pl",
        "allegro" to "allegro.pl",
        "pekao" to "pekao.pl",
        "pkobp" to "pkobp.pl",
        "ing" to "ing.pl",
        "bzwbk" to "bzwbk.pl",
        "mbank" to "mbank.pl",
        "olx" to "olx.pl",
        "facebook" to "facebook.com",
        "google" to "google.com",
        "netflix" to "netflix.com",
        "spotify" to "spotify.com",
        "amazon" to "amazon.com",
        "apple" to "apple.com",
        "marketul" to "unilever.com",
        "unilever" to "unilever.com",
        "blik" to "blik.com"
    )
    return mapa[klucz] ?: (if (klucz.isNotBlank()) klucz + ".com" else "placeholder.com")
}

/**
 * Pobiera logo firmy (alfa-nadawcy) z internetu raz i cache'uje lokalnie
 * w filesDir/loga_firm/<hash>.png. Gdy brak sieci / nie znaleziono → null
 * (wtedy UI pokaże inicjał/lub logo aplikacji, a pobieranie wróci przy
 * następnej recompozycji wątku, aż sieć będzie dostępna).
 */
internal fun pobierzLogoFirmy(context: Context, nazwa: String): Bitmap? {
    if (nazwa.isBlank()) return null
    val katalog = java.io.File(context.filesDir, "loga_firm")
    if (!katalog.exists()) katalog.mkdirs()
    val plik = java.io.File(katalog, nazwa.hashCode().toString() + ".png")

    // Cache — już pobrane wcześniej.
    if (plik.exists() && plik.length() > 0) {
        return BitmapFactory.decodeFile(plik.absolutePath)
    }

    return try {
        val domain = domenaFirmy(nazwa)
        // Google favicons — pewniejsze niż Clearbit (działa dla praktycznie każdej
        // domeny, zwraca favicon strony firmowej). 128px, przezroczyste tło.
        val url = java.net.URL("https://www.google.com/s2/favicons?sz=128&domain=$domain")
        val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 4000
            readTimeout = 4000
            setRequestProperty("User-Agent", "Mozilla/5.0")
        }
        val kod = conn.responseCode
        android.util.Log.i("LogoFirmy", "nazwa=$nazwa domena=$domain kod=$kod")
        if (kod == 200) {
            val bajty = conn.inputStream.use { it.readBytes() }
            if (bajty.isNotEmpty()) {
                plik.writeBytes(bajty)
                BitmapFactory.decodeByteArray(bajty, 0, bajty.size)
            } else null
        } else null
    } catch (e: Exception) {
        android.util.Log.w("LogoFirmy", "pobranie logo nieudane dla $nazwa", e)
        null // brak sieci etc. — spróbujemy przy następnej recompozycji
    }
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

internal fun pobierzAdresMms(context: Context, msgId: Long, msgBox: Int): String? {
    try {
        val targetType = if (msgBox == 1) 137 else 151
        var fallbackAddr: String? = null

        context.contentResolver.query(
            android.net.Uri.withAppendedPath(android.provider.Telephony.Mms.CONTENT_URI, "$msgId/addr"),
            arrayOf(android.provider.Telephony.Mms.Addr.ADDRESS, android.provider.Telephony.Mms.Addr.TYPE),
            null, null, null
        )?.use { c ->
            val addrIdx = c.getColumnIndex(android.provider.Telephony.Mms.Addr.ADDRESS)
            val typeIdx = c.getColumnIndex(android.provider.Telephony.Mms.Addr.TYPE)
            while (c.moveToNext()) {
                val addr = c.getString(addrIdx) ?: continue
                val type = if (typeIdx != -1) c.getInt(typeIdx) else 0
                if (addr.isNotBlank() && addr != "insert-address-token") {
                    if (type == targetType) {
                        return addr
                    }
                    if (fallbackAddr == null) {
                        fallbackAddr = addr
                    }
                }
            }
        }
        return fallbackAddr
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
        val plik = java.io.File(dir, "mms_" + System.currentTimeMillis() + ".jpg")
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
 * Wysyła zdjęcie (załącznik) jako MMS do [numer] przez SmsManager.sendMultimediaMessage.
 * [contentUri] to FileProvider Uri (z przygotujZdjecieDoMms).
 */
internal fun wyslijMms(context: Context, numer: String, contentUri: android.net.Uri, tekst: String = "") {
    try {
        val smsManager = android.telephony.SmsManager.getDefault()
        val sentIntent = android.app.PendingIntent.getBroadcast(
            context, 0,
            android.content.Intent("dialer.dominik.pl.MMS_SENT").setPackage(context.packageName),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        if (tekst.isBlank()) {
            smsManager.sendMultimediaMessage(context, contentUri, null, null, sentIntent)
        } else {
            smsManager.sendTextMessage(numer, null, tekst, null, null)
            smsManager.sendMultimediaMessage(context, contentUri, null, null, sentIntent)
        }
        Toast.makeText(context, "MMS wysłany", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Błąd wysyłania MMS", Toast.LENGTH_SHORT).show()
    }
}
