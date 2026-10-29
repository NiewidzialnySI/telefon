package dialer.dominik.pl.data

import android.content.Context

/**
 * Trwała lista numerów oznaczonych jako spam (zablokowanych).
 *
 * Współdzielona przez [UslugaIdentyfikacjiPolaczen] (blokuje przychodzące
 * połączenia z tych numerów) i dialer (dodaje numer do spamu).
 *
 * Zapis w SharedPreferences, więc przetrwa restart aplikacji.
 */
object BazaSpamu {
    private const val PREFS = "telefon_spam"
    private const val KLUCZ_NUMERY = "numery_spamu"

    /** Czy dany numer jest zablokowany jako spam? */
    fun czySpam(context: Context, numer: String): Boolean {
        return pobierzNumery(context).contains(normalizuj(numer))
    }

    /** Dodaje numer do listy spamów (normalizowany). */
    fun dodajDoSpamu(context: Context, numer: String) {
        val numery = pobierzNumery(context).toMutableSet()
        numery.add(normalizuj(numer))
        zapisz(context, numery)
    }

    /** Usuwa numer z listy spamów. */
    fun usunZeSpamu(context: Context, numer: String) {
        val numery = pobierzNumery(context).toMutableSet()
        numery.remove(normalizuj(numer))
        zapisz(context, numery)
    }

    private fun pobierzNumery(context: Context): Set<String> {
        val zapisane = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KLUCZ_NUMERY, emptySet()) ?: emptySet()
        return zapisane
    }

    private fun zapisz(context: Context, numery: Set<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KLUCZ_NUMERY, numery)
            .apply()
    }

    /** Normalizacja do porównania — cyfry + strip PL 48 (patrz [kluczNumeru]). */
    private fun normalizuj(numer: String): String = kluczNumeru(numer)
}