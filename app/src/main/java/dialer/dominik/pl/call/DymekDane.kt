package dialer.dominik.pl.call

import android.graphics.Bitmap

/**
 * Dane kontaktu do wyświetlenia w dymku rozmowy (DymekRozmowyService).
 *
 * Ustawiane przy minimalizacji pełnoekranowej rozmowy (EkranTrwajacejRozmowy.zwin)
 * zanim uruchomi się usługa dymka: [zdjecie] to bitmapa zdjęcia kontaktu (lub null,
 * gdy kontakt nie ma zdjęcia — wtedy pokazywane są [inicjaly] na zielonym gradiencie).
 */
object DymekDane {
    /** Zdjęcie kontaktu (Bitmap? — null gdy brak). */
    var zdjecie: Bitmap? = null

    /** Inicjały rozmówcy, pokazywane gdy brak zdjęcia. Domyślnie "?". */
    var inicjaly: String = "?"
}