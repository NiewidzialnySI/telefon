package dialer.dominik.pl

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dialer.dominik.pl.ui.components.IkonyAplikacji
import dialer.dominik.pl.ui.components.TloAmoled
import dialer.dominik.pl.ui.screens.KontaktyScreen
import dialer.dominik.pl.ui.screens.MainDialerScreen
import dialer.dominik.pl.ui.screens.UstawieniaScreen
import dialer.dominik.pl.ui.screens.PermissionsScreen
import dialer.dominik.pl.ui.screens.SmsScreen
import dialer.dominik.pl.ui.screens.WelcomeScreen
import dialer.dominik.pl.ui.theme.CzarnyAmoled
import dialer.dominik.pl.ui.theme.TelefonTheme
import dialer.dominik.pl.ui.theme.ZielonyMorski
import dialer.dominik.pl.ui.theme.ZielonyNeon

// Główna aktywność — wszystko po polsku
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Treść rysowana pod paskami systemowymi (edge-to-edge)
        enableEdgeToEdge()
        // Pending NotificationInd (MMS) — dociągnij także bez otwierania skrzynki,
        // żeby MmsPobranyIZapisanyReceiver mógł pokazać dymek po pobraniu.
        if (android.provider.Telephony.Sms.getDefaultSmsPackage(this) == packageName) {
            dialer.dominik.pl.sms.dociagnijOczekujaceMms(this)
        }
        setContent {
            // dynamicColor = false — pełna paleta zielono-czarna, bez nadpisywania tła tapetą
            TelefonTheme(dynamicColor = false) {
                NawigacjaAplikacji()
            }
        }
    }
}

/** Zakładki dolnego paska. */
enum class Zakladka { Glowny, Kontakty, Wiadomosci, Ustawienia }

// Klucze w SharedPreferences — zapamiętują, że powitanie już się odbyło
private const val PREFS = "telefon_prefs"
private const val KLUCZ_POWITANIE = "powitanie_odbyte"
private const val KLUCZ_UPRAWNIENIA = "uprawnienia_odbyte"

@Composable
fun NawigacjaAplikacji() {
    val kontekst = LocalContext.current
    val prefs = remember {
        kontekst.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    // Powitanie i uprawnienia pokazujemy TYLKO przy pierwszym uruchomieniu.
    // Potem aplikacja startuje od razu na ekranie głównym.
    var etap by rememberSaveable {
        mutableStateOf(
            when {
                !prefs.getBoolean(KLUCZ_POWITANIE, false) -> EtapStartowy.Powitanie
                !prefs.getBoolean(KLUCZ_UPRAWNIENIA, false) -> EtapStartowy.Uprawnienia
                else -> EtapStartowy.Gotowa
            }
        )
    }

    AnimatedContent(
        targetState = etap,
        transitionSpec = {
            fadeIn(tween(340)) togetherWith fadeOut(tween(220))
        },
        label = "etapStartowy"
    ) { aktualny ->
        when (aktualny) {
            EtapStartowy.Powitanie -> WelcomeScreen {
                prefs.edit().putBoolean(KLUCZ_POWITANIE, true).apply()
                etap = EtapStartowy.Uprawnienia
            }

            EtapStartowy.Uprawnienia -> PermissionsScreen(
                context = kontekst,
                onFinished = {
                    prefs.edit().putBoolean(KLUCZ_UPRAWNIENIA, true).apply()
                    etap = EtapStartowy.Gotowa
                }
            )

            EtapStartowy.Gotowa -> EkranZZakladkami(context = kontekst)
        }
    }
}

private enum class EtapStartowy { Powitanie, Uprawnienia, Gotowa }

/** Globalny stan nawigacji między zakładkami (np. kliknięcie numeru w czacie). */
object AppNavigationState {
    var docelowaZakladka: Zakladka? by mutableStateOf(null)
    var numerDoWybrania: String? by mutableStateOf(null)
}

/**
 * Główny szkielet aplikacji: zawartość zakładki + dolny pasek nawigacji.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EkranZZakladkami(context: Context) {
    var zakladka by rememberSaveable { mutableStateOf(Zakladka.Glowny) }
    // Numer, z którego przyszliśmy z dialera, żeby otworzyć czat SMS z tym numerem.
    var numerStartowySms by remember { mutableStateOf("") }
    var numerDoWybraniaNaMain by remember { mutableStateOf("") }

    LaunchedEffect(AppNavigationState.docelowaZakladka, AppNavigationState.numerDoWybrania) {
        AppNavigationState.docelowaZakladka?.let { z ->
            zakladka = z
            AppNavigationState.docelowaZakladka = null
        }
        AppNavigationState.numerDoWybrania?.let { num ->
            numerDoWybraniaNaMain = num
            AppNavigationState.numerDoWybrania = null
        }
    }
    // Czy na Wiadomościach jest otwarty czat (chowamy wtedy dolny pasek nawigacji).
    var czatSmsOtwarty by remember { mutableStateOf(false) }

    LaunchedEffect(zakladka) {
        if (zakladka != Zakladka.Wiadomosci) {
            czatSmsOtwarty = false
        }
    }

    // Gesty wstecz: na głównych ekranach (zakładkach) fizyczny wstecz jest
    // BLOCKOWANY — nie wychodzi z aplikacji i nie przeskakuje między zakładkami,
    // bo nawigację obsługują same ekrany (czaty/dialogi mają własne BackHandler).
    // Wstecz może działać tylko "wewnątrz" danego ekranu (np. czat → lista), nigdy
    // nie cofa z głównego widoku do Main.
    BackHandler {
        // No-op: nic nie robimy na poziomie zakładek.
    }

    // Zachowuje stan każdej zakładki między przełączeniami (SaveableStateHolder) —
    // dzięki temu powrót na ekran główny NIE pokazuje ponownie spinnera ładowania.
    val stanZakladek = rememberSaveableStateHolder()

    // Pasek zakładek NAKŁADANY na treść (nie Column) — jak w typowych rozwiązaniach
    // Compose (SO / GitHub): treść ma animowany bottom padding, pasek slideOut w tym
    // samym czasie. Wcześniej Column + fadeOut trzymał miejsce → pole pisania
    // czekało i wskakiwało dopiero po zniknięciu paska.
    val imeWidoczna = WindowInsets.isImeVisible
    val pokazPasekZakladek = !imeWidoczna && !czatSmsOtwarty
    val density = LocalDensity.current
    var wysokoscPaskaPx by remember { mutableIntStateOf(0) }
    val animSpecDol = tween<androidx.compose.ui.unit.Dp>(280, easing = FastOutSlowInEasing)
    val dolnyPadZakladek by animateDpAsState(
        targetValue = if (pokazPasekZakladek && wysokoscPaskaPx > 0) {
            with(density) { wysokoscPaskaPx.toDp() }
        } else if (pokazPasekZakladek) {
            68.dp // zanim zmierzymy pasek
        } else {
            0.dp
        },
        animationSpec = animSpecDol,
        label = "dolnyPadZakladek"
    )

    Box(modifier = Modifier.fillMaxSize().background(CzarnyAmoled)) {
        TloAmoled()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .navigationBarsPadding()
                .padding(bottom = dolnyPadZakladek)
        ) {
            AnimatedContent(
                targetState = zakladka,
                transitionSpec = {
                    val staryIndeks = initialState.ordinal
                    val nowyIndeks = targetState.ordinal
                    // M3: ruch (slide) = spring (fizyka), fade = tween — płynne bez sztywności.
                    if (nowyIndeks > staryIndeks) {
                        (slideInHorizontally(
                            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                        ) { it / 5 } + fadeIn(tween(260)))
                            .togetherWith(slideOutHorizontally(
                                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                            ) { -it / 5 } + fadeOut(tween(180)))
                    } else {
                        (slideInHorizontally(
                            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                        ) { -it / 5 } + fadeIn(tween(260)))
                            .togetherWith(slideOutHorizontally(
                                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                            ) { it / 5 } + fadeOut(tween(180)))
                    }
                },
                label = "przejscieZakladek"
            ) { cel ->
                stanZakladek.SaveableStateProvider(cel.name) {
                    when (cel) {
                        Zakladka.Glowny -> MainDialerScreen(
                            context = context,
                            onOpenSms = { numer ->
                                android.util.Log.i("KlikSms", "onOpenSms numer=$numer")
                                numerStartowySms = numer
                                zakladka = Zakladka.Wiadomosci
                            },
                            poczatkowyNumer = numerDoWybraniaNaMain,
                            onNumerSkonsumowany = { numerDoWybraniaNaMain = "" }
                        )
                        Zakladka.Kontakty -> KontaktyScreen(
                            context = context,
                            onBack = { zakladka = Zakladka.Glowny },
                            onOpenSms = { numer ->
                                android.util.Log.i("KlikSms", "onOpenSms numer=$numer")
                                numerStartowySms = numer
                                zakladka = Zakladka.Wiadomosci
                            }
                        )
                        Zakladka.Wiadomosci -> {
                            SmsScreen(
                                context = context,
                                onBack = { zakladka = Zakladka.Glowny },
                                numerStartowy = numerStartowySms,
                                onSmsStartowySkonsumowany = { numerStartowySms = "" },
                                onCzatOtworzony = { otwarty -> czatSmsOtwarty = otwarty }
                            )
                        }
                        Zakladka.Ustawienia -> UstawieniaScreen(
                            context = context,
                            onBack = { zakladka = Zakladka.Glowny }
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = pokazPasekZakladek,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .onSizeChanged { wysokoscPaskaPx = it.height },
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(280, easing = FastOutSlowInEasing)
            ) + fadeIn(tween(200)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(280, easing = FastOutSlowInEasing)
            ) + fadeOut(tween(200))
        ) {
            DolnyPasekZakladek(
                wybrana = zakladka,
                onWybierz = { zakladka = it }
            )
        }
    }
}

/**
 * Dolny pasek zakładek w stylu AMOLED — ciemne tło, aktywna zakładka
 * podświetlona zielenią marki.
 */
@Composable
private fun DolnyPasekZakladek(
    wybrana: Zakladka,
    onWybierz: (Zakladka) -> Unit
) {
    // Bez navigationBarsPadding — dodaje je nakładka w EkranZZakladkami,
    // żeby padding treści i slide paska były zsynchronizowane.
    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ElementZakladki(
                tytul = "Telefon",
                zaznaczona = wybrana == Zakladka.Glowny,
                ikona = IkonyAplikacji.TelefonLogo,
                modifier = Modifier.weight(1f),
                onClick = { onWybierz(Zakladka.Glowny) }
            )
            ElementZakladki(
                tytul = "Kontakty",
                zaznaczona = wybrana == Zakladka.Kontakty,
                ikona = IkonyAplikacji.NowyKontakt,
                modifier = Modifier.weight(1f),
                onClick = { onWybierz(Zakladka.Kontakty) }
            )
            ElementZakladki(
                tytul = "Wiadomości",
                zaznaczona = wybrana == Zakladka.Wiadomosci,
                ikona = IkonyAplikacji.Wiadomosc,
                modifier = Modifier.weight(1f),
                onClick = { onWybierz(Zakladka.Wiadomosci) }
            )
            ElementZakladki(
                tytul = "Ustawienia",
                zaznaczona = wybrana == Zakladka.Ustawienia,
                ikona = IkonyAplikacji.Ustawienia,
                modifier = Modifier.weight(1f),
                onClick = { onWybierz(Zakladka.Ustawienia) }
            )
        }
    }
}

@Composable
private fun ElementZakladki(
    tytul: String,
    zaznaczona: Boolean,
    ikona: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val kolorZawartosci = if (zaznaczona) Color(0xFF06231A) else Color.White.copy(alpha = 0.5f)
    val tlo = if (zaznaczona) {
        Brush.horizontalGradient(colors = listOf(ZielonyMorski, ZielonyNeon))
    } else {
        Brush.horizontalGradient(
            colors = listOf(Color.White.copy(alpha = 0.05f), Color.White.copy(alpha = 0.05f))
        )
    }

    Box(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(tlo)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = ikona,
            contentDescription = tytul,
            tint = kolorZawartosci,
            modifier = Modifier.size(26.dp)
        )
    }
}
