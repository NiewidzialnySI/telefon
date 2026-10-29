package dialer.dominik.pl.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.telecom.TelecomManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dialer.dominik.pl.sms.KanalWiadomosci
import dialer.dominik.pl.sms.czyKanalDostepny
import dialer.dominik.pl.sms.nazwa
import dialer.dominik.pl.sms.pobierzKanal
import dialer.dominik.pl.sms.pobierzKontoKomunikatora
import dialer.dominik.pl.sms.pobierzNumerDoDzwonienia
import dialer.dominik.pl.sms.pobierzNumerDoWiadomosci
import dialer.dominik.pl.sms.zapamietajNumerDoDzwonienia
import dialer.dominik.pl.sms.zapamietajNumerDoWiadomosci
import dialer.dominik.pl.sms.wyslijPrzezKomunikator
import dialer.dominik.pl.sms.zapamietajKanal
import dialer.dominik.pl.sms.zapamietajKontoKomunikatora
import dialer.dominik.pl.ui.components.IkonyAplikacji
import dialer.dominik.pl.ui.theme.CzarnyAmoled
import dialer.dominik.pl.ui.theme.ZielonyMorski
import dialer.dominik.pl.ui.theme.ZielonyNeon
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun IconMini(
    ikona: androidx.compose.ui.graphics.vector.ImageVector,
    opis: String,
    kolor: Color,
    podswietlona: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(
                if (podswietlona) ZielonyNeon.copy(alpha = 0.16f)
                else Color.White.copy(alpha = 0.07f)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = ikona,
            contentDescription = opis,
            tint = kolor,
            modifier = Modifier.size(15.dp)
        )
    }
}

/** Pojedynczy przycisk kanału wiadomości. */
@Composable
internal fun ElementKanalu(
    kanal: KanalWiadomosci,
    wybrany: Boolean,
    dostepny: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (wybrany) ZielonyNeon.copy(alpha = 0.16f)
                else Color.White.copy(alpha = 0.05f)
            )
            .border(
                1.dp,
                if (wybrany) ZielonyNeon.copy(alpha = 0.5f)
                else Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(50)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = kanal.nazwa(),
            color = when {
                wybrany -> ZielonyNeon
                dostepny -> Color.White.copy(alpha = 0.55f)
                else -> Color.White.copy(alpha = 0.22f)
            },
            fontSize = 11.sp,
            fontWeight = if (wybrany) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1
        )
    }
}

/**
 * Wspólny komunikat pustego stanu / braku uprawnienia.
 */
@Composable
internal fun KomunikatKontakty(tytul: String, opis: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = tytul,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Text(
                            text = opis,
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

/** Awatar kontaktu — zdjęcie z książki (jeśli jest), inaczej inicjał na zielonym tle. */
@Composable
internal fun AwatarKontaktu(
    fotoUri: String?,
    inicial: String,
    context: Context,
    rozmiar: androidx.compose.ui.unit.Dp
) {
    // Dekodowanie zdjęcia ZAWSZE na wątku IO (poza UI), żeby przewijanie listy
    // nie czkało — synchronny decodeStream na main thread to typowy jank przy
    // wjeździe nowych kart. W trakcie ładowania pokazujemy inicjał; bitmapa
    // podmienia się, gdy gotowa.
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(
        initialValue = null,
        key1 = fotoUri,
        key2 = context
    ) {
        value = withContext(Dispatchers.IO) {
            if (fotoUri.isNullOrBlank()) {
                null
            } else try {
                android.net.Uri.parse(fotoUri)?.let { uri ->
                    context.contentResolver.openInputStream(uri)?.use { ins ->
                        android.graphics.BitmapFactory.decodeStream(ins)?.asImageBitmap()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
        // Lokalna kopia, żeby smart-cast działał (bitmap to delegowana właściwość).
        val obraz = bitmap
        if (obraz != null) {
            Image(
                bitmap = obraz,
            contentDescription = null,
            modifier = Modifier
                .size(rozmiar)
                .clip(CircleShape)
                .border(1.dp, ZielonyNeon.copy(alpha = 0.22f), CircleShape),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop
        )
    } else {
        Box(
            modifier = Modifier
                .size(rozmiar)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(
                            ZielonyMorski.copy(alpha = 0.35f),
                            ZielonyNeon.copy(alpha = 0.12f)
                        )
                    )
                )
                .border(1.dp, ZielonyNeon.copy(alpha = 0.22f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = inicial,
                color = ZielonyNeon,
                fontSize = (rozmiar * 0.4f).value.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
