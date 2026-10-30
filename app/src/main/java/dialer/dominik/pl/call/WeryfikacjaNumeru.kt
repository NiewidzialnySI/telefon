package dialer.dominik.pl.call

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import dialer.dominik.pl.data.BazaSpamu
import dialer.dominik.pl.data.kluczNumeru
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/** Poziom zagrożenia numeru na podstawie ocen społeczności i bazy spamu. */
enum class PoziomZagrozenia {
    BEZPIECZNY,
    NEUTRALNY,
    PODEJRZANY,
    SPAM_OSZUSTWO
}

/** Pojedyncza opinia (sieć / lokalna). */
data class OpiniaUzytkownika(
    val autor: String,
    val data: String,
    val typOceny: String,
    val tresc: String,
    val zrodlo: String = "siec"
)

/** Wynik analizy wiarygodności numeru telefonu. */
data class WynikWeryfikacjiNumeru(
    val numer: String,
    val poziomZagrozenia: PoziomZagrozenia,
    val kategoria: String,
    val ocenaLiczbowa: String,
    val opisZagrozenia: String,
    val opinie: List<OpiniaUzytkownika>,
    /** Link do nieznanynumer.pl / numertel — „W sieci” / Opublikuj. */
    val urlSiec: String? = null,
    val atrybucja: String? = null,
    val laduje: Boolean = false
) {
    companion object {
        fun laduje(numer: String) = WynikWeryfikacjiNumeru(
            numer = numer,
            poziomZagrozenia = PoziomZagrozenia.NEUTRALNY,
            kategoria = "Szukam…",
            ocenaLiczbowa = "",
            opisZagrozenia = "Szukam opinii w sieci…",
            opinie = emptyList(),
            laduje = true
        )
    }
}

object WeryfikacjaNumeru {

    private const val TAG = "WeryfikacjaNumeru"
    private const val CACHE_TTL_MS = 24L * 60 * 60 * 1000
    private const val PREFS_CACHE = "weryfikacja_cache_v1"

    private data class CacheWpis(val wynik: WynikWeryfikacjiNumeru, val zapisanoMs: Long)

    private val cachePamiec = ConcurrentHashMap<String, CacheWpis>()

    /**
     * Sprawdza opinie: lokalny spam → cache → NumerTel + nieznanynumer.pl + OpinieLokalne.
     */
    suspend fun weryfikujNumer(context: Context, numer: String): WynikWeryfikacjiNumeru =
        withContext(Dispatchers.IO) {
            val klucz = kluczNumeru(numer)
            if (klucz.length < 7) {
                return@withContext WynikWeryfikacjiNumeru(
                    numer = numer,
                    poziomZagrozenia = PoziomZagrozenia.NEUTRALNY,
                    kategoria = "Za krótki numer",
                    ocenaLiczbowa = "",
                    opisZagrozenia = "Brak danych do weryfikacji",
                    opinie = emptyList()
                )
            }

            if (BazaSpamu.czySpam(context, numer)) {
                return@withContext WynikWeryfikacjiNumeru(
                    numer = numer,
                    poziomZagrozenia = PoziomZagrozenia.SPAM_OSZUSTWO,
                    kategoria = "Zablokowany przez Ciebie",
                    ocenaLiczbowa = "Na czarnej liście",
                    opisZagrozenia = "Numer znajduje się na Twojej liście spamu",
                    opinie = listOf(
                        OpiniaUzytkownika(
                            autor = "Ty",
                            data = "Lokalnie",
                            typOceny = "Spam",
                            tresc = "Dodałeś ten numer do bazy spamu w aplikacji.",
                            zrodlo = "lokalna"
                        )
                    ),
                    urlSiec = urlNieznanyNumer(klucz)
                )
            }

            cachePamiec[klucz]?.let { wpis ->
                if (System.currentTimeMillis() - wpis.zapisanoMs < CACHE_TTL_MS) {
                    return@withContext polaczZLokalnymi(context, numer, wpis.wynik)
                }
            }
            odczytajCacheDysk(context, klucz)?.let { wpis ->
                if (System.currentTimeMillis() - wpis.zapisanoMs < CACHE_TTL_MS) {
                    cachePamiec[klucz] = wpis
                    return@withContext polaczZLokalnymi(context, numer, wpis.wynik)
                }
            }

            val wynikSiec = pobierzZSieci(klucz, numer)
            zapiszCache(context, klucz, wynikSiec)
            polaczZLokalnymi(context, numer, wynikSiec)
        }

    /** Odświeża wynik po dodaniu lokalnej opinii (bez ponownego HTTP). */
    fun odswiezZLokalnymi(context: Context, poprzedni: WynikWeryfikacjiNumeru?): WynikWeryfikacjiNumeru? {
        if (poprzedni == null || poprzedni.laduje) return poprzedni
        return polaczZLokalnymi(context, poprzedni.numer, poprzedni.copy(opinie = poprzedni.opinie.filter { it.zrodlo != "lokalna" }))
    }

    private fun polaczZLokalnymi(
        context: Context,
        numer: String,
        baza: WynikWeryfikacjiNumeru
    ): WynikWeryfikacjiNumeru {
        val lokalne = OpinieLokalne.listaDlaNumeru(context, numer)
        if (lokalne.isEmpty()) return baza.copy(numer = numer)
        val poziomLokal = poziomZTypu(lokalne.first().typOceny)
        val poziom = gorszy(baza.poziomZagrozenia, poziomLokal)
        return baza.copy(
            numer = numer,
            poziomZagrozenia = poziom,
            opinie = (lokalne + baza.opinie).distinctBy { it.tresc.take(80) }.take(8),
            opisZagrozenia = if (lokalne.isNotEmpty() && baza.opisZagrozenia.isBlank()) {
                "Masz lokalną opinię o tym numerze"
            } else baza.opisZagrozenia
        )
    }

    private suspend fun pobierzZSieci(klucz: String, numerOrg: String): WynikWeryfikacjiNumeru =
        coroutineScope {
            val nt = async { pobierzNumerTel(klucz) }
            val nn = async { pobierzNieznanyNumer(klucz) }
            val numertel = nt.await()
            val nieznany = nn.await()

            val url = numertel?.url ?: nieznany?.url ?: urlNieznanyNumer(klucz)
            val atrybucja = when {
                numertel != null && nieznany != null -> "dane: numertel.pl · nieznanynumer.pl"
                numertel != null -> "dane: numertel.pl"
                nieznany != null -> "dane: nieznanynumer.pl"
                else -> null
            }

            if (numertel == null && nieznany == null) {
                return@coroutineScope WynikWeryfikacjiNumeru(
                    numer = numerOrg,
                    poziomZagrozenia = PoziomZagrozenia.NEUTRALNY,
                    kategoria = "Brak danych w sieci",
                    ocenaLiczbowa = "",
                    opisZagrozenia = "Nie znaleziono opinii — możesz dodać własną",
                    opinie = emptyList(),
                    urlSiec = url
                )
            }

            val poziom = gorszy(
                numertel?.poziom ?: PoziomZagrozenia.NEUTRALNY,
                nieznany?.poziom ?: PoziomZagrozenia.NEUTRALNY
            )
            val kategoria = numertel?.kategoria
                ?: nieznany?.kategoria
                ?: "Numer w bazach społeczności"
            val ocena = when {
                numertel?.ocenaTekst?.isNotBlank() == true -> numertel.ocenaTekst
                nieznany?.ocenaTekst?.isNotBlank() == true -> nieznany.ocenaTekst
                else -> ""
            }
            val opis = listOfNotNull(numertel?.opis, nieznany?.opis)
                .firstOrNull { it.isNotBlank() }
                ?: "Sprawdzono w publicznych bazach opinii"
            val opinie = (nieznany?.opinie.orEmpty()).take(5)

            WynikWeryfikacjiNumeru(
                numer = numerOrg,
                poziomZagrozenia = poziom,
                kategoria = kategoria,
                ocenaLiczbowa = ocena,
                opisZagrozenia = opis,
                opinie = opinie,
                urlSiec = url,
                atrybucja = atrybucja
            )
        }

    // ── NumerTel ──────────────────────────────────────────────

    private data class FragmentSieci(
        val poziom: PoziomZagrozenia,
        val kategoria: String,
        val ocenaTekst: String,
        val opis: String,
        val opinie: List<OpiniaUzytkownika> = emptyList(),
        val url: String? = null
    )

    private fun pobierzNumerTel(klucz: String): FragmentSieci? {
        val dziewiec = if (klucz.length == 11 && klucz.startsWith("48")) klucz.substring(2) else klucz
        if (dziewiec.length !in 7..11) return null
        return try {
            val url = URL("https://numertel.pl/api/v1/check/$dziewiec")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4000
                readTimeout = 4000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "TelefonDialer/1.0")
            }
            if (conn.responseCode !in 200..299) {
                conn.disconnect()
                return null
            }
            val body = conn.inputStream.bufferedReader().use(BufferedReader::readText)
            conn.disconnect()
            val json = JSONObject(body)
            val label = json.optString("spam_label", "brak_danych")
            val score = if (json.isNull("score_avg")) null else json.optDouble("score_avg")
            val total = json.optInt("total_opinions", 0)
            val isDno = json.optBoolean("is_dno", false)
            val dnoNote = json.optString("dno_note", "").takeIf { it.isNotBlank() }
            val operator = json.optString("operator", "").takeIf { it.isNotBlank() }
            val whitelist = json.optJSONObject("whitelist")
            val institution = whitelist?.optString("institution")?.takeIf { it.isNotBlank() }
            val wlLabel = whitelist?.optString("label")?.takeIf { it.isNotBlank() }
            val pageUrl = json.optString("url", "").takeIf { it.isNotBlank() }
                ?: "https://numertel.pl/numer/$dziewiec"

            val poziom = when {
                isDno -> PoziomZagrozenia.SPAM_OSZUSTWO
                label == "niebezpieczny" -> PoziomZagrozenia.SPAM_OSZUSTWO
                label == "podejrzany" || label == "zgloszony" -> PoziomZagrozenia.PODEJRZANY
                label == "dno_spoofing" -> PoziomZagrozenia.SPAM_OSZUSTWO
                institution != null -> PoziomZagrozenia.BEZPIECZNY
                label == "raczej_bezpieczny" -> PoziomZagrozenia.BEZPIECZNY
                else -> PoziomZagrozenia.NEUTRALNY
            }
            val kategoria = when {
                institution != null -> institution
                isDno || label == "dno_spoofing" -> "Spoofing / DNO UKE"
                label == "niebezpieczny" -> "Niebezpieczny (NumerTel)"
                label == "podejrzany" -> "Podejrzany (NumerTel)"
                label == "zgloszony" -> "Zgłoszony (NumerTel)"
                operator != null -> operator
                else -> "NumerTel"
            }
            val ocenaTekst = buildString {
                if (score != null) append("%.1f/5".format(score))
                if (total > 0) {
                    if (isNotEmpty()) append(" · ")
                    append("$total opinii")
                }
            }
            val opis = when {
                dnoNote != null -> dnoNote
                institution != null -> listOfNotNull(institution, wlLabel).joinToString(" — ")
                label == "niebezpieczny" -> "Społeczność oznacza numer jako niebezpieczny"
                label == "podejrzany" || label == "zgloszony" -> "Są zgłoszenia spamu o tym numerze"
                label == "raczej_bezpieczny" -> "Raczej bezpieczny wg NumerTel"
                else -> "Brak silnych sygnałów spamu (NumerTel)"
            }
            FragmentSieci(poziom, kategoria, ocenaTekst, opis, url = pageUrl)
        } catch (e: Exception) {
            Log.w(TAG, "NumerTel: ${e.message}")
            null
        }
    }

    // ── nieznanynumer.pl ──────────────────────────────────────

    private fun pobierzNieznanyNumer(klucz: String): FragmentSieci? {
        val warianty = buildList {
            add(klucz)
            if (klucz.length == 9) add("48$klucz")
            if (klucz.length == 11 && klucz.startsWith("48")) add(klucz.removePrefix("48"))
        }.distinct()

        for (w in warianty) {
            val frag = pobierzNieznanyNumerUrl("https://www.nieznanynumer.pl/numer/$w")
            if (frag != null) return frag
        }
        return null
    }

    private fun pobierzNieznanyNumerUrl(urlString: String): FragmentSieci? {
        return try {
            val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4000
                readTimeout = 4500
                instanceFollowRedirects = true
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36"
                )
                setRequestProperty("Accept", "text/html")
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                conn.disconnect()
                return null
            }
            val html = conn.inputStream.bufferedReader().use(BufferedReader::readText)
            conn.disconnect()
            if (html.contains("błąd 404", ignoreCase = true) ||
                html.contains("Nie odnaleziono strony", ignoreCase = true)
            ) return null

            var ratingValue: Int? = null
            var ratingCount: Int? = null
            Regex(
                """"ratingValue"\s*:\s*"?(\d+)"?""",
                RegexOption.IGNORE_CASE
            ).find(html)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { ratingValue = it }
            Regex(
                """"ratingCount"\s*:\s*"?(\d+)"?""",
                RegexOption.IGNORE_CASE
            ).find(html)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { ratingCount = it }

            // ratingValue na NN = „bezpieczeństwo” 0–100 (wyżej = bezpieczniej).
            val poziom = when {
                ratingValue == null && html.contains("Niebezpieczny", ignoreCase = true) &&
                    html.contains("rank r-1") -> PoziomZagrozenia.SPAM_OSZUSTWO
                ratingValue != null && ratingValue < 35 -> PoziomZagrozenia.SPAM_OSZUSTWO
                ratingValue != null && ratingValue < 55 -> PoziomZagrozenia.PODEJRZANY
                ratingValue != null && ratingValue >= 75 -> PoziomZagrozenia.BEZPIECZNY
                html.contains(">Nękający<") || html.contains("rank r-2") -> PoziomZagrozenia.PODEJRZANY
                else -> PoziomZagrozenia.NEUTRALNY
            }

            val opinie = mutableListOf<OpiniaUzytkownika>()
            val itemRegex = Regex(
                """class="comment-item[^"]*"[\s\S]*?<strong class="rank[^"]*">([^<]+)</strong>[\s\S]*?<p class="comment-text">([\s\S]*?)</p>[\s\S]*?<span class="date">[\s\S]*?</i>([^<]*)</span>""",
                RegexOption.IGNORE_CASE
            )
            for (m in itemRegex.findAll(html).take(5)) {
                val typ = m.groupValues[1].trim()
                val tresc = m.groupValues[2]
                    .replace(Regex("<[^>]*>"), "")
                    .replace("&nbsp;", " ")
                    .replace("&quot;", "\"")
                    .replace("&hellip;", "…")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                val data = m.groupValues[3].trim()
                if (tresc.isNotBlank()) {
                    opinie.add(
                        OpiniaUzytkownika(
                            autor = "nieznanynumer.pl",
                            data = data.ifBlank { "—" },
                            typOceny = typ,
                            tresc = tresc,
                            zrodlo = "nieznanynumer"
                        )
                    )
                }
            }

            // Fallback prostszy — same comment-text
            if (opinie.isEmpty()) {
                Regex(
                    """<p class="comment-text">([\s\S]*?)</p>""",
                    RegexOption.IGNORE_CASE
                ).findAll(html).take(3).forEach { m ->
                    val tresc = m.groupValues[1]
                        .replace(Regex("<[^>]*>"), "")
                        .replace("&nbsp;", " ")
                        .trim()
                    if (tresc.isNotBlank()) {
                        opinie.add(
                            OpiniaUzytkownika(
                                autor = "nieznanynumer.pl",
                                data = "—",
                                typOceny = "Opinia",
                                tresc = tresc,
                                zrodlo = "nieznanynumer"
                            )
                        )
                    }
                }
            }

            val ocenaTekst = buildString {
                if (ratingValue != null) {
                    // Skala 0–100 → przybliżenie /5
                    append("%.1f/5".format(ratingValue / 20.0))
                }
                if (ratingCount != null && ratingCount > 0) {
                    if (isNotEmpty()) append(" · ")
                    append("$ratingCount ocen")
                }
            }
            val kategoria = when (poziom) {
                PoziomZagrozenia.SPAM_OSZUSTWO -> "Nękający / niebezpieczny"
                PoziomZagrozenia.PODEJRZANY -> "Podejrzany (opinie społeczności)"
                PoziomZagrozenia.BEZPIECZNY -> "Raczej bezpieczny"
                PoziomZagrozenia.NEUTRALNY -> "Opinie społeczności"
            }
            val opis = when (poziom) {
                PoziomZagrozenia.SPAM_OSZUSTWO -> "Niskie zaufanie w nieznanynumer.pl"
                PoziomZagrozenia.PODEJRZANY -> "Mieszane / negatywne oceny społeczności"
                PoziomZagrozenia.BEZPIECZNY -> "Wysokie zaufanie w nieznanynumer.pl"
                PoziomZagrozenia.NEUTRALNY ->
                    if (opinie.isNotEmpty()) "Znaleziono opinie użytkowników"
                    else "Strona numeru istnieje — mało ocen"
            }

            FragmentSieci(
                poziom = poziom,
                kategoria = kategoria,
                ocenaTekst = ocenaTekst,
                opis = opis,
                opinie = opinie,
                url = urlString
            )
        } catch (e: Exception) {
            Log.w(TAG, "nieznanynumer: ${e.message}")
            null
        }
    }

    private fun urlNieznanyNumer(klucz: String): String {
        val path = if (klucz.length == 11 && klucz.startsWith("48")) klucz else klucz
        return "https://www.nieznanynumer.pl/numer/$path"
    }

    private fun gorszy(a: PoziomZagrozenia, b: PoziomZagrozenia): PoziomZagrozenia {
        val rank = mapOf(
            PoziomZagrozenia.BEZPIECZNY to 0,
            PoziomZagrozenia.NEUTRALNY to 1,
            PoziomZagrozenia.PODEJRZANY to 2,
            PoziomZagrozenia.SPAM_OSZUSTWO to 3
        )
        return if ((rank[a] ?: 0) >= (rank[b] ?: 0)) a else b
    }

    private fun poziomZTypu(typ: String): PoziomZagrozenia {
        val t = typ.lowercase()
        return when {
            "spam" in t || "niebezpiecz" in t || "oszust" in t -> PoziomZagrozenia.SPAM_OSZUSTWO
            "nękaj" in t || "nekaj" in t || "telemarket" in t || "podejrz" in t -> PoziomZagrozenia.PODEJRZANY
            "bezpiecz" in t || "przydat" in t -> PoziomZagrozenia.BEZPIECZNY
            else -> PoziomZagrozenia.NEUTRALNY
        }
    }

    private fun zapiszCache(context: Context, klucz: String, wynik: WynikWeryfikacjiNumeru) {
        val teraz = System.currentTimeMillis()
        val bezLokalnych = wynik.copy(opinie = wynik.opinie.filter { it.zrodlo != "lokalna" })
        cachePamiec[klucz] = CacheWpis(bezLokalnych, teraz)
        try {
            val o = JSONObject()
                .put("ms", teraz)
                .put("poziom", bezLokalnych.poziomZagrozenia.name)
                .put("kategoria", bezLokalnych.kategoria)
                .put("ocena", bezLokalnych.ocenaLiczbowa)
                .put("opis", bezLokalnych.opisZagrozenia)
                .put("url", bezLokalnych.urlSiec ?: "")
                .put("atrybucja", bezLokalnych.atrybucja ?: "")
            val arr = org.json.JSONArray()
            bezLokalnych.opinie.take(5).forEach { op ->
                arr.put(
                    JSONObject()
                        .put("a", op.autor)
                        .put("d", op.data)
                        .put("t", op.typOceny)
                        .put("c", op.tresc)
                        .put("z", op.zrodlo)
                )
            }
            o.put("opinie", arr)
            context.getSharedPreferences(PREFS_CACHE, Context.MODE_PRIVATE)
                .edit()
                .putString("c_$klucz", o.toString())
                .apply()
        } catch (e: Exception) {
            Log.w(TAG, "cache write: ${e.message}")
        }
    }

    private fun odczytajCacheDysk(context: Context, klucz: String): CacheWpis? {
        return try {
            val raw = context.getSharedPreferences(PREFS_CACHE, Context.MODE_PRIVATE)
                .getString("c_$klucz", null) ?: return null
            val o = JSONObject(raw)
            val ms = o.optLong("ms")
            val opinie = mutableListOf<OpiniaUzytkownika>()
            val arr = o.optJSONArray("opinie")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val x = arr.optJSONObject(i) ?: continue
                    opinie.add(
                        OpiniaUzytkownika(
                            autor = x.optString("a"),
                            data = x.optString("d"),
                            typOceny = x.optString("t"),
                            tresc = x.optString("c"),
                            zrodlo = x.optString("z", "siec")
                        )
                    )
                }
            }
            val poziom = runCatching {
                PoziomZagrozenia.valueOf(o.optString("poziom", PoziomZagrozenia.NEUTRALNY.name))
            }.getOrDefault(PoziomZagrozenia.NEUTRALNY)
            CacheWpis(
                WynikWeryfikacjiNumeru(
                    numer = klucz,
                    poziomZagrozenia = poziom,
                    kategoria = o.optString("kategoria"),
                    ocenaLiczbowa = o.optString("ocena"),
                    opisZagrozenia = o.optString("opis"),
                    opinie = opinie,
                    urlSiec = o.optString("url").takeIf { it.isNotBlank() },
                    atrybucja = o.optString("atrybucja").takeIf { it.isNotBlank() }
                ),
                ms
            )
        } catch (_: Exception) {
            null
        }
    }

    fun pobierzNazweKontaktu(context: Context, numer: String): String? {
        if (numer.isBlank()) return null
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(numer)
            )
            val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)?.takeIf { it.isNotBlank() }
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun pobierzZdjecieKontaktu(context: Context, numer: String): Bitmap? {
        if (numer.isBlank()) return null
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(numer)
            )
            val projection = arrayOf(ContactsContract.PhoneLookup.PHOTO_URI)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val photoUriStr = cursor.getString(0)
                    if (!photoUriStr.isNullOrBlank()) {
                        val inputStream = context.contentResolver.openInputStream(Uri.parse(photoUriStr))
                        BitmapFactory.decodeStream(inputStream)
                    } else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }
}
