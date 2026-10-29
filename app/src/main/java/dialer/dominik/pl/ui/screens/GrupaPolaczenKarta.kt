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
    // Pseudonim ustawiony w tej aplikacji (po nazwie z dziennika). Jeśli jest,
            // pokazujemy go zamiast nazwy systemowej — np. „Luluś" zamiast „Emilka Kowalska".
            val pseudonim = remember(context, grupa.nazwa) { pobierzPseudonim(context, grupa.nazwa ?: "") }
            // Notatka przypisana do tej rozmowy (zapisana podczas połączenia).
            // Cache w remember — unika synchronicznego odczytu SharedPreferences przy
            // każdej recompozycji (drag/scroll), który powodował lag. Świeżość po
            // powrocie z rozmowy załatwia odświeżenie listy w MainDialerScreen.
            val notatkaLogu = remember(context, grupa.numer) {
                NotatkiRozmow.pobierzNotatke(context, grupa.numer)
            }
            // Czy pokazać okno z treścią notatki (po kliknięciu ikony na kafelku).
            var pokazTrescNotatki by remember { mutableStateOf(false) }

    val etykietaTypu = when (grupa.typOstatniego) {
        CallLog.Calls.INCOMING_TYPE -> "Przychodzące"
        CallLog.Calls.OUTGOING_TYPE -> "Wychodzące"
        CallLog.Calls.MISSED_TYPE -> "Nieodebrane"
        CallLog.Calls.REJECTED_TYPE -> "Odrzucone"
        else -> "Połączenie"
    }
    val kolorTypu = when (grupa.typOstatniego) {
        CallLog.Calls.MISSED_TYPE, CallLog.Calls.REJECTED_TYPE -> CzerwonyPolaczenia
        else -> Color.White.copy(alpha = 0.5f)
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
                .clip(RoundedCornerShape(20.dp))
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
                .clip(RoundedCornerShape(20.dp))
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
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.verticalGradient(listOf(Color(0xFF131C18), Color(0xFF0B120F)))
                )
                .border(1.dp, ZielonyNeon.copy(alpha = 0.10f), RoundedCornerShape(20.dp))
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
                                                    color = Color.White,
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                        if (grupa.liczba > 1) {
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(Color.White.copy(alpha = 0.10f))
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${grupa.liczba}×",
                                    color = Color.White.copy(alpha = 0.65f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = "$etykietaTypu · ${formatDaty.format(Date(grupa.dataOstatniego))}",
                                            color = kolorTypu,
                                            fontSize = 12.sp
                                        )
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
                    // Ikona notatki — widoczna TYLKO, gdy do rozmowy jest notatka.
                    // Kliknięcie otwiera okno z jej treścią (na kafelku nie pokazujemy
                    // samej treści, żeby nie było wiadomo, którego połączenia dotyczy).
                    if (!notatkaLogu.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(ZielonyNeon.copy(alpha = 0.14f))
                                .clickable { pokazTrescNotatki = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = IkonyAplikacji.Notatka,
                                contentDescription = "Pokaż notatkę",
                                tint = ZielonyNeon,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                            .clickable { onWiadomosc(grupa.numer) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = IkonyAplikacji.Wiadomosc,
                            contentDescription = "Napisz wiadomość",
                            tint = Color.White.copy(alpha = 0.65f),
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
                            .background(Color.White.copy(alpha = 0.06f))
                    )
                    for (wpis in widoczne) {
                        val (znak, kolor, opis) = when (wpis.type) {
                            CallLog.Calls.INCOMING_TYPE -> Triple("↙", ZielonyNeon, "Przychodzące")
                            CallLog.Calls.OUTGOING_TYPE -> Triple("↗", Color(0xFF64B5F6), "Wychodzące")
                            CallLog.Calls.MISSED_TYPE -> Triple("✕", CzerwonyPolaczenia, "Nieodebrane")
                            CallLog.Calls.REJECTED_TYPE -> Triple("⊘", CzerwonyPolaczenia, "Odrzucone")
                            else -> Triple("•", Color.White.copy(alpha = 0.4f), "Połączenie")
                        }

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
                                Text(
                                    text = znak,
                                    color = kolor,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 20.sp
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
                                    color = Color.White.copy(alpha = 0.4f),
                                    fontSize = 11.sp
                                )
                            }
                            val czasTrwania = formatujCzasTrwania(wpis.duration)
                            if (czasTrwania.isNotEmpty()) {
                                Text(
                                    text = czasTrwania,
                                    color = Color.White.copy(alpha = 0.3f),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Okno z treścią notatki — po kliknięciu ikony notatki na kafelku.
    if (pokazTrescNotatki && !notatkaLogu.isNullOrBlank()) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { pokazTrescNotatki = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF14201A))
                    .border(1.dp, ZielonyNeon.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
                    .padding(20.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = IkonyAplikacji.Notatka,
                            contentDescription = null,
                            tint = ZielonyNeon,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Notatka do rozmowy",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = notatkaLogu,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 15.sp,
                        lineHeight = 22.sp
                    )
                    Spacer(Modifier.height(16.dp))
                    TextButton(onClick = { pokazTrescNotatki = false }) {
                        Text("Zamknij", color = ZielonyNeon, fontWeight = FontWeight.SemiBold)
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
