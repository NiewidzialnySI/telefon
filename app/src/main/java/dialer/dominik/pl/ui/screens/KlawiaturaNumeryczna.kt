package dialer.dominik.pl.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dialer.dominik.pl.ui.components.IkonyAplikacji
import dialer.dominik.pl.ui.theme.ZielonyMorski
import dialer.dominik.pl.ui.theme.ZielonyNeon

/** Klawiatura numeryczna z przyciskami akcji: wiadomość, dzwoń, dodaj kontakt. */
@Composable
internal fun KlawiaturaNumeryczna(
    onCyfraKliknieta: (String) -> Unit,
    onZadzwon: () -> Unit,
    onWiadomosc: () -> Unit,
    onNowyKontakt: () -> Unit
) {
    val przyciski = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("*", "0", "#")
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        for (rzad in przyciski) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                for (znak in rzad) {
                    // Długie przytrzymanie „*" wpisuje „+" (jak w standardowym dialerze).
                    val klikniety = if (znak == "*") {
                        Modifier.combinedClickable(
                            onClick = { onCyfraKliknieta("*") },
                            onLongClick = { onCyfraKliknieta("+") }
                        )
                    } else {
                        Modifier.clickable { onCyfraKliknieta(znak) }
                    }

                    Box(
                        modifier = Modifier
                            .size(62.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.06f))
                            .then(klikniety),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = znak,
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(0.92f),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Wiadomość — otwiera czat z wpisanym numerem
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.07f))
                    .clickable(onClick = onWiadomosc),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = IkonyAplikacji.Wiadomosc,
                    contentDescription = "Wiadomość",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(22.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(Brush.horizontalGradient(listOf(ZielonyMorski, ZielonyNeon)))
                    .clickable(onClick = onZadzwon),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = IkonyAplikacji.TelefonLogo,
                    contentDescription = "Zadzwoń",
                    tint = Color(0xFF06231A),
                    modifier = Modifier.size(34.dp)
                )
            }

            // Dodaj kontakt — ludzik z plusem
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.07f))
                    .clickable(onClick = onNowyKontakt),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = IkonyAplikacji.DodajKontakt,
                    contentDescription = "Utwórz kontakt",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}