package dialer.dominik.pl.sms

/**
 * Wspólne wykrywanie SMS-ów z kodami 2FA / OTP.
 *
 * Wzorowane na logice [SmsCode](https://github.com/tianma8023/SmsCode):
 * - najpierw musi być słowo kluczowe (kod / OTP / verification…),
 * - potem w pobliżu (okno ~30 znaków) krótki kod cyfrowy 4–8 znaków,
 * - preferujemy 6-cyfrowe, potem 4-cyfrowe; odrzucamy numery „telefoniczne” (8+ cyfr).
 *
 * Samo słowo „kod” (np. „kod rabatowy”, „kod pocztowy”) **bez** krótkiego
 * ciągu cyfr obok **nie** kwalifikuje wiadomości — stąd stare czyszczenie
 * oparte o `contains("kod")` dawało fałszywe trafienia i pomijało prawdziwe OTP.
 */
object WykrywanieOtp {

    /** Słowa kluczowe PL/EN typowe dla SMS-ów weryfikacyjnych. */
    private val SLOWA_KLUCZOWE = Regex(
        "(?i)(?<![\\p{L}])(?:" +
            "kod(?:em|u|zie)?|" +
            "otp|2\\s*-?\\s*fa|two[\\s-]?factor|" +
            "code|pin|passcode|password|hasł[oa]|" +
            "weryfikacj[aeiy]|" +
            "verification|" +
            "auth(?:entication|enticator)?|" +
            "one[\\s-]?time|" +
            "security\\s*code|" +
            "login\\s*code|" +
            "access\\s*code" +
            ")(?![\\p{L}])"
    )

    /** Kandydaci na kod: 4–8 cyfr, nie w środku dłuższego numeru. */
    private val KOD_CYFROWY = Regex("(?<!\\d)\\d{4,8}(?!\\d)")

    /** Okno sąsiedztwa słowo kluczowe ↔ kod (jak magicNumber=30 w SmsCode). */
    private const val OKNO_SASIEDZTWA = 30

    private const val POZIOM_6 = 4
    private const val POZIOM_4 = 3
    private const val POZIOM_INNY = 2

    /**
     * Wyciąga kod OTP z treści albo `null`, gdy to nie wygląda na SMS weryfikacyjny.
     */
    fun wykryj(tresc: String): String? {
        if (tresc.isBlank()) return null
        val keywordMatch = SLOWA_KLUCZOWE.find(tresc) ?: return null
        val keyword = keywordMatch.value
        val keywordIdx = keywordMatch.range.first

        val kandydaci = KOD_CYFROWY.findAll(tresc)
            .map { it.value to it.range.first }
            .filter { (kod, _) -> !wygladaNaNumerTelefonu(kod) }
            .toList()
        if (kandydaci.isEmpty()) return null

        val blisko = kandydaci.filter { (kod, idx) ->
            bliskoKeyword(keywordIdx, keyword.length, idx, kod.length, tresc.length)
        }
        val pula = if (blisko.isNotEmpty()) blisko else kandydaci

        var najlepszy: String? = null
        var maxPoziom = -1
        var minDystans = tresc.length
        for ((kod, idx) in pula) {
            val poziom = poziomDopasowania(kod)
            val dystans = kotlin.math.abs(keywordIdx - idx)
            if (poziom > maxPoziom || (poziom == maxPoziom && dystans < minDystans)) {
                maxPoziom = poziom
                minDystans = dystans
                najlepszy = kod
            }
        }
        return najlepszy
    }

    /** Czy treść to SMS z kodem 2FA/OTP (do czyszczenia skrzynki). */
    fun czyWiadomoscOtp(tresc: String): Boolean = wykryj(tresc) != null

    private fun poziomDopasowania(kod: String): Int = when {
        kod.matches(Regex("\\d{6}")) -> POZIOM_6
        kod.matches(Regex("\\d{4}")) -> POZIOM_4
        else -> POZIOM_INNY
    }

    private fun wygladaNaNumerTelefonu(kod: String): Boolean =
        kod.length >= 8 && kod.all { it.isDigit() }

    private fun bliskoKeyword(
        keywordIdx: Int,
        keywordLen: Int,
        kodIdx: Int,
        kodLen: Int,
        trescLen: Int
    ): Boolean {
        val begin = (kodIdx - OKNO_SASIEDZTWA).coerceAtLeast(0)
        val end = (kodIdx + kodLen + OKNO_SASIEDZTWA).coerceAtMost(trescLen)
        // Słowo kluczowe musi wypadać w oknie wokół kandydata na kod.
        return keywordIdx in begin until end ||
            (keywordIdx + keywordLen) in begin..end
    }
}
