package dialer.dominik.pl.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaPlayer
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dialer.dominik.pl.call.WeryfikacjaNumeru
import dialer.dominik.pl.nagranie.NagrywaniePliki
import dialer.dominik.pl.nagranie.TranskrypcjaForegroundService
import dialer.dominik.pl.nagranie.TranskrypcjaRozmow
import dialer.dominik.pl.ui.components.IkonyAplikacji
import dialer.dominik.pl.ui.theme.CzerwonyPolaczenia
import dialer.dominik.pl.ui.theme.LocalMotyw
import dialer.dominik.pl.ui.theme.ZielonyNeon
import dialer.dominik.pl.ui.theme.tloSrebrnyPolysk
import dialer.dominik.pl.ui.theme.czyMotywWiedzmin
import dialer.dominik.pl.ui.theme.motywAkcent
import dialer.dominik.pl.ui.theme.gradientKafelka
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

private data class PozycjaNagrania(
    val plik: File,
    val numer: String,
    val nazwaKontaktu: String?,
    val data: String,
    val rozmiarKb: Long,
    val transkrypcja: String?,
) {
    val tytul: String get() = nazwaKontaktu?.takeIf { it.isNotBlank() } ?: numer
    val inicial: String
        get() = tytul.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
}

/**
 * Zakładka nagrań — ten sam język UI co skrzynka SMS / kontakty:
 * gradient kafelków, swipe usuń, animateItem, kompaktowe akcje.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EkranNagran(context: Context) {
    val paleta = LocalMotyw.current
    val scope = rememberCoroutineScope()
    val stanTr by TranskrypcjaRozmow.stan.collectAsState()
    val listState = rememberLazyListState()
    val lifecycleOwner = LocalLifecycleOwner.current

    var lista by remember { mutableStateOf(emptyList<PozycjaNagrania>()) }
    var odtwarzany: File? by remember { mutableStateOf(null) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var rozwiniety: String? by remember { mutableStateOf(null) }
    var pozycjaMs by remember { mutableIntStateOf(0) }
    var dlugoscMs by remember { mutableIntStateOf(0) }
    var szuka by remember { mutableStateOf(false) }
    var suwak by remember { mutableFloatStateOf(0f) }

    fun odswiez() {
        scope.launch(Dispatchers.IO) {
            val nowa = zaladuj(context)
            withContext(Dispatchers.Main) { lista = nowa }
        }
    }

    fun stopPlay() {
        runCatching { player?.stop(); player?.release() }
        player = null
        odtwarzany = null
        pozycjaMs = 0
        dlugoscMs = 0
        suwak = 0f
        szuka = false
    }

    LaunchedEffect(Unit) { odswiez() }

    // Odśwież listę gdy tło skończy (nawet jeśli użytkownik został na ekranie).
    LaunchedEffect(stanTr) {
        when (stanTr) {
            is TranskrypcjaRozmow.Stan.Gotowe -> odswiez()
            is TranskrypcjaRozmow.Stan.Blad -> {
                Toast.makeText(
                    context,
                    (stanTr as TranskrypcjaRozmow.Stan.Blad).komunikat,
                    Toast.LENGTH_LONG,
                ).show()
            }
            else -> Unit
        }
    }

    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) odswiez()
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(obs)
            stopPlay()
        }
    }

    LaunchedEffect(player, odtwarzany) {
        val p = player ?: return@LaunchedEffect
        while (isActive && player === p) {
            if (!szuka) {
                pozycjaMs = runCatching { p.currentPosition }.getOrDefault(0)
                dlugoscMs = runCatching { p.duration }.getOrDefault(0).coerceAtLeast(0)
                suwak = pozycjaMs.toFloat()
            }
            delay(200)
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "NAGRANIA",
                        color = Color.White.copy(alpha = 0.42f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 2.2.sp
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { pad ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = 28.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = "status_tr") {
                    AnimatedVisibility(
                        visible = stanTr !is TranskrypcjaRozmow.Stan.Bezczynny &&
                            stanTr !is TranskrypcjaRozmow.Stan.Gotowe,
                        enter = fadeIn(tween(180)) + expandVertically(tween(220)),
                        exit = fadeOut(tween(140)) + shrinkVertically(tween(180))
                    ) {
                        PasekStatusuTranskrypcji(stanTr)
                    }
                }

                items(lista, key = { it.plik.absolutePath }) { poz ->
                    val gra = odtwarzany == poz.plik
                    KartaNagrania(
                        poz = poz,
                        gra = gra,
                        rozwinięty = rozwiniety == poz.plik.absolutePath,
                        suwak = if (gra) suwak else 0f,
                        dlugoscMs = if (gra) dlugoscMs else 0,
                        pozycjaMs = if (gra) pozycjaMs else 0,
                        onPlay = {
                            if (odtwarzany == poz.plik) {
                                stopPlay()
                            } else {
                                stopPlay()
                                runCatching {
                                    MediaPlayer().apply {
                                        setDataSource(poz.plik.absolutePath)
                                        setOnCompletionListener { stopPlay() }
                                        prepare()
                                        start()
                                        player = this
                                        odtwarzany = poz.plik
                                        dlugoscMs = duration.coerceAtLeast(0)
                                        pozycjaMs = 0
                                        suwak = 0f
                                    }
                                }.onFailure {
                                    Toast.makeText(context, "Nie można odtworzyć", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onSuwak = { v ->
                            szuka = true
                            suwak = v
                            pozycjaMs = v.toInt()
                        },
                        onSuwakKoniec = {
                            runCatching { player?.seekTo(suwak.toInt()) }
                            szuka = false
                        },
                        onTranskrybuj = {
                            if (TranskrypcjaForegroundService.czyTrwa() ||
                                stanTr !is TranskrypcjaRozmow.Stan.Bezczynny &&
                                stanTr !is TranskrypcjaRozmow.Stan.Gotowe &&
                                stanTr !is TranskrypcjaRozmow.Stan.Blad
                            ) {
                                Toast.makeText(
                                    context,
                                    "Transkrypcja już trwa w tle",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            } else {
                                TranskrypcjaForegroundService.start(context, poz.plik)
                                Toast.makeText(
                                    context,
                                    "Transkrypcja w tle — dostaniesz powiadomienie",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        },
                        onToggleTekst = {
                            rozwiniety = if (rozwiniety == poz.plik.absolutePath) null
                            else poz.plik.absolutePath
                        },
                        onUdostepnij = { udostepnij(context, poz.plik) },
                        onUsun = {
                            stopPlay()
                            val sciezka = poz.plik.absolutePath
                            lista = lista.filter { it.plik.absolutePath != sciezka }
                            scope.launch(Dispatchers.IO) {
                                poz.plik.delete()
                                TranskrypcjaRozmow.plikTranskrypcji(poz.plik).delete()
                            }
                        },
                        modifier = Modifier.animateItem(
                            fadeInSpec = tween(260, easing = FastOutSlowInEasing),
                            fadeOutSpec = tween(180, easing = FastOutSlowInEasing),
                            placementSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        )
                    )
                }
            }

            AnimatedVisibility(
                visible = lista.isEmpty(),
                enter = fadeIn(tween(220)),
                exit = fadeOut(tween(160)),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            IkonyAplikacji.Nagrywanie,
                            contentDescription = null,
                            tint = paleta.tekstDrugorzedny.copy(alpha = 0.55f),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Brak nagrań",
                            color = paleta.naTle.copy(alpha = 0.5f),
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PasekStatusuTranskrypcji(stan: TranskrypcjaRozmow.Stan) {
    val wiedzmin = czyMotywWiedzmin()
    val akcent = motywAkcent()
    val akcentMorski = if (wiedzmin) akcent else dialer.dominik.pl.ui.theme.ZielonyMorski

    val paleta = LocalMotyw.current
    when (stan) {
        is TranskrypcjaRozmow.Stan.Pobieranie -> {
            Column(Modifier.padding(bottom = 4.dp)) {
                Text(
                    "Pobieranie modelu PL (~1,5 GB)… ${(stan.postep * 100).toInt()}%",
                    color = paleta.tekstDrugorzedny,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { stan.postep },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = akcent,
                    trackColor = paleta.kafelekRamka
                )
            }
        }
        is TranskrypcjaRozmow.Stan.LadowanieModelu -> {
            Text(
                "Ładowanie modelu…",
                color = paleta.tekstDrugorzedny,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        is TranskrypcjaRozmow.Stan.Transkrybuje -> {
            Text(
                "Transkrypcja…",
                color = akcent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        is TranskrypcjaRozmow.Stan.Blad -> {
            Text(
                stan.komunikat,
                color = CzerwonyPolaczenia,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        else -> Unit
    }
}

@Composable
private fun KartaNagrania(
    poz: PozycjaNagrania,
    gra: Boolean,
    rozwinięty: Boolean,
    suwak: Float,
    dlugoscMs: Int,
    pozycjaMs: Int,
    onPlay: () -> Unit,
    onSuwak: (Float) -> Unit,
    onSuwakKoniec: () -> Unit,
    onTranskrybuj: () -> Unit,
    onToggleTekst: () -> Unit,
    onUdostepnij: () -> Unit,
    onUsun: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val wiedzmin = czyMotywWiedzmin()
    val akcent = motywAkcent()
    val akcentMorski = if (wiedzmin) akcent else dialer.dominik.pl.ui.theme.ZielonyMorski

    val paleta = LocalMotyw.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val ksztalt = RoundedCornerShape(paleta.promienKafla.dp)
    val maxMs = dlugoscMs.coerceAtLeast(1).toFloat()
    val density = LocalDensity.current
    val prog = with(density) { 96.dp.toPx() }
    val progPotwierdz = with(density) { 168.dp.toPx() }
    var zasieg by remember(poz.plik.absolutePath) { mutableFloatStateOf(0f) }
    // true = pierwszy swipe otworzył Usuń; drugi swipe (dalej) kasuje.
    var otwartyUsun by remember(poz.plik.absolutePath) { mutableStateOf(false) }
    var uzbrojonyPrzycisk by remember(poz.plik.absolutePath) { mutableStateOf(false) }
    val scopeSwipe = rememberCoroutineScope()
    val aktualneUsun by rememberUpdatedState(onUsun)

    var foto by remember(poz.numer) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(poz.numer) {
        foto = withContext(Dispatchers.IO) {
            if (poz.numer == "nieznany") null
            else WeryfikacjaNumeru.pobierzZdjecieKontaktu(context, poz.numer)
        }
    }

    fun animujDo(cel: Float, po: (() -> Unit)? = null) {
        val start = zasieg
        scopeSwipe.launch {
            Animatable(start).animateTo(
                cel,
                spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ) { zasieg = value }
            po?.invoke()
        }
    }

    fun animujPowrot() {
        otwartyUsun = false
        animujDo(0f)
    }

    fun animujOdkryjUsun() {
        otwartyUsun = true
        animujDo(-prog)
    }

    val tloPlay by animateColorAsState(
        targetValue = if (gra) akcent else akcent.copy(alpha = 0.14f),
        animationSpec = tween(220),
        label = "tloPlay"
    )
    val tintPlay by animateColorAsState(
        targetValue = if (gra) Color.Black else akcent,
        animationSpec = tween(220),
        label = "tintPlay"
    )
    val skalaPlay by animateFloatAsState(
        targetValue = if (gra) 1.06f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "skalaPlay"
    )

    Box(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(ksztalt)
                .background(CzerwonyPolaczenia.copy(alpha = 0.16f)),
            contentAlignment = Alignment.CenterEnd
        ) {
            if (zasieg < 0f) {
                Row(
                    Modifier
                        .padding(end = 16.dp)
                        .pointerInput(otwartyUsun) {
                            // Drugie przeciągnięcie po odkrytym „Usuń” → kasuj.
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    if (otwartyUsun && zasieg <= -progPotwierdz * 0.85f) {
                                        aktualneUsun()
                                    } else if (otwartyUsun) {
                                        animujOdkryjUsun()
                                    } else {
                                        animujPowrot()
                                    }
                                },
                                onDragCancel = {
                                    if (otwartyUsun) animujOdkryjUsun() else animujPowrot()
                                },
                                onHorizontalDrag = { _, dystans ->
                                    zasieg = (zasieg + dystans)
                                        .coerceIn(-progPotwierdz, 0f)
                                },
                            )
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        IkonyAplikacji.Kosz,
                        contentDescription = null,
                        tint = CzerwonyPolaczenia,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        if (otwartyUsun && zasieg <= -prog * 1.15f) "Potwierdź" else "Usuń",
                        color = CzerwonyPolaczenia,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(zasieg.roundToInt(), 0) }
                .clip(ksztalt)
                .background(paleta.gradientKafelka())
                .border(
                    1.dp,
                    if (gra) akcent.copy(alpha = 0.35f) else paleta.kafelekRamka,
                    ksztalt
                )
                .pointerInput(poz.plik.absolutePath, prog, otwartyUsun) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            when {
                                // Drugi swipe (dalej w lewo) przy otwartym Usuń → kasuj.
                                otwartyUsun && zasieg <= -progPotwierdz * 0.85f -> aktualneUsun()
                                // Pierwszy swipe → odkryj Usuń i zostaw.
                                zasieg <= -prog * 0.55f -> animujOdkryjUsun()
                                else -> animujPowrot()
                            }
                        },
                        onDragCancel = {
                            if (otwartyUsun) animujOdkryjUsun() else animujPowrot()
                        },
                        onHorizontalDrag = { _, dystans ->
                            val min = if (otwartyUsun) -progPotwierdz else -prog * 1.15f
                            zasieg = (zasieg + dystans).coerceIn(min, 0f)
                        }
                    )
                }
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AwatarNagrania(
                    bitmap = foto,
                    inicial = poz.inicial,
                    aktywny = gra
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        poz.tytul,
                        color = paleta.naTle,
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        buildString {
                            if (!poz.nazwaKontaktu.isNullOrBlank()) {
                                append(poz.numer)
                                append(" · ")
                            }
                            append(poz.data)
                            append(" · ")
                            append(poz.rozmiarKb)
                            append(" KB")
                        },
                        color = paleta.tekstDrugorzedny,
                        fontSize = 12.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .graphicsLayer {
                            scaleX = skalaPlay
                            scaleY = skalaPlay
                        }
                        .clip(CircleShape)
                        .then(
                            if (wiedzmin && !gra) Modifier.tloSrebrnyPolysk(CircleShape)
                            else Modifier.background(tloPlay)
                        )
                        .clickable(onClick = onPlay),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = gra,
                        transitionSpec = {
                            fadeIn(tween(140)) togetherWith fadeOut(tween(100))
                        },
                        label = "ikonaPlay"
                    ) { graj ->
                        Icon(
                            imageVector = if (graj) IkonyAplikacji.Wciszenie else IkonyAplikacji.Nagrywanie,
                            contentDescription = if (graj) "Stop" else "Odtwórz",
                            tint = if (wiedzmin && !graj) Color(0xFF1A1C1E) else tintPlay,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = gra && dlugoscMs > 0,
                enter = fadeIn(tween(180)) + expandVertically(tween(220, easing = FastOutSlowInEasing)),
                exit = fadeOut(tween(140)) + shrinkVertically(tween(180, easing = FastOutSlowInEasing))
            ) {
                Column(Modifier.padding(top = 10.dp)) {
                    Slider(
                        value = suwak.coerceIn(0f, maxMs),
                        onValueChange = onSuwak,
                        onValueChangeFinished = onSuwakKoniec,
                        valueRange = 0f..maxMs,
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = akcent,
                            activeTrackColor = akcent,
                            inactiveTrackColor = paleta.kafelekRamka
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(formatCzas(pozycjaMs), color = paleta.tekstDrugorzedny, fontSize = 11.sp)
                        Text(formatCzas(dlugoscMs), color = paleta.tekstDrugorzedny, fontSize = 11.sp)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!uzbrojonyPrzycisk) {
                    PrzyciskAkcjiNagrania(
                        ikona = IkonyAplikacji.Notatka,
                        etykieta = if (poz.transkrypcja != null) "Ponów" else "Tekst",
                        aktywny = poz.transkrypcja != null,
                        onClick = onTranskrybuj
                    )
                    if (poz.transkrypcja != null) {
                        PrzyciskAkcjiNagrania(
                            ikona = IkonyAplikacji.Edytuj,
                            etykieta = if (rozwinięty) "Ukryj" else "Pokaż",
                            aktywny = rozwinięty,
                            onClick = onToggleTekst
                        )
                    }
                    PrzyciskAkcjiNagrania(
                        ikona = IkonyAplikacji.StrzalkaWGore,
                        etykieta = "Wyślij",
                        aktywny = false,
                        onClick = onUdostepnij
                    )
                    Spacer(Modifier.weight(1f))
                    PrzyciskAkcjiNagrania(
                        ikona = IkonyAplikacji.Kosz,
                        etykieta = "Usuń",
                        aktywny = false,
                        niebezpieczny = true,
                        onClick = {
                            uzbrojonyPrzycisk = true
                            otwartyUsun = false
                            animujPowrot()
                        }
                    )
                } else {
                    PrzyciskUsunPrzeciagnij(
                        onPotwierdz = {
                            uzbrojonyPrzycisk = false
                            aktualneUsun()
                        },
                        onAnuluj = { uzbrojonyPrzycisk = false },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            AnimatedVisibility(
                visible = rozwinięty && !poz.transkrypcja.isNullOrBlank(),
                enter = fadeIn(tween(180)) + expandVertically(tween(220, easing = FastOutSlowInEasing)),
                exit = fadeOut(tween(140)) + shrinkVertically(tween(180, easing = FastOutSlowInEasing))
            ) {
                Text(
                    poz.transkrypcja.orEmpty(),
                    color = paleta.naTle.copy(alpha = 0.88f),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(paleta.naTle.copy(alpha = 0.04f))
                        .padding(12.dp)
                )
            }
        }
    }
}

@Composable
private fun AwatarNagrania(bitmap: Bitmap?, inicial: String, aktywny: Boolean) {
    val wiedzmin = czyMotywWiedzmin()
    val akcent = motywAkcent()
    val akcentMorski = if (wiedzmin) akcent else dialer.dominik.pl.ui.theme.ZielonyMorski

    val paleta = LocalMotyw.current
    val ramka by animateColorAsState(
        targetValue = if (aktywny) akcent.copy(alpha = 0.55f) else Color.Transparent,
        animationSpec = tween(220),
        label = "ramkaAwatar"
    )
    Box(
        modifier = Modifier
            .size(44.dp)
            .border(1.5.dp, ramka, CircleShape)
            .padding(1.dp)
            .clip(CircleShape)
            .background(akcent.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                inicial,
                color = akcent,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
private fun PrzyciskAkcjiNagrania(
    ikona: ImageVector,
    etykieta: String,
    aktywny: Boolean,
    niebezpieczny: Boolean = false,
    onClick: () -> Unit,
) {
    val wiedzmin = czyMotywWiedzmin()
    val akcent = motywAkcent()
    val akcentMorski = if (wiedzmin) akcent else dialer.dominik.pl.ui.theme.ZielonyMorski

    val paleta = LocalMotyw.current
    val kolor = when {
        niebezpieczny -> CzerwonyPolaczenia
        aktywny -> akcent
        else -> paleta.tekstDrugorzedny
    }
    val tlo by animateColorAsState(
        targetValue = when {
            niebezpieczny -> CzerwonyPolaczenia.copy(alpha = 0.12f)
            aktywny -> akcent.copy(alpha = 0.14f)
            else -> paleta.naTle.copy(alpha = 0.06f)
        },
        animationSpec = tween(200),
        label = "tloAkcji"
    )
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(tlo)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(ikona, contentDescription = etykieta, tint = kolor, modifier = Modifier.size(15.dp))
        Text(etykieta, color = kolor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

/**
 * Po kliknięciu „Usuń”: przeciągnij uchwyt w prawo, żeby potwierdzić kasowanie.
 */
@Composable
private fun PrzyciskUsunPrzeciagnij(
    onPotwierdz: () -> Unit,
    onAnuluj: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val paleta = LocalMotyw.current
    val scope = rememberCoroutineScope()
    var torPx by remember { mutableFloatStateOf(0f) }
    var drag by remember { mutableFloatStateOf(0f) }
    val uchwytPx = with(LocalDensity.current) { 30.dp.toPx() }
    val maxDrag = (torPx - uchwytPx).coerceAtLeast(0f)
    val prog = maxDrag * 0.78f
    val aktualnePotwierdz by rememberUpdatedState(onPotwierdz)

    fun snap(cel: Float) {
        val start = drag
        scope.launch {
            Animatable(start).animateTo(
                cel,
                spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
            ) { drag = value }
        }
    }

    Box(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CzerwonyPolaczenia.copy(alpha = 0.14f))
            .border(1.dp, CzerwonyPolaczenia.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(3.dp)
            .onSizeChanged { torPx = it.width.toFloat() }
    ) {
        Text(
            if (prog > 0f && drag >= prog) "Puść, aby usunąć" else "Przeciągnij, aby usunąć",
            color = CzerwonyPolaczenia.copy(alpha = 0.9f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.align(Alignment.Center)
        )
        Text(
            "Anuluj",
            color = paleta.tekstDrugorzedny,
            fontSize = 11.sp,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 10.dp)
                .clickable(onClick = onAnuluj)
        )
        Box(
            modifier = Modifier
                .offset { IntOffset(drag.roundToInt(), 0) }
                .size(30.dp)
                .align(Alignment.CenterStart)
                .clip(RoundedCornerShape(10.dp))
                .background(CzerwonyPolaczenia)
                .pointerInput(maxDrag) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (prog > 0f && drag >= prog) aktualnePotwierdz()
                            else snap(0f)
                        },
                        onDragCancel = { snap(0f) },
                        onHorizontalDrag = { _, dx ->
                            drag = (drag + dx).coerceIn(0f, maxDrag)
                        },
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                IkonyAplikacji.Kosz,
                contentDescription = "Przeciągnij, aby usunąć",
                tint = Color.White,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

private fun formatCzas(ms: Int): String {
    val s = TimeUnit.MILLISECONDS.toSeconds(ms.toLong().coerceAtLeast(0))
    val m = s / 60
    val r = s % 60
    return "%d:%02d".format(m, r)
}

private fun zaladuj(ctx: Context): List<PozycjaNagrania> {
    val fmt = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.forLanguageTag("pl-PL"))
    val cacheNazw = mutableMapOf<String, String?>()
    return NagrywaniePliki.lista(ctx).map { f ->
        val numer = f.nameWithoutExtension
            .substringAfterLast('_')
            .ifBlank { "nieznany" }
        val nazwa = cacheNazw.getOrPut(numer) {
            if (numer == "nieznany") null
            else WeryfikacjaNumeru.pobierzNazweKontaktu(ctx, numer)
        }
        PozycjaNagrania(
            plik = f,
            numer = numer,
            nazwaKontaktu = nazwa,
            data = fmt.format(Date(f.lastModified())),
            rozmiarKb = (f.length() / 1024).coerceAtLeast(1),
            transkrypcja = TranskrypcjaRozmow.odczytaj(f),
        )
    }
}

private fun udostepnij(ctx: Context, plik: File) {
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", plik)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "audio/wav"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(Intent.createChooser(intent, "Udostępnij nagranie"))
}
