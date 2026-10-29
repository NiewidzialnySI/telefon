package dialer.dominik.pl.call

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.telecom.Call
import android.telecom.CallAudioState
import android.telephony.SmsManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dialer.dominik.pl.ui.components.IkonyAplikacji
import dialer.dominik.pl.ui.screens.rozlaczRozmowe
import dialer.dominik.pl.ui.theme.CzarnyAmoled
import dialer.dominik.pl.ui.theme.CzerwonyPolaczenia
import dialer.dominik.pl.ui.theme.ZielonyGleboki
import dialer.dominik.pl.ui.theme.ZielonyMorski
import dialer.dominik.pl.ui.theme.ZielonyNeon
import kotlinx.coroutines.delay
import kotlin.math.roundToInt


/**
 * Ekran połączenia przychodzącego:
 * - Ukryty napis "Połączenie przychodzące..." oraz zbędne opisy.
 * - Mały, stylowy kafelek "SPAM" zamiast wielkiego czerwonego baneru.
 * - Opinie z internetu pokazywane tylko jeśli rzeczywiście istnieją.
 * - Przycisk SMS rozwijany płynną animacją z góry z krzyżykiem do zamknięcia.
 * - Slajder bez pomocniczego tekstu, z czystą ikoną.
 */
@Composable
internal fun EkranPolaczeniaPrzychodzacego(
    context: Context,
    numer: String,
    wynikAnalizy: WynikWeryfikacjiNumeru?,
    onOdbierz: () -> Unit,
    onOdrzuc: () -> Unit,
    onScisz: () -> Unit
) {
    var zdjecieKontaktu by remember(numer) { mutableStateOf<Bitmap?>(null) }
    var pokazRozwinieteSms by remember { mutableStateOf(false) }

    LaunchedEffect(numer) {
        zdjecieKontaktu = WeryfikacjaNumeru.pobierzZdjecieKontaktu(context, numer)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))

        // --- Awatar / Zdjęcie kontaktu ---
        Box(contentAlignment = Alignment.Center) {
            val animacja = rememberInfiniteTransition(label = "pulsPrzychodzace")
            val scalePuls by animacja.animateFloat(
                initialValue = 0.95f,
                targetValue = 1.05f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1200, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "scalePuls"
            )

            Box(
                modifier = Modifier
                    .size(130.dp)
                    .scale(scalePuls)
                    .background(
                        brush = Brush.radialGradient(
                            listOf(
                                ZielonyNeon.copy(alpha = 0.20f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
            )

            if (zdjecieKontaktu != null) {
                Image(
                    bitmap = zdjecieKontaktu!!.asImageBitmap(),
                    contentDescription = "Zdjęcie kontaktu",
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .border(2.dp, ZielonyNeon.copy(alpha = 0.6f), CircleShape)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.linearGradient(
                                listOf(ZielonyMorski.copy(alpha = 0.35f), ZielonyGleboki)
                            )
                        )
                        .border(1.5.dp, ZielonyNeon.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = inicjaly(numer),
                        color = ZielonyNeon,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        // --- Numer / Nazwa (Bez napisu "Połączenie przychodzące...") ---
        Text(
            text = numer.ifBlank { "Nieznany numer" },
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        // --- Mały kafelek / badge SPAM (jeśli wykryto zagrożenie) ---
        if (wynikAnalizy?.poziomZagrozenia == PoziomZagrozenia.SPAM_OSZUSTWO) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(CzerwonyPolaczenia.copy(alpha = 0.2f))
                    .border(1.dp, CzerwonyPolaczenia.copy(alpha = 0.4f), RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "SPAM",
                    color = CzerwonyPolaczenia,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
            }
            Spacer(Modifier.height(10.dp))
        }

        // --- Opinie z internetu (Pokazuj TYLKO jeśli istnieją faktyczne opinie społeczności) ---
        val prawdziweOpinie = wynikAnalizy?.opinie?.filter { 
            !it.tresc.contains("Numer nie figuruje", ignoreCase = true) && 
            !it.tresc.contains("brak negatywnych", ignoreCase = true) 
        } ?: emptyList()

        if (pravdizweOpinieNotEmpty(pravdizweOpinie(pravdizweOpinie = prawdziweOpinie))) {
            Text(
                text = "OPINIE Z INTERNETU:",
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier
                    .align(Alignment.Start)
                    .padding(bottom = 4.dp, top = 4.dp)
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.04f))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(prawdziweOpinie) { opinia ->
                    KartaOpiniiUzytkownika(opinia)
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // --- Panel rozwijany szybkiej wiadomości SMS z animacją ---
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

        // --- Przyciski: "Wyślij wiadomość" + "Ścisz" (chowane gdy panel SMS otwarty) ---
        AnimatedVisibility(
            visible = !pokazRozwinieteSms,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(100))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                        .clickable { pokazRozwinieteSms = true }
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = IkonyAplikacji.Wiadomosc,
                            contentDescription = null,
                            tint = ZielonyNeon,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Wiadomość",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                        .clickable(onClick = onScisz)
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = IkonyAplikacji.Wciszenie,
                            contentDescription = null,
                            tint = ZielonyNeon,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Ścisz",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // --- Slajder odbierania (bez pomocniczych napisów, czysta ikona) ---
        SlajderOdbieraniaPolaczenia(
            onOdbierz = onOdbierz,
            onOdrzuc = onOdrzuc
        )

        Spacer(Modifier.height(10.dp))
    }
}

private fun pravdizweOpinie(pravdizweOpinie: List<OpiniaUzytkownika>): List<OpiniaUzytkownika> = pravdizweOpinie
private fun pravdizweOpinieNotEmpty(list: List<OpiniaUzytkownika>): Boolean = list.isNotEmpty()

/**
 * Karta pojedynczej opinii z internetu.
 */
@Composable
private fun KartaOpiniiUzytkownika(opinia: OpiniaUzytkownika) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF14201A))
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${opinia.autor} · ${opinia.data}",
                color = ZielonyNeon.copy(alpha = 0.8f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = opinia.typOceny,
                color = if (opinia.typOceny.contains("Spam", ignoreCase = true)) CzerwonyPolaczenia else ZielonyNeon,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = opinia.tresc,
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 11.sp,
            lineHeight = 14.sp
        )
    }
}

/**
 * Panel rozwijany szybkiej wiadomości SMS z przyciskiem zamykania (krzyżykiem) po prawej u góry.
 */
@Composable
private fun PanelRozwinietySms(
    context: Context,
    numer: String,
    onZamknij: () -> Unit,
    onWyslane: () -> Unit
) {
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
            .background(Color(0xFF14201A))
            .border(1.dp, ZielonyNeon.copy(alpha = 0.3f), RoundedCornerShape(22.dp))
            .padding(16.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Nagłówek z krzyżykiem zamknięcia po prawej
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Szybka odpowiedź SMS",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable(onClick = onZamknij),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "✕", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(10.dp))

            szablony.forEach { tresc ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(ZielonyNeon.copy(alpha = 0.10f))
                        .border(1.dp, ZielonyNeon.copy(alpha = 0.20f), RoundedCornerShape(10.dp))
                        .clickable {
                            wyslijSmsIReaguj(context, numer, tresc)
                            onWyslane()
                        }
                        .padding(10.dp)
                ) {
                    Text(text = tresc, color = Color.White, fontSize = 12.sp)
                }
                Spacer(Modifier.height(6.dp))
            }

            Spacer(Modifier.height(4.dp))

            OutlinedTextField(
                value = wlasnyTekst,
                onValueChange = { wlasnyTekst = it },
                placeholder = { Text("Wpisz własną wiadomość…", fontSize = 11.sp) },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF0F1813),
                    unfocusedContainerColor = Color(0xFF0F1813),
                    focusedBorderColor = ZielonyNeon,
                    unfocusedBorderColor = ZielonyNeon.copy(alpha = 0.3f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onZamknij) {
                    Text("Anuluj", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
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
                    Text("Wyślij i odrzuć", color = ZielonyNeon, fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
        smsManager.sendTextMessage(numer, null, tresc, null, null)
        Toast.makeText(context, "Wysłano SMS", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Nie udało się wysłać SMS", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Slajder odbierania połączenia (bez pomocniczych napisów, z czystymi ikonami).
 */
@Composable
private fun SlajderOdbieraniaPolaczenia(
    onOdbierz: () -> Unit,
    onOdrzuc: () -> Unit
) {
    val density = LocalDensity.current
    val maxDragPx = remember { with(density) { 120.dp.toPx() } }

    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    val postep = (dragOffsetX / maxDragPx).coerceIn(-1f, 1f)

    val kolorTlaPrzyciku by animateColorAsState(
        targetValue = when {
            postep < -0.2f -> ZielonyNeon
            postep > 0.2f -> CzerwonyPolaczenia
            else -> Color(0xFF384740)
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
                        ZielonyNeon.copy(alpha = 0.20f),
                        Color(0xFF16221C),
                        CzerwonyPolaczenia.copy(alpha = 0.20f)
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(34.dp)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(dragOffsetX.roundToInt(), 0) }
                .size(58.dp)
                .clip(CircleShape)
                .background(kolorTlaPrzyciku)
                .border(1.5.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            // Odczytujemy BEZPOŚREDNIO aktualny stan dragOffsetX (przez
                            // delegowany getter), a nie przeliczony wcześniej `postep`.
                            // `postep` byłby wartością zamrożoną w chwili pierwszego
                            // rysunku (pointerInput(Unit) nie restartuje lambd),
                            // więc puszczenie zawsze widziałoby 0 i nic by nie robiło.
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
                tint = if (postep == 0f) Color.White else Color.Black,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

/**
 * Ekran trwającej rozmowy (aktywne połączenie).
 */
