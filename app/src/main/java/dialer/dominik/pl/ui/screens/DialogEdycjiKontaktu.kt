package dialer.dominik.pl.ui.screens

import android.Manifest
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dialer.dominik.pl.ui.components.IkonyAplikacji
import dialer.dominik.pl.ui.theme.CzerwonyPolaczenia
import dialer.dominik.pl.ui.theme.ZielonyMorski
import dialer.dominik.pl.ui.theme.ZielonyNeon
import dialer.dominik.pl.data.maUprawnienieZapisu
import dialer.dominik.pl.data.pelnaNazwa
import dialer.dominik.pl.data.pobierzDaneDoEdycji
import dialer.dominik.pl.data.zapiszEdycjeKontaktu
import dialer.dominik.pl.data.usunKontakt

@Composable
internal fun DialogEdytujKontakt(
    context: Context,
    kontakt: KontaktItem,
    onDismiss: () -> Unit,
    onZapisano: () -> Unit
) {
    val dane = remember(kontakt) { pobierzDaneDoEdycji(context, kontakt) }
    var imie by remember(kontakt) { mutableStateOf(dane.imie) }
    var nazwisko by remember(kontakt) { mutableStateOf(dane.nazwisko) }
    var firma by remember(kontakt) { mutableStateOf(dane.firma) }
    var pseudonim by remember(kontakt) {
        mutableStateOf(pobierzPseudonim(context, kontakt.name) ?: "")
    }
    var numery by remember(kontakt) {
        mutableStateOf(dane.numery.map { it.surowy }.toMutableList())
    }
    var uriZdjecia by remember { mutableStateOf<Uri?>(null) }
            var kadrowanyBytes by remember { mutableStateOf<ByteArray?>(null) }
            var pokazKadrowanie by remember { mutableStateOf(false) }
            var pokazPotwierdzenieUsuniecia by remember { mutableStateOf(false) }
            // true = user usunął zdjęcie (long-press → kosz); przy zapisie usuń zdjęcie w książce.
            var usunZdjecie by remember { mutableStateOf(false) }

    val launcherZdjecia = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
            if (uri != null) {
                uriZdjecia = uri
                pokazKadrowanie = true
                // Wybranie nowego zdjęcia anuluje wcześniejszą decyzję o usunięciu.
                usunZdjecie = false
            }
        }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { przyznano ->
        if (przyznano) {
            zapisEdycji(context, kontakt, imie, nazwisko, firma, dane.czyMaFirme, dane.rawContactId, pseudonim, numery, kadrowanyBytes, usunZdjecie, onZapisano, onDismiss)
                    } else {
                        Toast.makeText(context, "Brak uprawnienia do zapisu kontaktów", Toast.LENGTH_SHORT).show()
                    }
                }

    fun zapisz() {
            if (!maUprawnienieZapisu(context)) {
                launcher.launch(Manifest.permission.WRITE_CONTACTS)
            } else {
                            zapisEdycji(context, kontakt, imie, nazwisko, firma, dane.czyMaFirme, dane.rawContactId, pseudonim, numery, kadrowanyBytes, usunZdjecie, onZapisano, onDismiss)
                        }
                    }

        fun usun() {
            if (!maUprawnienieZapisu(context)) {
                launcher.launch(Manifest.permission.WRITE_CONTACTS)
                return
            }
            val sukces = usunKontakt(context, kontakt.contactId)
            if (sukces) {
                Toast.makeText(context, "Kontakt usunięty", Toast.LENGTH_SHORT).show()
                // Pseudonim zapisany w aplikacji też powinien zniknąć.
                zapiszPseudonim(context, kontakt.name, "")
                onZapisano()
                onDismiss()
            } else {
                Toast.makeText(context, "Nie udało się usunąć kontaktu", Toast.LENGTH_SHORT).show()
            }
        }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF16221C), Color(0xFF0D1611))
                    )
                )
                .border(
                    BorderStroke(1.dp, ZielonyNeon.copy(alpha = 0.25f)),
                    RoundedCornerShape(28.dp)
                )
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 680.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                NaglowekEdycji(onDismiss, onDelete = { pokazPotwierdzenieUsuniecia = true })

                // Klikalny awatar — wybór zdjęcia kontaktu z galerii.
                AvatarEdycji(
                                    context = context,
                                    inicial = (imie.firstOrNull() ?: nazwisko.firstOrNull())?.uppercaseChar()?.toString() ?: "?",
                                    zdjecieBytes = kadrowanyBytes,
                                    // Gdy user usunął zdjęcie (kosz) — nie pokazuj dotychczasowego zdjęcia
                                    // z książki, żeby awatar natychmiast zniknął do inicjału.
                                    fotoUri = if (usunZdjecie) null else kontakt.fotoUri,
                                    onClick = {
                                        launcherZdjecia.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    // Long-press na zdjęciu → kosz: usuń zdjęcie całkowicie przy zapisie.
                                    onUsunZdjecie = {
                                        kadrowanyBytes = null
                                        usunZdjecie = true
                                    }
                                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PoleTekstoweKontakt(
                        imie,
                        { imie = it },
                        "Imię",
                        IkonyAplikacji.NowyKontakt,
                        Modifier.weight(1f)
                    )
                    PoleTekstoweKontakt(
                        nazwisko,
                        { nazwisko = it },
                        "Nazwisko",
                        IkonyAplikacji.NowyKontakt,
                        Modifier.weight(1f)
                    )
                }

                PoleTekstoweKontakt(
                    pseudonim,
                    { pseudonim = it },
                    "Pseudonim",
                    IkonyAplikacji.Pseudonim,
                    Modifier.fillMaxWidth()
                )

                PoleTekstoweKontakt(
                    firma,
                    { firma = it },
                    "Firma",
                    IkonyAplikacji.Firma,
                    Modifier.fillMaxWidth()
                )

                SekcjaNumerow(numery) { nowe -> numery = nowe.toMutableList() }

                Spacer(Modifier.height(2.dp))

                PrzyciskiEdycji(onDismiss, ::zapisz)
            }
        }
    }

    // Okno kadrowania zdjęcia — po wyborze z galerii.
    if (pokazKadrowanie && uriZdjecia != null) {
        DialogKadrowaniaZdjecia(
            context = context,
            uri = uriZdjecia!!,
            onCancel = { pokazKadrowanie = false },
            onGotowy = { bytes ->
                                        android.util.Log.i("EdycjaZdjecia", "OTRZYMANO bytes: ${bytes.size}")
                                        kadrowanyBytes = bytes
                                        pokazKadrowanie = false
                                    }
                    )
                }

                // Potwierdzenie usunięcia kontaktu — niezależne okno, na końcu.
                if (pokazPotwierdzenieUsuniecia) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { pokazPotwierdzenieUsuniecia = false },
                        containerColor = Color(0xFF16221C),
                        title = {
                            Text("Usunąć kontakt?", color = Color.White, fontWeight = FontWeight.Bold)
                        },
                        text = {
                            Text(
                                "Kontakt „${kontakt.name}” zostanie trwale usunięty z książki telefonicznej.",
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                pokazPotwierdzenieUsuniecia = false
                                usun()
                            }) {
                                Text("Usuń", color = CzerwonyPolaczenia, fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { pokazPotwierdzenieUsuniecia = false }) {
                                Text("Anuluj", color = Color.White.copy(alpha = 0.7f))
                            }
                        }
                    )
                }
            }

@Composable
private fun NaglowekEdycji(onDismiss: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Edytuj kontakt",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            ),
            color = Color.White
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Kosz (usunięcie) — po lewej stronie krzyżyka.
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(CzerwonyPolaczenia.copy(alpha = 0.16f))
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = IkonyAplikacji.Kosz,
                    contentDescription = "Usuń kontakt",
                    tint = CzerwonyPolaczenia,
                    modifier = Modifier.size(20.dp)
                )
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.07f))
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = IkonyAplikacji.Zamknij,
                    contentDescription = "Zamknij",
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun SekcjaNumerow(numery: List<String>, onChange: (List<String>) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Numery telefonu",
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(ZielonyNeon.copy(alpha = 0.15f))
                .clickable { onChange(numery + "") },
            contentAlignment = Alignment.Center
        ) {
            Text("+", color = ZielonyNeon, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }

    numery.forEachIndexed { indeks, numer ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PoleTekstoweKontakt(
                value = numer,
                onValueChange = { nowy ->
                    val nowa = numery.toMutableList()
                    nowa[indeks] = nowy
                    onChange(nowa)
                },
                etykieta = "Numer ${indeks + 1}",
                ikona = IkonyAplikacji.Sluchawka,
                modifier = Modifier.weight(1f)
            )
            if (numery.size > 1) {
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0x33FF4D4D))
                        .clickable {
                            val nowa = numery.toMutableList()
                            nowa.removeAt(indeks)
                            onChange(nowa)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "✕",
                        color = Color(0xFF8A8A8A),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun PrzyciskiEdycji(onDismiss: () -> Unit, onSave: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(0.5f)
                .height(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = IkonyAplikacji.Zamknij,
                contentDescription = "Anuluj",
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(22.dp)
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .height(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.horizontalGradient(listOf(ZielonyMorski, ZielonyNeon)))
                .clickable { onSave() },
            contentAlignment = Alignment.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = IkonyAplikacji.Sprawdzenie,
                    contentDescription = "Zapisz",
                    tint = Color(0xFF06231A),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Zapisz",
                    color = Color(0xFF06231A),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
            }
        }

        /** Wykonuje zapis edycji — osobna funkcja, wywoływana z przycisku i z launcher-a. */
private fun zapisEdycji(
    context: Context,
    kontakt: KontaktItem,
    imie: String,
    nazwisko: String,
    firma: String,
    czyMaFirme: Boolean,
    rawContactId: Long,
    pseudonim: String,
    numery: List<String>,
        zdjecieBytes: ByteArray?,
        usunZdjecie: Boolean,
        onZapisano: () -> Unit,
        onDismiss: () -> Unit
    ) {
    val finalne = MutableList<NumerKontaktu>(numery.size) { i ->
            val surowy = numery[i].trim()
            // Zachowaj ID istniejącego numeru na tej samej pozycji (update treści po tym ID),
            // niezależnie od tego, czy numer się zmienił. Nowe pozycje ponad oryginalną
            // liczbę → id="" (insert). Bez tego zmiana numeru dostaje id="" → w zapisie
            // robi się INSERT zamiast UPDATE, a stary wiersz zostaje (duplikat), a w nagorze
            // przypadki update na nieistniejącym ID wywraca cały batch i zdjęcie też się nie zapisze.
            val istniejacy = kontakt.numery.getOrNull(i)
            if (surowy.isBlank()) {
                            // Puste pole — wiersz bez numeru; id="" (nie zachowuj) — wtedy
                            // zapiszEdycjeKontaktu usunie oryginalny numer (bo jego id wypadnie
                            // z finalneId), zamiast nadpisywać treść pustym stringiem.
                            NumerKontaktu(id = "", surowy = "", normalizowany = "")
                        } else if (istniejacy != null) {
                // Ta pozycja już miała numer — zachowaj jego ID (update, nawet gdy zmieniony).
                NumerKontaktu(id = istniejacy.id, surowy = surowy, normalizowany = normalizujKontakt(surowy))
            } else {
                // Nowa pozycja ponad oryginalne numery — insert.
                NumerKontaktu(id = "", surowy = surowy, normalizowany = normalizujKontakt(surowy))
            }
        }

    val nowaNazwa = pelnaNazwa(imie, nazwisko)
    val sukces = zapiszEdycjeKontaktu(context, kontakt, imie, nazwisko, firma, czyMaFirme, rawContactId, finalne, zdjecieBytes, usunZdjecie)

    if (sukces) {
        if (nowaNazwa == kontakt.name.trim()) {
            zapiszPseudonim(context, nowaNazwa, pseudonim)
        } else {
            przeniesPseudonim(context, kontakt.name, nowaNazwa, pseudonim)
            przeniesUlubione(context, kontakt.name, nowaNazwa)
        }
        Toast.makeText(context, "Kontakt zaktualizowany", Toast.LENGTH_SHORT).show()
        onZapisano()
        onDismiss()
    }
}

/** Klikalny awatar w oknie edycji — podgląd wybranego zdjęcia lub inicjał + ikona aparatu.
 *  Long-press na zdjęciu → czerwona poświata + ikona kosza na środku; kliknięcie kosza usuwa zdjęcie. */
@Composable
internal fun AvatarEdycji(
    context: Context,
    inicial: String,
    zdjecieBytes: ByteArray?,
    fotoUri: String? = null,
    onClick: () -> Unit,
    onUsunZdjecie: () -> Unit = {}
) {
    var pokazKosz by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(84.dp)
            .clip(CircleShape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = { pokazKosz = true }
            ),
        contentAlignment = Alignment.Center
    ) {
        // Priorytet: nowo wykadrowane zdjęcie, potem zdjęcie już zapisane w kontakcie.
        val bitmap = remember(zdjecieBytes, fotoUri) {
            try {
                when {
                    zdjecieBytes != null ->
                        BitmapFactory.decodeByteArray(zdjecieBytes, 0, zdjecieBytes.size)?.asImageBitmap()
                    !fotoUri.isNullOrBlank() ->
                        context.contentResolver.openInputStream(android.net.Uri.parse(fotoUri))
                            ?.use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
                    else -> null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "Zdjęcie kontaktu",
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                dialer.dominik.pl.ui.theme.ZielonyMorski.copy(alpha = 0.35f),
                                dialer.dominik.pl.ui.theme.ZielonyMorski.copy(alpha = 0.08f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = inicial,
                    color = ZielonyNeon,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        // Ikona aparatu (tylko gdy zdjęcia nie ma i nie w trybie usuwania).
        if (bitmap == null && !pokazKosz) {
            Icon(
                painter = painterResource(dialer.dominik.pl.R.drawable.ic_logo_nowoczesny),
                contentDescription = "Dodaj zdjęcie",
                tint = ZielonyNeon,
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }
        // Long-press aktywowany → czerwona poświata + kosz na środku, klik usuwa zdjęcie.
        if (pokazKosz) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(CzerwonyPolaczenia.copy(alpha = 0.55f))
                    .clickable(onClick = {
                        pokazKosz = false
                        onUsunZdjecie()
                    }),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                                    imageVector = IkonyAplikacji.Kosz,
                                    contentDescription = "Usuń zdjęcie",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
            }
        }
    }
}
