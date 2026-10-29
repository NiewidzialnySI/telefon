package dialer.dominik.pl.sms

import android.content.Context
import android.content.SharedPreferences

/**
 * Kanał, przez który wysyłamy wiadomość do danego kontaktu.
 *
 * SMS obsługujemy sami (jesteśmy domyślną aplikacją SMS).
 * Dla komunikatorów otwieramy ich aplikację z gotowym numerem i treścią —
 * pełna automatyczna wysyłka jest niemożliwa bez uprawnień systemowych.
 */
enum class KanalWiadomosci {
    Sms,
    Messenger,
    Signal
}

/** Nazwa wyświetlana kanału. */
fun KanalWiadomosci.nazwa(): String = when (this) {
    KanalWiadomosci.Sms -> "SMS"
    KanalWiadomosci.Messenger -> "Messenger"
    KanalWiadomosci.Signal -> "Signal"
}

/** Nazwa pakietu aplikacji obsługującej kanał — do sprawdzenia, czy jest zainstalowana. */
fun KanalWiadomosci.pakiet(): String? = when (this) {
    KanalWiadomosci.Sms -> null
    KanalWiadomosci.Messenger -> "com.facebook.orca"
    KanalWiadomosci.Signal -> "org.thoughtcrime.securesms"
}

private const val PLIK_PREFERENCJI = "telefon_kanaly"

/** Klucz w preferencjach — numer + kanał. */
private fun kluczKanaly(numer: String): String =
    "kanal_${numer.filter { it.isDigit() }}"

/** Klucz dla identyfikatora konta w komunikatorze (np. ID Messenger). */
private fun kluczKonta(numer: String, kanal: KanalWiadomosci): String =
    "konto_${kanal.name}_${numer.filter { it.isDigit() }}"

private fun prefs(context: Context): SharedPreferences =
    context.getSharedPreferences(PLIK_PREFERENCJI, Context.MODE_PRIVATE)

/** Zapisuje wybrany kanał dla numeru. */
fun zapamietajKanal(context: Context, numer: String, kanal: KanalWiadomosci) {
    prefs(context).edit().putString(kluczKanaly(numer), kanal.name).apply()
}

/** Odczytuje kanał dla numeru; domyślnie SMS. */
fun pobierzKanal(context: Context, numer: String): KanalWiadomosci {
    val zapisany = prefs(context).getString(kluczKanaly(numer), null) ?: return KanalWiadomosci.Sms
    return runCatching { KanalWiadomosci.valueOf(zapisany) }.getOrDefault(KanalWiadomosci.Sms)
}

/**
 * Zapisuje identyfikator konta w komunikatorze.
 *
 * Messenger nie przyjmuje numeru telefonu — wymaga wewnętrznego ID Facebooka.
 * Użytkownik wpisuje je raz, a my zapamiętujemy przy numerze.
 */
fun zapamietajKontoKomunikatora(
    context: Context,
    numer: String,
    kanal: KanalWiadomosci,
    identyfikator: String
) {
    prefs(context).edit().putString(kluczKonta(numer, kanal), identyfikator.trim()).apply()
}

/** Odczytuje zapisany identyfikator konta (np. ID Messenger). */
fun pobierzKontoKomunikatora(
    context: Context,
    numer: String,
    kanal: KanalWiadomosci
): String? = prefs(context).getString(kluczKonta(numer, kanal), null)?.takeIf { it.isNotBlank() }

private fun kluczNumeruDoDzwonienia(nazwaKontaktu: String): String =
    "numer_dzwon_${nazwaKontaktu.lowercase().trim()}"

fun zapamietajNumerDoDzwonienia(context: Context, nazwaKontaktu: String, numer: String) {
    prefs(context).edit().putString(kluczNumeruDoDzwonienia(nazwaKontaktu), numer.trim()).apply()
}

fun pobierzNumerDoDzwonienia(context: Context, nazwaKontaktu: String): String? =
    prefs(context).getString(kluczNumeruDoDzwonienia(nazwaKontaktu), null)?.takeIf { it.isNotBlank() }

private fun kluczNumeruDoWiadomosci(nazwaKontaktu: String): String =
    "numer_wiad_${nazwaKontaktu.lowercase().trim()}"

/**
 * Zapisuje, na który numer ma iść wiadomość, gdy kontakt ma ich kilka.
 *
 * Użytkownik chce np. dzwonić na 733…, ale pisać na 669… (bo tam jest Signal).
 * Preferencja jest per kontakt, niezależna od tego, który numer kliknął.
 */
fun zapamietajNumerDoWiadomosci(
    context: Context,
    nazwaKontaktu: String,
    numer: String
) {
    prefs(context).edit().putString(kluczNumeruDoWiadomosci(nazwaKontaktu), numer.trim()).apply()
}

/** Odczytuje preferowany numer do wiadomości; null gdy nie ustawiono. */
fun pobierzNumerDoWiadomosci(context: Context, nazwaKontaktu: String): String? =
    prefs(context).getString(kluczNumeruDoWiadomosci(nazwaKontaktu), null)?.takeIf { it.isNotBlank() }
