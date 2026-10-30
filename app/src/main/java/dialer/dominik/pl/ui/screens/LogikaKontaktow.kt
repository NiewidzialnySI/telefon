package dialer.dominik.pl.ui.screens
import androidx.compose.runtime.Immutable

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.telecom.TelecomManager
import dialer.dominik.pl.call.EkranRozmowy
import dialer.dominik.pl.call.StanPolaczenia
import dialer.dominik.pl.sms.KanalWiadomosci
import dialer.dominik.pl.sms.pobierzKanal
import dialer.dominik.pl.sms.wyslijPrzezKomunikator

/** Pojedynczy numer telefonu w obrębie kontaktu. */
@Immutable
data class NumerKontaktu(
    val id: String,
    val surowy: String,
    val normalizowany: String
)

/**
 * Kontakt = jedna osoba, nawet gdy ma kilka numerów.
 * Emilka ma np. 733… do dzwonienia i 669… przypisany do Signala.
 */
@Immutable
data class KontaktItem(
    /** ID kontaktu w ContactsContract (CONTACT_ID) — do edycji/usuwania w książce. */
    val contactId: Long,
    val name: String,
    val numery: List<NumerKontaktu>,
    /** Pseudonim ustawiony w tej aplikacji (SharedPreferences), np. "Luluś". */
    val pseudonim: String? = null,
    /** URI zdjęcia (miniaturka) z książki — null gdy kontakt nie ma foto. */
    val fotoUri: String? = null,
    /** Nazwa firmy/organizacji z książki — null gdy kontakt jej nie ma (do wyszukiwania). */
    val firma: String? = null
) {
    /** Numer używany domyślnie — pierwszy z listy. */
    val number: String get() = numery.firstOrNull()?.surowy ?: ""

    /** Czy kontakt ma więcej niż jeden numer. */
    val maWieleNumerow: Boolean get() = numery.size > 1

    /** Nazwa wyświetlana — pseudonim, jeśli ustawiony, inaczej imię i nazwisko. */
    val nazwaWyswietlana: String get() = pseudonim?.takeIf { it.isNotBlank() } ?: name
}

/** Dzwoni — najpierw przez TelecomManager, gdy aplikacja jest domyślnym dialerem. */
fun zadzwonDoKontaktu(context: Context, numer: String) {
    if (numer.isBlank()) return
    StanPolaczenia.rozmowca = numer
    StanPolaczenia.czyPrzychodzace = false
    StanPolaczenia.stan = StanPolaczenia.Stan.Dzwoni
    StanPolaczenia.czasStartu = 0L
    EkranRozmowy.pokaz(context)

    try {
        val telecom = context.getSystemService(TelecomManager::class.java)
        val jestDomyslny = telecom?.defaultDialerPackage == context.packageName
        if (jestDomyslny) {
            telecom?.placeCall(Uri.fromParts("tel", numer, null), Bundle())
            return
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    try {
        val intencja = Intent(Intent.ACTION_CALL, Uri.fromParts("tel", numer, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intencja)
    } catch (e: Exception) {
        e.printStackTrace()
        try {
            val intencja = Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", numer, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intencja)
        } catch (e2: Exception) {
            e2.printStackTrace()
        }
    }
}

/**
 * Otwiera wiadomość do kontaktu w wybranym kanale.
 *
 * - SMS: nasz czat w zakładce Wiadomości (callback onOpenSms).
 * - Messenger / Signal: od razu czat w ich aplikacji.
 */
fun napiszDoKontaktu(context: Context, numer: String, onOpenSms: ((String) -> Unit)?) {
    val kanal = pobierzKanal(context, numer)

    if (kanal != KanalWiadomosci.Sms) {
        wyslijPrzezKomunikator(context, kanal, numer, "")
        return
    }

    // SMS wysyłamy sami — otwórz nasz czat w zakładce Wiadomości.
    // Gdy callback nie jest podpięty, fallback do systemowej apki SMS.
    if (onOpenSms != null) {
        onOpenSms(numer)
    } else {
        try {
            val intencja = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$numer"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intencja)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

/**
 * Wczytuje kontakty jako osoby z listą numerów.
 *
 * Grupowanie po CONTACT_ID sprawia, że Emilka z dwoma numerami jest jedną kartą.
 * Numery w obrębie kontaktu są deduplikowane („663 659 496" == „663659496"),
 * a kolejność zachowuje tę z książki telefonicznej.
 *
 * Wynik jest cache'owany w pamięci na [CACHE_KONTAKTOW_MS] milisekund, żeby
 * powrót na ekran nie czytał całej książki + miniaturek za każdym razem.
 * Cache czyścimy jawnie przez [uniewaznijCacheKontaktow] po zapisie/edycji
 * kontaktu, pseudonimu albo ulubionych.
 */
fun pobierzKontakty(context: Context): List<KontaktItem> {
    val teraz = System.currentTimeMillis()
    if (cacheKontaktow != null && teraz - cacheCzas < CACHE_KONTAKTOW_MS) {
        return cacheKontaktow!!
    }
    val wynik = pobierzKontaktyBezCache(context)
    cacheKontaktow = wynik
    cacheCzas = teraz
    return wynik
}

/** Unieważnia cache kontaktów (po zapisie/edycji, zmianie pseudonimu/ulubionych). */
fun uniewaznijCacheKontaktow() {
    cacheKontaktow = null
    cacheCzas = 0L
}

private const val CACHE_KONTAKTOW_MS = 30_000L
private var cacheKontaktow: List<KontaktItem>? = null
private var cacheCzas: Long = 0L

/** Właściwe czytanie książki — bez cache. */
private fun pobierzKontaktyBezCache(context: Context): List<KontaktItem> {
    // contactId -> (nazwa, lista numerów)
    val wedlugKontaktu = linkedMapOf<Long, Pair<String, MutableList<NumerKontaktu>>>()

    try {
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone._ID,
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        context.contentResolver.query(
            uri,
            projection,
            null,
            null,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone._ID)
            val kontaktIdIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

            while (cursor.moveToNext()) {
                val id = if (idIdx != -1) cursor.getLong(idIdx) else 0L
                val kontaktId = if (kontaktIdIdx != -1) cursor.getLong(kontaktIdIdx) else id
                val name = if (nameIdx != -1) cursor.getString(nameIdx) else ""
                val numer = if (numIdx != -1) cursor.getString(numIdx) else ""
                if (name.isBlank() || numer.isBlank()) continue

                val normalizowany = normalizujKontakt(numer)
                val wpis = wedlugKontaktu[kontaktId]
                if (wpis == null) {
                    wedlugKontaktu[kontaktId] = name to mutableListOf(
                        NumerKontaktu(id.toString(), numer, normalizowany)
                    )
                } else {
                    // Ten sam numer zapisany inaczej nie trafia drugi raz
                    if (wpis.second.none { it.normalizowany == normalizowany }) {
                        wpis.second.add(NumerKontaktu(id.toString(), numer, normalizowany))
                    }
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    // Osobne query zdjęć — mapa contactId -> fotoUri (miniaturka).
    val mapajotoUri = mutableMapOf<Long, String>()
    try {
        val uriFot = ContactsContract.Contacts.CONTENT_URI
        val projFot = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.PHOTO_THUMBNAIL_URI
        )
        context.contentResolver.query(uriFot, projFot, null, null, null)?.use { c ->
            val idIdx = c.getColumnIndex(ContactsContract.Contacts._ID)
            val fotoIdx = c.getColumnIndex(ContactsContract.Contacts.PHOTO_THUMBNAIL_URI)
            while (c.moveToNext()) {
                val cid = if (idIdx != -1) c.getLong(idIdx) else 0L
                val fut = if (fotoIdx != -1) c.getString(fotoIdx) else null
                if (cid != 0L && !fut.isNullOrBlank()) {
                    mapajotoUri[cid] = fut
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    // Osobne query firm — mapa contactId -> nazwa firmy (Organization.COMPANY).
    val mapafirm = mutableMapOf<Long, String>()
    try {
        val uriF = ContactsContract.Data.CONTENT_URI
        val projF = arrayOf(
            ContactsContract.Data.CONTACT_ID,
            ContactsContract.CommonDataKinds.Organization.COMPANY
        )
        val selekcjaF = "${ContactsContract.Data.MIMETYPE} = ?"
        context.contentResolver.query(
            uriF,
            projF,
            selekcjaF,
            arrayOf(ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE),
            null
        )?.use { c ->
            val idIdx = c.getColumnIndex(ContactsContract.Data.CONTACT_ID)
            val firmaIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Organization.COMPANY)
            while (c.moveToNext()) {
                val cid = if (idIdx != -1) c.getLong(idIdx) else 0L
                val fir = if (firmaIdx != -1) c.getString(firmaIdx) else null
                if (cid != 0L && !fir.isNullOrBlank()) {
                    mapafirm[cid] = fir
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    // Jeden odczyt prefs zamiast N× getSharedPreferences w pętli.
    val mapaPseudo = context.getSharedPreferences(PREFS_PSEUDONIMY, Context.MODE_PRIVATE)
        .all
        .mapNotNull { (k, v) ->
            val p = (v as? String)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            k to p
        }
        .toMap()

    return wedlugKontaktu
        .map { (kontaktId, dane) ->
            val (nazwa, numery) = dane
            KontaktItem(
                kontaktId,
                nazwa,
                numery,
                pseudonim = mapaPseudo[nazwa.trim()],
                fotoUri = mapajotoUri[kontaktId],
                firma = mapafirm[kontaktId]
            )
        }
        .sortedBy { it.nazwaWyswietlana.lowercase() }
}

// ------------------- Pseudonimy (SharedPreferences) -------------------

private const val PREFS_PSEUDONIMY = "kontakty_pseudonimy"

/** Zwraca pseudonim kontaktu ustawiony w tej aplikacji (lub null, gdy brak). */
fun pobierzPseudonim(context: Context, nazwa: String): String? {
    val prefs = context.getSharedPreferences(PREFS_PSEUDONIMY, Context.MODE_PRIVATE)
    return prefs.getString(nazwa.trim(), null)?.takeIf { it.isNotBlank() }
}

/** Zapisuje/aktualizuje pseudonim kontaktu w tej aplikacji ("" usuwa). */
fun zapiszPseudonim(context: Context, nazwa: String, pseudonim: String) {
    val prefs = context.getSharedPreferences(PREFS_PSEUDONIMY, Context.MODE_PRIVATE)
    val klucz = nazwa.trim()
    val edytor = prefs.edit()
    if (pseudonim.isBlank()) edytor.remove(klucz) else edytor.putString(klucz, pseudonim.trim())
    edytor.apply()
}

/**
 * Przenosi pseudonim pod nową nazwę po zmianie imienia/nazwiska kontaktu.
 * Niczego nie robi, gdy pusta [staraNazwa] lub [nowaNazwa] == [staraNazwa].
 */
fun przeniesPseudonim(context: Context, staraNazwa: String, nowaNazwa: String, nowyPseudonim: String) {
    val stara = staraNazwa.trim()
    val nowa = nowaNazwa.trim()
    if (stara.isBlank() || nowa == stara) return
    val prefs = context.getSharedPreferences(PREFS_PSEUDONIMY, Context.MODE_PRIVATE)
    val edytor = prefs.edit()
    // Usuń stary klucz — pseudonim przenosimy (nie kopiujemy) pod nową nazwę.
    edytor.remove(stara)
    if (nowyPseudonim.isNotBlank()) edytor.putString(nowa, nowyPseudonim.trim())
    edytor.apply()
}

/** Nazwa prefs ulubionych (SharedPreferences "kontakty_ulubione", klucz "nazwy"). */
private const val PREFS_ULUBIONE = "kontakty_ulubione"

/**
 * Przenosi kontakt w ulubionych pod nową nazwę po zmianie imienia/nazwiska.
 * Niczego nie robi, gdy pusta [staraNazwa] lub [nowaNazwa] == [staraNazwa].
 */
fun przeniesUlubione(context: Context, staraNazwa: String, nowaNazwa: String) {
    val stara = staraNazwa.trim()
    val nowa = nowaNazwa.trim()
    if (stara.isBlank() || nowa == stara) return
    val prefs = context.getSharedPreferences(PREFS_ULUBIONE, Context.MODE_PRIVATE)
    val zestaw = prefs.getStringSet("nazwy", emptySet())?.toMutableSet() ?: return
    if (stara in zestaw) {
        zestaw.remove(stara)
        zestaw.add(nowa)
        prefs.edit().putStringSet("nazwy", zestaw).apply()
    }
}

/**
 * Normalizuje numer kontaktu do porównania — usuwa spacje, myślniki,
 * nawiasy i prefiks +48, ale tylko gdy zostaje 9 cyfr (numer polski).
 * Numery zagraniczne zachowują prefiks kraju.
 */
fun normalizujKontakt(numer: String): String {
    val cyfry = numer.filter { it.isDigit() }
    if (cyfry.startsWith("48") && cyfry.length == 11) {
        return cyfry.substring(2)
    }
    if (cyfry.length == 9) return cyfry
    return cyfry
}