package dialer.dominik.pl.data

/**
 * Wspólna normalizacja numerów do porównań (spam, notatki, grupowanie).
 *
 * Zostawia same cyfry; zdejmuje prefiks PL (`48` / `0048`), gdy wynik to
 * 9-cyfrowy numer krajowy — żeby `+48 600…`, `48600…` i `600…` trafiały w ten sam klucz.
 */
fun kluczNumeru(numer: String): String {
    var cyfry = numer.filter { it.isDigit() }
    when {
        cyfry.startsWith("0048") && cyfry.length == 13 -> cyfry = cyfry.removePrefix("0048")
        cyfry.startsWith("48") && cyfry.length == 11 -> cyfry = cyfry.removePrefix("48")
    }
    return cyfry
}
