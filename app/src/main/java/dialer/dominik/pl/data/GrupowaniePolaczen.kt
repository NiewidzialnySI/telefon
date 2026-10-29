package dialer.dominik.pl.data
import dialer.dominik.pl.ui.screens.CallLogEntry

import java.util.Calendar

/** Zgrupowane połączenia z jednym numerem. */
data class GrupaPolaczen(
    val numer: String,
    val nazwa: String?,
    val wpisy: List<CallLogEntry>,
    val dataOstatniego: Long,
    /** Liczba połączeń w domyślnym zakresie (ostatni miesiąc) — liczona raz przy tworzeniu grupy. */
    val liczba: Int,
    /** Ukryte przez użytkownika — wraca, gdy pojawi się nowe połączenie. */
    val ukryty: Boolean = false
) {
    val typOstatniego: Int get() = wpisy.first().type
}

/** Początek zakresu domyślnego — ostatnie 30 dni (jeden miesiąc). */
fun domyslnyZakres(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
    add(Calendar.DAY_OF_YEAR, -30)
}.timeInMillis

/**
 * Grupuje połączenia po numerze w podanym zakresie czasu.
 *
 * Lista pokazuje WSZYSTKIE rozmowy z zakresu, ale każdy numer występuje
 * tylko raz — z licznikiem, ile razy dzwonił. Starsze rozmowy doładowują
 * się przy przewijaniu na dół przez [nastepnyZakres].
 */
fun grupujPolaczenia(
    wszystkie: List<CallLogEntry>,
    zakresOd: Long
): List<GrupaPolaczen> {
    // Normalizujemy numer RAZ na wpis (groupBy wykonałby to wielokrotnie
    // dla tego samego numeru), potem grupujemy po znormalizowanym kluczu.
    val zakres = zakresOd
    // Próg licznika (ostatnie 30 dni) liczony raz — nie tworzymy Calendar per grupa.
    val progLicznika = domyslnyZakres()
    return wszystkie
        .filter { it.date >= zakres }
        .map { it to normalizujNumer(it.number) }
        .groupBy { it.second }
        .map { (_, pary) ->
            val wpisy = pary.map { it.first }.sortedByDescending { it.date }
            GrupaPolaczen(
                // Do wyświetlania i dzwonienia bierzemy numer z najnowszego wpisu
                numer = wpisy.first().number,
                nazwa = wpisy.firstOrNull()?.name?.takeIf { it.isNotBlank() },
                wpisy = wpisy,
                dataOstatniego = wpisy.first().date,
                // Liczba połączeń tylko z ostatniego miesiąca — liczona raz
                liczba = wpisy.count { it.date >= progLicznika }
            )
        }
        .sortedByDescending { it.dataOstatniego }
}

/**
 * Kolejny zakres po przewinięciu na dół — dokłada starsze rozmowy.
 * Cofa się o kolejny miesiąc, aż obejmie wszystko (wtedy zwraca 0).
 */
fun nastepnyZakres(obecny: Long, najstarsza: Long): Long {
    if (obecny == 0L) return 0L
    val kandydat = Calendar.getInstance().apply {
        timeInMillis = obecny
        add(Calendar.DAY_OF_YEAR, -30)
    }.timeInMillis
    return if (kandydat <= najstarsza) 0L else kandydat
}

/**
 * Normalizuje numer do porównywania — TYLKO polski prefiks +48.
 *
 * Usuwa spacje, myślniki i nawiasy (kosmetyka zapisu), a także wiodące „+48"
 * oraz „0048" — ale wyłącznie wtedy, gdy po skróceniu zostaje 9 cyfr,
 * czyli poprawny polski numer krajowy. Numery zagraniczne pozostają nietknięte:
 * „+49 30 123456" ma po skróceniu 10 cyfr, więc nie jest ruszane.
 */
fun normalizujNumer(numer: String): String {
    // Same cyfry i ewentualny wiodący plus
    val oczyszczony = numer.filter { it.isDigit() || it == '+' }

    val bezPrefiksu = when {
        oczyszczony.startsWith("+48") -> oczyszczony.removePrefix("+48")
        oczyszczony.startsWith("0048") -> oczyszczony.removePrefix("0048")
        else -> null
    }

    // Skracamy tylko, gdy wynik to poprawny 9-cyfrowy numer polski
    return if (bezPrefiksu != null && bezPrefiksu.length == 9) bezPrefiksu else oczyszczony
}

/**
 * Filtruje grupy ukryte przez użytkownika.
 *
 * Ukrycie działa do momentu, aż pojawi się NOWE połączenie z tym numerem.
 * Zapisujemy czas ukrycia — jeśli najnowsze połączenie jest późniejsze,
 * grupa wraca na listę.
 */
fun filtrujUkryte(
    grupy: List<GrupaPolaczen>,
    ukryteDo: Map<String, Long>
): List<GrupaPolaczen> {
    return grupy.filter { grupa ->
        val ukrytaDo = ukryteDo[normalizujNumer(grupa.numer)] ?: return@filter true
        // Wraca, gdy pojawiło się połączenie nowsze niż moment ukrycia
        grupa.dataOstatniego > ukrytaDo
    }
}
