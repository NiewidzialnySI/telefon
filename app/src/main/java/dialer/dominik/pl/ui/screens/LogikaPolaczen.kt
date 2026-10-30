package dialer.dominik.pl.ui.screens
import androidx.compose.runtime.Immutable

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.CallLog
import android.provider.ContactsContract
import android.telecom.TelecomManager
import dialer.dominik.pl.call.EkranRozmowy
import dialer.dominik.pl.call.OczekiwanieNaPolaczenie
import dialer.dominik.pl.call.StanPolaczenia
import dialer.dominik.pl.call.odrzucPrzychodzace
import android.telecom.Call
/** Sugestia kontaktu dla dialera: nazwa, numer(y), zdjęcie. */
@Immutable
data class ContactSuggestion(
    val name: String,
    val number: String? = null,
    val fotoUri: String? = null,
    val numery: List<String> = emptyList()
)

/** Wpis z dziennika połączeń. */
@Immutable
data class CallLogEntry(
    val id: Long,
    val number: String,
    val name: String?,
    val date: Long,
    val type: Int,
    val duration: Long
)

/** Pobiera do 500 ostatnich połączeń z dziennika, od najnowszych. */
internal fun pobierzOstatniePolaczenia(context: Context): List<CallLogEntry> {
    val lista = mutableListOf<CallLogEntry>()
    try {
        val cursor = context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            null,
            null,
            null,
            "${CallLog.Calls.DATE} DESC"
        )
        cursor?.use {
            val idIdx = it.getColumnIndex(CallLog.Calls._ID)
            val numIdx = it.getColumnIndex(CallLog.Calls.NUMBER)
            val nameIdx = it.getColumnIndex(CallLog.Calls.CACHED_NAME)
            val dateIdx = it.getColumnIndex(CallLog.Calls.DATE)
            val typeIdx = it.getColumnIndex(CallLog.Calls.TYPE)
            val durIdx = it.getColumnIndex(CallLog.Calls.DURATION)

            var pobrano = 0
            while (it.moveToNext() && pobrano < 500) {
                val id = if (idIdx != -1) it.getLong(idIdx) else 0L
                val number = if (numIdx != -1) it.getString(numIdx) ?: "" else ""
                val name = if (nameIdx != -1) it.getString(nameIdx) else null
                val date = if (dateIdx != -1) it.getLong(dateIdx) else 0L
                val type = if (typeIdx != -1) it.getInt(typeIdx) else CallLog.Calls.OUTGOING_TYPE
                val dur = if (durIdx != -1) it.getLong(durIdx) else 0L

                lista.add(CallLogEntry(id, number, name, date, type, dur))
                pobrano++
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return lista
}

/**
 * Zwraca ID najnowszego połączenia z CallLog — jedno tanie zapytanie,
 * służy do detekcji zmiany historii bez pobierania całej listy.
 * Zwraca -1, gdy brak połączeń lub brak dostępu.
 */
internal fun pobierzNajnowszyIdPolaczenia(context: Context): Long {
    return try {
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls._ID),
            null,
            null,
            "${CallLog.Calls.DATE} DESC LIMIT 1"
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(CallLog.Calls._ID)
                if (idx != -1) cursor.getLong(idx) else -1L
            } else {
                -1L
            }
        } ?: -1L
    } catch (e: Exception) {
        // Brak uprawnienia READ_CALL_LOG lub inny błąd — traktujemy jak "brak danych"
        -1L
    }
}

/** Wyszukuje kontakty po nazwie i numerze (max 10 wyników). */
internal fun wyszukajKontakty(context: Context, query: String): List<ContactSuggestion> {
    val q = normalizujKontakt(query)
    if (q.isBlank()) return emptyList()

    val kontakty = pobierzKontakty(context)
    val numeryPerKontakt = mutableMapOf<Long, MutableList<String>>()
    val dane = mutableMapOf<Long, Pair<String, String?>>() // nazwa, fotoUri

    for (kontakt in kontakty) {
        val pasuje = kontakt.nazwaWyswietlana.lowercase().contains(query.lowercase()) ||
                        kontakt.pseudonim?.lowercase()?.contains(query.lowercase()) == true ||
                        kontakt.numery.any { it.normalizowany.contains(q) }
                if (!pasuje) continue

                dane.getOrPut(kontakt.contactId) { kontakt.nazwaWyswietlana to kontakt.fotoUri }
                val lista = numeryPerKontakt.getOrPut(kontakt.contactId) { mutableListOf() }
                kontakt.numery.map { it.normalizowany }.filter { it !in lista }.forEach { lista.add(it) }
            }

            return dane.entries
                .sortedWith(
                    compareBy(
                        { -(numeryPerKontakt[it.key]?.count { n -> n.contains(q) } ?: 0) },
                        { it.value.first.lowercase() }
                    )
                )
        .take(10)
        .map { (id, para) ->
                    val numery = numeryPerKontakt[id] ?: emptyList()
                    ContactSuggestion(
                        name = para.first,
                        number = numery.firstOrNull(),
                        fotoUri = para.second,
                        numery = numery
                    )
                }
}

/** Czy aplikacja jest domyślnym dialerem? */
internal fun czyJestDomyslnymDialerem(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager = context.getSystemService(RoleManager::class.java)
        if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
            return roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
        }
    }
    val telecomManager = context.getSystemService(TelecomManager::class.java)
    return telecomManager?.defaultDialerPackage == context.packageName
}

/** Otwiera ustawienia, żeby ustawić aplikację jako domyślny dialer. */
internal fun ustawJakoDomyslnyDialer(context: Context) {
    try {
        val intencja = Intent("android.settings.MANAGE_DEFAULT_APPS_SETTINGS").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intencja)
        return
    } catch (e: Exception) {
        e.printStackTrace()
    }

    try {
        val intencja = Intent(
            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(intencja)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

/** Wykonuje połączenie przez TelecomManager lub intencję systemową. */
internal fun wykonajPolaczenie(context: Context, numer: String) {
    if (numer.isBlank()) return

    StanPolaczenia.rozmowca = numer
        StanPolaczenia.numerRozmowy = numer
        StanPolaczenia.czyPrzychodzace = false
    StanPolaczenia.stan = StanPolaczenia.Stan.Dzwoni
    StanPolaczenia.czasStartu = 0L
    // Reset stanu UI z poprzedniej rozmowy, żeby nowe połączenie nie dziedziczyło
    // np. otwartej klawiatury DTMF ani włączonego głośnika/wyciszenia.
    StanPolaczenia.klawiatura = false
    StanPolaczenia.buforDtmf = ""
    StanPolaczenia.wyciszony = false
    StanPolaczenia.glosnik = false
    StanPolaczenia.bluetoothAktywny = false
    StanPolaczenia.zadanaTrasa = null
    EkranRozmowy.pokaz(context)

    // Odraczamy faktyczne nawiązanie, żeby użytkownik mógł w tym czasie
    // anulować połączenie z ekranu rozmowy (zanim zacznie dzwonić).
    OczekiwanieNaPolaczenie.rozpocznij(context, numer)
}

/**
 * Faktycznie wybiera numer (po odczekaniu opóźnienia). Wydzielone z
 * [wykonajPolaczenie], żeby dało się je anulować przez [OczekiwanieNaPolaczenie].
 */
internal fun wykonajFaktycznePolaczenie(context: Context, numer: String) {
    if (numer.isBlank()) return

    val maCALL = maUprawnienie(context, Manifest.permission.CALL_PHONE)
    val jestDomyslny = czyJestDomyslnymDialerem(context)

    if (jestDomyslny && maCALL) {
        try {
            val telecom = context.getSystemService(TelecomManager::class.java)
            val uri = Uri.fromParts("tel", numer, null)
            if (telecom != null) {
                telecom.placeCall(uri, dialer.dominik.pl.call.WyborSim.extrasDoPlaceCall(context))
                return
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    if (!jestDomyslny && maCALL) {
        try {
            val intencja = Intent(Intent.ACTION_CALL, Uri.fromParts("tel", numer, null))
            intencja.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intencja)
            return
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    try {
            val intencja = Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", numer, null))
            intencja.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intencja)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Niezawodne rozłączenie rozmowy.
     *
     * Najpierw anuluje oczekiwanie na nawiązanie (jeśli jeszcze nie zaczęło
     * dzwonić) — dzięki temu drugie kliknięcie „Rozłącz" zanim odbiorca
     * dostanie dzwonek = zero połączeń, zero dzwonienia u niego.
     * Potem rozłącza aktywny Call przez disconnect() z fallbackiem do systemowego
     * endCall() (który działa nawet gdy instancja Call jest przestarzała).
     *
     * @param usunLog true → usuń najnowszy wpis z logu połączeń (całkowite
     *        anulowanie, bez śladu w historii).
     */
    internal fun rozlaczRozmowe(context: Context, usunLog: Boolean) {
        // 1) Jeśli czekamy jeszcze na faktyczne wybranie — po prostu odwołaj.
        OczekiwanieNaPolaczenie.anuluj()

        // 2) Rozłącz aktywny Call (jeśli jest).
        // RINGING → reject(DECLINED)=REJECTED; disconnect() na dzwoniącym = busy tone.
        val aktywne = StanPolaczenia.polaczenie
        var rozlaczono = false
        if (aktywne != null) {
            try {
                val stanCall = aktywne.state
                val dzwoni = stanCall == Call.STATE_RINGING ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                        stanCall == Call.STATE_SIMULATED_RINGING)
                if (dzwoni) {
                    odrzucPrzychodzace(aktywne)
                } else {
                    @Suppress("DEPRECATION")
                    aktywne.disconnect()
                }
                rozlaczono = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 3) Fallback: systemowe zakończenie (niezależne od instancji Call).
        if (!rozlaczono) {
            try {
                val telecom = context.getSystemService(TelecomManager::class.java)
                telecom?.endCall()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 4) Usuń wpis z logu połączeń (gdy prosimy o pełne anulowanie).
        if (usunLog && StanPolaczenia.numerRozmowy.isNotBlank()) {
            usunNajnowszyWpisZLogu(context, StanPolaczenia.numerRozmowy)
        }

        // 5) Zresetuj stan i zamknij ekran.
        StanPolaczenia.polaczenie = null
        StanPolaczenia.stan = StanPolaczenia.Stan.Brak
        StanPolaczenia.czasStartu = 0L
        EkranRozmowy.zamknij(context)
    }

    /** Usuwa najnowszy wpis z CallLog dla podanego numeru (jeśli istnieje). */
    private fun usunNajnowszyWpisZLogu(context: Context, numer: String) {
        try {
            val cyfry = numer.filter { it.isDigit() }
            if (cyfry.isBlank()) return
            context.contentResolver.delete(
                CallLog.Calls.CONTENT_URI,
                CallLog.Calls.NUMBER + " LIKE ?",
                arrayOf("%$cyfry%")
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

/** Otwiera systemowy ekran dodania kontaktu z gotowym numerem. */
internal fun utworzKontaktIntent(context: Context, numer: String) {
    try {
        val intencja = Intent(Intent.ACTION_INSERT).apply {
            type = ContactsContract.Contacts.CONTENT_TYPE
            putExtra(ContactsContract.Intents.Insert.PHONE, numer)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intencja)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

/** Formatuje czas trwania połączenia jako mm:ss lub h:mm:ss. */
internal fun formatujCzasTrwania(sekundy: Long): String {
    if (sekundy <= 0) return ""
    val h = sekundy / 3600
    val m = (sekundy % 3600) / 60
    val s = sekundy % 60
    return if (h > 0) {
        "%d:%02d:%02d".format(h, m, s)
    } else {
        "%d:%02d".format(m, s)
    }
}