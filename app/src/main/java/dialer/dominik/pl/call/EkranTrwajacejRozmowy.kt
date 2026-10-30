package dialer.dominik.pl.call

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.telecom.CallAudioState
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dialer.dominik.pl.ui.components.IkonyAplikacji
import dialer.dominik.pl.ui.screens.rozlaczRozmowe
import dialer.dominik.pl.ui.theme.CzarnyAmoled
import dialer.dominik.pl.ui.theme.CzerwonyPolaczenia
import dialer.dominik.pl.ui.theme.ZielonyMorski
import dialer.dominik.pl.ui.theme.NaPrzycisku
import dialer.dominik.pl.ui.theme.ZielonyNeon
import dialer.dominik.pl.ui.theme.tloMokrejKrwi
import androidx.compose.ui.graphics.Color as ComposeColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class FazaRozmowy {
    Incoming,
    Dialing,
    Active
}

/**
 * Jedyny ekran rozmowy: stały shell (awatar + nazwa + status),
 * zmienia się tylko dolny chrome wg fazy Incoming / Dialing / Active.
 */
@Composable
internal fun EkranTrwajacejRozmowy(
    context: Context,
    onZakoncz: () -> Unit,
    wynikAnalizy: WynikWeryfikacjiNumeru? = null,
    onWynikZmieniony: (WynikWeryfikacjiNumeru) -> Unit = {}
) {
    val stan = StanPolaczenia
    val usluga = TelefonInCallService.instancja
    val scope = rememberCoroutineScope()

    val faza = when {
        stan.czyPrzychodzace && stan.stan == StanPolaczenia.Stan.Dzwoni -> FazaRozmowy.Incoming
        !stan.czyPrzychodzace && stan.stan == StanPolaczenia.Stan.Dzwoni -> FazaRozmowy.Dialing
        else -> FazaRozmowy.Active
    }

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
        zdjecieKontaktu = withContext(Dispatchers.IO) {
            val full = WeryfikacjaNumeru.pobierzZdjecieKontaktu(context, stan.numerRozmowy)
                ?: return@withContext null
            skalujBitmapDoMax(full, 256)
        }
    }

    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val rozmiarAwatara = 124.dp
    val rozmiarDymka = 64.dp
    val skalaDymka = rozmiarDymka / rozmiarAwatara

    // Start Animatable OD RAZU w pozycji dymka — inaczej 1. klatka = awatar na środku
    // (miganie). Wzorzec floating bubble: jedna animacja tylko w Compose.
    val wracaZDymkaInit = StanPolaczenia.wracaZDymka
    val aproksSzer = with(density) { config.screenWidthDp.dp.toPx() }
    val bazaYInit = with(density) { 64.dp.toPx() + 62.dp.toPx() }
    val startAxInit = remember {
        when {
            wracaZDymkaInit && DymekDane.maPowrot -> DymekDane.powrotCx - aproksSzer / 2f
            wracaZDymkaInit -> {
                val margines = with(density) { 32.dp.toPx() }
                (aproksSzer - margines * 2f) - aproksSzer / 2f
            }
            else -> 0f
        }
    }
    val startAyInit = remember {
        when {
            wracaZDymkaInit && DymekDane.maPowrot -> DymekDane.powrotCy - bazaYInit
            wracaZDymkaInit -> with(density) { 120.dp.toPx() } - bazaYInit
            else -> 0f
        }
    }
    val ax = remember { Animatable(startAxInit) }
    val ay = remember { Animatable(startAyInit) }
    val skala = remember { Animatable(if (wracaZDymkaInit) skalaDymka else 1f) }
    val krycie = remember { Animatable(if (wracaZDymkaInit) 0f else 1f) }

    var szerokoscPx by remember { mutableIntStateOf(0) }
    var wysokoscPx by remember { mutableIntStateOf(0) }

    val sprezyna = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    fun gumowy(dy: Float, h: Float): Float = dy / (1f + dy / (h * 1.6f))
    fun bazaXAwatara(): Float = if (szerokoscPx > 0) szerokoscPx / 2f else aproksSzer / 2f
    fun bazaYAwatara(): Float = bazaYInit

    fun celDymkaX(): Float {
        val margines = with(density) { 32.dp.toPx() }
        val cel = (if (szerokoscPx > 0) szerokoscPx.toFloat() else aproksSzer) - margines - margines
        return cel - bazaXAwatara()
    }

    fun celDymkaY(): Float {
        val gornyMargin = with(density) { 120.dp.toPx() }
        return gornyMargin - bazaYAwatara()
    }

    fun przygotujDaneDymka() {
        DymekDane.zdjecie = zdjecieKontaktu?.let { skalujBitmapDoMax(it, 128) }
        DymekDane.inicjaly = inicjaly(stan.rozmowca)
    }

    // Live wartości podczas przeciągania — bez scope.launch na każdą klatkę.
    var trzymaMinimize by remember { mutableStateOf(false) }
    var liveAy by remember { mutableFloatStateOf(0f) }
    var liveSkala by remember { mutableFloatStateOf(1f) }
    var liveKrycie by remember { mutableFloatStateOf(1f) }
    val ayDraw = if (trzymaMinimize) liveAy else ay.value
    val skalaDraw = if (trzymaMinimize) liveSkala else skala.value
    val krycieDraw = if (trzymaMinimize) liveKrycie else krycie.value

    val pulsAktywny = !trzymaMinimize && (
        faza == FazaRozmowy.Dialing ||
            (faza == FazaRozmowy.Active && stan.stan == StanPolaczenia.Stan.Rozmowa)
        )
    val puls = PulsHaloAwatara(pulsAktywny)

    suspend fun wroc() {
        coroutineScope {
            launch { ax.animateTo(0f, sprezyna) }
            launch { ay.animateTo(0f, sprezyna) }
            launch { skala.animateTo(1f, sprezyna) }
            launch { krycie.animateTo(1f, tween(200)) }
        }
    }

    suspend fun zwin() {
        przygotujDaneDymka()
        val rozmiarDymkaPx = with(density) { 64.dp.roundToPx() }
        val sx = (bazaXAwatara() + ax.value - rozmiarDymkaPx / 2f).toInt()
        val sy = (bazaYAwatara() + ay.value - rozmiarDymkaPx / 2f).toInt()
        krycie.snapTo(0f)
        DymekRozmowyService.startPozycja(context, sx, sy)
        EkranRozmowy.zamknij(context)
    }

    LaunchedEffect(Unit) {
        if (!StanPolaczenia.wracaZDymka && !wracaZDymkaInit) return@LaunchedEffect
        StanPolaczenia.wracaZDymka = false
        // Doprecyzuj pozycję po realnym pomiarze layoutu (bez „skoku” z 0,0).
        snapshotFlow { szerokoscPx }.first { it > 0 }
        if (DymekDane.maPowrot) {
            val dokladneAx = DymekDane.powrotCx - bazaXAwatara()
            val dokladneAy = DymekDane.powrotCy - bazaYAwatara()
            DymekDane.maPowrot = false
            // Tylko drobna korekta jeśli aproksymacja ≠ pomiar — bez snap z zera.
            if (kotlin.math.abs(dokladneAx - ax.value) > 2f) ax.snapTo(dokladneAx)
            if (kotlin.math.abs(dokladneAy - ay.value) > 2f) ay.snapTo(dokladneAy)
        }
        launch { ax.animateTo(0f, sprezyna) }
        launch { ay.animateTo(0f, sprezyna) }
        launch { skala.animateTo(1f, sprezyna) }
        launch { krycie.animateTo(1f, tween(280)) }
    }

    val statusText = when (faza) {
        FazaRozmowy.Incoming -> "Połączenie przychodzące"
        FazaRozmowy.Dialing -> "Dzwonię…"
        FazaRozmowy.Active -> when (stan.stan) {
            StanPolaczenia.Stan.Rozmowa -> formatujCzas(sekundy)
            StanPolaczenia.Stan.Zawieszone -> "Zawieszone"
            StanPolaczenia.Stan.Rozlaczone -> "Zakończono"
            else -> ""
        }
    }
    val statusKolor = when {
        faza == FazaRozmowy.Active && stan.stan == StanPolaczenia.Stan.Rozmowa -> ZielonyNeon
        faza == FazaRozmowy.Dialing -> ZielonyNeon
        else -> dialer.dominik.pl.ui.theme.LocalMotyw.current.naTle.copy(alpha = 0.55f)
    }

    val gestMinimalizacji = faza == FazaRozmowy.Active

    val wysokoscEkranuDp = LocalConfiguration.current.screenHeightDp
    // Na niższych ekranach mniej miejsca pod awatarem — kontrolki bliżej dołu.
    val spacerAwatara = when {
        wysokoscEkranuDp < 700 -> 120.dp
        wysokoscEkranuDp < 800 -> 148.dp
        else -> 168.dp
    }
    val paddingGora = if (wysokoscEkranuDp < 700) 16.dp else 28.dp
    val paddingDol = if (wysokoscEkranuDp < 700) 8.dp else 12.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged {
                if (it.height > 0) wysokoscPx = it.height
                if (it.width > 0) szerokoscPx = it.width
            }
            .then(
                if (gestMinimalizacji) {
                    Modifier.pointerInput(Unit) {
                        var dy = 0f
                        detectVerticalDragGestures(
                            onDragStart = {
                                dy = 0f
                                trzymaMinimize = true
                                liveAy = ay.value
                                liveSkala = skala.value
                                liveKrycie = krycie.value
                                scope.launch {
                                    ax.stop(); ay.stop(); skala.stop(); krycie.stop()
                                }
                            },
                            onVerticalDrag = { change, delta ->
                                change.consume()
                                dy = (dy + delta).coerceAtLeast(0f)
                                // Max przeciągnięcie ≈ połowa ekranu — awatar idzie za palcem.
                                val h = (if (wysokoscPx > 0) wysokoscPx else 2400) * 0.5f
                                val visY = gumowy(dy, h).coerceAtMost(h)
                                val p = (visY / h).coerceIn(0f, 1f)
                                liveAy = visY
                                liveSkala = 1f - (1f - skalaDymka) * p
                                liveKrycie = 1f - p
                            },
                            onDragEnd = {
                                // Do połowy ekranu (p>=0.5) = handoff do dymka; poniżej = wróć.
                                val h = (if (wysokoscPx > 0) wysokoscPx else 2400) * 0.5f
                                val p = (gumowy(dy, h) / h).coerceIn(0f, 1f)
                                scope.launch {
                                    ay.snapTo(liveAy)
                                    skala.snapTo(liveSkala)
                                    krycie.snapTo(liveKrycie)
                                    trzymaMinimize = false
                                    if (p >= 0.5f) zwin() else wroc()
                                }
                            },
                            onDragCancel = {
                                scope.launch {
                                    ay.snapTo(liveAy)
                                    skala.snapTo(liveSkala)
                                    krycie.snapTo(liveKrycie)
                                    trzymaMinimize = false
                                    wroc()
                                }
                            }
                        )
                    }
                } else {
                    Modifier
                }
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = krycieDraw }
                .background(CzarnyAmoled)
        ) {
            dialer.dominik.pl.ui.components.TloAmoled()
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 24.dp)
                .padding(top = paddingGora, bottom = paddingDol)
                .graphicsLayer { alpha = krycieDraw },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(spacerAwatara))

            Text(
                text = stan.rozmowca.ifBlank { "Nieznany numer" },
                color = dialer.dominik.pl.ui.theme.LocalMotyw.current.naTle,
                fontSize = if (wysokoscEkranuDp < 700) 22.sp else 26.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (faza != FazaRozmowy.Incoming && wynikAnalizy != null) {
                KartaReputacjiNumeru(
                    context = context,
                    numer = stan.numerRozmowy.ifBlank { stan.rozmowca },
                    wynik = wynikAnalizy,
                    kompakt = true,
                    onWynikZmieniony = onWynikZmieniony
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            AnimatedContent(
                targetState = statusText,
                transitionSpec = {
                    fadeIn(tween(180)) togetherWith fadeOut(tween(120))
                },
                label = "statusRozmowy"
            ) { tekst ->
                Text(
                    text = tekst,
                    color = statusKolor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.2.sp
                )
            }

            AnimatedContent(
                targetState = faza,
                transitionSpec = {
                    fadeIn(tween(200)) togetherWith fadeOut(tween(150))
                },
                label = "chromeRozmowy",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { biezacaFaza ->
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    when (biezacaFaza) {
                        FazaRozmowy.Incoming -> {
                            ChromePrzychodzace(
                                context = context,
                                numer = stan.numerRozmowy.ifBlank { stan.rozmowca },
                                wynikAnalizy = wynikAnalizy,
                                onOdbierz = {
                                    ZarzadcaDzwonka.zatrzymaj()
                                    stan.polaczenie?.answer(0)
                                },
                                onOdrzuc = {
                                    ZarzadcaDzwonka.zatrzymaj()
                                    // REJECTED (decline), nie disconnect→BUSY u dzwoniącego.
                                    odrzucPrzychodzace(stan.polaczenie)
                                    onZakoncz()
                                },
                                onScisz = { ZarzadcaDzwonka.scisz() },
                                onWynikZmieniony = onWynikZmieniony
                            )
                        }
                        FazaRozmowy.Dialing, FazaRozmowy.Active -> {
                            if (biezacaFaza == FazaRozmowy.Active && !stan.klawiatura) {
                                PrzyciskNagrywaniaRozmowy(context = context)
                                Spacer(modifier = Modifier.height(10.dp))
                                OutlinedTextField(
                                    value = stan.notatkaRozmowy,
                                    onValueChange = { nowa ->
                                        // Tylko w pamięci sesji — trwały zapis po zakończeniu
                                        // rozmowy przypina notatkę do wpisu CallLog.
                                        stan.notatkaRozmowy = nowa
                                    },
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                                    placeholder = {
                                        Text(
                                            "Notatka do tej rozmowy…",
                                            color = dialer.dominik.pl.ui.theme.LocalMotyw.current.naTle.copy(alpha = 0.35f),
                                            fontSize = 14.sp
                                        )
                                    },
                                    singleLine = false,
                                    minLines = 1,
                                    maxLines = 2,
                                    textStyle = androidx.compose.ui.text.TextStyle(
                                        color = dialer.dominik.pl.ui.theme.LocalMotyw.current.naTle,
                                        fontSize = 14.sp
                                    ),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = ZielonyNeon.copy(alpha = 0.6f),
                                        unfocusedBorderColor = dialer.dominik.pl.ui.theme.LocalMotyw.current.naTle.copy(alpha = 0.15f),
                                        focusedContainerColor = dialer.dominik.pl.ui.theme.LocalMotyw.current.naTle.copy(alpha = 0.04f),
                                        unfocusedContainerColor = dialer.dominik.pl.ui.theme.LocalMotyw.current.naTle.copy(alpha = 0.04f),
                                        cursorColor = ZielonyNeon
                                    ),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                            }

                            PasekKontrolekRozmowy(
                                context = context,
                                fazaDialing = biezacaFaza == FazaRozmowy.Dialing
                            )
                        }
                    }
                }
            }
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 64.dp)
                .size(124.dp)
                .graphicsLayer {
                    translationX = ax.value
                    translationY = ayDraw
                    scaleX = skalaDraw
                    scaleY = skalaDraw
                }
        ) {
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .scale(puls)
                    .background(
                        brush = Brush.radialGradient(
                            listOf(
                                ZielonyMorski.copy(alpha = 0.22f),
                                ZielonyMorski.copy(alpha = 0.06f),
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
                                listOf(
                                    ZielonyMorski.copy(alpha = 0.55f),
                                    ZielonyMorski.copy(alpha = 0.18f),
                                    Color.Black.copy(alpha = 0.55f)
                                )
                            ),
                            shape = CircleShape
                        )
                        .border(1.dp, ZielonyNeon.copy(alpha = 0.45f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = inicjaly(stan.rozmowca),
                        color = ZielonyNeon,
                        fontSize = 42.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

/**
 * Wspólny pasek wycisz / BT / głośnik / DTMF + rozłącz.
 * Dialing: optymistyczne przełączanie + countdown i usunLog=true.
 * Active: bezpośrednie akcje na usłudze, usunLog=false.
 */
@Composable
private fun PasekKontrolekRozmowy(
    context: Context,
    fazaDialing: Boolean
) {
    val stan = StanPolaczenia
    val usluga = TelefonInCallService.instancja
    val scope = rememberCoroutineScope()

    AnimatedVisibility(
        visible = stan.klawiatura,
        enter = slideInVertically(tween(200, easing = FastOutSlowInEasing)) { it / 4 } + fadeIn(tween(180)),
        exit = slideOutVertically(tween(160, easing = FastOutSlowInEasing)) { it / 4 } + fadeOut(tween(140))
    ) {
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

    Spacer(modifier = Modifier.height(if (stan.klawiatura) 12.dp else 0.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        PrzyciskOpcjiZLongClick(
            ikona = IkonyAplikacji.MikrofonWylaczony,
            etykieta = "Wycisz",
            aktywny = stan.wyciszony,
            onKlik = {
                if (fazaDialing) {
                    // Optymistycznie w UI; realne setMuted przy ACTIVE (zastosujWyboryPoOdebraniu).
                    stan.wyciszony = !stan.wyciszony
                } else {
                    usluga?.przelaczWyciszenie()
                }
            }
        )
        PrzyciskOpcjiZLongClick(
            ikona = IkonyAplikacji.Bluetooth,
            etykieta = "Bluetooth",
            aktywny = stan.bluetoothAktywny,
            onKlik = {
                val wlacz = !stan.bluetoothAktywny
                stan.bluetoothAktywny = wlacz
                if (wlacz) stan.glosnik = false
                val trasa = if (wlacz) {
                    CallAudioState.ROUTE_BLUETOOTH
                } else {
                    CallAudioState.ROUTE_WIRED_OR_EARPIECE
                }
                stan.zadanaTrasa = trasa
                if (!fazaDialing) usluga?.ustawTrase(trasa)
            },
            onDlugieKlikniecie = { otworzWybiorce() }
        )
        PrzyciskOpcjiZLongClick(
            ikona = IkonyAplikacji.Glosnik,
            etykieta = "Głośnik",
            aktywny = stan.glosnik,
            onKlik = {
                val wlacz = !stan.glosnik
                stan.glosnik = wlacz
                if (wlacz) stan.bluetoothAktywny = false
                val trasa = if (wlacz) {
                    CallAudioState.ROUTE_SPEAKER
                } else {
                    CallAudioState.ROUTE_WIRED_OR_EARPIECE
                }
                stan.zadanaTrasa = trasa
                if (!fazaDialing) usluga?.ustawTrase(trasa)
            }
        )
        PrzyciskOpcjiZLongClick(
            ikona = IkonyAplikacji.Klawiatura,
            etykieta = "Klawiatura",
            aktywny = stan.klawiatura,
            onKlik = { stan.klawiatura = !stan.klawiatura }
        )
    }

    if (pokazWybiorceBluetooth) {
        val paletaBt = dialer.dominik.pl.ui.theme.LocalMotyw.current
        androidx.compose.ui.window.Dialog(onDismissRequest = { pokazWybiorceBluetooth = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(paletaBt.kafelek)
                    .border(1.dp, paletaBt.kafelekRamka, RoundedCornerShape(24.dp))
                    .padding(20.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Wybierz urządzenie Bluetooth",
                        color = paletaBt.naTle,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(14.dp))
                    val urzadzenia = stan.urzadzeniaBluetooth
                    if (urzadzenia.isEmpty()) {
                        Text(
                            "Brak sparowanych/podłączonych urządzeń audio Bluetooth",
                            color = paletaBt.tekstDrugorzedny,
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
                                        Toast.makeText(
                                            context,
                                            "Wybrano: ${bezpiecznaNazwaBluetooth(dev)}",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        pokazWybiorceBluetooth = false
                                    }
                                    .padding(14.dp)
                            ) {
                                Text(
                                    text = bezpiecznaNazwaBluetooth(dev),
                                    color = paletaBt.naTle,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    TextButton(onClick = { pokazWybiorceBluetooth = false }) {
                        Text("Zamknij", color = paletaBt.tekstDrugorzedny)
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    val odliczaniePozostale = if (fazaDialing) OczekiwanieNaPolaczenie.odliczanie else 0

    Box(
        modifier = Modifier
            .size(68.dp)
            .clip(CircleShape)
            .tloMokrejKrwi(CircleShape)
            .clickable {
                rozlaczRozmowe(context, usunLog = fazaDialing)
            },
        contentAlignment = Alignment.Center
    ) {
        if (odliczaniePozostale > 0) {
            Text(
                text = odliczaniePozostale.toString(),
                color = ComposeColor(0xFFFFF5F3),
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold
            )
        } else {
            Icon(
                imageVector = IkonyAplikacji.Sluchawka,
                contentDescription = if (fazaDialing) "Anuluj połączenie" else "Zakończ połączenie",
                tint = ComposeColor(0xFFFFF5F3),
                modifier = Modifier.size(30.dp)
            )
        }
    }

    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
private fun PrzyciskNagrywaniaRozmowy(context: android.content.Context) {
    val nagrywa = dialer.dominik.pl.nagranie.NagrywanieKontroler.nagrywa
    PrzyciskOpcjiZLongClick(
        ikona = IkonyAplikacji.Nagrywanie,
        etykieta = "Nagrywaj",
        aktywny = nagrywa,
        onKlik = {
            val ctrl = dialer.dominik.pl.nagranie.NagrywanieKontroler
            if (ctrl.nagrywa) {
                ctrl.stop()
            } else {
                dialer.dominik.pl.nagranie.ShizukuKlient.dolacz(context)
                dialer.dominik.pl.nagranie.ShizukuKlient.odswiez()
                if (!dialer.dominik.pl.nagranie.NagrywanieUstawienia.czyZgodaPrawna(context) ||
                    !dialer.dominik.pl.nagranie.NagrywanieUstawienia.czyWlaczone(context)
                ) {
                    Toast.makeText(
                        context,
                        "Włącz nagrywanie w Ustawieniach",
                        Toast.LENGTH_LONG
                    ).show()
                    return@PrzyciskOpcjiZLongClick
                }
                ctrl.start(context, StanPolaczenia.numerRozmowy)
            }
        }
    )
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
        targetValue = if (aktywny) ZielonyNeon else dialer.dominik.pl.ui.theme.LocalMotyw.current.naTle.copy(alpha = 0.08f),
        animationSpec = tween(220),
        label = "tloOpcji"
    )
    val kolorIkony by animateColorAsState(
        targetValue = if (aktywny) NaPrzycisku else dialer.dominik.pl.ui.theme.LocalMotyw.current.naTle,
        animationSpec = tween(220),
        label = "kolorIkony"
    )

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
                            czyDlugieKlikniecie = false
                        } else {
                            onKlik()
                        }
                    },
                    onLongClick = {
                        czyDlugieKlikniecie = true
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

@Composable
private fun PulsHaloAwatara(aktywny: Boolean): Float {
    if (!aktywny) return 1f
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
    return puls
}

/** Skaluje bitmapę do max. boku (np. awatar / dymek) — mniej pamięci niż pełne zdjęcie kontaktu. */
private fun skalujBitmapDoMax(bmp: Bitmap, maxBok: Int): Bitmap {
    val skala = minOf(1f, maxBok.toFloat() / maxOf(bmp.width, bmp.height).coerceAtLeast(1))
    if (skala >= 1f) return bmp
    val w = (bmp.width * skala).toInt().coerceAtLeast(1)
    val h = (bmp.height * skala).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(bmp, w, h, true)
}
