package dialer.dominik.pl.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import dialer.dominik.pl.sms.pobierzKanal
import dialer.dominik.pl.sms.zapamietajKanal
import dialer.dominik.pl.ui.components.IkonyAplikacji
import dialer.dominik.pl.ui.theme.CzerwonyPolaczenia
import dialer.dominik.pl.ui.theme.ZielonyMorski
import dialer.dominik.pl.ui.theme.ZielonyNeon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Ekran NOWEJ WIADOMOŚCI (czat-look, wiele odbiorców):
 *  - nagłówek + chipsy wybranych,
 *  - pole wyszukiwania (numer/imię/nazwisko/firma/pseudonim) — na żywo filtruje kontakty,
 *  - lista kontaktów wypełnia środek; kliknięcie       dodaje odbiorcę,
 *  - pole wiadomości + przycisk wysyłki na dole, jak w czacie.
 */
@Composable
internal fun EkranNowejWiadomosci(
    context: Context,
    numerPoczatkowy: String,
    onWyslano: () -> Unit
) {
    val wybrani = remember { mutableStateListOf<String>() }
    val wyslane = remember { mutableStateListOf<String>() }
    var szukane by remember { mutableStateOf("") }
    var tresc by remember { mutableStateOf("") }
    var sugestie by remember { mutableStateOf<List<ContactSuggestion>>(emptyList()) }
    var ladowanie by remember { mutableStateOf(true) }
    val maKontakty = context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    // Załącznik zdjęcia (MMS) — jak w zwykłym czacie.
    var zalacznikUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var zalacznikPodglad by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var pokazWyborZalacznika by remember { mutableStateOf(false) }
    val tymczasowyZdjecieFile = remember { mutableStateOf<java.io.File?>(null) }
    val launcherGaleria = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val bmp = try {
                android.graphics.BitmapFactory.decodeStream(context.contentResolver.openInputStream(it))?.let { o ->
                    val skala = minOf(1f, 1024f / maxOf(o.width, o.height))
                    if (skala < 1f) android.graphics.Bitmap.createScaledBitmap(o, (o.width * skala).toInt(), (o.height * skala).toInt(), true) else o
                }
            } catch (e: Exception) { e.printStackTrace(); null }
            if (bmp != null) { zalacznikUri = przygotujZdjecieDoMms(context, it); zalacznikPodglad = bmp }
            else Toast.makeText(context, "Nie uda\u0142o si\u0119 doda\u0107 zdj\u0119cia", Toast.LENGTH_SHORT).show()
        }
    }
    val launcherKamera = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.TakePicture()
    ) { ok ->
        if (ok) {
            val plik = tymczasowyZdjecieFile.value
            if (plik != null) {
                val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".fileprovider", plik)
                val bmp = try {
                    android.graphics.BitmapFactory.decodeStream(context.contentResolver.openInputStream(uri))?.let { o ->
                        val skala = minOf(1f, 1024f / maxOf(o.width, o.height))
                        if (skala < 1f) android.graphics.Bitmap.createScaledBitmap(o, (o.width * skala).toInt(), (o.height * skala).toInt(), true) else o
                    }
                } catch (e: Exception) { e.printStackTrace(); null }
                if (bmp != null) { zalacznikUri = uri; zalacznikPodglad = bmp }
                else Toast.makeText(context, "Nie uda\u0142o si\u0119 doda\u0107 zdj\u0119cia", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Numer początkowy od razu odbiorcą.
    LaunchedEffect(numerPoczatkowy) {
        if (numerPoczatkowy.isNotBlank() && numerPoczatkowy !in wybrani) wybrani.add(numerPoczatkowy)
    }

    // Pobierz kontakty (cała książka na start, filtr na żywo przy wpisywaniu).
    LaunchedEffect(szukane, maKontakty) {
        if (!maKontakty) { sugestie = emptyList(); ladowanie = false; return@LaunchedEffect }
        ladowanie = true
        sugestie = withContext(Dispatchers.IO) { wyszukajKontaktyDlaSms(context, szukane.trim()) }
        ladowanie = false
    }

    fun dodaj(numer: String) {
        val n = normalizujKontakt(numer)
        if (n.isNotBlank() && n !in wybrani) wybrani.add(n)
        if (szukane.isNotEmpty()) szukane = ""
    }

    fun wyslij() {
        if (wybrani.isEmpty() || tresc.isBlank()) {
            Toast.makeText(context, "Dodaj odbiorcę i wpisz treść", Toast.LENGTH_SHORT).show(); return
        }
        val pierwszy = wybrani.first()
        val kanal = pobierzKanal(context, pierwszy)
        zapamietajKanal(context, pierwszy, kanal)
        wybrani.forEach { wyslijWiadomosc(context, it, tresc, kanal) }
        wyslane.add(tresc)
        tresc = ""
        Toast.makeText(context, "Wysłano do ${wybrani.size} osób", Toast.LENGTH_SHORT).show()
    }

    Column(
        // Przezroczyste tło — ukazuje animację tła aplikacji (jak w zwykłym czacie).
        Modifier.fillMaxSize()
            .windowInsetsPadding(WindowInsets.ime)
    ) {
        // Nagłówek (przezroczyste tło — przepuszcza animację tła).
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("NOWA WIADOMOŚĆ", color = Color.White.copy(alpha = 0.42f), fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 2.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = { onWyslano() }, modifier = Modifier.size(34.dp)) {
                Icon(IkonyAplikacji.Zamknij, contentDescription = "Zamknij", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
            }
        }

        // Chipsy wybranych.
        if (wybrani.isNotEmpty()) {
            LazyRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                items(wybrani) { n ->
                    Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFF14221B), border = BorderStroke(1.dp, ZielonyNeon.copy(alpha = 0.3f))) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 5.dp, bottom = 5.dp)) {
                            Text(resolveContactName(context, n) ?: n, color = Color.White, fontSize = 13.sp, maxLines = 1)
                            Spacer(Modifier.width(6.dp))
                            Text("×", color = CzerwonyPolaczenia, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.clip(CircleShape).clickable { wybrani.remove(n) }.padding(4.dp))
                        }
                    }
                }
            }
        }

        // Pole wyszukiwania kontaktów — dodaje przez PRZYCISK "+" (nie przez klik w pole).
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(Brush.verticalGradient(listOf(Color(0xFF131C18), Color(0xFF0B120F)))).then(Modifier.border(1.dp, ZielonyNeon.copy(alpha = 0.14f), RoundedCornerShape(16.dp)))) {
                BasicTextField(szukane, { szukane = it },
                    textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
                    cursorBrush = SolidColor(ZielonyNeon),
                    decorationBox = { if (szukane.isEmpty()) Text("Szukaj: numer, imię, nazwisko, firma…", color = Color.White.copy(alpha = 0.35f), fontSize = 14.sp); it() })
            }
            Spacer(Modifier.width(8.dp))
            // Okrągły przycisk "+" — dodaje wpisany numer (identyczny styl jak przycisk wysyłki czatu).
            Box(
                Modifier.size(46.dp).clip(CircleShape)
                    .then(if (szukane.isNotBlank()) Modifier.background(Brush.horizontalGradient(listOf(ZielonyMorski, ZielonyNeon))) else Modifier.background(ZielonyNeon.copy(alpha = 0.16f)))
                    .clickable { dodaj(szukane) },
                contentAlignment = Alignment.Center
            ) { Text("+", color = if (szukane.isNotBlank()) Color(0xFF06231A) else ZielonyNeon, fontSize = 26.sp, fontWeight = FontWeight.Bold) }
        }

        // Lista wysłanych wiadomości (góra) + kontakty (sugestie) — JEDNA lista, pasek u dołu jak w czacie.
        val pokazWyslane = wyslane.isNotEmpty()
        if (ladowanie) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ZielonyNeon, modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
            }
        } else if (sugestie.isEmpty() && !pokazWyslane) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(if (maKontakty) "Brak wyników — wpisz numer, np. +48…" else "Brak dostępu do kontaktów",
                    color = Color.White.copy(alpha = 0.4f), fontSize = 14.sp)
            }
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (pokazWyslane) {
                    item(key = "naglowek-wyslane") {
                        Text("WYSŁANE", color = Color.White.copy(alpha = 0.35f), fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 2.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
                    }
                    items(wyslane.reversed(), key = { "w"+it }) { w ->
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                            Box(Modifier.clip(RoundedCornerShape(16.dp)).background(Brush.horizontalGradient(listOf(ZielonyMorski, ZielonyNeon))).padding(horizontal = 14.dp, vertical = 10.dp)) {
                                Text(w, color = Color(0xFF06231A), fontSize = 14.sp)
                            }
                        }
                    }
                }
                if (sugestie.isNotEmpty()) {
                    item(key = "naglowek-kontakty") {
                        Text("DOSTĘPNE KONTAKTY", color = Color.White.copy(alpha = 0.35f), fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 2.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
                    }
                    items(sugestie, key = { it.name + it.number }) { s ->
                        val nrDoDodania = s.numery.firstOrNull() ?: s.number ?: ""
                        val dodany = nrDoDodania in wybrani
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                .background(Brush.verticalGradient(listOf(Color(0xFF131C18), Color(0xFF0B120F))))
                                .then(if (dodany) Modifier.border(1.dp, ZielonyNeon.copy(alpha = 0.5f), RoundedCornerShape(16.dp)) else Modifier.border(1.dp, ZielonyNeon.copy(alpha = 0.1f), RoundedCornerShape(16.dp)))
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(s.name, color = Color.White, fontWeight = FontWeight.Medium, maxLines = 1)
                                if (s.numery.isNotEmpty()) Text(s.numery.joinToString("  •  "), fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (dodany) {
                                Text("✓", color = ZielonyNeon, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            } else {
                                Box(
                                    Modifier.size(34.dp).clip(CircleShape)
                                        .background(Brush.horizontalGradient(listOf(ZielonyMorski, ZielonyNeon)))
                                        .clickable { dodaj(nrDoDodania) },
                                    contentAlignment = Alignment.Center
                                ) { Text("+", color = Color(0xFF06231A), fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                }
            }
        }

// Pasek wpisywania (jak czat) — z przyciskiem załącznika (MMS) przy lewym rogu.
        Box(
            Modifier.fillMaxWidth().padding(4.dp, 6.dp, 4.dp, 12.dp).height(54.dp)
                .clip(RoundedCornerShape(16.dp)).background(Color(0xFF131C18))
                .border(1.dp, ZielonyNeon.copy(alpha = 0.14f), RoundedCornerShape(16.dp))
        ) {
            // Przycisk załącznika (spinacz) — lewy róg, pełna wysokość + menu opcji nad nim.
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(58.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                    .then(if (zalacznikUri != null) Modifier.background(Brush.horizontalGradient(listOf(ZielonyMorski, ZielonyNeon))) else Modifier.background(ZielonyNeon.copy(alpha = 0.16f)))
                    .clickable { pokazWyborZalacznika = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(IkonyAplikacji.Zlacznik, contentDescription = "Dodaj za\u0142\u0105cznik", tint = if (zalacznikUri != null) Color(0xFF06231A) else ZielonyNeon, modifier = Modifier.size(28.dp))
                if (pokazWyborZalacznika) {
                    Popup(
                        alignment = Alignment.TopStart,
                        offset = IntOffset(0, -220),
                        onDismissRequest = { pokazWyborZalacznika = false }
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            OpcjaZalacznikaN("Galeria", IkonyAplikacji.Galeria) {
                                pokazWyborZalacznika = false
                                launcherGaleria.launch("image/*")
                            }
                            OpcjaZalacznikaN("Zr\u00f3b zdj\u0119cie", IkonyAplikacji.Kamera) {
                                pokazWyborZalacznika = false
                                val plik = java.io.File(context.cacheDir, "images").apply { mkdirs() }
                                val cel = java.io.File(plik, "cam_" + System.currentTimeMillis() + ".jpg")
                                tymczasowyZdjecieFile.value = cel
                                val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".fileprovider", cel)
                                launcherKamera.launch(uri)
                            }
                        }
                    }
                }
            }
            // Pole wpisywania — odsunięte od przycisków.
            BasicTextField(tresc, { tresc = it }, textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                cursorBrush = SolidColor(ZielonyNeon), maxLines = 6,
                modifier = Modifier.align(Alignment.CenterStart).fillMaxWidth().padding(start = 64.dp, end = 64.dp).padding(vertical = 6.dp),
                decorationBox = { if (tresc.isEmpty()) Text("Napisz wiadomo\u015b\u0107\u2026", color = Color.White.copy(alpha = 0.32f), fontSize = 15.sp); it() })
            // Przycisk wysyłki — prawy róg, pełna wysokość.
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(58.dp)
                    .clip(RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp))
                    .then(if (tresc.isNotBlank() || zalacznikUri != null) Modifier.background(Brush.horizontalGradient(listOf(ZielonyMorski, ZielonyNeon))) else Modifier.background(ZielonyNeon.copy(alpha = 0.18f)))
                    .clickable {
                        if (zalacznikUri != null) {
                            // MMS ze zdjęciem.
                            zalacznikUri?.let { uri -> wyslijMms(context, wybrani.firstOrNull() ?: "", uri, tresc) }
                            zalacznikUri = null; zalacznikPodglad = null
                            tresc = ""
                            onWyslano()
                        } else if (tresc.isNotBlank()) {
                            wyslij()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(IkonyAplikacji.Strzalka, contentDescription = "Wy\u015blij", tint = if (tresc.isNotBlank() || zalacznikUri != null) Color(0xFF06231A) else ZielonyNeon, modifier = Modifier.size(26.dp))
            }
        }
    }
}

/** Pojedyncza opcja w menu załącznika nowej wiadomości — bez tła (ikona + tekst). */
@Composable
private fun OpcjaZalacznikaN(tekst: String, ikona: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(ikona, contentDescription = null, tint = ZielonyNeon, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(tekst, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}
