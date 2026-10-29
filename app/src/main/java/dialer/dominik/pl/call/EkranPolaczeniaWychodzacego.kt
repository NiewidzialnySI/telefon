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
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import kotlin.math.roundToInt

/**
 * Bezpieczny odczyt nazwy urządzenia Bluetooth. `BluetoothDevice.getName()`
 * wewnętrznie woła AdapterService.getRemoteName(), które na Androidzie 12+
 * wymaga uprawnienia BLUETOOTH_CONNECT — bez niego rzuca SecurityException
 * i crashuje ekran. Łapiemy wyjątek i zwracamy wynik dostępny bez uprawnienia.
 */
/** Dzielona w pakiecie `call`. */
internal fun bezpiecznaNazwaBluetooth(dev: android.bluetooth.BluetoothDevice): String {
    return try {
        dev.name ?: "Urządzenie Bluetooth"
    } catch (e: SecurityException) {
        // Brak uprawnienia BLUETOOTH_CONNECT — nie mamy dostępu do nazwy.
        "Urządzenie Bluetooth"
    } catch (e: Exception) {
        "Urządzenie Bluetooth"
    }
}

@Composable
internal fun EkranPolaczeniaWychodzacego(
    context: Context,
    numer: String,
    onZakoncz: () -> Unit
) {
    val stan = StanPolaczenia
    // Lokalna referencja do InCallService (może być null na starcie połączenia —
    // przyciski trasy/wyciszenia używają odpornych zAkcjamiGdyGotowa zamiast tego).
    val usluga = TelefonInCallService.instancja
    // Scope do akcji na transie/wyciszeniu — na starcie połączenia instancja usługi
    // może być jeszcze null, więc wykonujemy akcję z krótką pętlą ponawiania.
    val scope = rememberCoroutineScope()
    var zdjecieKontaktu by remember(numer) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(numer) {
        zdjecieKontaktu = WeryfikacjaNumeru.pobierzZdjecieKontaktu(context, numer)
    }

    val animacja = rememberInfiniteTransition(label = "pulsWychodzace")
    val scalePuls by animacja.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulsWychodzaceAwatar"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        // --- Awatar / Zdjęcie z poświatą ---
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .scale(scalePuls)
                    .background(
                        brush = Brush.radialGradient(
                            listOf(
                                ZielonyMorski.copy(alpha = 0.25f),
                                ZielonyMorski.copy(alpha = 0.05f),
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
                        .size(124.dp)
                        .clip(CircleShape)
                        .border(2.dp, ZielonyNeon.copy(alpha = 0.5f), CircleShape)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(124.dp)
                        .background(
                            brush = Brush.radialGradient(
                                listOf(Color(0xFF12362A), Color(0xFF081A14))
                            ),
                            shape = CircleShape
                        )
                        .border(1.5.dp, ZielonyNeon.copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = inicjaly(numer),
                        color = ZielonyNeon,
                        fontSize = 42.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // --- Nazwa / Numer ---
        Text(
            text = numer.ifBlank { "Nieznany numer" },
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        // --- Status ---
        Text(
            text = "Dzwonię…",
            color = ZielonyNeon,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.5.sp
        )

        Spacer(modifier = Modifier.weight(1f))

        // --- Klawiatura DTMF (jeśli włączona) ---
        AnimatedVisibility(visible = stan.klawiatura) {
            KlawiaturaDtmf(onCyfra = { usluga?.wyslijTon(it) })
        }

        Spacer(modifier = Modifier.height(20.dp))

        var pokazWybiorceBluetoothWych by remember { mutableStateOf(false) }

        // Prośba o uprawnienie BLUETOOTH_CONNECT (Android 12/S+). Bez niego odczyt
        // nazw urządzeń (dev.name) w oknie wyboru Bluetooth rzuca SecurityException.
        // Po udzieleniu otwieramy okno od razu.
        val launcherBluetooth = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { przyznane ->
            if (przyznane) {
                pokazWybiorceBluetoothWych = true
            } else {
                Toast.makeText(
                    context,
                    "Brak uprawnienia Bluetooth — nazwy urządzeń niedostępne",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        fun otworzWybiorceBluetooth() {
            // Na Androidzie 12+ (S/API 31) potrzebujemy uprawnienia runtime; poniżej jest nadawane
            // automatycznie, więc okno otwieramy wprost.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
            ) {
                launcherBluetooth.launch(Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                pokazWybiorceBluetoothWych = true
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            PrzyciskOpcjiZLongClick(
                ikona = IkonyAplikacji.MikrofonWylaczony,
                etykieta = "Wycisz",
                aktywny = stan.wyciszony,
                onKlik = {
                    // Optymistycznie przełącz wizualnie od razu; realnie wykonaj,
                    // gdy rozmowa będzie połączona (zAkcjamiGdyGotowa czeka na stan Rozmowa).
                    stan.wyciszony = !stan.wyciszony
                    scope.launch { zAkcjamiGdyGotowa { przelaczWyciszenie() } }
                }
            )
            PrzyciskOpcjiZLongClick(
                ikona = IkonyAplikacji.Bluetooth,
                etykieta = "Bluetooth",
                aktywny = stan.bluetoothAktywny,
                onKlik = {
                    // Optymistycznie przełącz wizualnie + zapamiętaj żądaną trasę,
                    // żeby wybór przetrwał do połączenia (przycisk nie "zgaśnie").
                    stan.bluetoothAktywny = !stan.bluetoothAktywny
                    val nowaTrasa = if (stan.bluetoothAktywny) {
                        CallAudioState.ROUTE_BLUETOOTH
                    } else {
                        CallAudioState.ROUTE_WIRED_OR_EARPIECE
                    }
                    stan.zadanaTrasa = nowaTrasa
                    scope.launch { zAkcjamiGdyGotowa { ustawTrase(nowaTrasa) } }
                },
                onDlugieKlikniecie = {
                    otworzWybiorceBluetooth()
                }
            )
            PrzyciskOpcjiZLongClick(
                ikona = IkonyAplikacji.Glosnik,
                etykieta = "Głośnik",
                aktywny = stan.glosnik,
                onKlik = {
                    // Optymistycznie przełącz wizualnie + zapamiętaj żądaną trasę,
                    // żeby wybór przetrwał do połączenia (przycisk nie "zgaśnie").
                    stan.glosnik = !stan.glosnik
                    val nowaTrasa = if (stan.glosnik) {
                        CallAudioState.ROUTE_SPEAKER
                    } else {
                        CallAudioState.ROUTE_WIRED_OR_EARPIECE
                    }
                    stan.zadanaTrasa = nowaTrasa
                    scope.launch { zAkcjamiGdyGotowa { ustawTrase(nowaTrasa) } }
                }
            )
            PrzyciskOpcjiZLongClick(
                ikona = IkonyAplikacji.Klawiatura,
                etykieta = "Klawiatura",
                aktywny = stan.klawiatura,
                onKlik = { stan.klawiatura = !stan.klawiatura }
            )
        }

        if (pokazWybiorceBluetoothWych) {
            androidx.compose.ui.window.Dialog(onDismissRequest = { pokazWybiorceBluetoothWych = false }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF14201A))
                        .border(1.dp, ZielonyNeon.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
                        .padding(20.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Wybierz urządzenie Bluetooth",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(14.dp))

                        val urzadzenia = stan.urzadzeniaBluetooth
                        if (urzadzenia.isEmpty()) {
                            Text(
                                text = "Brak sparowanych/podłączonych urządzeń audio Bluetooth",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                        } else {
                            urzadzenia.forEach { dev ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(ZielonyNeon.copy(alpha = 0.10f))
                                        .border(1.dp, ZielonyNeon.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                                        .clickable {
                                            usluga?.wybierzBluetooth(dev)
                                            Toast.makeText(context, "Wybrano: ${bezpiecznaNazwaBluetooth(dev)}", Toast.LENGTH_SHORT).show()
                                            pokazWybiorceBluetoothWych = false
                                        }
                                        .padding(14.dp)
                                        ) {
                                        Text(
                                        text = bezpiecznaNazwaBluetooth(dev),
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                        }

                        Spacer(Modifier.height(10.dp))
                        TextButton(onClick = { pokazWybiorceBluetoothWych = false }) {
                            Text("Zamknij", color = Color.White.copy(alpha = 0.6f))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // --- Przycisk zakończenia / anulowania połączenia ---
        // Gdy trwa odliczanie (ile zostało do faktycznego wybrania numeru), przycisk
        // pokazuje cyfrę 3→2→1→0. Klik w trakcie odliczania = pełne anulowanie
        // (zero dzwonienia u odbiorcy, zero wpisu w logu).
        val odliczaniePozostale = OczekiwanieNaPolaczenie.odliczanie
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(CzerwonyPolaczenia)
                .clickable {
                    // Połączenie wychodzące nieodebrane — pełne anulowanie
                    // (zero dzwonienia u odbiorcy, zero wpisu w logu).
                    rozlaczRozmowe(context, usunLog = true)
                },
            contentAlignment = Alignment.Center
        ) {
            if (odliczaniePozostale > 0) {
                Text(
                    text = odliczaniePozostale.toString(),
                    color = Color.White,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Icon(
                    imageVector = IkonyAplikacji.Sluchawka,
                    contentDescription = "Anuluj połączenie",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
