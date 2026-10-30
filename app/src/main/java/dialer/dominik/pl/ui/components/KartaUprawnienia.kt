package dialer.dominik.pl.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dialer.dominik.pl.ui.theme.ZielonyMorski
import dialer.dominik.pl.ui.theme.NaPrzycisku
import dialer.dominik.pl.ui.theme.ZielonyNeon

/** Kształty wspólne dla karty uprawnienia — spójne z ekranem powitalnym. */
private val KsztaltKarty = RoundedCornerShape(28.dp)

/**
 * Karta jednego uprawnienia w stylu ekranu powitalnego:
 * ciemne „szkło" na czerni, cienka zielona obwódka, okrągła ikona w środku,
 * tytuł, opis i duży gradientowy przycisk CTA.
 */
@Composable
fun KartaUprawnieniaAmoled(
    ikona: androidx.compose.ui.graphics.vector.ImageVector,
    tytul: String,
    etykietaPrzycisku: String,
    modifier: Modifier = Modifier,
    opis: String? = null,
    onKlik: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(KsztaltKarty)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF16211C),
                        Color(0xFF0C1410)
                    )
                )
            )
            .border(
                border = BorderStroke(1.dp, ZielonyNeon.copy(alpha = 0.16f)),
                shape = KsztaltKarty
            )
            .padding(horizontal = 24.dp, vertical = 32.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    ZielonyMorski.copy(alpha = 0.22f),
                                    ZielonyMorski.copy(alpha = 0.04f),
                                    Color.Transparent
                                )
                            ),
                            shape = CircleShape
                        )
                )
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF1B3B2E),
                                    Color(0xFF0F241B)
                                )
                            ),
                            shape = CircleShape
                        )
                        .border(1.dp, ZielonyMorski.copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = ikona,
                        contentDescription = null,
                        tint = ZielonyNeon,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            Text(
                text = tytul,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )

            if (!opis.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = opis,
                    color = Color.White.copy(alpha = 0.58f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            PrzyciskGlowny(
                tekst = etykietaPrzycisku,
                onClick = onKlik
            )
        }
    }
}

/**
 * Przycisk główny — ta sama forma co „Rozpocznij" na ekranie powitalnym,
 * ale w wersji 56 dp, żeby zmieścił się w karcie.
 */
@Composable
fun PrzyciskGlowny(
    tekst: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val zrodlo = remember { MutableInteractionSource() }
    val wcisniety by zrodlo.collectIsPressedAsState()
    val skala by animateFloatAsState(
        targetValue = if (wcisniety) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "skalaPrzyciskuGlownego"
    )
    val ksztalt = RoundedCornerShape(20.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .scale(skala)
            .clip(ksztalt)
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(ZielonyMorski, ZielonyNeon)
                )
            )
            .clickable(
                interactionSource = zrodlo,
                indication = androidx.compose.material3.ripple(
                    color = Color.Black.copy(alpha = 0.18f)
                ),
                onClick = onClick
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = tekst,
            color = NaPrzycisku,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = IkonyAplikacji.Strzalka,
            contentDescription = null,
            tint = NaPrzycisku,
            modifier = Modifier.size(18.dp)
        )
    }
}
