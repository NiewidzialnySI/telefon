package dialer.dominik.pl

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dialer.dominik.pl.sms.OdbiorcaSms
import dialer.dominik.pl.ui.screens.EkranCzatuSzczegoly
import dialer.dominik.pl.ui.screens.SmsWatek
import dialer.dominik.pl.ui.screens.pobierzWątkiSms
import dialer.dominik.pl.ui.screens.resolveContactName
import dialer.dominik.pl.ui.theme.TelefonTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Aktywność czatu wyświetlana W DYNKU (bubble) po kliknięciu w dymek SMS.
 * Renders TYLKO czat (dymki wiadomości + pole wpisywania), bez paska zakładek
 * i bez nagłówka — zgodnie z życzeniem użytkownika. Dedykowana aktywność:
 * manifest ma allowEmbedded=true + resizeableActivity=true, dzięki czemu
 * system może ją osadzić w nakładce dymka.
 *
 * Odbiera numer nadawcy przez extra "NUMER" i buduje wątek z systemowej skrzynki.
 */
class BubbleChatActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val numer = intent?.getStringExtra(EXTRA_NUMER) ?: ""
        val kontekst = applicationContext

        setContent {
            TelefonTheme(dynamicColor = false) {
                // Ładujemy wątek na bieżąco (numer może być wśród istniejących komunikatów).
                var watek by remember { mutableStateOf<SmsWatek?>(null) }
                var odswiez by remember { mutableStateOf(0) }
                // Klucz zawiera czas ostatniej odebranej wiadomości — gdy przyjdzie
                // nowy SMS (także od tego nadawcy), efekt restartuje i przeładowuje
                // wątek, więc czat w dymku pokazuje najnowsze wiadomości.
                androidx.compose.runtime.LaunchedEffect(odswiez, OdbiorcaSms.ostatniaWiadomosc?.czas) {
                    watek = withContext(Dispatchers.IO) {
                        val wszystkie = pobierzWątkiSms(kontekst)
                        val norm = dialer.dominik.pl.ui.screens.normalizujAdresSms(numer)
                        val znaleziony = wszystkie.firstOrNull {
                            it.address == numer ||
                                dialer.dominik.pl.ui.screens.normalizujAdresSms(it.address) == norm
                        }
                        val nazwa = znaleziony?.contactName
                            ?: resolveContactName(kontekst, numer)
                        val pseudo = znaleziony?.pseudonim
                            ?: nazwa?.let { dialer.dominik.pl.ui.screens.pobierzPseudonim(kontekst, it) }
                        (znaleziony ?: SmsWatek(
                            address = numer,
                            contactName = nazwa,
                            lastMessage = "",
                            lastDate = 0L,
                            messages = emptyList()
                        )).copy(
                            contactName = nazwa,
                            pseudonim = pseudo
                        )
                    }
                }

                val aktualny = watek
                if (aktualny != null) {
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .imePadding(),
                        color = Color(0xFF0A100D)
                    ) {
                        EkranCzatuSzczegoly(
                            context = kontekst,
                            watek = aktualny,
                            pokazNaglowek = true,
                            onWiadomoscWyslana = {
                                // Odśwież wątek po wysłaniu wiadomości.
                                odswiez++
                            }
                        )
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_NUMER = "NUMER"
    }
}