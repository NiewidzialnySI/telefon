package dialer.dominik.pl.call

import android.graphics.Bitmap

/**
 * Dane kontaktu + pozycja powrotu z dymka (odwrotność minimalizacji).
 */
object DymekDane {
    var zdjecie: Bitmap? = null
    var inicjaly: String = "?"

    /** Środek dymka w px ekranu — skąd awatar ma wystartować przy powrocie. */
    var powrotCx: Float = 0f
    var powrotCy: Float = 0f
    var maPowrot: Boolean = false
}
