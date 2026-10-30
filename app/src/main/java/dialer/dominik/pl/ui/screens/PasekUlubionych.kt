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
import androidx.compose.runtime.collectAsState
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
import dialer.dominik.pl.ui.components.IkonyAplikacji
import dialer.dominik.pl.ui.theme.CzerwonyPolaczenia
import dialer.dominik.pl.ui.theme.LocalMotyw
import dialer.dominik.pl.ui.theme.MotywStan
import dialer.dominik.pl.ui.theme.ZielonyGleboki
import dialer.dominik.pl.ui.theme.ZielonyMorski
import dialer.dominik.pl.ui.theme.ZielonyNeon
import dialer.dominik.pl.ui.theme.gradientKafelka
import dialer.dominik.pl.ui.theme.tloSrebrnyPolysk
import androidx.compose.ui.graphics.Color
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun PasekUlubionych(
    kontakty: List<KontaktItem>,
    onKlik: (KontaktItem, Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val motywStan by MotywStan.stan.collectAsState()
    val wariantAwataru = if (motywStan.paletaId == "wiedzmin") {
        AwatarWariant.Wiedzmin
    } else {
        AwatarWariant.Domyslny
    }

    LazyRow(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)
    ) {
        items(kontakty.size, key = { kontakty[it].name }) { indeks ->
            val kontakt = kontakty[indeks]
            val inicial = kontakt.nazwaWyswietlana.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            val kolorZnaku = kolorZnakuWiedzmina(indeks)
            var centerX by remember { mutableFloatStateOf(0f) }
            var centerY by remember { mutableFloatStateOf(0f) }

            // Kliknięcie → pole wyboru akcji wychodzące z dokładnego punktu awatara.
            Column(
                modifier = Modifier
                    .width(72.dp)
                    .onGloballyPositioned { coordinates ->
                        val bounds = coordinates.boundsInRoot()
                        centerX = bounds.center.x
                        centerY = bounds.center.y
                    }
                    .clickable {
                        val screenW = with(density) { configuration.screenWidthDp.dp.toPx() }
                        val screenH = with(density) { configuration.screenHeightDp.dp.toPx() }
                        val pX = if (screenW > 0f) (centerX / screenW).coerceIn(0.1f, 0.9f) else 0.5f
                        val pY = if (screenH > 0f) (centerY / screenH).coerceIn(0.1f, 0.9f) else 0.2f
                        onKlik(kontakt, pX, pY)
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AwatarKontaktu(
                    fotoUri = kontakt.fotoUri,
                    inicial = inicial,
                    context = context,
                    rozmiar = 56.dp,
                    wariant = wariantAwataru,
                    kolorWiedzmina = if (wariantAwataru == AwatarWariant.Wiedzmin) kolorZnaku else null,
                    indeksUlubionego = indeks
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = kontakt.nazwaWyswietlana,
                    color = LocalMotyw.current.naTle.copy(alpha = 0.8f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Kafelek akcji (zadzwoń / wyślij wiadomość) w polu wyboru dla ulubionego kontaktu. */
@Composable
internal fun KafelekAkcjiUlubionego(
    ikona: androidx.compose.ui.graphics.vector.ImageVector,
    etykieta: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val motyw = LocalMotyw.current
    val motywStan by MotywStan.stan.collectAsState()
    val wiedzmin = motywStan.paletaId == "wiedzmin"
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(brush = motyw.gradientKafelka())
            .border(1.dp, motyw.kafelekRamka, RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 18.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .then(
                    if (wiedzmin) {
                        Modifier.tloSrebrnyPolysk(CircleShape, animuj = false)
                    } else {
                        Modifier
                            .background(ZielonyNeon.copy(alpha = 0.14f))
                            .border(1.dp, ZielonyNeon.copy(alpha = 0.35f), CircleShape)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = ikona,
                contentDescription = etykieta,
                tint = if (wiedzmin) Color(0xFF1A1C1E) else ZielonyNeon,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = etykieta,
            color = motyw.naTle,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

/**
 * Karta zgrupowanych połączeń z jednym numerem.
 */
