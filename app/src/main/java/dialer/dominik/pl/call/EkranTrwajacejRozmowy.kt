package dialer.dominik.pl.call

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telephony.SmsManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.layout.onSizeChanged
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first

@Composable

internal fun EkranTrwajacejRozmowy(context: Context, onZakoncz: () -> Unit) {
    val stan = StanPolaczenia
    val usluga = TelefonInCallService.instancja

    var sekundy by remember { mutableLongStateOf(0L) }
    LaunchedEffect(stan.czasStartu, stan.stan) {
        if (stan.stan == StanPolaczenia.Stan.Rozmowa && stan.czasStartu > 0L) {
            while (true) {
                sekundy = (System.currentTimeMillis() - stan.czasStartu) / 1000
                delay(1000)
            }
        }
    }

    var zdjecieKontaktu by remember(stan.numerRozmowy) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(stan.numerRozmowy) {
        zdjecieKontaktu = WeryfikacjaNumeru.pobierzZdjecieKontaktu(context, stan.numerRozmowy)
    }

    val animacja = rememberInfiniteTransition(label = "pulsRozmowy")
    val puls by animacja.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulsAwatara"
    )

    // === Minimalizacja: przeciągnięcie w dół = reszta gaśnie, awatar maleje i leci do rogu ===
        // Wszystko przez Animatable + spring() (a nie animateFloatAsState z tween(120)),
        // żeby nowy gest mógł w każdej chwili przejąć ruch i zatrzymać trwające animacje.
        val density = LocalDensity.current
        val scope = rememberCoroutineScope()

        // Przesunięcia i skala awatara (ax/ay w px względem pozycji bazowej).
        val ax = remember { Animatable(0f) }
        val ay = remember { Animatable(0f) }
        val skala = remember { Animatable(1f) }
        // Krycie reszty ekranu (tło, treść, pasek statusu).
        val krycie = remember { Animatable(1f) }

        var szerokoscPx by remember { mutableIntStateOf(0) }
        var wysokoscPx by remember { mutableIntStateOf(0) }

        val rozmiarAwatara = 124.dp
        val rozmiarDymka = 64.dp
        val skalaDymka = rozmiarDymka / rozmiarAwatara   // 64/124 ≈ 0.516f

        val sprezyna = spring<Float>(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        )

        /** Opór gumy przy przeciąganiu w dół. */
                fun gumowy(dy: Float, h: Float): Float = dy / (1f + dy / (h * 1.6f))

                /** Pozycja bazowa awatara (środek ekranu w poziomie, góra w pionie). */
                fun bazaXAwatara(): Float = if (szerokoscPx > 0) szerokoscPx / 2f else 540f
                fun bazaYAwatara(): Float = with(density) { 64.dp.toPx() + 62.dp.toPx() }   // padding + połowa awatara

                /**
                                 * Miejsce docelowe dymka (lewym górnym rogiem na: prawy brzeg + niżej niż
                                 * pasek powiadomień). celDymkaX/Y to translation względem środka awatara —
                                 * stąd odejmowanie pozycji bazowej (bazaXAwatara / bazaYAwatara).
                                 */
                                fun celDymkaX(): Float {
                                    val margines = with(density) { 32.dp.toPx() }
                                    // Dymek ma kończyć się 32dp od prawej krawędzi: chcemy aby środek awatara
                                    // (bazaXAwatara) przesunięty o ax znalazł się na (szer - margines - 32dp) od prawej.
                                    val cel = (if (szerokoscPx > 0) szerokoscPx.toFloat() else 1080f) - margines - with(density) { 32.dp.toPx() }
                                    return cel - bazaXAwatara()
                                }
                                fun celDymkaY(): Float {
                                    val gornyMargin = with(density) { 120.dp.toPx() }   // niżej niż pasek powiadomień
                                    return gornyMargin - bazaYAwatara()
                                }

        /** Ustawia dane dymka (zdjęcie/inicjały) zanim ruszy usługa. */
        fun przygotujDaneDymka() {
            DymekDane.zdjecie = zdjecieKontaktu
            DymekDane.inicjaly = inicjaly(stan.rozmowca)
        }

        suspend fun wroc() {
            coroutineScope {
                launch { ax.animateTo(0f, sprezyna) }
                launch { ay.animateTo(0f, sprezyna) }
                launch { skala.animateTo(1f, sprezyna) }
                launch { krycie.animateTo(1f, tween(200)) }
            }
        }

        suspend fun zwin() {
            coroutineScope {
                launch { ax.animateTo(celDymkaX(), sprezyna) }
                launch { ay.animateTo(celDymkaY(), sprezyna) }
                launch { skala.animateTo(skalaDymka, sprezyna) }
                launch { krycie.animateTo(0f, tween(200)) }
            }
            // Po dojściu do rogu: zwolnij dymek i zamknij ekran rozmowy.
            przygotujDaneDymka()
            DymekRozmowyService.start(context)
        }

        // Powrót z dymka: awatar startuje z rogu i leci na środek sprężyną.
        LaunchedEffect(Unit) {
            if (StanPolaczenia.wracaZDymka) {
                StanPolaczenia.wracaZDymka = false
                // Czekaj, aż rozmiar ekranu będzie znany (szerokoscPx > 0).
                snapshotFlow { szerokoscPx }.first { it > 0 }
                ax.snapTo(celDymkaX())
                ay.snapTo(celDymkaY())
                skala.snapTo(skalaDymka)
                krycie.snapTo(1f)
                launch { ax.animateTo(0f, sprezyna) }
                launch { ay.animateTo(0f, sprezyna) }
                launch { skala.animateTo(1f, sprezyna) }
            }
        }

    Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged {
                    if (it.height > 0) wysokoscPx = it.height
                    if (it.width > 0) szerokoscPx = it.width
                }
                .pointerInput(Unit) {
                    var dy = 0f
                    detectVerticalDragGestures(
                        onDragStart = {
                            dy = 0f
                            scope.launch {
                                ax.stop(); ay.stop(); skala.stop(); krycie.stop()
                            }
                        },
                        onVerticalDrag = { change, delta ->
                            change.consume()
                            dy = (dy + delta).coerceAtLeast(0f)
                            val h = (if (wysokoscPx > 0) wysokoscPx else 2400) * 0.6f
                            val visY = gumowy(dy, h)
                            val p = (visY / h).coerceIn(0f, 1f)
                            scope.launch {
                                ay.snapTo(visY)
                                skala.snapTo(1f - (1f - skalaDymka) * p)
                                krycie.snapTo(1f - p)
                            }
                        },
                        onDragEnd = {
                            val h = (if (wysokoscPx > 0) wysokoscPx else 2400) * 0.6f
                            val p = (gumowy(dy, h) / h).coerceIn(0f, 1f)
                            scope.launch {
                                if (p >= 0.5f) zwin() else wroc()
                            }
                        },
                        onDragCancel = {
                            scope.launch { wroc() }
                        }
                    )
                }
        ) {
            // Tło — gaśnie z krycie, żeby prześwitywała aplikacja pod spodem.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = krycie.value }
                    .background(CzarnyAmoled)
            ) {
                dialer.dominik.pl.ui.components.TloAmoled()
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(horizontal = 24.dp)
                    .padding(top = 40.dp, bottom = 32.dp)
                    .graphicsLayer { alpha = krycie.value },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Miejsce na awatar nad tytułem: awatar zaczyna się na 64dp (padding), ma 124dp,
                                // więc kończy się na 188dp. Column ma padding top=40 — Spacer 176dp daje
                                // tytuł na 40+176=216dp = 28dp pod awatarem (jak na ekranie wychodzącym).
                                Spacer(modifier = Modifier.height(176.dp))

                Text(
                                    text = stan.rozmowca.ifBlank { "Nieznany numer" },
                                    color = Color.White,
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                text = when (stan.stan) {
                    StanPolaczenia.Stan.Dzwoni -> "Dzwonię…"
                    StanPolaczenia.Stan.Rozmowa -> formatujCzas(sekundy)
                    StanPolaczenia.Stan.Zawieszone -> "Zawieszone"
                    StanPolaczenia.Stan.Rozlaczone -> "Zakończono"
                    StanPolaczenia.Stan.Brak -> ""
                },
                color = when (stan.stan) {
                    StanPolaczenia.Stan.Rozmowa -> ZielonyNeon
                    else -> Color.White.copy(alpha = 0.55f)
                },
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.2.sp
            )

            Spacer(modifier = Modifier.weight(1f))

            OutlinedTextField(
                value = stan.notatkaRozmowy,
                onValueChange = { nowa ->
                    stan.notatkaRozmowy = nowa
                    if (stan.numerRozmowy.isNotBlank()) {
                        NotatkiRozmow.zapiszNotatke(context, stan.numerRozmowy, nowa)
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                placeholder = { Text("Notatka do tej rozmowy…", color = Color.White.copy(alpha = 0.35f), fontSize = 14.sp) },
                singleLine = false,
                minLines = 1,
                maxLines = 3,
                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ZielonyNeon.copy(alpha = 0.6f),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                    focusedContainerColor = Color.White.copy(alpha = 0.04f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.04f),
                    cursorColor = ZielonyNeon
                ),
                shape = RoundedCornerShape(14.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            AnimatedVisibility(visible = stan.klawiatura) {
                KlawiaturaDtmf(onCyfra = { usluga?.wyslijTon(it) })
            }

            var pokazWybiorceBluetooth by remember { mutableStateOf(false) }
            val launcherBluetooth = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { przyznane ->
                if (przyznane) pokazWybiorceBluetooth = true
                else Toast.makeText(context, "Brak uprawnienia Bluetooth — nazwy urządzeń niedostępne", Toast.LENGTH_SHORT).show()
            }
            fun otworzWybiorce() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
                ) {
                    launcherBluetooth.launch(Manifest.permission.BLUETOOTH_CONNECT)
                } else {
                    pokazWybiorceBluetooth = true
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                PrzyciskOpcjiZLongClick(ikona = IkonyAplikacji.MikrofonWylaczony, etykieta = "Wycisz", aktywny = stan.wyciszony, onKlik = { usluga?.przelaczWyciszenie() })
                PrzyciskOpcjiZLongClick(
                    ikona = IkonyAplikacji.Bluetooth, etykieta = "Bluetooth", aktywny = stan.bluetoothAktywny,
                    onKlik = {
                        val nowaTrasa = if (stan.bluetoothAktywny) CallAudioState.ROUTE_WIRED_OR_EARPIECE else CallAudioState.ROUTE_BLUETOOTH
                        usluga?.ustawTrase(nowaTrasa)
                    },
                    onDlugieKlikniecie = { otworzWybiorce() }
                )
                PrzyciskOpcjiZLongClick(
                    ikona = IkonyAplikacji.Glosnik, etykieta = "Glosnik", aktywny = stan.glosnik,
                    onKlik = {
                        val nowaTrasa = if (stan.glosnik) CallAudioState.ROUTE_WIRED_OR_EARPIECE else CallAudioState.ROUTE_SPEAKER
                        usluga?.ustawTrase(nowaTrasa)
                    }
                )
                PrzyciskOpcjiZLongClick(ikona = IkonyAplikacji.Klawiatura, etykieta = "Klawiatura", aktywny = stan.klawiatura, onKlik = { stan.klawiatura = !stan.klawiatura })
            }

            if (pokazWybiorceBluetooth) {
                androidx.compose.ui.window.Dialog(onDismissRequest = { pokazWybiorceBluetooth = false }) {
                    Box(
                        modifier = Modifier.fillMaxWidth(0.9f).clip(RoundedCornerShape(24.dp))
                            .background(Color(0xFF14201A))
                            .border(1.dp, ZielonyNeon.copy(alpha = 0.3f), RoundedCornerShape(24.dp)).padding(20.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Wybierz urządzenie Bluetooth", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(14.dp))
                            val urzadzenia = stan.urzadzeniaBluetooth
                            if (urzadzenia.isEmpty()) {
                                Text("Brak sparowanych/podłączonych urządzeń audio Bluetooth", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, textAlign = TextAlign.Center)
                            } else {
                                urzadzenia.forEach { dev ->
                                    Box(
                                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                            .background(ZielonyNeon.copy(alpha = 0.10f))
                                            .border(1.dp, ZielonyNeon.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                                            .clickable {
                                                usluga?.wybierzBluetooth(dev)
                                                Toast.makeText(context, "Wybrano: ${bezpiecznaNazwaBluetooth(dev)}", Toast.LENGTH_SHORT).show()
                                                pokazWybiorceBluetooth = false
                                            }.padding(14.dp)
                                    ) {
                                        Text(text = bezpiecznaNazwaBluetooth(dev), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    }
                                    Spacer(Modifier.height(8.dp))
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            TextButton(onClick = { pokazWybiorceBluetooth = false }) { Text("Zamknij", color = Color.White.copy(alpha = 0.6f)) }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Box(
                modifier = Modifier.size(72.dp).clip(CircleShape).background(CzerwonyPolaczenia)
                    .clickable { rozlaczRozmowe(context, usunLog = false) },
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = IkonyAplikacji.Sluchawka, contentDescription = "Zakończ połączenie", tint = Color.White, modifier = Modifier.size(32.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Awatar — osobną warstwą NAD resztą: wycentrowany na górze (nad tytułem).
                // Przesuwa się ax/ay, maleje skala i leci do prawego górnego rogu (dymka).
                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .padding(top = 64.dp)
                                        .size(124.dp)
                        .graphicsLayer {
                            translationX = ax.value
                            translationY = ay.value
                            scaleX = skala.value
                            scaleY = skala.value
                        }
                ) {
                    Box(
                        modifier = Modifier.size(200.dp).scale(puls).background(
                            brush = Brush.radialGradient(listOf(ZielonyMorski.copy(alpha = 0.22f), ZielonyMorski.copy(alpha = 0.06f), Color.Transparent)),
                            shape = CircleShape
                        )
                    )
                    if (zdjecieKontaktu != null) {
                        Image(
                            bitmap = zdjecieKontaktu!!.asImageBitmap(),
                            contentDescription = "Zdjęcie kontaktu",
                            modifier = Modifier.size(124.dp).clip(CircleShape).border(2.dp, ZielonyNeon.copy(alpha = 0.5f), CircleShape)
                        )
                    } else {
                        Box(
                            modifier = Modifier.size(124.dp).background(
                                brush = Brush.radialGradient(listOf(Color(0xFF12362A), Color(0xFF081A14))), shape = CircleShape
                            ).border(1.dp, ZielonyNeon.copy(alpha = 0.25f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = inicjaly(stan.rozmowca), color = ZielonyNeon, fontSize = 42.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                        }
                    }
                }
    }
}

                                                                        @Composable
                                                                        internal fun PrzyciskOpcjiZLongClick(
                                        ikona: androidx.compose.ui.graphics.vector.ImageVector,
    etykieta: String,
    aktywny: Boolean,
    onKlik: () -> Unit,
    onDlugieKlikniecie: (() -> Unit)? = null
) {
    val tlo by animateColorAsState(
        targetValue = if (aktywny) ZielonyNeon else Color.White.copy(alpha = 0.08f),
        animationSpec = tween(220),
        label = "tloOpcji"
    )
    val kolorIkony by animateColorAsState(
        targetValue = if (aktywny) Color(0xFF06231A) else Color.White,
        animationSpec = tween(220),
        label = "kolorIkony"
    )

    // Po długim przytrzymaniu Compose może dodać kliknięcie (onClick) też po puszczeniu.
    // Flaga blokuje to „kłamiące" kliknięcie, żeby long-press nie zadziałał też jak klik.
    val handler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }
    var czyDlugieKlikniecie by remember { mutableStateOf(false) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(tlo)
                .combinedClickable(
                    onClick = {
                        if (czyDlugieKlikniecie) {
                            // To kliknięcie „dogorywa" po długim przytrzymaniu — ignorujemy.
                            czyDlugieKlikniecie = false
                        } else {
                            onKlik()
                        }
                    },
                    onLongClick = {
                        czyDlugieKlikniecie = true
                        // Ochrona: wygaś flagę po chwili, gdyby gest nie zakończył się kliknięciem
                        // (np. użytkownik zboczył z przycisku) — żeby nie zignorować następnego kliknięcia.
                        handler.removeCallbacksAndMessages(null)
                        handler.postDelayed({ czyDlugieKlikniecie = false }, 800)
                        if (onDlugieKlikniecie != null) {
                            onDlugieKlikniecie()
                        } else {
                            onKlik()
                        }
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = ikona,
                contentDescription = etykieta,
                tint = kolorIkony,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

/** Klawiatura DTMF do wybierania tonów w trakcie rozmowy. */
