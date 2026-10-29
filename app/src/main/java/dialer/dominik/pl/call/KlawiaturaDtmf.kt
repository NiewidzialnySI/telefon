package dialer.dominik.pl.call

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dialer.dominik.pl.ui.theme.ZielonyNeon

@Composable
internal fun KlawiaturaDtmf(onCyfra: (Char) -> Unit) {
    val bufor = StanPolaczenia.buforDtmf
    val rzedy = listOf("123", "456", "789", "*0#")

    Column(
        modifier = Modifier.fillMaxWidth(0.9f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = bufor.ifBlank { " " },
            color = if (bufor.isBlank()) Color.Transparent else ZielonyNeon,
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 2.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(2.dp))
        for (rzad in rzedy) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                for (znak in rzad) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                            .clickable { onCyfra(znak) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = znak.toString(), color = Color.White, fontSize = 22.sp)
                    }
                }
            }
        }
    }
}

internal fun inicjaly(tekst: String): String {
    val czysty = tekst.trim()
    if (czysty.isEmpty()) return "?"
    if (czysty.first().isDigit()) return czysty.takeLast(2)
    return czysty.split(" ").take(2).mapNotNull { it.firstOrNull()?.uppercase() }.joinToString("")
}

internal fun formatujCzas(sekundy: Long): String {
    val h = sekundy / 3600
    val m = (sekundy % 3600) / 60
    val s = sekundy % 60
    return if (h > 0) {
        String.format("%d:%02d:%02d", h, m, s)
    } else {
        String.format("%02d:%02d", m, s)
    }
}

/**
 * Bezpieczny odczyt nazwy urządzenia Bluetooth. `BluetoothDevice.getName()`
 * na Androidzie 12+ wymaga BLUETOOTH_CONNECT — bez niego rzuca SecurityException.
 */
internal fun bezpiecznaNazwaBluetooth(dev: android.bluetooth.BluetoothDevice): String {
    return try {
        dev.name ?: "Urządzenie Bluetooth"
    } catch (_: SecurityException) {
        "Urządzenie Bluetooth"
    } catch (_: Exception) {
        "Urządzenie Bluetooth"
    }
}
