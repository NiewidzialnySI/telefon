package dialer.dominik.pl.call

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import dialer.dominik.pl.data.BazaSpamu
import dialer.dominik.pl.ui.theme.CzerwonyPolaczenia
import dialer.dominik.pl.ui.theme.LocalMotyw

private val TypyOceny = listOf(
    "Spam",
    "Telemarketing",
    "Neutralny",
    "Przydatny",
    "Bezpieczny"
)

fun otworzOpinieWSieci(context: Context, wynik: WynikWeryfikacjiNumeru?, numerFallback: String) {
    val url = wynik?.urlSiec?.takeIf { it.isNotBlank() }
        ?: run {
            val cyfry = numerFallback.filter { it.isDigit() }
            val path = when {
                cyfry.length == 11 && cyfry.startsWith("48") -> cyfry
                cyfry.length == 9 -> cyfry
                else -> cyfry.takeLast(9)
            }
            "https://www.nieznanynumer.pl/numer/$path"
        }
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    } catch (_: Exception) {
        Toast.makeText(context, "Brak przeglądarki", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun KartaReputacjiNumeru(
    context: Context,
    numer: String,
    wynik: WynikWeryfikacjiNumeru?,
    kompakt: Boolean = false,
    onWynikZmieniony: (WynikWeryfikacjiNumeru) -> Unit = {}
) {
    var pokazDialog by remember { mutableStateOf(false) }
    var rozwiniety by remember { mutableStateOf(!kompakt) }
    val paleta = LocalMotyw.current

    if (pokazDialog) {
        DialogDodajOpinie(
            context = context,
            numer = numer,
            urlSiec = wynik?.urlSiec,
            onZamknij = { pokazDialog = false },
            onZapisano = {
                pokazDialog = false
                val odswiezony = WeryfikacjaNumeru.odswiezZLokalnymi(context, wynik)
                    ?: wynik
                if (odswiezony != null) onWynikZmieniony(odswiezony)
            }
        )
    }

    if (wynik == null) return

    val kolorBadge = when (wynik.poziomZagrozenia) {
        PoziomZagrozenia.SPAM_OSZUSTWO -> CzerwonyPolaczenia
        PoziomZagrozenia.PODEJRZANY -> Color(0xFFFFB74D)
        PoziomZagrozenia.BEZPIECZNY -> paleta.accent
        PoziomZagrozenia.NEUTRALNY -> paleta.naTle.copy(alpha = 0.55f)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(paleta.naTle.copy(alpha = 0.06f))
            .border(1.dp, paleta.naTle.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
            .clickable(enabled = kompakt) { rozwiniety = !rozwiniety }
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                if (wynik.laduje) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = paleta.accent
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (wynik.laduje) "Szukam w sieci…" else wynik.kategoria,
                        color = paleta.naTle,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (wynik.ocenaLiczbowa.isNotBlank()) {
                        Text(
                            text = wynik.ocenaLiczbowa,
                            color = kolorBadge,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            if (!wynik.laduje) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(kolorBadge.copy(alpha = 0.18f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = when (wynik.poziomZagrozenia) {
                            PoziomZagrozenia.SPAM_OSZUSTWO -> "SPAM"
                            PoziomZagrozenia.PODEJRZANY -> "UWAGA"
                            PoziomZagrozenia.BEZPIECZNY -> "OK"
                            PoziomZagrozenia.NEUTRALNY -> "?"
                        },
                        color = kolorBadge,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (!wynik.laduje && wynik.opisZagrozenia.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = wynik.opisZagrozenia,
                color = paleta.naTle.copy(alpha = 0.65f),
                fontSize = 11.sp,
                maxLines = if (rozwiniety) 3 else 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (rozwiniety && !wynik.laduje) {
            val opinie = wynik.opinie.filter {
                !it.tresc.contains("Numer nie figuruje", ignoreCase = true)
            }.take(4)
            if (opinie.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "OPINIE",
                    color = paleta.naTle.copy(alpha = 0.4f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(4.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = if (kompakt) 100.dp else 120.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(opinie) { op ->
                        KartaPojedynczejOpinii(op)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PrzyciskReputacji(
                    tekst = "Dodaj opinię",
                    modifier = Modifier.weight(1f),
                    onClick = { pokazDialog = true }
                )
                PrzyciskReputacji(
                    tekst = "W sieci",
                    modifier = Modifier.weight(1f),
                    onClick = { otworzOpinieWSieci(context, wynik, numer) }
                )
            }
            wynik.atrybucja?.let { atr ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = atr,
                    color = paleta.naTle.copy(alpha = 0.35f),
                    fontSize = 9.sp
                )
            }
        } else if (kompakt && !wynik.laduje) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Dotknij, by zobaczyć opinie",
                color = paleta.naTle.copy(alpha = 0.35f),
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun KartaPojedynczejOpinii(opinia: OpiniaUzytkownika) {
    val paleta = LocalMotyw.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(paleta.kafelek)
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${opinia.autor} · ${opinia.data}",
                color = paleta.accent.copy(alpha = 0.85f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = opinia.typOceny,
                color = if (
                    opinia.typOceny.contains("Spam", true) ||
                    opinia.typOceny.contains("Niebezpiecz", true) ||
                    opinia.typOceny.contains("Nękaj", true)
                ) CzerwonyPolaczenia else paleta.accent,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = opinia.tresc,
            color = paleta.naTle.copy(alpha = 0.85f),
            fontSize = 11.sp,
            lineHeight = 14.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PrzyciskReputacji(
    tekst: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val paleta = LocalMotyw.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(paleta.naTle.copy(alpha = 0.08f))
            .border(1.dp, paleta.naTle.copy(alpha = 0.14f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = tekst,
            color = paleta.naTle,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun DialogDodajOpinie(
    context: Context,
    numer: String,
    urlSiec: String?,
    onZamknij: () -> Unit,
    onZapisano: () -> Unit
) {
    var typ by remember { mutableStateOf(TypyOceny[2]) }
    var tresc by remember { mutableStateOf("") }
    val paleta = LocalMotyw.current

    Dialog(onDismissRequest = onZamknij) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(paleta.tlo)
                .border(1.dp, paleta.naTle.copy(alpha = 0.12f), RoundedCornerShape(18.dp))
                .padding(16.dp)
        ) {
            Text(
                text = "Twoja opinia",
                color = paleta.naTle,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = numer,
                color = paleta.naTle.copy(alpha = 0.5f),
                fontSize = 12.sp
            )
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TypyOceny.chunked(3).forEach { rzad ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        rzad.forEach { t ->
                            val sel = typ == t
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (sel) paleta.accent.copy(alpha = 0.25f)
                                        else paleta.naTle.copy(alpha = 0.06f)
                                    )
                                    .clickable { typ = t }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = t,
                                    color = if (sel) paleta.accent else paleta.naTle.copy(alpha = 0.7f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = tresc,
                onValueChange = { if (it.length <= 280) tresc = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text("Krótki opis…", color = paleta.naTle.copy(alpha = 0.35f))
                },
                minLines = 2,
                maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = paleta.accent.copy(alpha = 0.6f),
                    unfocusedBorderColor = paleta.naTle.copy(alpha = 0.2f),
                    focusedTextColor = paleta.naTle,
                    unfocusedTextColor = paleta.naTle
                )
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onZamknij) {
                    Text("Anuluj", color = paleta.naTle.copy(alpha = 0.6f))
                }
                TextButton(
                    onClick = {
                        OpinieLokalne.dodaj(context, numer, typ, tresc)
                        if (typ.equals("Spam", ignoreCase = true)) {
                            BazaSpamu.dodajDoSpamu(context, numer)
                        }
                        Toast.makeText(context, "Zapisano lokalnie", Toast.LENGTH_SHORT).show()
                        onZapisano()
                    }
                ) {
                    Text("Zapisz", color = paleta.accent, fontWeight = FontWeight.Bold)
                }
                TextButton(
                    onClick = {
                        OpinieLokalne.dodaj(context, numer, typ, tresc)
                        if (typ.equals("Spam", ignoreCase = true)) {
                            BazaSpamu.dodajDoSpamu(context, numer)
                        }
                        onZapisano()
                        otworzOpinieWSieci(
                            context,
                            WynikWeryfikacjiNumeru(
                                numer = numer,
                                poziomZagrozenia = PoziomZagrozenia.NEUTRALNY,
                                kategoria = "",
                                ocenaLiczbowa = "",
                                opisZagrozenia = "",
                                opinie = emptyList(),
                                urlSiec = urlSiec
                            ),
                            numer
                        )
                    }
                ) {
                    Text("Opublikuj", color = paleta.accent)
                }
            }
        }
    }
}
