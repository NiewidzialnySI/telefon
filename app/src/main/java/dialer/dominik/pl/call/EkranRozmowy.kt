package dialer.dominik.pl.call

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/** Uruchamia ekran rozmowy (aktywność z przezroczystym tłem nad blokadą). */
object EkranRozmowy {
    /**
     * Otwiera ekran rozmowy. Gdy `zDymka == true`, EkranTrwajacejRozmowy wystartuje
     * z awatarem w rogu i przyleci nim na środek sprężyną (powrót z minimalizacji).
     */
    fun pokaz(context: Context, zDymka: Boolean = false) {
        StanPolaczenia.wracaZDymka = zDymka
        try {
            val intencja = Intent(context, EkranRozmowyActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION)
            }
            context.startActivity(intencja)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun zamknij(context: Context) {
        try {
            val intencja = Intent(context, EkranRozmowyActivity::class.java).apply {
                action = AKCJA_ZAMKNIJ
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            context.startActivity(intencja)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    const val AKCJA_ZAMKNIJ = "dialer.dominik.pl.ZAMKNIJ_EKRAN_ROZMOWY"
}

/**
 * Główny pełnoekranowy ekran połączenia — zawsze ten sam shell UI;
 * faza Incoming / Dialing / Active zmienia tylko status i dolny chrome.
 */
@Composable
fun EkranRozmowy(context: Context, onZakoncz: () -> Unit = {}) {
    val stan = StanPolaczenia

    val numerDoWeryfikacji = stan.numerRozmowy.ifBlank { stan.rozmowca }
    var wynikAnalizy by remember(numerDoWeryfikacji) {
        mutableStateOf<WynikWeryfikacjiNumeru?>(
            if (numerDoWeryfikacji.isNotBlank()) WynikWeryfikacjiNumeru.laduje(numerDoWeryfikacji)
            else null
        )
    }
    LaunchedEffect(numerDoWeryfikacji) {
        if (numerDoWeryfikacji.isNotBlank()) {
            wynikAnalizy = WynikWeryfikacjiNumeru.laduje(numerDoWeryfikacji)
            wynikAnalizy = WeryfikacjaNumeru.weryfikujNumer(context, numerDoWeryfikacji)
        } else {
            wynikAnalizy = null
        }
    }
    val czySpam = wynikAnalizy?.poziomZagrozenia == PoziomZagrozenia.SPAM_OSZUSTWO &&
        wynikAnalizy?.laduje != true

    LaunchedEffect(stan.czyPrzychodzace, stan.stan, czySpam) {
        if (stan.czyPrzychodzace && stan.stan == StanPolaczenia.Stan.Dzwoni) {
            ZarzadcaDzwonka.uruchom(context, czySpam = czySpam)
        } else {
            ZarzadcaDzwonka.zatrzymaj()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            ZarzadcaDzwonka.zatrzymaj()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        EkranTrwajacejRozmowy(
            context = context,
            onZakoncz = onZakoncz,
            wynikAnalizy = wynikAnalizy,
            onWynikZmieniony = { wynikAnalizy = it }
        )
    }
}
