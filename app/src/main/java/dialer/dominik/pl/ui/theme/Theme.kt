package dialer.dominik.pl.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.materialkolor.DynamicMaterialTheme
import dialer.dominik.pl.ui.components.ZapamietajPrzejscieMotywu

private val KsztaltyAplikacji = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/**
 * Motyw aplikacji — seed + jasny/ciemny + zaawansowane z [MotywStan].
 * Przejście jasny↔ciemny: circular reveal ze zrzutu (bez animate całej palety).
 */
@Composable
fun TelefonTheme(
    content: @Composable () -> Unit
) {
    val kontekst = LocalContext.current
    MotywStan.zapewnijWczytane(kontekst)
    val stan by MotywStan.stan.collectAsState()

    val ciemnyDocelowy = when (stan.tryb) {
        TrybMotywu.Jasny -> false
        TrybMotywu.Ciemny -> true
        TrybMotywu.System -> isSystemInDarkTheme()
    }

    val styl = remember(stan.stylPalety) { PaletteStyleName.doMaterial(stan.stylPalety) }

    ZapamietajPrzejscieMotywu(ciemny = ciemnyDocelowy) { ciemny ->
        val view = LocalView.current
        if (!view.isInEditMode) {
            SideEffect {
                val window = (view.context as? Activity)?.window ?: return@SideEffect
                @Suppress("DEPRECATION")
                window.statusBarColor = Color.Transparent.toArgb()
                @Suppress("DEPRECATION")
                window.navigationBarColor = Color.Transparent.toArgb()
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !ciemny
                    isAppearanceLightNavigationBars = !ciemny
                }
            }
        }

        DynamicMaterialTheme(
            seedColor = stan.seed,
            useDarkTheme = ciemny,
            withAmoled = ciemny,
            style = styl,
            secondary = stan.secondary,
            tertiary = stan.tertiary,
            // animate=true + pełny overlay = przycinanie; przejście robi circular reveal
            animate = false,
            shapes = KsztaltyAplikacji,
            typography = Typography,
            content = {
                val scheme = MaterialTheme.colorScheme
                val ekran = LocalEkranMotywu.current
                val paleta = remember(scheme, stan, ciemny, ekran) {
                    MotywPaleta.zSchematu(scheme, stan, ciemny, ekran)
                }
                MotywAktualny.ustaw(paleta)
                CompositionLocalProvider(LocalMotyw provides paleta) {
                    Box(Modifier.fillMaxSize()) {
                        content()
                    }
                }
            }
        )
    }
}

/** Nadpisania Fazy 3 dla konkretnego ekranu. */
@Composable
fun MotywEkranu(ekran: EkranMotywu, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalEkranMotywu provides ekran) {
        val stan by MotywStan.stan.collectAsState()
        val scheme = MaterialTheme.colorScheme
        val ciemny = when (stan.tryb) {
            TrybMotywu.Jasny -> false
            TrybMotywu.Ciemny -> true
            TrybMotywu.System -> isSystemInDarkTheme()
        }
        val paleta = remember(scheme, stan, ciemny, ekran) {
            MotywPaleta.zSchematu(scheme, stan, ciemny, ekran)
        }
        CompositionLocalProvider(LocalMotyw provides paleta) {
            content()
        }
    }
}
