package dialer.dominik.pl.call

import android.content.Context
import dialer.dominik.pl.data.kluczNumeru
import org.json.JSONArray
import org.json.JSONObject

/**
 * Opinie użytkownika o numerach — SharedPreferences (JSON).
 * Widoczne lokalnie na dzwonku / rozmowie; publikacja w sieci = Intent VIEW.
 */
object OpinieLokalne {

    private const val PREFS = "opinie_lokalne_v1"
    private const val K_JSON = "opinie_json"

    data class OpiniaLokalna(
        val numerKlucz: String,
        val typOceny: String,
        val tresc: String,
        val czasMs: Long
    )

    fun listaDlaNumeru(context: Context, numer: String): List<OpiniaUzytkownika> {
        val klucz = kluczNumeru(numer)
        if (klucz.isBlank()) return emptyList()
        return wszystkie(context)
            .filter { it.numerKlucz == klucz }
            .sortedByDescending { it.czasMs }
            .map {
                OpiniaUzytkownika(
                    autor = "Ty",
                    data = formatujDate(it.czasMs),
                    typOceny = it.typOceny,
                    tresc = it.tresc,
                    zrodlo = "lokalna"
                )
            }
    }

    fun dodaj(
        context: Context,
        numer: String,
        typOceny: String,
        tresc: String
    ) {
        val klucz = kluczNumeru(numer)
        if (klucz.isBlank()) return
        val lista = wszystkie(context).toMutableList()
        lista.add(
            0,
            OpiniaLokalna(
                numerKlucz = klucz,
                typOceny = typOceny.trim().ifBlank { "Neutralny" },
                tresc = tresc.trim().ifBlank { typOceny },
                czasMs = System.currentTimeMillis()
            )
        )
        // Max 5 opinii na numer — najnowsze.
        val przyciete = lista
            .groupBy { it.numerKlucz }
            .flatMap { (_, grupy) -> grupy.sortedByDescending { it.czasMs }.take(5) }
            .sortedByDescending { it.czasMs }
            .take(200)
        zapisz(context, przyciete)
    }

    private fun wszystkie(context: Context): List<OpiniaLokalna> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(K_JSON, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    add(
                        OpiniaLokalna(
                            numerKlucz = o.optString("n"),
                            typOceny = o.optString("t"),
                            tresc = o.optString("c"),
                            czasMs = o.optLong("ms")
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun zapisz(context: Context, lista: List<OpiniaLokalna>) {
        val arr = JSONArray()
        lista.forEach { op ->
            arr.put(
                JSONObject()
                    .put("n", op.numerKlucz)
                    .put("t", op.typOceny)
                    .put("c", op.tresc)
                    .put("ms", op.czasMs)
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(K_JSON, arr.toString())
            .apply()
    }

    private fun formatujDate(ms: Long): String {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = ms }
        val d = cal.get(java.util.Calendar.DAY_OF_MONTH)
        val m = cal.get(java.util.Calendar.MONTH) + 1
        val y = cal.get(java.util.Calendar.YEAR)
        return "%02d.%02d.%04d".format(d, m, y)
    }
}
