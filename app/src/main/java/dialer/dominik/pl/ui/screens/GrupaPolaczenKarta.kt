package dialer.dominik.pl.ui.screens

import android.Manifest
import android.app.role.RoleManager
import android.content.ContentProviderOperation
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.CallLog
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseInCubic
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dialer.dominik.pl.data.BazaSpamu
import dialer.dominik.pl.call.NotatkiRozmow
import dialer.dominik.pl.ui.components.IkonyAplikacji
import dialer.dominik.pl.ui.theme.CzerwonyPolaczenia
import dialer.dominik.pl.ui.theme.ZielonyGleboki
import dialer.dominik.pl.ui.theme.ZielonyMorski
import dialer.dominik.pl.ui.theme.ZielonyNeon
import dialer.dominik.pl.ui.theme.gradientKafelka
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dialer.dominik.pl.data.GrupaPolaczen

@Composable
internal fun GrupaPolaczenKarta(
    grupa: GrupaPolaczen,
    widoczne: List<CallLogEntry>,
    rozwinieta: Boolean,
    onRozwin: () -> Unit,
    onZadzwon: () -> Unit,
    onWiadomosc: (String) -> Unit,
    onUkryj: () -> Unit,
    onDodajKontakt: () -> Unit,
    onSpam: () -> Unit,
    modifier: Modifier = Modifier
) {
    val formatDaty = remember { SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()) }
    val context = LocalContext.current
    val paletaMotywu = dialer.dominik.pl.ui.theme.LocalMotyw.current
    val ksztaltKafla = RoundedCornerShape(paletaMotywu.promienKafla.dp)
    // Pseudonim ustawiony w tej aplikacji (po nazwie z dziennika). Jeśli jest,
            // pokazujemy go zamiast nazwy systemowej — np. „Luluś" zamiast „Emilka Kowalska".
            val pseudonim = remember(context, grupa.nazwa) { pobierzPseudonim(context, grupa.nazwa ?: "") }
            // Notatki per wpis CallLog (nie per numer / kafelek grupy).
            var notatkiWpisow by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
            // Dymek podglądu: id wpisu + treść; znika po 3 s.
            var dymekNotatki by remember { mutableStateOf<Pair<Long, String>?>(null) }
            // Edycja: id wpisu + bieżąca treść w polu.
            var edycjaNotatki by remember { mutableStateOf<Pair<Long, String>?>(null) }
            val idsWpisow = remember(widoczne) { widoczne.map { it.id } }
            LaunchedEffect(rozwinieta, idsWpisow) {
                withContext(Dispatchers.IO) {
                    NotatkiRozmow.dociagnijOczekujace(context)
                    NotatkiRozmow.mapaNotatek(context)
                }.let { notatkiWpisow = it }
            }
            LaunchedEffect(dymekNotatki) {
                val pokazany = dymekNotatki ?: return@LaunchedEffect
                delay(3000)
                if (dymekNotatki == pokazany) dymekNotatki = null
            }

    val etykietaTypu = when (grupa.typOstatniego) {
        CallLog.Calls.INCOMING_TYPE -> "Przychodzące"
        CallLog.Calls.OUTGOING_TYPE -> "Wychodzące"
        CallLog.Calls.MISSED_TYPE -> "Nieodebrane"
        CallLog.Calls.REJECTED_TYPE -> "Odrzucone"
        else -> "Połączenie"
    }
    val ikonaTypu = when (grupa.typOstatniego) {
        CallLog.Calls.INCOMING_TYPE -> IkonyAplikacji.PolaczeniePrzychodzace
        CallLog.Calls.OUTGOING_TYPE -> IkonyAplikacji.PolaczenieWychodzace
        CallLog.Calls.MISSED_TYPE -> IkonyAplikacji.PolaczenieNieodebrane
        CallLog.Calls.REJECTED_TYPE -> IkonyAplikacji.PolaczenieOdrzucone
        else -> IkonyAplikacji.TelefonLogo
    }
    val kolorTypu = when (grupa.typOstatniego) {
        CallLog.Calls.MISSED_TYPE, CallLog.Calls.REJECTED_TYPE -> CzerwonyPolaczenia
        else -> paletaMotywu.naTle.copy(alpha = 0.5f)
    }

    var zasieg by remember { mutableFloatStateOf(0f) }
    // Próg przeciągnięcia liczony raz na kartę (nie przy każdej recompozycji).
    val gestyDensity = LocalDensity.current
    val prog = remember { with(gestyDensity) { 120.dp.toPx() } }

    Box(modifier = modifier.fillMaxWidth()) {
        // Tło po PRAWĘJ — widoczne przy przeciągnięciu w LEWO (Ukryj)
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(ksztaltKafla)
                .background(CzerwonyPolaczenia.copy(alpha = 0.18f)),
            contentAlignment = Alignment.CenterEnd
        ) {
            if (zasieg < 0f) {
                Row(
                    modifier = Modifier.padding(end = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = IkonyAplikacji.Zamknij,
                        contentDescription = null,
                        tint = CzerwonyPolaczenia,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Ukryj",
                        color = CzerwonyPolaczenia,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        // Tło po LEWEJ — widoczne przy przeciągnięciu w PRAWO (Spam)
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(ksztaltKafla)
                .background(CzerwonyPolaczenia.copy(alpha = 0.25f)),
            contentAlignment = Alignment.CenterStart
        ) {
            if (zasieg > 0f) {
                Row(
                    modifier = Modifier.padding(start = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = IkonyAplikacji.Tarcza,
                        contentDescription = null,
                        tint = CzerwonyPolaczenia,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Spam",
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
                                .graphicsLayer {
                                    // graphicsLayer zamiast offset: przy przeciąganiu przesuwana jest
                                    // warstwa renderowania, bez ponownego layoutu/recomposition treści
                                    // (w tym 10 wpisów) — to eliminuje lag podczas gestu Ukryj/Spam.
                                    translationX = zasieg
                                }
                .clip(ksztaltKafla)
                .background(paletaMotywu.gradientKafelka())
                .border(
                    1.dp,
                    paletaMotywu.kafelekRamka,
                    ksztaltKafla
                )
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            when {
                                // w lewo → ukryj
                                zasieg < -prog -> onUkryj()
                                // w prawo → spam (z potwierdzeniem)
                                zasieg > prog -> onSpam()
                            }
                            zasieg = 0f
                        },
                        onHorizontalDrag = { _, dystans ->
                            // Dwukierunkowo: lewo (ujemne) i prawo (dodatnie)
                            zasieg = (zasieg + dystans).coerceIn(-prog * 1.4f, prog * 1.4f)
                        }
                    )
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = onRozwin,
                        onLongClick = onDodajKontakt
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                                                    text = pseudonim ?: grupa.nazwa ?: grupa.numer,
                                                    color = paletaMotywu.naTle,
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                        if (grupa.liczba > 1) {
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(paletaMotywu.naTle.copy(alpha = 0.10f))
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${grupa.liczba}×",
                                    color = paletaMotywu.tekstDrugorzedny,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = ikonaTypu,
                            contentDescription = etykietaTypu,
                            tint = kolorTypu,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "$etykietaTypu · ${formatDaty.format(Date(grupa.dataOstatniego))}",
                            color = kolorTypu,
                            fontSize = 12.sp
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        ZielonyMorski.copy(alpha = 0.22f),
                                        ZielonyNeon.copy(alpha = 0.30f)
                                    )
                                )
                            )
                            .clickable(onClick = onZadzwon),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = IkonyAplikacji.TelefonLogo,
                            contentDescription = "Zadzwoń",
                            tint = ZielonyNeon,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(paletaMotywu.naTle.copy(alpha = 0.08f))
                            .clickable { onWiadomosc(grupa.numer) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = IkonyAplikacji.Wiadomosc,
                            contentDescription = "Napisz wiadomość",
                            tint = paletaMotywu.naTle.copy(alpha = 0.65f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            AnimatedVisibility(visible = rozwinieta) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(paletaMotywu.naTle.copy(alpha = 0.06f))
                    )
                    for (wpis in widoczne) {
                        val (ikona, kolor, opis) = when (wpis.type) {
                            CallLog.Calls.INCOMING_TYPE -> Triple(
                                IkonyAplikacji.PolaczeniePrzychodzace, ZielonyNeon, "Przychodzące"
                            )
                            CallLog.Calls.OUTGOING_TYPE -> Triple(
                                IkonyAplikacji.PolaczenieWychodzace, Color(0xFF64B5F6), "Wychodzące"
                            )
                            CallLog.Calls.MISSED_TYPE -> Triple(
                                IkonyAplikacji.PolaczenieNieodebrane, CzerwonyPolaczenia, "Nieodebrane"
                            )
                            CallLog.Calls.REJECTED_TYPE -> Triple(
                                IkonyAplikacji.PolaczenieOdrzucone, CzerwonyPolaczenia, "Odrzucone"
                            )
                            else -> Triple(
                                IkonyAplikacji.TelefonLogo, paletaMotywu.naTle.copy(alpha = 0.4f), "Połączenie"
                            )
                        }

                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(kolor.copy(alpha = 0.20f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = ikona,
                                        contentDescription = opis,
                                        tint = kolor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = opis,
                                        color = kolor,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = formatDaty.format(Date(wpis.date)),
                                        color = paletaMotywu.naTle.copy(alpha = 0.4f),
                                        fontSize = 11.sp
                                    )
                                }
                                val notatkaWpisu = notatkiWpisow[wpis.id]
                                if (!notatkaWpisu.isNullOrBlank()) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(ZielonyNeon.copy(alpha = 0.14f))
                                            .clickable {
                                                dymekNotatki = wpis.id to notatkaWpisu
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = IkonyAplikacji.Notatka,
                                            contentDescription = "Pokaż notatkę",
                                            tint = ZielonyNeon,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(paletaMotywu.naTle.copy(alpha = 0.08f))
                                            .clickable {
                                                dymekNotatki = null
                                                edycjaNotatki = wpis.id to notatkaWpisu
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = IkonyAplikacji.Edytuj,
                                            contentDescription = "Edytuj notatkę",
                                            tint = paletaMotywu.naTle.copy(alpha = 0.7f),
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                }
                                val czasTrwania = formatujCzasTrwania(wpis.duration)
                                if (czasTrwania.isNotEmpty()) {
                                    Text(
                                        text = czasTrwania,
                                        color = paletaMotywu.naTle.copy(alpha = 0.3f),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                            val trescDymka = dymekNotatki?.takeIf { it.first == wpis.id }?.second
                            AnimatedVisibility(
                                visible = !trescDymka.isNullOrBlank(),
                                enter = fadeIn(tween(180)) +
                                    expandVertically(tween(220, easing = FastOutSlowInEasing)) +
                                    scaleIn(
                                        initialScale = 0.92f,
                                        animationSpec = tween(220, easing = FastOutSlowInEasing),
                                        transformOrigin = TransformOrigin(0.15f, 0f)
                                    ),
                                exit = fadeOut(tween(220)) +
                                    shrinkVertically(tween(220, easing = FastOutSlowInEasing))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 60.dp, end = 16.dp, bottom = 10.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color(0xFF1A2E24))
                                        .border(
                                            1.dp,
                                            ZielonyNeon.copy(alpha = 0.28f),
                                            RoundedCornerShape(14.dp)
                                        )
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        text = trescDymka.orEmpty(),
                                        color = paletaMotywu.naTle.copy(alpha = 0.9f),
                                        fontSize = 13.5.sp,
                                        lineHeight = 18.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val edycja = edycjaNotatki
    if (edycja != null) {
        var pole by remember(edycja.first) { mutableStateOf(edycja.second) }
        androidx.compose.ui.window.Dialog(onDismissRequest = { edycjaNotatki = null }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF14201A))
                    .border(1.dp, ZielonyNeon.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
                    .padding(20.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = IkonyAplikacji.Edytuj,
                            contentDescription = null,
                            tint = ZielonyNeon,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Edytuj notatkę",
                            color = paletaMotywu.naTle,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = pole,
                        onValueChange = { pole = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 5,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = paletaMotywu.naTle,
                            fontSize = 15.sp
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ZielonyNeon.copy(alpha = 0.6f),
                            unfocusedBorderColor = paletaMotywu.naTle.copy(alpha = 0.15f),
                            focusedContainerColor = paletaMotywu.naTle.copy(alpha = 0.04f),
                            unfocusedContainerColor = paletaMotywu.naTle.copy(alpha = 0.04f),
                            cursorColor = ZielonyNeon
                        )
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                NotatkiRozmow.zapiszNotatke(context, edycja.first, "")
                                notatkiWpisow = notatkiWpisow - edycja.first
                                edycjaNotatki = null
                                dymekNotatki = null
                            }
                        ) {
                            Text("Usuń", color = CzerwonyPolaczenia)
                        }
                        Row {
                            TextButton(onClick = { edycjaNotatki = null }) {
                                Text("Anuluj", color = paletaMotywu.naTle.copy(alpha = 0.55f))
                            }
                            TextButton(
                                onClick = {
                                    val nowa = pole.trim()
                                    NotatkiRozmow.zapiszNotatke(context, edycja.first, nowa)
                                    notatkiWpisow = if (nowa.isBlank()) {
                                        notatkiWpisow - edycja.first
                                    } else {
                                        notatkiWpisow + (edycja.first to nowa)
                                    }
                                    edycjaNotatki = null
                                    if (nowa.isNotBlank()) {
                                        dymekNotatki = edycja.first to nowa
                                    }
                                }
                            ) {
                                Text("Zapisz", color = ZielonyNeon, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}
// DialogDodajKontakt, PoleTekstoweKontakt i zapiszNowyKontakt
// przeniesione do DialogKontaktu.kt.

/**
 * Czyści wklejony/wpisany numer do dzwonienia:
 * - usuwa spacje, myślniki, nawiasy i kropki (kosmetyka zapisu),
 * - wiodący „+" i „00" usuwa TYLKO, gdy to polski numer (po skróceniu 9 cyfr)
 *   — dla numerów zagranicznych „+" zostawia, bo jest wymagany przy wybieraniu.
 */
