package dialer.dominik.pl.call

import android.content.Context

/**
 * Notatki przypisywane do rozmów (logów połączeń) — mapa numer → notatka.
 * Zapis w SharedPreferences: klucz to znormalizowany numer (bez spacji/+48),
 * wartość to treść notatki zapisana podczas rozmowy.
 */
object NotatkiRozmow {

    private const val PREFS_NOTATKI = "notatki_rozmow"

    /**
     * Zwraca notatkę dla danego numeru (lub null, gdy brak).
     * Działa niezależnie od formatu numeru (normalizuje klucz).
     */
    fun pobierzNotatke(context: Context, numer: String): String? {
        if (numer.isBlank()) return null
        val prefs = context.getSharedPreferences(PREFS_NOTATKI, Context.MODE_PRIVATE)
        return prefs.getString(normalizujNumer(numer), null)?.takeIf { it.isNotBlank() }
    }

    /**
     * Zapisuje notatkę dla danego numeru. Pusta notatka usuwa wpis.
     */
    fun zapiszNotatke(context: Context, numer: String, tresc: String) {
        if (numer.isBlank()) return
        val prefs = context.getSharedPreferences(PREFS_NOTATKI, Context.MODE_PRIVATE)
        val klucz = normalizujNumer(numer)
        val editor = prefs.edit()
        if (tresc.isBlank()) {
            editor.remove(klucz)
        } else {
            editor.putString(klucz, tresc.trim())
        }
        editor.apply()
    }

    /** Normalizacja numeru do formy klucza: tylko cyfry, bez +48 i spacji. */
    private fun normalizujNumer(numer: String): String {
        var cyfry = numer.filter { it.isDigit() }
        if (cyfry.startsWith("48") && cyfry.length == 11) {
            cyfry = cyfry.removePrefix("48")
        }
        return cyfry
    }
}