package dialer.dominik.pl.call

import android.content.Context
import dialer.dominik.pl.data.kluczNumeru

/**
 * Notatki przypisywane do rozmów (logów połączeń) — mapa numer → notatka.
 * Zapis w SharedPreferences: klucz to [kluczNumeru], wartość to treść notatki.
 */
object NotatkiRozmow {

    private const val PREFS_NOTATKI = "notatki_rozmow"

    fun pobierzNotatke(context: Context, numer: String): String? {
        if (numer.isBlank()) return null
        val prefs = context.getSharedPreferences(PREFS_NOTATKI, Context.MODE_PRIVATE)
        return prefs.getString(kluczNumeru(numer), null)?.takeIf { it.isNotBlank() }
    }

    fun zapiszNotatke(context: Context, numer: String, tresc: String) {
        if (numer.isBlank()) return
        val prefs = context.getSharedPreferences(PREFS_NOTATKI, Context.MODE_PRIVATE)
        val klucz = kluczNumeru(numer)
        val editor = prefs.edit()
        if (tresc.isBlank()) {
            editor.remove(klucz)
        } else {
            editor.putString(klucz, tresc.trim())
        }
        editor.apply()
    }
}