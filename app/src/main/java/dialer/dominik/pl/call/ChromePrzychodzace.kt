package dialer.dominik.pl.call

import android.content.Context
import android.telephony.SmsManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dialer.dominik.pl.ui.components.IkonyAplikacji
import dialer.dominik.pl.ui.theme.CzerwonyPolaczenia
import kotlin.math.roundToInt

/**
 * Dolny chrome fazy Incoming: badge spamu, opinie, SMS, Ścisz, slajder odbierz/odrzuć.
 */
@Composable
internal fun ColumnScope.ChromePrzychodzace(
    context: Context,
    numer: String,
    wynikAnalizy: WynikWeryfikacjiNumeru?,
    onOdbierz: () -> Unit,
    onOdrzuc: () -> Unit,
    onScisz: () -> Unit,
    onWynikZmieniony: (WynikWeryfikacjiNumeru) -> Unit = {}
) {
    var pokazRozwinieteSms by remember { mutableStateOf(false) }

    if (wynikAnalizy != null) {
        KartaReputacjiNumeru(
            context = context,
            numer = numer,
            wynik = wynikAnalizy,
            kompakt = false,
            onWynikZmieniony = onWynikZmieniony
        )
        Spacer(Modifier.height(10.dp))
    }

    Spacer(Modifier.weight(1f))

    AnimatedVisibility(
        visible = pokazRozwinieteSms,
        enter = fadeIn(tween(200)) + expandVertically(tween(250)) + scaleIn(initialScale = 0.95f),
        exit = fadeOut(tween(150)) + shrinkVertically(tween(200)) + scaleOut(targetScale = 0.95f)
    ) {
        PanelRozwinietySms(
            context = context,
            numer = numer,
            onZamknij = { pokazRozwinieteSms = false },
            onWyslane = onOdrzuc
        )
    }

    AnimatedVisibility(
        visible = !pokazRozwinieteSms,
        enter = fadeIn(tween(150)),
        exit = fadeOut(tween(100))
    ) {
        val paletaAkcje = dialer.dominik.pl.ui.theme.LocalMotyw.current
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(paletaAkcje.naTle.copy(alpha = 0.08f))
                    .border(1.dp, paletaAkcje.naTle.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                    .clickable { pokazRozwinieteSms = true }
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = IkonyAplikacji.Wiadomosc,
                        contentDescription = null,
                        tint = paletaAkcje.accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Wiadomość",
                        color = paletaAkcje.naTle,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(paletaAkcje.naTle.copy(alpha = 0.08f))
                    .border(1.dp, paletaAkcje.naTle.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                    .clickable(onClick = onScisz)
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = IkonyAplikacji.Wciszenie,
                        contentDescription = "Ścisz dzwonek",
                        tint = paletaAkcje.accent,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Ścisz",
                        color = paletaAkcje.naTle,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }

    Spacer(Modifier.height(20.dp))
    SlajderOdbieraniaPolaczenia(onOdbierz = onOdbierz, onOdrzuc = onOdrzuc)
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun PanelRozwinietySms(
    context: Context,
    numer: String,
    onZamknij: () -> Unit,
    onWyslane: () -> Unit
) {
    val paleta = dialer.dominik.pl.ui.theme.LocalMotyw.current
    val szablony = listOf(
        "Nie mogę teraz rozmawiać, oddzwonię później.",
        "Jestem w pracy, proszę napisać wiadomość SMS.",
        "Jadę samochodem, odezwę się za chwilę."
    )
    var wlasnyTekst by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(paleta.kafelek)
            .border(1.dp, paleta.accent.copy(alpha = 0.3f), RoundedCornerShape(22.dp))
            .padding(16.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Szybka odpowiedź SMS",
                    color = paleta.naTle,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(paleta.naTle.copy(alpha = 0.08f))
                        .clickable(onClick = onZamknij),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "✕", color = paleta.naTle, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(10.dp))

            szablony.forEach { tresc ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(paleta.accent.copy(alpha = 0.10f))
                        .border(1.dp, paleta.accent.copy(alpha = 0.20f), RoundedCornerShape(10.dp))
                        .clickable {
                            wyslijSmsIReaguj(context, numer, tresc)
                            onWyslane()
                        }
                        .padding(10.dp)
                ) {
                    Text(text = tresc, color = paleta.naTle, fontSize = 12.sp)
                }
                Spacer(Modifier.height(6.dp))
            }

            Spacer(Modifier.height(4.dp))

            OutlinedTextField(
                value = wlasnyTekst,
                onValueChange = { wlasnyTekst = it },
                placeholder = {
                    Text(
                        "Wpisz własną wiadomość…",
                        fontSize = 11.sp,
                        color = paleta.tekstDrugorzedny
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = paleta.tlo.copy(alpha = if (paleta.ciemny) 0.55f else 0.35f),
                    unfocusedContainerColor = paleta.tlo.copy(alpha = if (paleta.ciemny) 0.45f else 0.25f),
                    focusedBorderColor = paleta.accent,
                    unfocusedBorderColor = paleta.accent.copy(alpha = 0.3f),
                    focusedTextColor = paleta.naTle,
                    unfocusedTextColor = paleta.naTle
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onZamknij) {
                    Text("Anuluj", color = paleta.naTle.copy(alpha = 0.6f), fontSize = 12.sp)
                }
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = {
                        if (wlasnyTekst.isNotBlank()) {
                            wyslijSmsIReaguj(context, numer, wlasnyTekst)
                            onWyslane()
                        }
                    }
                ) {
                    Text("Wyślij i odrzuć", color = paleta.accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

private fun wyslijSmsIReaguj(context: Context, numer: String, tresc: String) {
    try {
        val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
        val czesci = smsManager.divideMessage(tresc)
        if (czesci.isNullOrEmpty()) {
            Toast.makeText(context, "Nie udało się wysłać SMS", Toast.LENGTH_SHORT).show()
            return
        }
        if (czesci.size == 1) {
            smsManager.sendTextMessage(numer, null, czesci[0], null, null)
        } else {
            smsManager.sendMultipartTextMessage(numer, null, czesci, null, null)
        }
        Toast.makeText(context, "Wysłano SMS", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Nie udało się wysłać SMS", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun SlajderOdbieraniaPolaczenia(
    onOdbierz: () -> Unit,
    onOdrzuc: () -> Unit
) {
    val paleta = dialer.dominik.pl.ui.theme.LocalMotyw.current
    val density = LocalDensity.current
    val maxDragPx = remember { with(density) { 120.dp.toPx() } }

    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    val postep = (dragOffsetX / maxDragPx).coerceIn(-1f, 1f)

    val kolorTlaPrzyciku by animateColorAsState(
        targetValue = when {
            postep < -0.2f -> paleta.przycisk
            postep > 0.2f -> CzerwonyPolaczenia
            else -> paleta.naTle.copy(alpha = if (paleta.ciemny) 0.22f else 0.18f)
        },
        label = "kolorTlaSlajdera"
    )

    val ikonaPrzycisku = when {
        postep < -0.2f -> IkonyAplikacji.TelefonLogo
        postep > 0.2f -> IkonyAplikacji.Sluchawka
        else -> IkonyAplikacji.TelefonLogo
    }

    Box(
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .height(68.dp)
            .clip(RoundedCornerShape(34.dp))
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        paleta.accent.copy(alpha = 0.20f),
                        paleta.kafelek,
                        CzerwonyPolaczenia.copy(alpha = 0.20f)
                    )
                )
            )
            .border(1.dp, paleta.naTle.copy(alpha = 0.15f), RoundedCornerShape(34.dp)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(dragOffsetX.roundToInt(), 0) }
                .size(58.dp)
                .clip(CircleShape)
                .background(kolorTlaPrzyciku)
                .border(1.5.dp, paleta.naTle.copy(alpha = 0.5f), CircleShape)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (dragOffsetX <= -0.6f * maxDragPx) {
                                onOdbierz()
                            } else if (dragOffsetX >= 0.6f * maxDragPx) {
                                onOdrzuc()
                            }
                            dragOffsetX = 0f
                        },
                        onDragCancel = {
                            dragOffsetX = 0f
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            dragOffsetX = (dragOffsetX + dragAmount).coerceIn(-maxDragPx, maxDragPx)
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = ikonaPrzycisku,
                contentDescription = null,
                tint = when {
                    postep < -0.2f -> paleta.naPrzycisku
                    postep > 0.2f -> Color.White
                    else -> paleta.naTle
                },
                modifier = Modifier.size(28.dp)
            )
        }
    }
}
