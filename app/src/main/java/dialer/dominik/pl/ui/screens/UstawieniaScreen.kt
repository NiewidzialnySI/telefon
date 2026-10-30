package dialer.dominik.pl.ui.screens

import android.app.Activity
import android.content.Context
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.scale
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.graphics.vector.ImageVector
import dialer.dominik.pl.ui.components.IkonyAplikacji
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dialer.dominik.pl.backup.BackupDyskGoogle
import dialer.dominik.pl.backup.BackupForegroundService
import dialer.dominik.pl.backup.BackupStan
import dialer.dominik.pl.nagranie.NagrywanieUstawienia
import dialer.dominik.pl.nagranie.ShellKlient
import dialer.dominik.pl.nagranie.ShizukuKlient
import dialer.dominik.pl.nagranie.ShizukuStan
import dialer.dominik.pl.nagranie.adb.AdbParowanieService
import dialer.dominik.pl.nagranie.adb.ShellAdbBootstrap
import dialer.dominik.pl.update.AktualizacjaAplikacji
import dialer.dominik.pl.ui.theme.AnimacjaTla
import dialer.dominik.pl.ui.theme.CzerwonyPolaczenia
import dialer.dominik.pl.ui.theme.EkranMotywu
import dialer.dominik.pl.ui.theme.LocalMotyw
import dialer.dominik.pl.ui.theme.MotywPaletyPresety
import dialer.dominik.pl.ui.theme.MotywStan
import dialer.dominik.pl.ui.theme.NaPrzycisku
import dialer.dominik.pl.ui.theme.NadpisaniaEkranu
import dialer.dominik.pl.ui.theme.TrybMotywu
import dialer.dominik.pl.ui.theme.ZielonyMorski
import dialer.dominik.pl.ui.theme.ZielonyNeon
import dialer.dominik.pl.ui.theme.czyMotywWiedzmin
import dialer.dominik.pl.ui.theme.motywAkcent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private val MIESIAC_MS = TimeUnit.DAYS.toMillis(30)

/**
 * Zakładka Ustawienia — kafelek konta Google + backup w tle.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UstawieniaScreen(context: Context, onBack: () -> Unit = {}) {
    val wiedzmin = czyMotywWiedzmin()
    val akcent = motywAkcent()
    val akcentMorski = if (wiedzmin) akcent else dialer.dominik.pl.ui.theme.ZielonyMorski
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val stan by BackupStan.stan.collectAsState()
    var oczekujeZgodyNa by remember { mutableStateOf(BackupStan.Tryb.Backup) }
    var laczenieKonta by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        BackupStan.wczytajCache(context)
        BackupForegroundService.usunZaleglyPostep(context)
    }

    fun startSerwisu(token: String, przywroc: Boolean) {
        BackupStan.tokenTymczasowy = token
        BackupForegroundService.start(context, przywroc = przywroc)
    }

    fun poTokenie(token: String, tryb: BackupStan.Tryb) {
        scope.launch {
            BackupDyskGoogle.odswiezProfilIMeta(context, token)
            when (tryb) {
                BackupStan.Tryb.Backup -> startSerwisu(token, przywroc = false)
                BackupStan.Tryb.Przywroc -> startSerwisu(token, przywroc = true)
                BackupStan.Tryb.Bezczynny -> Unit
            }
        }
    }

    val launcherZgody = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val act = activity ?: return@rememberLauncherForActivityResult
        scope.launch {
            when (val auth = BackupDyskGoogle.tokenZIntentu(act, result.data)) {
                is BackupDyskGoogle.AuthWynik.Token -> {
                    if (laczenieKonta) {
                        laczenieKonta = false
                        BackupDyskGoogle.odswiezProfilIMeta(context, auth.accessToken)
                    } else {
                        poTokenie(auth.accessToken, oczekujeZgodyNa)
                    }
                }
                is BackupDyskGoogle.AuthWynik.Blad -> {
                    laczenieKonta = false
                    BackupStan.blad(auth.komunikat)
                    Toast.makeText(context, auth.komunikat, Toast.LENGTH_LONG).show()
                }
                else -> laczenieKonta = false
            }
        }
    }

    fun autoryzujIUruchom(tryb: BackupStan.Tryb) {
        val act = activity
        if (act == null) {
            Toast.makeText(context, "Brak Activity", Toast.LENGTH_SHORT).show()
            return
        }
        if (BackupStan.czyTrwa()) return
        scope.launch {
            when (val auth = BackupDyskGoogle.autoryzuj(act)) {
                is BackupDyskGoogle.AuthWynik.Token -> poTokenie(auth.accessToken, tryb)
                is BackupDyskGoogle.AuthWynik.WymagaZgody -> {
                    oczekujeZgodyNa = tryb
                    laczenieKonta = false
                    launcherZgody.launch(
                        IntentSenderRequest.Builder(auth.pendingIntent.intentSender).build()
                    )
                }
                is BackupDyskGoogle.AuthWynik.Blad -> {
                    BackupStan.blad(auth.komunikat)
                    Toast.makeText(context, auth.komunikat, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun polaczKonto() {
        val act = activity ?: return
        if (BackupStan.czyTrwa()) return
        scope.launch {
            when (val auth = BackupDyskGoogle.autoryzuj(act)) {
                is BackupDyskGoogle.AuthWynik.Token -> {
                    BackupDyskGoogle.odswiezProfilIMeta(context, auth.accessToken)
                }
                is BackupDyskGoogle.AuthWynik.WymagaZgody -> {
                    laczenieKonta = true
                    launcherZgody.launch(
                        IntentSenderRequest.Builder(auth.pendingIntent.intentSender).build()
                    )
                }
                is BackupDyskGoogle.AuthWynik.Blad -> {
                    Toast.makeText(context, auth.komunikat, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Ciche odświeżenie profilu przy wejściu (bez dialogu, jeśli brak zgody).
    LaunchedEffect(Unit) {
        val act = activity ?: return@LaunchedEffect
        when (val auth = BackupDyskGoogle.autoryzuj(act)) {
            is BackupDyskGoogle.AuthWynik.Token -> {
                BackupDyskGoogle.odswiezProfilIMeta(context, auth.accessToken)
            }
            else -> Unit
        }
    }

    LaunchedEffect(stan.sukces, stan.blad) {
        stan.sukces?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
        stan.blad?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "USTAWIENIA",
                        color = Color.White.copy(alpha = 0.42f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 2.2.sp
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { odstepy ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(odstepy),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                KafelekKontaIKopii(
                    profil = stan.profil,
                    stan = stan,
                    onPolacz = { polaczKonto() },
                    onBackup = { autoryzujIUruchom(BackupStan.Tryb.Backup) },
                    onPrzywroc = { autoryzujIUruchom(BackupStan.Tryb.Przywroc) }
                )
            }

            item {
                Text(
                    text = "NAGRYWANIE",
                    color = akcent.copy(alpha = 0.55f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.4.sp,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                )
            }
            item {
                SekcjaNagrywaniaRozmow(context = context)
            }

            item {
                Text(
                    text = "AKTUALIZACJA",
                    color = akcent.copy(alpha = 0.55f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.4.sp,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                )
            }
            item {
                SekcjaAktualizacji(context = context)
            }

            item {
                Text(
                    text = "MOTYW",
                    color = akcent.copy(alpha = 0.55f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.4.sp,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                )
            }
            item {
                SekcjaMotywu(context = context)
            }
        }
    }
}

@Composable
private fun SekcjaMotywu(context: Context) {
    val wiedzmin = czyMotywWiedzmin()
    val akcent = motywAkcent()
    val akcentMorski = if (wiedzmin) akcent else dialer.dominik.pl.ui.theme.ZielonyMorski

    val motyw by MotywStan.stan.collectAsState()
    val paleta = LocalMotyw.current

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // System / Jasny / Ciemny
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(paleta.kafelek)
                .border(1.dp, paleta.kafelekRamka, RoundedCornerShape(16.dp))
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                TrybMotywu.System to "System",
                TrybMotywu.Jasny to "Jasny",
                TrybMotywu.Ciemny to "Ciemny"
            ).forEach { (tryb, etykieta) ->
                val aktywny = motyw.tryb == tryb
                val skala by animateFloatAsState(
                    targetValue = if (aktywny) 1f else 0.96f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "skalaTryb"
                )
                val tlo by animateColorAsState(
                    targetValue = if (aktywny) akcent else Color.Transparent,
                    animationSpec = tween(280),
                    label = "tloTryb"
                )
                val tekst by animateColorAsState(
                    targetValue = when {
                        aktywny && akcent.luminance() > 0.45f -> NaPrzycisku
                        aktywny -> Color.White
                        else -> Color.White.copy(alpha = 0.55f).let {
                            if (!paleta.ciemny) Color.Black.copy(alpha = 0.55f) else it
                        }
                    },
                    animationSpec = tween(280),
                    label = "tekstTryb"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .scale(skala)
                        .clip(RoundedCornerShape(12.dp))
                        .background(tlo)
                        .clickable { MotywStan.ustawTryb(context, tryb) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = etykieta,
                        color = tekst,
                        fontSize = 13.sp,
                        fontWeight = if (aktywny) FontWeight.SemiBold else FontWeight.Medium
                    )
                }
            }
        }

        // Siatka nazwanych palet: 3 swatche (seed / secondary / tertiary) + nazwa.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MotywPaletyPresety.Wszystkie.chunked(2).forEach { rzad ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rzad.forEach { preset ->
                        val wybrany = motyw.paletaId == preset.id ||
                            (motyw.paletaId == "custom" && motyw.seed.toArgb() == preset.seed.toArgb())
                        val skala by animateFloatAsState(
                            targetValue = if (wybrany) 1.02f else 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMedium
                            ),
                            label = "skalaPaleta"
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .scale(skala)
                                .clip(RoundedCornerShape(14.dp))
                                .background(paleta.kafelek)
                                .border(
                                    width = if (wybrany) 2.dp else 1.dp,
                                    color = if (wybrany) akcent else paleta.kafelekRamka,
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable { MotywStan.ustawPalete(context, preset) }
                                .padding(10.dp)
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOfNotNull(
                                    preset.seed,
                                    preset.secondary,
                                    preset.tertiary
                                ).forEach { k ->
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(k)
                                            .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = preset.nazwa,
                                color = paleta.naTle,
                                fontSize = 12.sp,
                                fontWeight = if (wybrany) FontWeight.SemiBold else FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }
                    if (rzad.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        var rozwinZaawansowane by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(paleta.kafelek)
                .border(1.dp, paleta.kafelekRamka, RoundedCornerShape(14.dp))
                .clickable { rozwinZaawansowane = !rozwinZaawansowane }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Zaawansowane",
                color = paleta.naTle,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (rozwinZaawansowane) "▲" else "▼",
                color = paleta.tekstDrugorzedny,
                fontSize = 12.sp
            )
        }

        AnimatedVisibility(
            visible = rozwinZaawansowane,
            enter = expandVertically(tween(220)) + fadeIn(tween(180)),
            exit = shrinkVertically(tween(180)) + fadeOut(tween(140))
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                EtykietaMotywu("Animacja tła", paleta.tekstDrugorzedny)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AnimacjaTla.entries.chunked(2).forEach { rzad ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rzad.forEach { anim ->
                                val wybrany = motyw.animacjaTla == anim
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            if (wybrany) akcent.copy(alpha = 0.16f)
                                            else paleta.kafelek
                                        )
                                        .border(
                                            width = if (wybrany) 2.dp else 1.dp,
                                            color = if (wybrany) akcent else paleta.kafelekRamka,
                                            shape = RoundedCornerShape(14.dp)
                                        )
                                        .clickable { MotywStan.ustawAnimacjeTla(context, anim) }
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        text = anim.etykieta,
                                        color = paleta.naTle,
                                        fontSize = 13.sp,
                                        fontWeight = if (wybrany) FontWeight.SemiBold else FontWeight.Medium
                                    )
                                    Text(
                                        text = anim.opis,
                                        color = paleta.tekstDrugorzedny,
                                        fontSize = 11.sp,
                                        maxLines = 2
                                    )
                                }
                            }
                            if (rzad.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }

                if (motyw.animacjaTla == AnimacjaTla.Wiedzmin || motyw.paletaId == "wiedzmin") {
                    EtykietaMotywu("Tło Wiedźmin", paleta.tekstDrugorzedny)
                    SekcjaTlaWiedzmina(
                        motyw = motyw,
                        paleta = paleta,
                        context = context
                    )
                }

                EtykietaMotywu("Kolor przycisków", paleta.tekstDrugorzedny)
                RzedPresetowKoloru(
                    aktualny = motyw.kolorPrzycisku,
                    domyslnyOznaczaNull = true,
                    onWybierz = { MotywStan.ustawKolorPrzycisku(context, it) }
                )

                EtykietaMotywu("Kolor kafelków", paleta.tekstDrugorzedny)
                RzedPresetowKoloru(
                    aktualny = motyw.kolorKafelka,
                    domyslnyOznaczaNull = true,
                    onWybierz = { MotywStan.ustawKolorKafelka(context, it) },
                    dodatkowe = listOf(
                        Color(0xFF131C18),
                        Color(0xFF1A2430),
                        Color(0xFFF2F4F3),
                        Color(0xFFE8EEF0)
                    )
                )

                EtykietaMotywu("Kolor aurory (1)", paleta.tekstDrugorzedny)
                RzedPresetowKoloru(
                    aktualny = motyw.aurora1,
                    domyslnyOznaczaNull = true,
                    onWybierz = { MotywStan.ustawAuroraKolory(context, it, motyw.aurora2) }
                )
                EtykietaMotywu("Kolor aurory (2)", paleta.tekstDrugorzedny)
                RzedPresetowKoloru(
                    aktualny = motyw.aurora2,
                    domyslnyOznaczaNull = true,
                    onWybierz = { MotywStan.ustawAuroraKolory(context, motyw.aurora1, it) }
                )

                TextButton(onClick = { MotywStan.resetZaawansowane(context) }) {
                    Text("Reset zaawansowanych", color = CzerwonyPolaczenia, fontSize = 13.sp)
                }

                EtykietaMotywu("Motyw ekranu", paleta.tekstDrugorzedny)
                EkranMotywu.entries.forEach { ekran ->
                    KafelekEkranuMotywu(context = context, ekran = ekran, motyw = motyw)
                }
                TextButton(onClick = { MotywStan.resetEkrany(context) }) {
                    Text("Reset motywów ekranów", color = CzerwonyPolaczenia, fontSize = 13.sp)
                }
            }
        }

        TextButton(
            onClick = { MotywStan.resetMotywu(context) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Reset motywu", color = CzerwonyPolaczenia, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun EtykietaMotywu(tekst: String, kolor: Color) {
    Text(
        text = tekst,
        color = kolor,
        fontSize = 12.sp,
        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
    )
}

@Composable
private fun SekcjaTlaWiedzmina(
    motyw: MotywStan.Stan,
    paleta: dialer.dominik.pl.ui.theme.MotywPaleta,
    context: Context
) {
    val wiedzmin = czyMotywWiedzmin()
    val akcent = motywAkcent()
    val akcentMorski = if (wiedzmin) akcent else dialer.dominik.pl.ui.theme.ZielonyMorski

    val maWlasne = !motyw.wiedzminTloWlasneSciezka.isNullOrBlank()
    val wybierzObraz = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val ok = MotywStan.ustawWiedzminTloWlasne(context, uri)
        Toast.makeText(
            context,
            if (ok) "Własne tło zapisane" else "Nie udało się wczytać obrazu",
            Toast.LENGTH_SHORT
        ).show()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(paleta.kafelek)
            .border(1.dp, paleta.kafelekRamka, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Statyczny obraz",
                    color = paleta.naTle,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Wyłącza pętlę wideo — pokazuje kadr (lub własne tło)",
                    color = paleta.tekstDrugorzedny,
                    fontSize = 11.sp
                )
            }
            Switch(
                checked = motyw.wiedzminTloStatyczne,
                onCheckedChange = { MotywStan.ustawWiedzminTloStatyczne(context, it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = akcent,
                    checkedTrackColor = akcentMorski.copy(alpha = 0.55f)
                )
            )
        }

        TextButton(
            onClick = {
                wybierzObraz.launch(
                    androidx.activity.result.PickVisualMediaRequest(
                        ActivityResultContracts.PickVisualMedia.ImageOnly
                    )
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = if (maWlasne) "Zmień własne tło" else "Wgraj własne tło",
                color = akcent,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
        }

        if (maWlasne) {
            Text(
                text = "Używane jest Twoje zdjęcie z galerii",
                color = paleta.tekstDrugorzedny,
                fontSize = 11.sp
            )
            TextButton(
                onClick = {
                    MotywStan.usunWiedzminTloWlasne(context)
                    Toast.makeText(context, "Przywrócono domyślne tło", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Przywróć domyślne tło", color = CzerwonyPolaczenia, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun RzedPresetowKoloru(
    aktualny: Color?,
    domyslnyOznaczaNull: Boolean,
    onWybierz: (Color?) -> Unit,
    dodatkowe: List<Color> = emptyList()
) {
    val wiedzmin = czyMotywWiedzmin()
    val akcent = motywAkcent()
    val akcentMorski = if (wiedzmin) akcent else dialer.dominik.pl.ui.theme.ZielonyMorski

    val kolory = MotywStan.PRESETY + dodatkowe
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (domyslnyOznaczaNull) {
            val auto = aktualny == null
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(
                        if (auto) 2.dp else 1.dp,
                        if (auto) akcent else Color.White.copy(alpha = 0.2f),
                        CircleShape
                    )
                    .clickable { onWybierz(null) },
                contentAlignment = Alignment.Center
            ) {
                Text("A", color = akcent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        kolory.distinctBy { it.toArgb() }.take(8).forEach { kolor ->
            val wybrany = aktualny != null && aktualny.toArgb() == kolor.toArgb()
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(kolor)
                    .border(
                        if (wybrany) 2.dp else 1.dp,
                        if (wybrany) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.12f),
                        CircleShape
                    )
                    .clickable { onWybierz(kolor) }
            )
        }
    }
}

@Composable
private fun KafelekEkranuMotywu(
    context: Context,
    ekran: EkranMotywu,
    motyw: MotywStan.Stan
) {
    val wiedzmin = czyMotywWiedzmin()
    val akcent = motywAkcent()
    val akcentMorski = if (wiedzmin) akcent else dialer.dominik.pl.ui.theme.ZielonyMorski

    val paleta = LocalMotyw.current
    val nad = motyw.ekrany[ekran]
    var rozwin by remember(ekran) { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(paleta.kafelek)
            .border(1.dp, paleta.kafelekRamka, RoundedCornerShape(14.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { rozwin = !rozwin }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = ekran.etykieta,
                color = paleta.naTle,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (nad != null) "własny" else "globalny",
                color = if (nad != null) akcent else paleta.tekstDrugorzedny,
                fontSize = 11.sp
            )
        }
        AnimatedVisibility(visible = rozwin) {
            Column(
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EtykietaMotywu("Akcent ekranu", paleta.tekstDrugorzedny)
                RzedPresetowKoloru(
                    aktualny = nad?.seed,
                    domyslnyOznaczaNull = true,
                    onWybierz = { seed ->
                        MotywStan.ustawNadpisanieEkranu(
                            context,
                            ekran,
                            NadpisaniaEkranu(
                                seed = seed,
                                kafelek = nad?.kafelek,
                                przycisk = nad?.przycisk
                            )
                        )
                    }
                )
                EtykietaMotywu("Kafelek ekranu", paleta.tekstDrugorzedny)
                RzedPresetowKoloru(
                    aktualny = nad?.kafelek,
                    domyslnyOznaczaNull = true,
                    onWybierz = { kaf ->
                        MotywStan.ustawNadpisanieEkranu(
                            context,
                            ekran,
                            NadpisaniaEkranu(
                                seed = nad?.seed,
                                kafelek = kaf,
                                przycisk = nad?.przycisk
                            )
                        )
                    },
                    dodatkowe = listOf(Color(0xFF131C18), Color(0xFFF2F4F3))
                )
            }
        }
    }
}

/** Jedna karta: konto Google + akcje kopii zapasowej. */
@Composable
private fun KafelekKontaIKopii(
    profil: BackupStan.Profil?,
    stan: BackupStan.StanUi,
    onPolacz: () -> Unit,
    onBackup: () -> Unit,
    onPrzywroc: () -> Unit
) {
    val wiedzmin = czyMotywWiedzmin()
    val akcent = motywAkcent()
    val akcentMorski = if (wiedzmin) akcent else dialer.dominik.pl.ui.theme.ZielonyMorski

    val paleta = LocalMotyw.current
    val ksztalt = RoundedCornerShape(22.dp)
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(
        initialValue = null,
        key1 = profil?.zdjecieUrl
    ) {
        val url = profil?.zdjecieUrl
        value = if (url.isNullOrBlank()) null
        else withContext(Dispatchers.IO) {
            runCatching {
                URL(url).openStream().use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
            }.getOrNull()
        }
    }

    val backupTrwa = stan.tryb == BackupStan.Tryb.Backup
    val przywrocTrwa = stan.tryb == BackupStan.Tryb.Przywroc
    val postepBackup by animateFloatAsState(
        targetValue = if (backupTrwa) stan.postep.coerceAtLeast(0.04f) else 0f,
        animationSpec = tween(280),
        label = "postepBackup"
    )
    val postepPrzywroc by animateFloatAsState(
        targetValue = if (przywrocTrwa) stan.postep.coerceAtLeast(0.04f) else 0f,
        animationSpec = tween(280),
        label = "postepPrzywroc"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ksztalt)
            .background(
                Brush.verticalGradient(
                    if (paleta.ciemny) listOf(Color(0xFF16352A), Color(0xFF0C1812))
                    else listOf(paleta.kafelek, paleta.tlo)
                )
            )
            .border(1.dp, akcent.copy(alpha = 0.22f), ksztalt)
    ) {
        // --- Nagłówek konta ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = profil == null, onClick = onPolacz)
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(akcentMorski.copy(alpha = 0.35f))
                    .border(2.dp, akcent.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                val obraz = bitmap
                if (obraz != null) {
                    Image(
                        bitmap = obraz,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = (profil?.imie?.firstOrNull() ?: '?').uppercaseChar().toString(),
                        color = akcent,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (profil != null) {
                    Text(
                        text = profil.imie,
                        color = paleta.naTle,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (profil.email.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = profil.email,
                            color = paleta.tekstDrugorzedny,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Text(
                        text = "Połącz konto Google",
                        color = paleta.naTle,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "Dotknij, aby zalogować się i włączyć backup",
                        color = paleta.tekstDrugorzedny,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Separator
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(1.dp)
                .background(akcent.copy(alpha = 0.12f))
        )

        // --- Akcje kopii ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PrzyciskPostepu(
                    tytul = if (backupTrwa) {
                        stan.etykieta.ifBlank { "Tworzenie…" }
                    } else {
                        "Utwórz"
                    },
                    postep = postepBackup,
                    aktywnyPostep = backupTrwa,
                    enabled = !BackupStan.czyTrwa(),
                    onKlik = onBackup,
                    modifier = Modifier.weight(1f)
                )
                PrzyciskPostepu(
                    tytul = if (przywrocTrwa) {
                        stan.etykieta.ifBlank { "Przywracanie…" }
                    } else {
                        "Przywróć"
                    },
                    postep = postepPrzywroc,
                    aktywnyPostep = przywrocTrwa,
                    wariantDrugorzędny = true,
                    enabled = !BackupStan.czyTrwa(),
                    onKlik = onPrzywroc,
                    modifier = Modifier.weight(1f)
                )
            }
            DataOstatniegoBackupu(ostatniMs = stan.ostatniBackupMs, wewnatrzKarty = true)
        }
    }
}

@Composable
private fun PrzyciskPostepu(
    tytul: String,
    postep: Float,
    aktywnyPostep: Boolean,
    enabled: Boolean,
    wariantDrugorzędny: Boolean = false,
    onKlik: () -> Unit,
    modifier: Modifier = Modifier
) {
    val wiedzmin = czyMotywWiedzmin()
    val akcent = motywAkcent()
    val akcentMorski = if (wiedzmin) akcent else dialer.dominik.pl.ui.theme.ZielonyMorski

    val paleta = LocalMotyw.current
    val ksztalt = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(ksztalt)
            .background(
                if (wariantDrugorzędny) paleta.tlo.copy(alpha = if (paleta.ciemny) 0.35f else 0.55f)
                else akcent.copy(alpha = 0.10f)
            )
            .border(
                1.dp,
                if (wariantDrugorzędny) paleta.kafelekRamka
                else akcent.copy(alpha = 0.28f),
                ksztalt
            )
            .clickable(enabled = enabled && !aktywnyPostep, onClick = onKlik)
    ) {
        if (postep > 0.01f) {
            val wypelnienie = Modifier
                .fillMaxHeight()
                .fillMaxWidth(postep.coerceIn(0f, 1f))
            Box(
                modifier = if (wariantDrugorzędny) {
                    wypelnienie.background(akcent.copy(alpha = 0.20f))
                } else {
                    wypelnienie.background(
                        Brush.horizontalGradient(
                            listOf(
                                akcentMorski.copy(alpha = 0.65f),
                                akcent.copy(alpha = 0.40f)
                            )
                        )
                    )
                }
            )
        }
        Text(
            text = if (aktywnyPostep && postep > 0f) {
                "$tytul  ${(postep * 100).toInt()}%"
            } else {
                tytul
            },
            color = if (wariantDrugorzędny) paleta.naTle.copy(alpha = 0.85f) else akcent,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 6.dp)
        )
    }
}

@Composable
private fun DataOstatniegoBackupu(
    ostatniMs: Long,
    wewnatrzKarty: Boolean = false
) {
    val wiedzmin = czyMotywWiedzmin()
    val akcent = motywAkcent()
    val akcentMorski = if (wiedzmin) akcent else dialer.dominik.pl.ui.theme.ZielonyMorski

    val paleta = LocalMotyw.current
    val stary = ostatniMs <= 0L || System.currentTimeMillis() - ostatniMs > MIESIAC_MS
    val tekst = if (ostatniMs <= 0L) {
        "Brak kopii zapasowej"
    } else {
        val fmt = remember {
            SimpleDateFormat("d MMMM yyyy, HH:mm", Locale.forLanguageTag("pl-PL"))
        }
        "Ostatni backup: ${fmt.format(Date(ostatniMs))}"
    }
    val kolor = when {
        stary -> CzerwonyPolaczenia
        else -> paleta.tekstDrugorzedny
    }
    val tlo = when {
        stary -> CzerwonyPolaczenia.copy(alpha = 0.12f)
        wewnatrzKarty -> akcent.copy(alpha = 0.06f)
        else -> paleta.naTle.copy(alpha = 0.04f)
    }
    val ramka = when {
        stary -> CzerwonyPolaczenia.copy(alpha = 0.35f)
        wewnatrzKarty -> akcent.copy(alpha = 0.10f)
        else -> paleta.naTle.copy(alpha = 0.08f)
    }
    val ksztalt = RoundedCornerShape(if (wewnatrzKarty) 12.dp else 14.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ksztalt)
            .background(tlo)
            .border(1.dp, ramka, ksztalt)
            .padding(horizontal = 14.dp, vertical = if (wewnatrzKarty) 10.dp else 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = tekst + if (stary && ostatniMs > 0L) "  ·  starszy niż miesiąc" else "",
            color = kolor,
            fontSize = 12.sp,
            fontWeight = if (stary) FontWeight.Medium else FontWeight.Normal,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Kafelek nagrywania: włącz + Auto + 3 ikony statusu (Wi‑Fi / ADB / sparowane).
 * Parowanie i start usługi robi się sam — bez ściany tekstu.
 */
@Composable
private fun SekcjaNagrywaniaRozmow(context: Context) {
    val wiedzmin = czyMotywWiedzmin()
    val akcent = motywAkcent()
    val akcentMorski = if (wiedzmin) akcent else dialer.dominik.pl.ui.theme.ZielonyMorski

    val paleta = LocalMotyw.current
    val scope = rememberCoroutineScope()
    val ksztalt = RoundedCornerShape(16.dp)
    val lifecycleOwner = LocalLifecycleOwner.current

    var wlaczone by remember {
        mutableStateOf(NagrywanieUstawienia.czyWlaczone(context))
    }
    var auto by remember {
        mutableStateOf(NagrywanieUstawienia.czyAuto(context))
    }
    var diag by remember { mutableStateOf(ShellAdbBootstrap.zdiagnozuj(context)) }
    var busy by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }
    val shizukuStan = ShizukuKlient.stan
    val shellOk = ShellKlient.czyShellGotowy()

    fun odswiezDiag() {
        diag = ShellAdbBootstrap.zdiagnozuj(context)
    }

    fun automatyzujStart() {
        scope.launch {
            odswiezDiag()
            if (ShellKlient.czyShellGotowy()) {
                // Usługa OK — wyłącz Wireless debugging jeśli zostało włączone.
                runCatching {
                    ShellAdbBootstrap.sprobujWylaczycAdbWifiPoStarcie(context)
                }
                odswiezDiag()
                toast = null
                return@launch
            }
            busy = true
            toast = null
            if (!diag.prefsSparowane) {
                // adb_wifi + a11y (WRITE_SECURE) + nawigacja do okna z kodem + pair + powrót.
                AdbParowanieService.startujIOtworzUstawienia(context)
                toast = "Auto‑parowanie w toku…"
            } else {
                when (val w = ShellAdbBootstrap.uruchomZOpisem(context)) {
                    is ShellAdbBootstrap.WynikUruchom.Ok -> toast = null
                    is ShellAdbBootstrap.WynikUruchom.Blad -> {
                        toast = w.komunikat.take(80)
                        if (w.wymagaParowania) {
                            AdbParowanieService.startujIOtworzUstawienia(context)
                        } else if (w.otworzUstawienia) {
                            ShellAdbBootstrap.otworzWirelessDebugging(context)
                        }
                    }
                }
            }
            odswiezDiag()
            busy = false
            if (!ShellKlient.czyShellGotowy()) {
                runCatching { ShizukuKlient.sprobujPolaczyc(context) }
            }
        }
    }

    fun wlacNagrywanie(on: Boolean) {
        wlaczone = on
        NagrywanieUstawienia.ustawWlaczone(context, on)
        NagrywanieUstawienia.ustawZgodaPrawna(context, on)
        if (on) {
            if (!NagrywanieUstawienia.czyAuto(context)) {
                auto = true
                NagrywanieUstawienia.ustawAuto(context, true)
            }
            if (!NagrywanieUstawienia.czyPowiadomieniaShizuku(context)) {
                NagrywanieUstawienia.ustawPowiadomieniaShizuku(context, true)
            }
            automatyzujStart()
            // Bez force — własna ADB wystarczy; alert dopiero gdy naprawdę brak silnika.
            dialer.dominik.pl.nagranie.ShizukuStraznik.sprawdz(context, forceAlert = false)
        } else {
            toast = null
            dialer.dominik.pl.nagranie.PowiadomieniaShizuku.anulujBrak(context)
        }
    }

    LaunchedEffect(Unit) {
        ShizukuKlient.dolacz(context)
        ShizukuKlient.odswiez()
        odswiezDiag()
    }

    // Lekki polling statusu gdy kafelek otwarty i włączone (parowanie w tle).
    LaunchedEffect(wlaczone) {
        if (!wlaczone) return@LaunchedEffect
        var wylaczonoAdbWifi = false
        while (true) {
            kotlinx.coroutines.delay(2_000)
            odswiezDiag()
            if (ShellKlient.czyShellGotowy()) {
                toast = null
                busy = false
                // Po udanym starcie (np. auto‑parowanie) domknij adb_wifi raz.
                if (!wylaczonoAdbWifi &&
                    dialer.dominik.pl.nagranie.ShizukuAdbNaprawa.czyAdbWifi(context)
                ) {
                    wylaczonoAdbWifi = true
                    runCatching {
                        ShellAdbBootstrap.sprobujWylaczycAdbWifiPoStarcie(context)
                    }
                    odswiezDiag()
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) {
                ShizukuKlient.dolacz(context)
                ShizukuKlient.odswiez()
                odswiezDiag()
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ksztalt)
            .background(paleta.kafelek)
            .border(1.dp, paleta.kafelekRamka, ksztalt)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Nagrywanie",
                color = paleta.naTle,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "Auto",
                color = paleta.tekstDrugorzedny,
                fontSize = 12.sp,
                modifier = Modifier.padding(end = 8.dp)
            )
            Switch(
                checked = auto,
                enabled = wlaczone,
                onCheckedChange = {
                    auto = it
                    NagrywanieUstawienia.ustawAuto(context, it)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = akcent,
                    checkedTrackColor = akcentMorski.copy(alpha = 0.55f)
                )
            )
            Spacer(Modifier.width(6.dp))
            Switch(
                checked = wlaczone,
                onCheckedChange = { wlacNagrywanie(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = akcent,
                    checkedTrackColor = akcentMorski.copy(alpha = 0.55f)
                )
            )
        }

        AnimatedVisibility(
            visible = wlaczone,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusIkonaNagrywania(
                        ikona = IkonyAplikacji.Wifi,
                        ok = diag.wifi,
                        opis = "Wi‑Fi",
                        modifier = Modifier.weight(1f),
                    )
                    StatusIkonaNagrywania(
                        ikona = IkonyAplikacji.AdbWifi,
                        ok = diag.adbWifi,
                        opis = "ADB",
                        modifier = Modifier.weight(1f),
                    )
                    StatusIkonaNagrywania(
                        ikona = IkonyAplikacji.Sprawdzenie,
                        ok = diag.uslugaZyje || shellOk,
                        opis = when {
                            diag.uslugaZyje || shellOk -> "OK"
                            diag.prefsSparowane -> "spar."
                            else -> "para"
                        },
                        polowa = diag.prefsSparowane && !diag.uslugaZyje && !shellOk,
                        modifier = Modifier.weight(1f),
                    )
                }

                val etykieta = when {
                    busy -> "Łączę…"
                    shellOk || diag.uslugaZyje -> "Gotowe — obie strony"
                    !diag.wifi -> "Włącz Wi‑Fi"
                    !diag.prefsSparowane -> "Sparuj (zostań na oknie z kodem)"
                    !diag.adbWifi -> "Uruchom Wireless debugging"
                    else -> "Uruchom usługę"
                }
                val gotowe = shellOk || diag.uslugaZyje
                if (!gotowe) {
                    PrzyciskPostepu(
                        tytul = etykieta,
                        postep = 0f,
                        aktywnyPostep = busy,
                        enabled = !busy,
                        onKlik = { automatyzujStart() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(
                        text = etykieta,
                        color = akcent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }

                val tip = toast
                if (!tip.isNullOrBlank()) {
                    Text(
                        text = tip,
                        color = paleta.tekstDrugorzedny,
                        fontSize = 11.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Zapas Shizuku — tylko gdy ADB jeszcze nie żyje.
                if (!gotowe &&
                    shizukuStan !is ShizukuStan.Gotowe &&
                    shizukuStan !is ShizukuStan.Laczenie
                ) {
                    PrzyciskPostepu(
                        tytul = ShizukuKlient.etykietaAkcji(shizukuStan),
                        postep = 0f,
                        aktywnyPostep = false,
                        enabled = true,
                        onKlik = { scope.launch { ShizukuKlient.napraw(context) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusIkonaNagrywania(
    ikona: ImageVector,
    ok: Boolean,
    opis: String,
    modifier: Modifier = Modifier,
    polowa: Boolean = false,
) {
    val wiedzmin = czyMotywWiedzmin()
    val akcent = motywAkcent()
    val akcentMorski = if (wiedzmin) akcent else dialer.dominik.pl.ui.theme.ZielonyMorski

    val paleta = LocalMotyw.current
    val kolor = when {
        ok -> akcent
        polowa -> akcentMorski
        else -> paleta.tekstDrugorzedny.copy(alpha = 0.45f)
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(paleta.tlo.copy(alpha = if (paleta.ciemny) 0.35f else 0.55f))
            .border(1.dp, kolor.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = ikona,
            contentDescription = opis,
            tint = kolor,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = opis,
            color = kolor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

/**
 * Kafelek aktualizacji — minimalny (jak KernelSU ListItem / Obtainium):
 * wersja + Auto + jedna akcja. Bez opisów i changelogu.
 */
@Composable
private fun SekcjaAktualizacji(context: Context) {
    val wiedzmin = czyMotywWiedzmin()
    val akcent = motywAkcent()
    val akcentMorski = if (wiedzmin) akcent else dialer.dominik.pl.ui.theme.ZielonyMorski

    val paleta = LocalMotyw.current
    val scope = rememberCoroutineScope()
    val stan by AktualizacjaAplikacji.stan.collectAsState()
    var auto by remember { mutableStateOf(AktualizacjaAplikacji.autoSprawdzaj(context)) }
    val ksztalt = RoundedCornerShape(16.dp)
    val zajety = stan is AktualizacjaAplikacji.Stan.Sprawdzanie ||
        stan is AktualizacjaAplikacji.Stan.Pobieranie

    LaunchedEffect(Unit) {
        if (AktualizacjaAplikacji.githubToken(context).isNotBlank()) {
            AktualizacjaAplikacji.ustawGithubToken(context, "")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ksztalt)
            .background(paleta.kafelek)
            .border(1.dp, paleta.kafelekRamka, ksztalt)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = !zajety) {
                        scope.launch {
                            AktualizacjaAplikacji.sprawdz(context, cichyGdyAktualna = false)
                        }
                    }
                    .padding(vertical = 4.dp, horizontal = 2.dp)
            ) {
                Text(
                    text = "v${AktualizacjaAplikacji.lokalnaWersja()}",
                    color = paleta.naTle,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val podtytul = when (val s = stan) {
                    is AktualizacjaAplikacji.Stan.Sprawdzanie -> "Sprawdzanie…"
                    is AktualizacjaAplikacji.Stan.Aktualna -> "aktualna"
                    is AktualizacjaAplikacji.Stan.Dostepna -> s.info.versionName
                    is AktualizacjaAplikacji.Stan.Pobieranie -> "${(s.postep01 * 100).toInt()}%"
                    is AktualizacjaAplikacji.Stan.GotowaDoInstalacji -> "gotowe"
                    is AktualizacjaAplikacji.Stan.Blad -> s.komunikat
                    else -> "dotknij, by sprawdzić"
                }
                Text(
                    text = podtytul,
                    color = when (stan) {
                        is AktualizacjaAplikacji.Stan.Blad -> CzerwonyPolaczenia
                        is AktualizacjaAplikacji.Stan.Dostepna,
                        is AktualizacjaAplikacji.Stan.GotowaDoInstalacji,
                        is AktualizacjaAplikacji.Stan.Aktualna -> akcent
                        else -> paleta.tekstDrugorzedny
                    },
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = "Auto",
                color = paleta.tekstDrugorzedny,
                fontSize = 12.sp,
                modifier = Modifier.padding(end = 8.dp)
            )
            Switch(
                checked = auto,
                onCheckedChange = {
                    auto = it
                    AktualizacjaAplikacji.ustawAutoSprawdzaj(context, it)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = akcent,
                    checkedTrackColor = akcentMorski.copy(alpha = 0.55f)
                )
            )
        }

        when (val s = stan) {
            is AktualizacjaAplikacji.Stan.Dostepna -> {
                PrzyciskPostepu(
                    tytul = s.info.versionName,
                    postep = 0f,
                    aktywnyPostep = false,
                    enabled = true,
                    onKlik = {
                        scope.launch { AktualizacjaAplikacji.pobierz(context, s.info) }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            is AktualizacjaAplikacji.Stan.Pobieranie -> {
                PrzyciskPostepu(
                    tytul = s.info.versionName,
                    postep = s.postep01,
                    aktywnyPostep = true,
                    enabled = false,
                    onKlik = {},
                    modifier = Modifier.fillMaxWidth()
                )
            }
            is AktualizacjaAplikacji.Stan.GotowaDoInstalacji -> {
                PrzyciskPostepu(
                    tytul = "Zainstaluj",
                    postep = 0f,
                    aktywnyPostep = false,
                    enabled = true,
                    onKlik = { AktualizacjaAplikacji.zainstaluj(context, s.plik) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            else -> Unit
        }
    }
}
