package dialer.dominik.pl.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dialer.dominik.pl.R
import dialer.dominik.pl.ui.components.LogoAnimowane
import dialer.dominik.pl.ui.components.PrzyciskRozpocznij
import dialer.dominik.pl.ui.components.TloAmoled
import kotlinx.coroutines.delay

private const val OPOZNIENIE_PRZYCISKU_MS = 340L

/** Ekran powitalny — tylko logo i „Rozpocznij”. */
@Composable
fun WelcomeScreen(onStart: () -> Unit) {
    var startLogo by remember { mutableStateOf(false) }
    var pokazPrzycisk by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        startLogo = true
        delay(OPOZNIENIE_PRZYCISKU_MS)
        pokazPrzycisk = true
    }

    val wejscieLogo by animateFloatAsState(
        targetValue = if (startLogo) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "wejscieLogo"
    )
    val alfaPrzycisku by animateFloatAsState(
        targetValue = if (pokazPrzycisk) 1f else 0f,
        animationSpec = tween(durationMillis = 560),
        label = "wejsciePrzycisku"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        TloAmoled()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 26.dp)
                .padding(bottom = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.weight(1f))

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val bok = minOf(maxWidth * 0.55f, 220.dp)
                LogoAnimowane(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(bok)
                        .alpha(wejscieLogo)
                        .scale(0.84f + 0.16f * wejscieLogo),
                    wejscieAnimacji = wejscieLogo
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            PrzyciskRozpocznij(
                tekst = stringResource(R.string.welcome_start),
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(alfaPrzycisku)
                    .scale(0.95f + 0.05f * alfaPrzycisku),
                onClick = onStart
            )
        }
    }
}
