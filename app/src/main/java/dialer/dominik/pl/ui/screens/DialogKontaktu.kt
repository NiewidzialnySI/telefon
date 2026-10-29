package dialer.dominik.pl.ui.screens

import android.Manifest
import android.accounts.AccountManager
import android.content.ContentProviderOperation
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dialer.dominik.pl.sms.KanalWiadomosci
import dialer.dominik.pl.sms.czyKanalDostepny
import dialer.dominik.pl.sms.zapamietajKanal
import dialer.dominik.pl.ui.components.IkonyAplikacji
import dialer.dominik.pl.ui.components.icons.IkonyKanalow
import dialer.dominik.pl.ui.theme.ZielonyMorski
import dialer.dominik.pl.ui.theme.ZielonyNeon

/** Okno dialogowe tworzenia nowego kontaktu. */
@Composable
internal fun DialogDodajKontakt(
    context: Context,
    domyslnyNumer: String,
    onDismiss: () -> Unit,
    onZapisano: () -> Unit
) {
    var imie by remember { mutableStateOf("") }
    var nazwisko by remember { mutableStateOf("") }
    var pseudonim by remember { mutableStateOf("") }
    var firma by remember { mutableStateOf("") }
    var numer by remember { mutableStateOf(domyslnyNumer) }
    var kanal by remember { mutableStateOf(KanalWiadomosci.Sms) }
    var uriZdjecia by remember { mutableStateOf<Uri?>(null) }
    var zdjecieBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pokazKadrowanie by remember { mutableStateOf(false) }

    // Wybór zdjęcia z galerii → kadrowanie → bytes (zapisywane razem z kontaktem).
    val launcherZdjecia = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            uriZdjecia = uri
            pokazKadrowanie = true
        }
    }

    // Prośba o uprawnienie zapisu kontaktów (WRITE_CONTACTS) przy kliknięciu Zapisz.
    // Bez tego ContactsContract.applyBatch rzuca SecurityException i kod
    // wpada w fallback (systemowy wybór aplikacji) — mylące dla użytkownika.
    val launcherUprawnien = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { przyznano ->
        if (przyznano) {
            val sukces = zapiszNowyKontakt(context, imie, nazwisko, pseudonim, firma, numer, kanal, zdjecieBytes)
            Toast.makeText(
                context,
                if (sukces) "Kontakt zapisany!" else "Nie udało się zapisać kontaktu",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            Toast.makeText(
                context,
                "Brak uprawnienia do zapisu kontaktów",
                Toast.LENGTH_SHORT
            ).show()
        }
        onZapisano()
    }

    fun zapiszLubPoprosOUprawnienie() {
        if (maUprawnienie(context, Manifest.permission.WRITE_CONTACTS)) {
            val sukces = zapiszNowyKontakt(context, imie, nazwisko, pseudonim, firma, numer, kanal, zdjecieBytes)
            Toast.makeText(
                context,
                if (sukces) "Kontakt zapisany!" else "Nie udało się zapisać kontaktu",
                Toast.LENGTH_SHORT
            ).show()
            onZapisano()
        } else {
            launcherUprawnien.launch(Manifest.permission.WRITE_CONTACTS)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF16221C),
                            Color(0xFF0D1611)
                        )
                    )
                )
                .border(
                    border = BorderStroke(1.dp, ZielonyNeon.copy(alpha = 0.25f)),
                    shape = RoundedCornerShape(28.dp)
                )
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // --- Nagłówek: Tytuł z ikoną zamknięcia w rogu ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Nowy Kontakt",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        ),
                        color = Color.White
                    )
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

                // --- Awatar — klik wybiera zdjęcie z galerii (kadrowanie przed zapisem) ---
                AvatarEdycji(
                    context = context,
                    inicial = (imie.trim().firstOrNull() ?: nazwisko.trim().firstOrNull())
                        ?.uppercaseChar()?.toString() ?: "+",
                    zdjecieBytes = zdjecieBytes,
                    onClick = {
                        launcherZdjecia.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                )

                // --- Pola formularza z ikonami ---
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PoleTekstoweKontakt(
                            value = imie,
                            onValueChange = { imie = it },
                            etykieta = "Imię",
                            ikona = IkonyAplikacji.NowyKontakt,
                            modifier = Modifier.weight(1f)
                        )
                        PoleTekstoweKontakt(
                            value = nazwisko,
                            onValueChange = { nazwisko = it },
                            etykieta = "Nazwisko",
                            ikona = IkonyAplikacji.NowyKontakt,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    PoleTekstoweKontakt(
                        value = numer,
                        onValueChange = { numer = it },
                        etykieta = "Numer telefonu",
                        ikona = IkonyAplikacji.TelefonLogo,
                        modifier = Modifier.fillMaxWidth()
                    )

                    PoleTekstoweKontakt(
                        value = pseudonim,
                        onValueChange = { pseudonim = it },
                        etykieta = "Pseudonim",
                        ikona = IkonyAplikacji.Pseudonim,
                        modifier = Modifier.fillMaxWidth()
                    )

                    PoleTekstoweKontakt(
                        value = firma,
                        onValueChange = { firma = it },
                        etykieta = "Firma",
                        ikona = IkonyAplikacji.Firma,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // --- Preferowany kanał wiadomości: SMS / Facebook / Signal (ikony) ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    KanalIkona.entries.forEach { element ->
                        KanalIkonaPrzycisk(
                            element = element,
                            wybrany = kanal == element.kanal,
                            dostepny = czyKanalDostepny(context, element.kanal),
                            onClick = { kanal = element.kanal }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // --- Przyciski Akcji — ikonowe, okrągłe ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Anuluj (ikona)
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

                    // Zapisz (ikona)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(ZielonyMorski, ZielonyNeon)
                                )
                            )
                            .clickable { zapiszLubPoprosOUprawnienie() },
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
        }
    }

    // Okno kadrowania zdjęcia — po wyborze z galerii.
    if (pokazKadrowanie && uriZdjecia != null) {
        DialogKadrowaniaZdjecia(
            context = context,
            uri = uriZdjecia!!,
            onCancel = { pokazKadrowanie = false },
            onGotowy = { bytes ->
                zdjecieBytes = bytes
                pokazKadrowanie = false
            }
        )
    }
}

/** Kanały wiadomości do wyboru w oknie nowego kontaktu: ikona + podpis. */
private enum class KanalIkona(val kanal: KanalWiadomosci, val etykieta: String, val ikona: ImageVector) {
    Sms(KanalWiadomosci.Sms, "SMS", IkonyKanalow.Sms),
    Messenger(KanalWiadomosci.Messenger, "Facebook", IkonyKanalow.Messenger),
    Signal(KanalWiadomosci.Signal, "Signal", IkonyKanalow.Signal)
}

/** Okrągły przycisk kanału: mniejsza ikona w kole, zaznaczony = neonowa ramka i tło. */
@Composable
private fun KanalIkonaPrzycisk(
    element: KanalIkona,
    wybrany: Boolean,
    dostepny: Boolean,
    onClick: () -> Unit
) {
    val kolorTla = if (wybrany) ZielonyNeon.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.05f)
    val kolorRamki = if (wybrany) ZielonyNeon.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.10f)
    val kolorIkony = when {
        wybrany -> ZielonyNeon
        dostepny -> Color.White.copy(alpha = 0.78f)
        else -> Color.White.copy(alpha = 0.28f)
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(kolorTla)
                .border(1.dp, kolorRamki, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = element.ikona,
                contentDescription = element.etykieta,
                tint = kolorIkony,
                modifier = Modifier.size(21.dp)
            )
        }
        Text(
            text = element.etykieta,
            color = if (wybrany) ZielonyNeon else Color.White.copy(alpha = 0.5f),
            fontSize = 10.5.sp,
            fontWeight = if (wybrany) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

/** Pole tekstowe w stylu AMOLED z ikoną. */
@Composable
internal fun PoleTekstoweKontakt(
    value: String,
    onValueChange: (String) -> Unit,
    etykieta: String,
    ikona: ImageVector,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(etykieta, fontSize = 12.sp) },
        leadingIcon = {
            Icon(
                imageVector = ikona,
                contentDescription = null,
                tint = ZielonyNeon,
                modifier = Modifier.size(18.dp)
            )
        },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color(0xFF14201A),
            unfocusedContainerColor = Color(0xFF121A15),
            focusedBorderColor = ZielonyNeon,
            unfocusedBorderColor = ZielonyNeon.copy(alpha = 0.2f),
            focusedLabelColor = ZielonyNeon,
            unfocusedLabelColor = Color.White.copy(alpha = 0.5f),
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = ZielonyNeon
        ),
        modifier = modifier
    )
}

/** Zapisuje nowy kontakt do książki telefonicznej przez ContactsContract. */
/**
 * Automatycznie wykrywa główne konto Google użytkownika.
 *
 * 1. Zlicza kontakty na kontach "com.google" — wybiera konto z największą liczbą kontaktów (główne konto).
 * 2. Jeśli baza kontaktów jest pusta, sprawdza pierwsze konto Google zarejestrowane w systemie (AccountManager).
 * 3. Fallback: Zwraca Pair(null, null) dla zapisu lokalnego w telefonie.
 */
private fun pobierzDomyslneKontoGoogle(context: Context): Pair<String?, String?> {
    val zliczeniaKont = mutableMapOf<String, Int>()

    val uri = ContactsContract.RawContacts.CONTENT_URI
    val projection = arrayOf(
        ContactsContract.RawContacts.ACCOUNT_NAME,
        ContactsContract.RawContacts.ACCOUNT_TYPE
    )
    val selection = "${ContactsContract.RawContacts.ACCOUNT_TYPE} = ?"
    val selectionArgs = arrayOf("com.google")

    try {
        context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(ContactsContract.RawContacts.ACCOUNT_NAME)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIdx)
                if (!name.isNullOrBlank()) {
                    zliczeniaKont[name] = (zliczeniaKont[name] ?: 0) + 1
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    // Wybieramy konto z największą liczbą kontaktów (główne konto użytkownika)
    val glowneKontoGoogle = zliczeniaKont.maxByOrNull { it.value }?.key
    if (glowneKontoGoogle != null) {
        return Pair(glowneKontoGoogle, "com.google")
    }

    // Fallback 1: Jeśli baza kontaktów jest pusta, sprawdź pierwsze konto Google w systemie
    try {
        val accountManager = AccountManager.get(context)
        val googleAccounts = accountManager.getAccountsByType("com.google")
        val pierwszeKonto = googleAccounts.firstOrNull()?.name
        if (pierwszeKonto != null) {
            return Pair(pierwszeKonto, "com.google")
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    // Fallback 2: Brak konta Google -> zapis lokalny w telefonie
    return Pair(null, null)
}

/** Zapisuje nowy kontakt do książki telefonicznej przez ContactsContract. */
internal fun zapiszNowyKontakt(
    context: Context,
    imie: String,
    nazwisko: String,
    pseudonim: String,
    firma: String,
    numer: String,
    kanal: KanalWiadomosci = KanalWiadomosci.Sms,
    zdjecie: ByteArray? = null
): Boolean {
    val operations = java.util.ArrayList<ContentProviderOperation>()

    // Automatycznie wykrywamy główne konto Google w telefonie użytkownika
    val (accountName, accountType) = pobierzDomyslneKontoGoogle(context)

    val rawContactInsertIndex = operations.size
    operations.add(
        ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
            .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, accountType)
            .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, accountName)
            .build()
    )

    val pelneImie = "$imie $nazwisko".trim()
    if (pelneImie.isNotBlank()) {
        operations.add(
            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactInsertIndex)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME, imie)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME, nazwisko)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, pelneImie)
                .build()
        )
    }

    if (pseudonim.isNotBlank()) {
        operations.add(
            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactInsertIndex)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Nickname.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Nickname.NAME, pseudonim)
                .build()
        )
    }

    if (firma.isNotBlank()) {
        operations.add(
            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactInsertIndex)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Organization.COMPANY, firma)
                .build()
        )
    }

    if (numer.isNotBlank()) {
        operations.add(
            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactInsertIndex)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, numer)
                .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                .build()
        )
    }

    if (zdjecie != null) {
        operations.add(
            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactInsertIndex)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Photo.PHOTO, zdjecie)
                .build()
        )
    }

    return try {
        context.contentResolver.applyBatch(ContactsContract.AUTHORITY, operations)
        if (numer.isNotBlank()) zapamietajKanal(context, numer, kanal)
        true
    } catch (e: Exception) {
        e.printStackTrace()
        utworzKontaktIntent(context, numer)
        false
    }
}