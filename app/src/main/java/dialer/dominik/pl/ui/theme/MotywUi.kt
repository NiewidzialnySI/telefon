package dialer.dominik.pl.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Tekst główny zależny od motywu (zamiast sztywnego Color.White). */
@Composable
fun motywTekst(alpha: Float = 1f): Color =
    LocalMotyw.current.naTle.copy(alpha = alpha)

@Composable
fun motywTekstDrugi(alpha: Float = 1f): Color =
    LocalMotyw.current.tekstDrugorzedny.copy(alpha = alpha.coerceIn(0f, 1f))

@Composable
fun motywKafelek(): Color = LocalMotyw.current.kafelek

@Composable
fun motywRamka(): Color = LocalMotyw.current.kafelekRamka

/** Klasyczny gradient kafelka (góra → dół), czytelny w jasnym i ciemnym motywie. */
fun MotywPaleta.gradientKafelka(alfaGora: Float = 1f): Brush {
    val gora = kafelek.copy(alpha = alfaGora.coerceIn(0f, 1f))
    val dol = if (ciemny) {
        Color(
            red = (kafelek.red * 0.52f).coerceIn(0f, 1f),
            green = (kafelek.green * 0.58f).coerceIn(0f, 1f),
            blue = (kafelek.blue * 0.55f).coerceIn(0f, 1f),
            alpha = 1f
        )
    } else {
        Color(
            red = (kafelek.red * 0.90f).coerceIn(0f, 1f),
            green = (kafelek.green * 0.90f).coerceIn(0f, 1f),
            blue = (kafelek.blue * 0.90f).coerceIn(0f, 1f),
            alpha = 1f
        )
    }
    return Brush.verticalGradient(listOf(gora, dol))
}

/** Tekst zawsze czytelny na ciemnym pasku/dekoracji (nie zależy od jasnego motywu). */
val TekstNaCiemnym = Color.White
val TekstNaCiemnymDrugi = Color.White.copy(alpha = 0.85f)
