package dialer.dominik.pl.ui.screens

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.telecom.TelecomManager
import dialer.dominik.pl.call.EkranRozmowy
import dialer.dominik.pl.call.PowiadomieniaPolaczen
import dialer.dominik.pl.call.StanPolaczenia
import dialer.dominik.pl.call.PowiadomieniaTrwajacejRozmowy
import dialer.dominik.pl.call.ZarzadcaDzwonka
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dialer.dominik.pl.ui.components.IkonyAplikacji
import dialer.dominik.pl.ui.theme.ZielonyNeon

/**
 * Zakładka Ustawienia — proste przełączniki i informacje o aplikacji.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UstawieniaScreen(context: Context, onBack: () -> Unit = {}) {
    var odswiez by remember { mutableStateOf(0) }

    val jestDialer = remember(odswiez) {
        runCatching {
            context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage == context.packageName
        }.getOrDefault(false)
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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                UstawienieKarta(
                    tytul = "Domyślny dialer",
                    opis = if (jestDialer) "Telefon jest domyślną aplikacją do połączeń" else "Utaw Telefon jako domyślny do połączeń",
                    ikona = IkonyAplikacji.TelefonLogo,
                    akcja = "Ustaw",
                    onKlik = {
                        ustawJakoDomyslnyDialer(context)
                    }
                )
            }
            item {
                UstawienieKarta(
                    tytul = "Testuj połączenie (SPAM)",
                    opis = "Symulacja przychodzącego spamu z delikatną wibracją",
                    ikona = IkonyAplikacji.TelefonLogo,
                    akcja = "Testuj",
                    onKlik = {
                        StanPolaczenia.rozmowca = "57575446"
                        StanPolaczenia.czyPrzychodzace = true
                        StanPolaczenia.stan = StanPolaczenia.Stan.Dzwoni
                        StanPolaczenia.czasStartu = 0L
                        EkranRozmowy.pokaz(context)
                    }
                )
            }
            item {
                UstawienieKarta(
                    tytul = "Testuj połączenie (Czysty numer)",
                    opis = "Symulacja zwykłego połączenia bez spamu",
                    ikona = IkonyAplikacji.TelefonLogo,
                    akcja = "Testuj",
                    onKlik = {
                        StanPolaczenia.rozmowca = "+48600700800"
                        StanPolaczenia.czyPrzychodzace = true
                        StanPolaczenia.stan = StanPolaczenia.Stan.Dzwoni
                        StanPolaczenia.czasStartu = 0L
                        EkranRozmowy.pokaz(context)
                    }
                )
            }
            item {
                UstawienieKarta(
                    tytul = "Testuj połączenie (Wychodzące)",
                    opis = "Symulacja ekranu w trakcie dzwonienia do kogoś",
                    ikona = IkonyAplikacji.TelefonLogo,
                    akcja = "Testuj",
                    onKlik = {
                        StanPolaczenia.rozmowca = "Jan Kowalski"
                        StanPolaczenia.czyPrzychodzace = false
                        StanPolaczenia.stan = StanPolaczenia.Stan.Dzwoni
                        StanPolaczenia.czasStartu = 0L
                        EkranRozmowy.pokaz(context)
                    }
                )
            }
            item {
                UstawienieKarta(
                    tytul = "Testuj rozmowę (Aktywna rozmowa)",
                    opis = "Symulacja ekranu gdy rozmówca odbierze (licznik czasu)",
                    ikona = IkonyAplikacji.TelefonLogo,
                    akcja = "Testuj",
                    onKlik = {
                        StanPolaczenia.rozmowca = "Anna Nowak"
                        StanPolaczenia.czyPrzychodzace = false
                        StanPolaczenia.stan = StanPolaczenia.Stan.Rozmowa
                        StanPolaczenia.czasStartu = System.currentTimeMillis() - 24000
                        EkranRozmowy.pokaz(context)
                    }
                )
            }
            item {
                UstawienieKarta(
                    tytul = "Testuj baner (powiadomienie)",
                    opis = "Symulacja przychodzącego połączenia przy odblokowanym telefonie (baner heads-up)",
                    ikona = IkonyAplikacji.TelefonLogo,
                    akcja = "Testuj",
                    onKlik = {
                        ZarzadcaDzwonka.uruchom(context)
                        PowiadomieniaPolaczen.pokazBaner(context, "+486****0400 800 (test)")
                    }
                )
            }
            item {
                UstawienieKarta(
                    tytul = "Testuj dymek (bubble)",
                    opis = "Pokazuje trwającą rozmowę jako przemieszczalny dymek z powiadomieniem",
                    ikona = IkonyAplikacji.Dymek,
                    akcja = "Testuj",
                    onKlik = {
                        StanPolaczenia.rozmowca = "Anna Nowak"
                        PowiadomieniaPolaczen.pokazBubble(context, "Anna Nowak")
                        PowiadomieniaTrwajacejRozmowy.pokazPowiadomienie(context, "Anna Nowak", false)
                    }
                )
            }
            item {
                UstawienieKarta(
                    tytul = "Testuj trwałe powiadomienie rozmowy",
                    opis = "Stałe powiadomienie z akcjami Wycisz/Rozłącz i powrotem do ekranu",
                    ikona = IkonyAplikacji.TelefonLogo,
                    akcja = "Testuj",
                    onKlik = {
                        StanPolaczenia.rozmowca = "Anna Nowak"
                        PowiadomieniaTrwajacejRozmowy.pokazPowiadomienie(context, "Anna Nowak", false)
                    }
                )
            }
            item {
                UstawienieKarta(
                    tytul = "O aplikacji",
                    opis = "Telefon • dialer i wiadomości (wersja 1.0)",
                    ikona = IkonyAplikacji.Ustawienia,
                    akcja = null,
                    onKlik = {}
                )
            }
        }
    }
}

@Composable
private fun UstawienieKarta(
    tytul: String,
    opis: String,
    ikona: androidx.compose.ui.graphics.vector.ImageVector,
    akcja: String?,
    onKlik: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF131C18))
            .border(1.dp, ZielonyNeon.copy(alpha = 0.14f), RoundedCornerShape(16.dp))
            .clickable(onClick = onKlik)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(ZielonyNeon.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "⛭",
                color = ZielonyNeon,
                fontSize = 18.sp
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = tytul,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = opis,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp
            )
        }
        if (akcja != null) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(ZielonyNeon.copy(alpha = 0.2f))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = akcja,
                    color = ZielonyNeon,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
