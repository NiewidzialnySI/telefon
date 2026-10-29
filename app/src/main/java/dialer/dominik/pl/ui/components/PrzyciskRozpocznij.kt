package dialer.dominik.pl.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.vector.ImageVector.Builder
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import dialer.dominik.pl.ui.theme.ZielonyMorski
import dialer.dominik.pl.ui.theme.ZielonyNeon
import dialer.dominik.pl.ui.theme.ZielonyGleboki

/**
 * Duży przycisk "Rozpocznij" — pełna szerokość, wysokość 64 dp (wygodny dla kciuka),
 * gradient zieleni marki, miękka poświata pod spodem i animacja wciśnięcia (scale).
 * Wygląda jak nowoczesne CTA w aplikacjach typu Revolut / Monzo / Spotify.
 */
@Composable
fun PrzyciskRozpocznij(
    tekst: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val zrodloInterakcji = remember { MutableInteractionSource() }
    val wcisniety by zrodloInterakcji.collectIsPressedAsState()

    // Wciśnięcie: lekkie zmniejszenie, sprężyste odbicie po puszczeniu
    val skala by animateFloatAsState(
        targetValue = if (wcisniety) 0.972f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "skalaPrzycisku"
    )

    val ksztalt = RoundedCornerShape(22.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .scale(skala)
            .shadow(
                elevation = if (wcisniety) 6.dp else 18.dp,
                shape = ksztalt,
                ambientColor = ZielonyMorski.copy(alpha = 0.55f),
                spotColor = ZielonyMorski.copy(alpha = 0.65f)
            )
            .clip(ksztalt)
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(ZielonyMorski, ZielonyNeon)
                )
            )
            .clickable(
                interactionSource = zrodloInterakcji,
                indication = androidx.compose.material3.ripple(
                    color = Color.Black.copy(alpha = 0.18f)
                ),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = tekst,
                color = Color(0xFF06231A),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.2.sp
            )
            Spacer(modifier = Modifier.width(10.dp))
            Icon(
                imageVector = IkonaStrzalka,
                contentDescription = null,
                tint = Color(0xFF06231A),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/**
 * Wtórny przycisk konturowy — cieńszy, do mniej ważnych akcji.
 */
@Composable
fun PrzyciskKonturowy(
    tekst: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val ksztalt = RoundedCornerShape(18.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(ksztalt)
            .background(Color.White.copy(alpha = 0.06f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.OutlinedButton(
            onClick = onClick,
            shape = ksztalt,
            border = BorderStroke(1.dp, ZielonyNeon.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth().height(54.dp)
        ) {
            Text(text = tekst, color = ZielonyNeon, fontSize = 15.sp)
        }
    }
}

/** Ikona strzałki "dalej" rysowana wektorowo, żeby nie dodawać zależności ikon. */
private val IkonaStrzalka: ImageVector by lazy {
    Builder(
        name = "StrzalkaDalej",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = androidx.compose.ui.graphics.SolidColor(Color.Black)) {
            moveTo(12f, 4f)
            lineTo(10.59f, 5.41f)
            lineTo(16.17f, 11f)
            lineTo(4f, 11f)
            lineTo(4f, 13f)
            lineTo(16.17f, 13f)
            lineTo(10.59f, 18.59f)
            lineTo(12f, 20f)
            lineTo(20f, 12f)
            close()
        }
    }.build()
}
