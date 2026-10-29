package dialer.dominik.pl.ui.screens

import android.content.Context
import android.provider.Telephony
import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dialer.dominik.pl.sms.StanWysylki
import dialer.dominik.pl.sms.stanZKolumnSms
import dialer.dominik.pl.ui.components.IkonyAplikacji
import dialer.dominik.pl.ui.components.LogoAnimowane
import dialer.dominik.pl.ui.theme.CzerwonyPolaczenia
import dialer.dominik.pl.ui.theme.ZielonyMorski
import dialer.dominik.pl.ui.theme.ZielonyNeon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
internal enum class SpanType { LINK, PHONE, CODE }

internal data class TextSpan(
    val start: Int,
    val end: Int,
    val type: SpanType,
    val value: String
)

internal fun parseTextSpans(text: String): List<TextSpan> {
    val spans = mutableListOf<TextSpan>()
    
    // 1. URLs
    val urlMatcher = android.util.Patterns.WEB_URL.matcher(text)
    while (urlMatcher.find()) {
        spans.add(TextSpan(urlMatcher.start(), urlMatcher.end(), SpanType.LINK, urlMatcher.group()))
    }
    
    // 2. Phones
    val phoneMatcher = android.util.Patterns.PHONE.matcher(text)
    while (phoneMatcher.find()) {
        val start = phoneMatcher.start()
        val end = phoneMatcher.end()
        if (spans.none { start < it.end && end > it.start }) {
            val phone = phoneMatcher.group()
            if (phone.length >= 7) {
                spans.add(TextSpan(start, end, SpanType.PHONE, phone))
            }
        }
    }
    
    // 3. Verification codes (4 to 8 digits)
    val codeRegex = Regex("\\b\\d{4,8}\\b")
    codeRegex.findAll(text).forEach { match ->
        val start = match.range.first
        val end = match.range.last + 1
        if (spans.none { start < it.end && end > it.start }) {
            spans.add(TextSpan(start, end, SpanType.CODE, match.value))
        }
    }
    
    return spans.sortedBy { it.start }
}

/** Karta (dymek) wiadomości nowoczesnej: przychodząca ciemna, wychodząca zielony gradient. */
@Composable
internal fun KartaWiadomosciNowoczesna(
    wpis: SmsWpis,
    onLongPress: () -> Unit,
    przypieta: Boolean,
    onImageClick: (android.graphics.Bitmap) -> Unit,
    podswietlona: Boolean = false,
    podswietlonaGlowna: Boolean = false,
    szukanaFraza: String? = null,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val wychodzaca = wpis.type == Telephony.Sms.MESSAGE_TYPE_SENT ||
        wpis.type == Telephony.Sms.MESSAGE_TYPE_OUTBOX ||
        wpis.type == Telephony.Sms.MESSAGE_TYPE_QUEUED ||
        wpis.type == Telephony.Sms.MESSAGE_TYPE_FAILED
    val stanWysylki = if (wychodzaca) stanZKolumnSms(wpis.type, wpis.status) else null

    // Pulsująca ramka dla AKTUALNego trafienia wyszukiwania.
    val ramkaAlpha by animateFloatAsState(
        targetValue = if (podswietlonaGlowna) 1f else 0f,
        animationSpec = if (podswietlonaGlowna) infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse
        ) else tween(0),
        label = "highlightPulse"
    )

    // Leniwe ładowanie bitmapy MMS dla widocznego kafelka na ekranie
    var mmsBitmap by remember(wpis.id) { mutableStateOf(wpis.bitmap) }
    LaunchedEffect(wpis.mmsMsgId) {
        if (mmsBitmap == null && wpis.mmsMsgId != null) {
            mmsBitmap = withContext(Dispatchers.IO) {
                pobierzCzescObrazu(context, wpis.mmsMsgId)
            }
        }
    }

    val hasText = wpis.body.isNotBlank() && wpis.body != "📷 [MMS]" && wpis.body != "MMS"
    val hasImage = mmsBitmap != null || wpis.mmsMsgId != null
    if (!hasText && !hasImage) return

    val czasFormat = remember(wpis.date) {
        val format = if (android.text.format.DateUtils.isToday(wpis.date)) {
            java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        } else {
            java.text.SimpleDateFormat("dd.MM.yyyy, HH:mm", java.util.Locale.getDefault())
        }
        format.format(java.util.Date(wpis.date))
    }

    val annotatedString = remember(wpis.body, podswietlona, szukanaFraza) {
        val spans = parseTextSpans(wpis.body)
        androidx.compose.ui.text.buildAnnotatedString {
            var lastIdx = 0
            for (span in spans) {
                if (span.start > lastIdx) {
                    append(wpis.body.substring(lastIdx, span.start))
                }
                val urlOrAction = when (span.type) {
                    SpanType.LINK -> {
                        var u = span.value
                        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://$u"
                        u
                    }
                    SpanType.PHONE -> "tel:${span.value}"
                    SpanType.CODE -> "code:${span.value}"
                }

                pushLink(
                    androidx.compose.ui.text.LinkAnnotation.Url(
                        url = urlOrAction,
                        styles = androidx.compose.ui.text.TextLinkStyles(
                            style = androidx.compose.ui.text.SpanStyle(
                                color = if (wychodzaca) Color(0xFF033023) else ZielonyNeon,
                                textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                                fontWeight = FontWeight.Medium
                            )
                        ),
                        linkInteractionListener = { link ->
                            val item = (link as? androidx.compose.ui.text.LinkAnnotation.Url)?.url ?: ""
                            if (item.startsWith("tel:")) {
                                val phone = item.removePrefix("tel:")
                                dialer.dominik.pl.AppNavigationState.docelowaZakladka = dialer.dominik.pl.Zakladka.Glowny
                                dialer.dominik.pl.AppNavigationState.numerDoWybrania = phone
                            } else if (item.startsWith("code:")) {
                                val code = item.removePrefix("code:")
                                kopiujDoSchowka(context, code)
                                Toast.makeText(context, "Skopiowano kod: $code", Toast.LENGTH_SHORT).show()
                            } else {
                                try {
                                    uriHandler.openUri(item)
                                } catch (_: Exception) {
                                    Toast.makeText(context, "Nie można otworzyć linku", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                )
                append(wpis.body.substring(span.start, span.end))
                pop()
                lastIdx = span.end
            }
            if (lastIdx < wpis.body.length) {
                append(wpis.body.substring(lastIdx))
            }
            // Wyróżnij wyszukiwaną frazę bursztynowym kolorem (tylko w aktualnym trafieniu).
            val fraza = szukanaFraza?.trim().orEmpty()
            if (fraza.isNotEmpty()) {
                val frazaL = fraza.lowercase()
                val cialoL = wpis.body.lowercase()
                var od = 0
                while (true) {
                    val idx = cialoL.indexOf(frazaL, startIndex = od)
                    if (idx == -1) break
                    addStyle(
                        androidx.compose.ui.text.SpanStyle(
                            color = Color(0xFFFFD54F),
                            fontWeight = FontWeight.Bold,
                            background = androidx.compose.ui.graphics.Color(0x33FFEB3B)
                        ),
                        idx,
                        idx + fraza.length
                    )
                    od = idx + fraza.length
                }
            }
        }
    }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = if (wychodzaca) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Column(
            horizontalAlignment = if (wychodzaca) Alignment.End else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                Text(
                    text = czasFormat,
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp
                )
                if (stanWysylki != null) {
                    StatusWysylkiWskaznik(stan = stanWysylki)
                }
            }
            Column(
                Modifier
                    .widthIn(max = 300.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .then(
                        if (wychodzaca)
                            Modifier.background(
                                if (stanWysylki == StanWysylki.BLAD)
                                    Brush.horizontalGradient(listOf(Color(0xFF5A1A1A), Color(0xFF8B2E2E)))
                                else
                                    Brush.horizontalGradient(listOf(ZielonyMorski, ZielonyNeon))
                            )
                        else
                            Modifier.background(Brush.verticalGradient(listOf(Color(0xFF131C18), Color(0xFF0B120F))))
                    )
                    .then(
                        if (podswietlonaGlowna) Modifier.border(2.dp, ZielonyNeon.copy(alpha = ramkaAlpha), RoundedCornerShape(18.dp))
                        else if (podswietlona) Modifier.border(1.5.dp, ZielonyNeon.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
                        else if (przypieta && wychodzaca) Modifier.border(1.dp, ZielonyNeon.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
                        else if (przypieta) Modifier.border(1.dp, ZielonyNeon.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
                        else Modifier
                    )
                    .combinedClickable(
                        onClick = {
                            mmsBitmap?.let { bmp ->
                                onImageClick(bmp)
                            }
                        },
                        onLongClick = onLongPress
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box {
                    mmsBitmap?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "MMS",
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .then(
                                    if (stanWysylki == StanWysylki.WYSYLANIE) Modifier.alpha(0.55f)
                                    else Modifier
                                ),
                            contentScale = ContentScale.Crop
                        )
                    }
                    // Logo podczas wysyłki MMS (do czasu SENT).
                    if (stanWysylki == StanWysylki.WYSYLANIE && (mmsBitmap != null || wpis.mmsMsgId != null || wpis.bitmap != null)) {
                        LogoAnimowane(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(56.dp),
                            wejscieAnimacji = 1f,
                            pokazPierscien = false
                        )
                    }
                }
                if (hasText) {
                    Text(
                        text = annotatedString,
                        // Podświetlenie rozjaśnia tło — wybielamy tekst, by był czytelny.
                        color = when {
                            podswietlona -> Color.White
                            wychodzaca -> Color(0xFF06231A)
                            else -> Color.White
                        },
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

internal fun zapiszZdjecieDoGalerii(context: Context, bitmap: android.graphics.Bitmap) {
    try {
        val filename = "MMS_${System.currentTimeMillis()}.jpg"
        val resolver = context.contentResolver
        val contentValues = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/TelefonMMS")
        }
        val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        val fos = uri?.let { resolver.openOutputStream(it) }
        fos?.use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 100, it)
            Toast.makeText(context, "Zapisano zdjęcie w Galerii", Toast.LENGTH_SHORT).show()
        } ?: Toast.makeText(context, "Błąd zapisu zdjęcia", Toast.LENGTH_SHORT).show()
    } catch (_: Exception) {
        Toast.makeText(context, "Błąd zapisu zdjęcia", Toast.LENGTH_SHORT).show()
    }
}

/** Pojedynczy wiersz w menu akcji wiadomości. */
@Composable
internal fun ListRowMenu(tekst: String, ikona: androidx.compose.ui.graphics.vector.ImageVector, czerwony: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 22.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(ikona, contentDescription = null, tint = if (czerwony) CzerwonyPolaczenia else Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(tekst, color = if (czerwony) CzerwonyPolaczenia else Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

/** Pojedyncza opcja w menu załącznika — bez tła (przezroczyste), tylko ikona + tekst. */
@Composable
/**
 * Status przy dymku wychodzącym:
 * - Wysyłanie… — jeszcze w OUTBOX
 * - Wysłano ✓ — SMSC przyjął (telefon wysłał)
 * - Dostarczono ✓✓ — raport od telefonu odbiorcy (nie każdy operator go zwraca)
 * - Błąd — wysyłka lub niedostarczenie
 */
internal fun StatusWysylkiWskaznik(stan: StanWysylki) {
    when (stan) {
        StanWysylki.WYSYLANIE -> {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                LogoAnimowane(
                    modifier = Modifier.size(14.dp),
                    wejscieAnimacji = 1f,
                    pokazPierscien = false
                )
                Text("Wysyłanie…", color = ZielonyNeon.copy(alpha = 0.7f), fontSize = 10.sp)
            }
        }
        StanWysylki.WYSŁANO -> {
            Text("Wysłano ✓", color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp)
        }
        StanWysylki.DOSTARCZONO -> {
            Text("Dostarczono ✓✓", color = ZielonyNeon.copy(alpha = 0.85f), fontSize = 10.sp)
        }
        StanWysylki.BLAD -> {
            Text("Nie dostarczono", color = CzerwonyPolaczenia.copy(alpha = 0.9f), fontSize = 10.sp)
        }
    }
}

