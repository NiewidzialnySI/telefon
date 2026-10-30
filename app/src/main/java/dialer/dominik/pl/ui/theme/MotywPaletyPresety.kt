package dialer.dominik.pl.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.PaletteStyle

/**
 * Nazwane palety barw (nie pojedynczy kolor).
 *
 * Zgodnie z Material 3 / material-color-utilities:
 * — primary (seed) = akcent wiodący
 * — secondary = spokojniejszy towarzysz (np. różowy → lekko fioletowy)
 * — tertiary = kontrastujący akcent
 * Schemat M3 generuje z nich role: onPrimary, surface, onSurface itd.
 *
 * @see <a href="https://m3.material.io/styles/color/roles">M3 Color roles</a>
 * @see <a href="https://github.com/material-foundation/material-color-utilities">material-color-utilities</a>
 */
data class MotywPaletaNazwana(
    val id: String,
    val nazwa: String,
    val seed: Color,
    val secondary: Color? = null,
    val tertiary: Color? = null,
    val styl: PaletteStyle = PaletteStyle.TonalSpot
)

object MotywPaletyPresety {

    val Wszystkie: List<MotywPaletaNazwana> = listOf(
        MotywPaletaNazwana(
            id = "neon_zielony",
            nazwa = "Neon zielony",
            seed = Color(0xFF69F0AE),
            secondary = Color(0xFF00E676),
            tertiary = Color(0xFFA7FFEB),
            styl = PaletteStyle.TonalSpot
        ),
        MotywPaletaNazwana(
            id = "rozowa",
            nazwa = "Różowa",
            // Róż + liliowy fiolet (jak klasyczne M2 pink / purple swatches)
            seed = Color(0xFFFF80AB),
            secondary = Color(0xFFCE93D8),
            tertiary = Color(0xFFF8BBD0),
            styl = PaletteStyle.Vibrant
        ),
        MotywPaletaNazwana(
            id = "fioletowa",
            nazwa = "Fioletowa",
            seed = Color(0xFFB388FF),
            secondary = Color(0xFFEA80FC),
            tertiary = Color(0xFF80D8FF),
            styl = PaletteStyle.Vibrant
        ),
        MotywPaletaNazwana(
            id = "cyjan",
            nazwa = "Cyjan",
            seed = Color(0xFF00E5FF),
            secondary = Color(0xFF448AFF),
            tertiary = Color(0xFF84FFFF),
            styl = PaletteStyle.TonalSpot
        ),
        MotywPaletaNazwana(
            id = "niebieska",
            nazwa = "Niebieska",
            seed = Color(0xFF448AFF),
            secondary = Color(0xFF40C4FF),
            tertiary = Color(0xFFB388FF),
            styl = PaletteStyle.TonalSpot
        ),
        MotywPaletaNazwana(
            id = "pomaranczowa",
            nazwa = "Pomarańczowa",
            seed = Color(0xFFFFAB40),
            secondary = Color(0xFFFF80AB),
            tertiary = Color(0xFFFFD180),
            styl = PaletteStyle.Vibrant
        ),
        // Vault-Tec / Pip-Boy — kanoniczne barwy fan-UI (żółty VT, niebieski VT, fosfor CRT)
        MotywPaletaNazwana(
            id = "fallout",
            nazwa = "Vault-Tec",
            seed = Color(0xFFF5D76E),       // Vault-Tec yellow
            secondary = Color(0xFF003E7E),  // Vault-Tec navy
            tertiary = Color(0xFF18FF6D),   // Pip-Boy phosphor green
            styl = PaletteStyle.Vibrant
        ),
    )

    fun poId(id: String?): MotywPaletaNazwana? =
        id?.let { idSzukane -> Wszystkie.firstOrNull { it.id == idSzukane } }

    fun dopasujDoSeeda(seed: Color): MotywPaletaNazwana? =
        Wszystkie.firstOrNull { it.seed.toArgb() == seed.toArgb() }
}
