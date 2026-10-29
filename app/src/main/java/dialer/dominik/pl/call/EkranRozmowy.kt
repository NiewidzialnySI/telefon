package dialer.dominik.pl.call

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.telecom.Call
import android.telecom.CallAudioState
import android.telephony.SmsManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dialer.dominik.pl.ui.components.IkonyAplikacji
import dialer.dominik.pl.ui.screens.rozlaczRozmowe
import dialer.dominik.pl.ui.theme.CzarnyAmoled
import dialer.dominik.pl.ui.theme.CzerwonyPolaczenia
import dialer.dominik.pl.ui.theme.ZielonyGleboki
import dialer.dominik.pl.ui.theme.ZielonyMorski
import dialer.dominik.pl.ui.theme.ZielonyNeon
import kotlinx.coroutines.delay
import kotlin.math.roundToInt


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
 * Główny pełnoekranowy ekran połączenia.
 * Automatycznie przełącza się między połączeniem przychodzącym a trwającą rozmową.
 */
@Composable
fun EkranRozmowy(context: Context, onZakoncz: () -> Unit = {}) {
    val stan = StanPolaczenia

    // Sprawdzamy czy to spam by dostosować wibrację (delikatniejsza dla spamu)
    var wynikAnalizy by remember(stan.rozmowca) { mutableStateOf<WynikWeryfikacjiNumeru?>(null) }
    LaunchedEffect(stan.rozmowca) {
        if (stan.rozmowca.isNotBlank()) {
            wynikAnalizy = WeryfikacjaNumeru.weryfikujNumer(context, stan.rozmowca)
        }
    }
    val czySpam = wynikAnalizy?.poziomZagrozenia == PoziomZagrozenia.SPAM_OSZUSTWO

    // Uruchomienie dzwonka i wibracji dla połączenia przychodzącego
    LaunchedEffect(stan.czyPrzychodzace, stan.stan, czySpam) {
        if (stan.czyPrzychodzace && stan.stan == StanPolaczenia.Stan.Dzwoni) {
            ZarzadcaDzwonka.uruchom(context, czySpam = czySpam)
        } else {
            ZarzadcaDzwonka.zatrzymaj()
        }
    }

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            ZarzadcaDzwonka.zatrzymaj()
        }
    }

    // Zewnętrzny Box NIE ma tła — aktywność jest przezroczysta, więc podczas
        // minimalizacji prześwituje aplikacja pod spodem. Pełne tło (CzarnyAmoled +
        // TloAmoled) rysują dopiero ekrany przychodzący/wychodzący; dla trwającej
        // rozmowy tło (z alpha) rysuje EkranTrwajacejRozmowy.
        Box(modifier = Modifier.fillMaxSize()) {
            if (stan.czyPrzychodzace && stan.stan == StanPolaczenia.Stan.Dzwoni) {
                Box(Modifier.fillMaxSize().background(CzarnyAmoled)) {
                    dialer.dominik.pl.ui.components.TloAmoled()
                    EkranPolaczeniaPrzychodzacego(
                        context = context,
                        numer = stan.rozmowca,
                        wynikAnalizy = wynikAnalizy,
                        onOdbierz = {
                            ZarzadcaDzwonka.zatrzymaj()
                            stan.polaczenie?.answer(0)
                        },
                        onOdrzuc = {
                            ZarzadcaDzwonka.zatrzymaj()
                            stan.polaczenie?.reject(false, null)
                            onZakoncz()
                        },
                        onScisz = {
                            // Wycisza dzwonek i łagodzi wibracje (delikatny puls).
                            ZarzadcaDzwonka.scisz()
                        }
                    )
                }
            } else if (!stan.czyPrzychodzace && stan.stan == StanPolaczenia.Stan.Dzwoni) {
                Box(Modifier.fillMaxSize().background(CzarnyAmoled)) {
                    dialer.dominik.pl.ui.components.TloAmoled()
                    EkranPolaczeniaWychodzacego(
                        context = context,
                        numer = stan.rozmowca,
                        onZakoncz = {
                            stan.polaczenie?.disconnect()
                            onZakoncz()
                        }
                    )
                }
            } else {
                EkranTrwajacejRozmowy(
                    context = context,
                    onZakoncz = onZakoncz
                )
            }
        }
    }
