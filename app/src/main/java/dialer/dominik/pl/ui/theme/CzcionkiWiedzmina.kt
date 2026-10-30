package dialer.dominik.pl.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dialer.dominik.pl.R

/**
 * Czcionki motywu Wiedźmin (użytek niekomercyjny / osobisty):
 * - Handvetica Neue (Zetafonts, trial CC BY-NC) — tekst UI
 *   https://www.1001fonts.com/handvetica-neue-font.html
 * - Alte DIN 1451 Mittelschrift (Peter Wiegel, SIL OFL) — cyfry / zwarte etykiety
 *   (legalny DIN zamiast płatnego PFDinTextCondPro od Parachute)
 */
object CzcionkiWiedzmina {
    val HelveticaNeue: FontFamily = FontFamily(
        Font(R.font.helvetica_neue_regular, FontWeight.Normal),
        Font(R.font.helvetica_neue_regular, FontWeight.Medium),
        Font(R.font.helvetica_neue_bold, FontWeight.SemiBold),
        Font(R.font.helvetica_neue_bold, FontWeight.Bold)
    )

    val PfDinTextCondPro: FontFamily = FontFamily(
        Font(R.font.pfdin_text_cond_pro_regular, FontWeight.Normal),
        Font(R.font.pfdin_text_cond_pro_semibold, FontWeight.Medium),
        Font(R.font.pfdin_text_cond_pro_semibold, FontWeight.SemiBold),
        Font(R.font.pfdin_text_cond_pro_bold, FontWeight.Bold)
    )
}

fun typografiaWiedzmina(): Typography {
    val h = CzcionkiWiedzmina.HelveticaNeue
    // Handvetica trial nie ma czytelnych cyfr — body/label = system (numery, daty).
    val czytelna = FontFamily.Default
    return Typography(
        displayLarge = TextStyle(fontFamily = h, fontWeight = FontWeight.Bold, fontSize = 57.sp, lineHeight = 64.sp),
        displayMedium = TextStyle(fontFamily = h, fontWeight = FontWeight.Bold, fontSize = 45.sp, lineHeight = 52.sp),
        displaySmall = TextStyle(fontFamily = h, fontWeight = FontWeight.SemiBold, fontSize = 36.sp, lineHeight = 44.sp),
        headlineLarge = TextStyle(fontFamily = h, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 40.sp),
        headlineMedium = TextStyle(fontFamily = h, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 36.sp),
        headlineSmall = TextStyle(fontFamily = h, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp),
        titleLarge = TextStyle(fontFamily = h, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
        titleMedium = TextStyle(fontFamily = h, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
        titleSmall = TextStyle(fontFamily = h, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
        bodyLarge = TextStyle(fontFamily = czytelna, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp),
        bodyMedium = TextStyle(fontFamily = czytelna, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp),
        bodySmall = TextStyle(fontFamily = czytelna, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
        labelLarge = TextStyle(fontFamily = czytelna, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
        labelMedium = TextStyle(fontFamily = czytelna, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
        labelSmall = TextStyle(fontFamily = czytelna, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp)
    )
}

/** Nazwy / nagłówki — Handvetica. */
@Composable
fun czcionkaMotywuUi(): FontFamily {
    val stan by MotywStan.stan.collectAsState()
    return if (stan.paletaId == "wiedzmin") CzcionkiWiedzmina.HelveticaNeue else FontFamily.Default
}

/** Numery telefonu / daty — zawsze czytelne cyfry (system). */
@Composable
fun czcionkaMotywuCyfr(): FontFamily = FontFamily.Default

/** Cyfry na klawiaturze (DIN). */
val CzcionkaKlawiaturyWiedzmin: FontFamily
    get() = CzcionkiWiedzmina.PfDinTextCondPro

