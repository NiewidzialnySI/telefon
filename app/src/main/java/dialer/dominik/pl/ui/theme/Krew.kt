package dialer.dominik.pl.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode

/**
 * Realistyczna czerwień krwi (hemoglobin / wet blood), nie flat Material Red.
 *
 * Paleta: Blood Omen #8A0303, arterial #C41E1E, dried #4A0E0E
 * (whatcoloristhis / schemecolor blood-red-black) + mokry highlight.
 */
object Krew {
    /** Świeża, tlenowa — jasny akcent / „kropla”. */
    val Tetnicza = Color(0xFFC41E1E)

    /** Klasyczna blood red — ikony, tekst „Usuń”, nieodebrane. */
    val Klasyczna = Color(0xFF8A0303)

    /** Cięższa, żylna — baza przycisku. */
    val Zylna = Color(0xFF6B0F1A)

    /** Zaschnięta / cień. */
    val Sucha = Color(0xFF4A0E0E)

    /** Prawie czarna krawędź. */
    val Skroplona = Color(0xFF2A0708)

    /** Główny kolor destrukcji w UI (rozłącz, kosz, spam…). */
    val Akcent: Color = Klasyczna

    /**
     * Mokry gradient (highlight u góry → głęboka czerwień → cień).
     */
    fun pedzelMokry(
        sizeHint: Offset = Offset(120f, 120f)
    ): Brush {
        val cx = sizeHint.x * 0.32f
        val cy = sizeHint.y * 0.28f
        return Brush.radialGradient(
            colorStops = arrayOf(
                0.00f to Color(0xFFE85A4A),
                0.18f to Tetnicza,
                0.42f to Klasyczna,
                0.72f to Zylna,
                1.00f to Sucha
            ),
            center = Offset(cx, cy),
            radius = maxOf(sizeHint.x, sizeHint.y) * 0.95f,
            tileMode = TileMode.Clamp
        )
    }

    fun pedzelPionowy(): Brush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFB91C1C),
            Klasyczna,
            Zylna,
            Skroplona
        )
    )
}

/** Tło przycisku rozłącz / destrukcja — mokra krew. */
fun Modifier.tloMokrejKrwi(shape: Shape = CircleShape): Modifier = composed {
    this.background(brush = Krew.pedzelMokry(), shape = shape)
}
