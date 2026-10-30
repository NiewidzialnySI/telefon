package dialer.dominik.pl.call

import android.content.Context
import android.provider.CallLog
import dialer.dominik.pl.data.kluczNumeru

/**
 * Notatki przypisane do konkretnego wpisu w dzienniku połączeń (CallLog._ID),
 * nie do numeru / głównego kafelka grupy.
 *
 * Prefs: klucz `call_<id>` → treść. Stare klucze po numerze są jednorazowo
 * migrowane na najnowszy wpis tego numeru.
 */
object NotatkiRozmow {

    private const val PREFS_NOTATKI = "notatki_rozmow"
    private const val PREFIX_CALL = "call_"
    private const val PREFIX_OCZEK = "oczek_"

    private fun kluczWpis(id: Long) = "$PREFIX_CALL$id"

    fun pobierzNotatke(context: Context, callLogId: Long): String? {
        if (callLogId <= 0L) return null
        val prefs = context.getSharedPreferences(PREFS_NOTATKI, Context.MODE_PRIVATE)
        return prefs.getString(kluczWpis(callLogId), null)?.takeIf { it.isNotBlank() }
    }

    /** Mapa id wpisu → notatka (jedno odczytanie prefs). */
    fun mapaNotatek(context: Context): Map<Long, String> {
        migrujStarePoNumerze(context)
        val prefs = context.getSharedPreferences(PREFS_NOTATKI, Context.MODE_PRIVATE)
        val wynik = mutableMapOf<Long, String>()
        for ((k, v) in prefs.all) {
            if (!k.startsWith(PREFIX_CALL)) continue
            val id = k.removePrefix(PREFIX_CALL).toLongOrNull() ?: continue
            val tresc = (v as? String)?.takeIf { it.isNotBlank() } ?: continue
            wynik[id] = tresc
        }
        return wynik
    }

    fun zapiszNotatke(context: Context, callLogId: Long, tresc: String) {
        if (callLogId <= 0L) return
        val prefs = context.getSharedPreferences(PREFS_NOTATKI, Context.MODE_PRIVATE)
        val klucz = kluczWpis(callLogId)
        val editor = prefs.edit()
        if (tresc.isBlank()) editor.remove(klucz)
        else editor.putString(klucz, tresc.trim())
        editor.apply()
    }

    /**
     * Po zakończeniu rozmowy: szuka wpisu CallLog pasującego do numeru/czasu
     * i zapisuje pod jego ID. Przy opóźnionym zapisie systemowym trzyma notatkę
     * jako oczekującą i [dociagnijOczekujace] podłączy ją przy odświeżeniu historii.
     */
    fun zapiszPoRozmowie(
        context: Context,
        numer: String,
        czasStartu: Long,
        tresc: String
    ) {
        if (numer.isBlank()) return
        val trimmed = tresc.trim()
        if (trimmed.isBlank()) {
            usunOczekujaca(context, numer, czasStartu)
            return
        }
        val id = znajdzIdPolaczenia(context, numer, czasStartu)
        if (id != null && id > 0L) {
            zapiszNotatke(context, id, trimmed)
            usunOczekujaca(context, numer, czasStartu)
        } else {
            zapiszOczekujaca(context, numer, czasStartu, trimmed)
        }
    }

    /** Próbuje przypisać notatki oczekujące do wpisów CallLog (po odświeżeniu historii). */
    fun dociagnijOczekujace(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NOTATKI, Context.MODE_PRIVATE)
        val oczekujace = prefs.all.filterKeys { it.startsWith(PREFIX_OCZEK) }
        if (oczekujace.isEmpty()) return
        val editor = prefs.edit()
        var zmieniono = false
        for ((k, v) in oczekujace) {
            val tresc = (v as? String)?.takeIf { it.isNotBlank() } ?: continue
            val reszta = k.removePrefix(PREFIX_OCZEK)
            val sep = reszta.lastIndexOf('_')
            if (sep <= 0) continue
            val czas = reszta.substring(0, sep).toLongOrNull() ?: continue
            val kluczNum = reszta.substring(sep + 1)
            val id = znajdzIdPolaczeniaPoKluczu(context, kluczNum, czas)
            if (id != null && id > 0L) {
                editor.putString(kluczWpis(id), tresc)
                editor.remove(k)
                zmieniono = true
            }
        }
        if (zmieniono) editor.apply()
    }

    private fun kluczOczek(numer: String, czasStartu: Long): String {
        val czas = if (czasStartu > 0L) czasStartu else System.currentTimeMillis()
        return "$PREFIX_OCZEK${czas}_${kluczNumeru(numer)}"
    }

    private fun zapiszOczekujaca(context: Context, numer: String, czasStartu: Long, tresc: String) {
        context.getSharedPreferences(PREFS_NOTATKI, Context.MODE_PRIVATE)
            .edit()
            .putString(kluczOczek(numer, czasStartu), tresc.trim())
            .apply()
    }

    private fun usunOczekujaca(context: Context, numer: String, czasStartu: Long) {
        context.getSharedPreferences(PREFS_NOTATKI, Context.MODE_PRIVATE)
            .edit()
            .remove(kluczOczek(numer, czasStartu))
            .apply()
    }

    /** Najbliższy wpis CallLog dla numeru względem czasu startu rozmowy. */
    fun znajdzIdPolaczenia(context: Context, numer: String, czasStartu: Long): Long? {
        return znajdzIdPolaczeniaPoKluczu(context, kluczNumeru(numer), czasStartu)
    }

    private fun znajdzIdPolaczeniaPoKluczu(
        context: Context,
        kluczNum: String,
        czasStartu: Long
    ): Long? {
        if (kluczNum.isBlank()) return null
        val teraz = System.currentTimeMillis()
        val od = (if (czasStartu > 0L) czasStartu else teraz) - 120_000L
        return try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls._ID, CallLog.Calls.NUMBER, CallLog.Calls.DATE),
                "${CallLog.Calls.DATE} >= ?",
                arrayOf(od.toString()),
                "${CallLog.Calls.DATE} DESC"
            )?.use { c ->
                val idIdx = c.getColumnIndex(CallLog.Calls._ID)
                val numIdx = c.getColumnIndex(CallLog.Calls.NUMBER)
                val dateIdx = c.getColumnIndex(CallLog.Calls.DATE)
                var najlepszyId: Long? = null
                var najlepszaRoznica = Long.MAX_VALUE
                val cel = if (czasStartu > 0L) czasStartu else teraz
                while (c.moveToNext()) {
                    val numerWpis = if (numIdx != -1) c.getString(numIdx) ?: "" else ""
                    if (kluczNumeru(numerWpis) != kluczNum) continue
                    val id = if (idIdx != -1) c.getLong(idIdx) else 0L
                    val data = if (dateIdx != -1) c.getLong(dateIdx) else 0L
                    if (id <= 0L) continue
                    val roznica = kotlin.math.abs(data - cel)
                    if (roznica < najlepszaRoznica) {
                        najlepszaRoznica = roznica
                        najlepszyId = id
                    }
                }
                // Akceptuj tylko sensownie bliski wpis (10 min) — unikamy starej rozmowy.
                if (najlepszaRoznica <= 600_000L) najlepszyId else null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Jednorazowa migracja: stare notatki kluczowane numerem → najnowszy wpis CallLog.
     */
    private fun migrujStarePoNumerze(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NOTATKI, Context.MODE_PRIVATE)
        val stare = prefs.all.filterKeys {
            !it.startsWith(PREFIX_CALL) && !it.startsWith(PREFIX_OCZEK)
        }
        if (stare.isEmpty()) return
        val editor = prefs.edit()
        for ((k, v) in stare) {
            val tresc = (v as? String)?.takeIf { it.isNotBlank() }
            if (tresc == null) {
                editor.remove(k)
                continue
            }
            val id = znajdzNajnowszyIdDlaNumeru(context, k)
            if (id != null && id > 0L) {
                editor.putString(kluczWpis(id), tresc)
            }
            editor.remove(k)
        }
        editor.apply()
    }

    private fun znajdzNajnowszyIdDlaNumeru(context: Context, kluczLubNumer: String): Long? {
        val klucz = kluczNumeru(kluczLubNumer)
        if (klucz.isBlank()) return null
        return try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls._ID, CallLog.Calls.NUMBER),
                null,
                null,
                "${CallLog.Calls.DATE} DESC"
            )?.use { c ->
                val idIdx = c.getColumnIndex(CallLog.Calls._ID)
                val numIdx = c.getColumnIndex(CallLog.Calls.NUMBER)
                while (c.moveToNext()) {
                    val numerWpis = if (numIdx != -1) c.getString(numIdx) ?: "" else ""
                    if (kluczNumeru(numerWpis) != klucz) continue
                    val id = if (idIdx != -1) c.getLong(idIdx) else 0L
                    if (id > 0L) return@use id
                }
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
