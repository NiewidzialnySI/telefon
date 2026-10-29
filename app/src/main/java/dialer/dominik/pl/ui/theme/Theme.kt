package dialer.dominik.pl.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

// Paleta zielono-czarna — gotowa na zmianę motywu przez użytkownika w przyszłości

// Schemat jasny
private val JasneKolory = lightColorScheme(
    primary = ZielonyGleboki,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB9F6CA),
    onPrimaryContainer = ZielonyGleboki,
    secondary = ZielonySredni,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC8E6C9),
    onSecondaryContainer = ZielonyGleboki,
    tertiary = ZielonyJasny,
    onTertiary = CzarnyGleboki,
    background = BialyCzysty,
    onBackground = CzarnyGleboki,
    surface = BialyCzysty,
    onSurface = CzarnyGleboki,
    surfaceVariant = Color(0xFFE0EDE4),
    onSurfaceVariant = Color(0xFF3F4A44),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF2F6F3),
    surfaceContainer = Color(0xFFEBF1ED),
    surfaceContainerHigh = Color(0xFFE5ECE7),
    surfaceContainerHighest = Color(0xFFDFE7E1),
    outline = Color(0xFF6F7B74),
    error = Color(0xFFBA1A1A)
)

// Schemat ciemny
private val CiemneKolory = darkColorScheme(
    primary = ZielonySredni,
    onPrimary = Color.White,
    primaryContainer = ZielonyGleboki,
    onPrimaryContainer = Color(0xFFB9F6CA),
    secondary = ZielonyJasny,
    onSecondary = CzarnyGleboki,
    secondaryContainer = Color(0xFF1F5A44),
    onSecondaryContainer = Color(0xFFC8F7DA),
    tertiary = ZielonyJasny,
    onTertiary = CzarnyGleboki,
    background = CzarnyGleboki,
    onBackground = Color.White,
    surface = CzarnyGleboki,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF2B3B33),
    onSurfaceVariant = Color(0xFFC2D1C8),
    surfaceContainerLowest = Color(0xFF0B0B0B),
    surfaceContainerLow = Color(0xFF1A1C1B),
    surfaceContainer = Color(0xFF1E2220),
    surfaceContainerHigh = Color(0xFF272D2A),
    surfaceContainerHighest = Color(0xFF313833),
    outline = Color(0xFF8A9891),
    error = Color(0xFFFFB4AB)
)

private val KsztaltyAplikacji = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/**
 * Motyw aplikacji — wszystko po polsku.
 * Tryb jasny/ciemny wybierany automatycznie wg ustawień urządzenia.
 * Na Androidzie 12+ tło i powierzchnie pochodzą z tapety (dynamicColor),
 * ale kolor główny zawsze pochodzi z palety marki.
 */
@Composable
fun TelefonTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val kontekst = LocalContext.current
    val schemat = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val systemowy = if (darkTheme) dynamicDarkColorScheme(kontekst)
            else dynamicLightColorScheme(kontekst)
            systemowy.copy(
                primary = if (darkTheme) ZielonySredni else ZielonyGleboki,
                onPrimary = Color.White
            )
        }
        darkTheme -> CiemneKolory
        else -> JasneKolory
    }
    MaterialTheme(
        colorScheme = schemat,
        typography = Typography,
        shapes = KsztaltyAplikacji,
        content = content
    )
}
