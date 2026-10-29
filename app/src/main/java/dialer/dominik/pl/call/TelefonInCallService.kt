package dialer.dominik.pl.call

import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.content.Context
import dialer.dominik.pl.ui.screens.wykonajFaktycznePolaczenie
import dialer.dominik.pl.ui.screens.pobierzPseudonim
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/**
 * Stan aktualnego połączenia — jeden wspólny obiekt dla InCallService i UI.
 *
 * InCallService żyje poza Compose, a ekran rozmowy jest w Compose.
 * Ten obiekt jest mostem: pola to `mutableStateOf`, więc każda zmiana
 * natychmiast odświeża interfejs.
 */
object StanPolaczenia {

    /** Aktualnie prowadzone połączenie (null = brak). */
    var polaczenie: Call? by mutableStateOf(null)

    /** Numer lub nazwa rozmówcy do wyświetlenia. */
    var rozmowca: String by mutableStateOf("")

    /** Surowy numer (bez nazwy) rozmówcy — do pobierania zdjęcia kontaktu. */
    var numerRozmowy: String by mutableStateOf("")

    /** Czy to połączenie przychodzące (dzwoni do nas). */
    var czyPrzychodzace: Boolean by mutableStateOf(false)

    /** Stan połączenia: Nowe, Dzwoni, Rozmowa, Zawieszone, Rozlaczone. */
    var stan: Stan by mutableStateOf(Stan.Brak)

    /** Czas rozpoczęcia rozmowy (millis) — do licznika trwania. */
    var czasStartu: Long by mutableStateOf(0L)

    /** Czy mikrofon jest wyciszony. */
    var wyciszony: Boolean by mutableStateOf(false)

    /** Czy głośnik jest włączony. */
    var glosnik: Boolean by mutableStateOf(false)

    /** Czy Bluetooth jest aktywny jako trasa audio. */
    var bluetoothAktywny: Boolean by mutableStateOf(false)

    /** Dostępne urządzenia Bluetooth do wyboru. */
    var urzadzeniaBluetooth: List<android.bluetooth.BluetoothDevice> by mutableStateOf(emptyList())

    /** Dostępne urządzenia audio (słuchawka, głośnik, Bluetooth). */
    var urzadzenia: List<CallAudioState> by mutableStateOf(emptyList())

    /** Czy trwa nagrywanie. */
    var nagrywanie: Boolean by mutableStateOf(false)

    /** Czy klawiatura DTMF jest otwarta w UI. */
    var klawiatura: Boolean by mutableStateOf(false)

    /**
     * Trasa audio ZADANA przez użytkownika PRZED połączeniem (głośnik/BT podczas
     * dzwonienia). Podczas `STATE_DIALING` platforma ignoruje `setAudioRoute`, więc
     * realnie nie da się jeszcze przełączyć — ale decyzja usera musi PRZETRWAĆ do
     * momentu połączenia, żeby przycisk nie "gasł", gdy `onCallAudioStateChanged`
     * nadpisze stan na podstawie rzeczywistego audio (jeszcze EARPIECE). null = brak
     * wymuszenia (podążaj za audio); wartość = tras do wymuszenia na tym połączeniu.
     */
    var zadanaTrasa: Int? by mutableStateOf(null)

    /** Treść notatki wpisanej podczas rozmowy (wczytana dla tego numeru). */
    var notatkaRozmowy: String by mutableStateOf("")

    /**
     * Czy ekran rozmowy otworzono z dymka (true) — wtedy awatar startuje z rogu
     * i leci na środek sprężyną. Jednorazowa flaga: EkranTrwajacejRozmowy czyści ją
     * zaraz po odczytaniu.
     */
    var wracaZDymka: Boolean by mutableStateOf(false)

    enum class Stan { Brak, Dzwoni, Rozmowa, Zawieszone, Rozlaczone }
}

/**
 * Wykonuje akcję, gdy usługa [TelefonInCallService] jest dostępna ORAZ trwa połączenie
 * (stan `Rozmowa`).
 *
 * Platforma (Telecom/InCallService) IGNORUJE `setAudioRoute` / `setMuted`, dopóki
 * połączenie nie ma aktywnego audio — tzn. podczas `STATE_DIALING` / `STATE_CONNECTING`
 * (gdy dzwoni) przyciski Głośnik/Wycisz/BT cicho nic nie robią, choć Call już istnieje.
 * Dopiero przy `STATE_ACTIVE` (rozmowa) trasa i wyciszenie faktycznie działają.
 *
 * Ta funkcja: (1) czeka aż `TelefonInCallService.instancja` != null (Telecom binduje
 * asynchronicznie po pokazaniu ekranu), (2) czeka aż `StanPolaczenia.stan` == `Rozmowa`,
 * potem wykonuje akcję w try-catch. Max ~15 s (połączenie zwykle łączy się w kilka sekund);
 * jeśli po tym czasie nie ma rozmowy — akcja się nie wykonuje.
 */
internal suspend fun zAkcjamiGdyGotowa(akcja: TelefonInCallService.() -> Unit) {
    var proba = 0
    while (proba < 150) { // 150 × 100 ms = 15 s max
        val usluga = TelefonInCallService.instancja
        val wRozmowie = StanPolaczenia.stan == StanPolaczenia.Stan.Rozmowa
        if (usluga != null && wRozmowie) {
            try { usluga.akcja() } catch (e: Exception) { /* ignoruj przejściowe */ }
            return
        }
        delay(100)
        proba++
    }
}

/**
 * Odroczenie faktycznego nawiązania połączenia wychodzącego.
 *
 * Po kliknięciu „Zadzwoń" pokazujemy od razu ekran rozmowy z możliwością
 * anulowania, ale samo wybranie numeru (placeCall / ACTION_CALL) wykonujemy
 * dopiero po [OPOZNIENIE_MS]. Jeśli użytkownik kliknie „Anuluj" w tym oknie,
 * połączenie w ogóle nie zostanie nawiązane.
 */
object OczekiwanieNaPolaczenie {
    /** Opóźnienie od kliknięcia do faktycznego wybrania numeru (ms) = 3 s odliczania 3→2→1→0. */
    const val OPOZNIENIE_MS = 3000L

    /** Liczba kroków odliczania pokazywana na przycisku Rozłącz (od tej w dół do zera). */
    const val LICZBA_POCZATKOWA = 3

    /** Krok odliczania w ms (3000 / 3 = 1000 ms na cyfrę). */
    private val KROK_MS = OPOZNIENIE_MS / LICZBA_POCZATKOWA

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var runnable: Runnable? = null

    /** Czy czekamy jeszcze na faktyczne nawiązanie (można anulować). */
    var czyOczekuje: Boolean = false
        private set

    /** Aktualna cyfra odliczania pokazywana na przycisku Rozłącz (3, 2, 1, 0). */
    var odliczanie: Int by mutableStateOf(0)
        private set

    /** Planuje nawiązanie połączenia po odliczeniu 3→2→1→0. */
    fun rozpocznij(context: Context, numer: String) {
        anuluj()
        czyOczekuje = true
        odliczanie = LICZBA_POCZATKOWA
        // Co sekundę zmniejszamy licznik, aż do zera. Przy każdym kroku aktualizujemy
        // UI (mutableStateOf), więc przycisk Rozłącz widzi aktualną cyfrę.
        val zadanie = object : Runnable {
            override fun run() {
                if (!czyOczekuje) return
                odliczanie -= 1
                if (odliczanie > 0) {
                    handler.postDelayed(this, KROK_MS)
                } else {
                    // Odliczyliśmy do zera — faktycznie wybierz numer.
                    czyOczekuje = false
                    runnable = null
                    wykonajFaktycznePolaczenie(context, numer)
                }
            }
        }
        runnable = zadanie
        handler.postDelayed(zadanie, KROK_MS)
    }

    /** Odwołuje zaplanowane nawiązanie (gdy nadal czekamy). Zwraca true, jeśli anulowano. */
    fun anuluj(): Boolean {
        val było = czyOczekuje
        val zadanie = runnable
        if (zadanie != null) {
            handler.removeCallbacks(zadanie)
        }
        runnable = null
        czyOczekuje = false
        odliczanie = 0
        return było
    }
}

/**
 * Usługa, przez którą system przekazuje aplikacji informacje o połączeniach.
 * Wymaga roli ROLE_DIALER — bez niej system użyje swojej tarczy.
 */
@Suppress("DEPRECATION")
class TelefonInCallService : InCallService() {

    private val sluchaczPolaczen = object : Call.Callback() {
        override fun onStateChanged(call: Call, stan: Int) {
            aktualizujStan(call, stan)
        }

        override fun onCallDestroyed(call: Call) {
            if (StanPolaczenia.polaczenie === call) {
                StanPolaczenia.stan = StanPolaczenia.Stan.Brak
                StanPolaczenia.polaczenie = null
                EkranRozmowy.zamknij(this@TelefonInCallService)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instancja = this
    }

    override fun onDestroy() {
        instancja = null
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        call.registerCallback(sluchaczPolaczen)

        StanPolaczenia.polaczenie = call
        val numer = call.details.handle?.schemeSpecificPart ?: ""
        StanPolaczenia.numerRozmowy = numer
        // Reset stanu UI z poprzedniej rozmowy (klawiatura DTMF, głośnik, wyciszenie) —
        // żeby nowe połączenie (też przychodzące) nie dziedziczyło stanu.
        StanPolaczenia.klawiatura = false
        StanPolaczenia.wyciszony = false
        StanPolaczenia.glosnik = false
        // Wczytaj istniejącą notatkę dla tego numeru (jeśli była wcześniej).
        StanPolaczenia.notatkaRozmowy = NotatkiRozmow.pobierzNotatke(this, numer) ?: ""
        // Priorytet: pseudonim > nazwa kontaktu > surowy numer.
        // PhoneLookup łączy numery jednego kontaktu po CONTACT_ID,
        // więc zadziała nawet gdy kontakt ma 2 numery w książce.
        val nazwa = WeryfikacjaNumeru.pobierzNazweKontaktu(this, numer)
        val pseudonim = nazwa?.let { pobierzPseudonim(this, it) }
        StanPolaczenia.rozmowca = pseudonim
            ?.takeIf { it.isNotBlank() }
            ?: nazwa
            ?: numer.ifBlank { "Nieznany numer" }
        @Suppress("DEPRECATION")
        aktualizujStan(call, call.state)

        // Zresetuj trasę audio do słuchawki na starcie KAŻDEGO nowego połączenia.
        // System (TelecomManager) pamięta ostatnią trasę między połączeniami —
        // bez tego resetu następna rozmowa dziedziczy "Głośnik"/BT z poprzedniej,
        // co user odbiera jako "samo się ustawia na głośnomówiący". Domyślnie każda
        // rozmowa startuje na słuchawce; głośnik/BT user włącza sam, gdy chce.
        try {
            setAudioRoute(CallAudioState.ROUTE_WIRED_OR_EARPIECE)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Zawsze pokazuj nasz dedykowany pełnoekranowy interfejs rozmowy i anuluj ewentualne powiadomienia w tle
        PowiadomieniaPolaczen.anuluj(this)
        pokazPowiadomienieLubEkran(this, call)
    }

    /** Rozróżnia: zablokowany ekran → pełny ekran rozmowy; odblokowany → baner. */
    private fun pokazPowiadomienieLubEkran(context: Context, call: Call) {
        val tylkoDzwoni = call.state == Call.STATE_RINGING
        val keyguard = context.getSystemService(android.app.KeyguardManager::class.java)
        val ekranZablokowany = keyguard?.isKeyguardLocked == true

        val rozmowca = StanPolaczenia.rozmowca

        if (ekranZablokowany || !tylkoDzwoni) {
            // Pełnoekranowy ekran dzwonienia/rozmowy (nad blokadą).
            EkranRozmowy.pokaz(context)
        } else {
            // Telefon odblokowany i użytkownik z niego korzysta → baner heads-up.
            PowiadomieniaPolaczen.pokazBaner(context, rozmowca)
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        call.unregisterCallback(sluchaczPolaczen)
        PowiadomieniaPolaczen.anuluj(this)
        PowiadomieniaTrwajacejRozmowy.anuluj(this)
        NagrywanieRozmowService.stop(this)
        StanPolaczenia.nagrywanie = false
        // Rozmowa się skończyła — zamknij własny dymek (nakładka), jeśli jest.
        DymekRozmowyService.stop(this)
        // Zapisz notatkę do logu tego numeru (trwale w SharedPreferences).
        if (StanPolaczenia.numerRozmowy.isNotBlank()) {
            NotatkiRozmow.zapiszNotatke(this, StanPolaczenia.numerRozmowy, StanPolaczenia.notatkaRozmowy)
        }
        StanPolaczenia.notatkaRozmowy = ""
        StanPolaczenia.zadanaTrasa = null
        if (StanPolaczenia.polaczenie === call) {
            StanPolaczenia.polaczenie = null
            StanPolaczenia.stan = StanPolaczenia.Stan.Brak
            StanPolaczenia.czasStartu = 0L
            // Zamknij ekran rozmowy — połączenie się skończyło
            EkranRozmowy.zamknij(this)
        }
    }

    @Deprecated("CallAudioState zastąpione przez CallEndpoint w API 34")
    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        super.onCallAudioStateChanged(audioState)
        audioState ?: return
        StanPolaczenia.wyciszony = audioState.isMuted
        // Jeśli user zadaje trasę (głośnik/BT przy dzwonieniu), trzymaj jego wybór —
        // nie nadpisuj go rzeczywistym audio (które przy połączeniu to jeszcze EARPIECE,
        // więc przycisk by "zgasił" mimo woli usera). Trasę realnie wymuszamy osobno,
        // gdy rozmowa się połączy.
        val zadana = StanPolaczenia.zadanaTrasa
        if (zadana == null) {
            StanPolaczenia.glosnik = audioState.route == CallAudioState.ROUTE_SPEAKER
            StanPolaczenia.bluetoothAktywny = audioState.route == CallAudioState.ROUTE_BLUETOOTH
        } else if (audioState.route == zadana) {
            // Trasa już realnie ustawiona na zadaną — zwolnij wymuszenie, niech
            // kolejne zmiany audio znów aktualizują UI normalnie.
            StanPolaczenia.zadanaTrasa = null
            StanPolaczenia.glosnik = audioState.route == CallAudioState.ROUTE_SPEAKER
            StanPolaczenia.bluetoothAktywny = audioState.route == CallAudioState.ROUTE_BLUETOOTH
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            StanPolaczenia.urzadzeniaBluetooth = audioState.supportedBluetoothDevices?.toList() ?: emptyList()
        }
    }

    /** Wybiera domyślną trasę Bluetooth. */
    @Suppress("UNUSED_PARAMETER")
    fun wybierzBluetooth(device: android.bluetooth.BluetoothDevice?) {
        setAudioRoute(CallAudioState.ROUTE_BLUETOOTH)
    }

    /** Wycisza lub włącza mikrofon zgodnie z optymistycznym stanem UI (`StanPolaczenia.wyciszony`). */
    fun przelaczWyciszenie() {
        // UI przełączyło już StanPolaczenia.wyciszony (optymistycznie, żeby przycisk
        // zareagował natychmiast). Ustawiamy mikrofon na ten zapamiętany stan, a nie
        // czyścimy z aktualnego callAudioState (który przy połączeniu może się różnić).
        setMuted(StanPolaczenia.wyciszony)
    }

    /** Przełącza między słuchawką, głośnikiem i Bluetooth. */
    fun ustawTrase(trasa: Int) {
        setAudioRoute(trasa)
    }

    /** Wysyła ton DTMF (klawiatura w trakcie rozmowy). */
    fun wyslijTon(cyfra: Char) {
        StanPolaczenia.polaczenie?.playDtmfTone(cyfra)
        StanPolaczenia.polaczenie?.stopDtmfTone()
    }

    private fun aktualizujStan(call: Call, stan: Int) {
        if (stan == Call.STATE_RINGING) {
            StanPolaczenia.czyPrzychodzace = true
        } else if (stan == Call.STATE_DIALING || stan == Call.STATE_CONNECTING) {
            StanPolaczenia.czyPrzychodzace = false
        }
        StanPolaczenia.stan = when (stan) {
            Call.STATE_NEW, Call.STATE_CONNECTING, Call.STATE_DIALING -> StanPolaczenia.Stan.Dzwoni
            Call.STATE_RINGING -> StanPolaczenia.Stan.Dzwoni
            Call.STATE_ACTIVE -> {
                if (StanPolaczenia.czasStartu == 0L) {
                    StanPolaczenia.czasStartu = System.currentTimeMillis()
                }
                // Pokaż trwałe powiadomienie rozmowy — użytkownik może zamknąć
                // dymek (bubble), a rozmowa i tak trwa; powiadomienie zawsze
                // pozwala wrócić do ekranu, wyciszyć mikrofon lub rozłączyć.
                PowiadomieniaTrwajacejRozmowy.pokazPowiadomienie(
                    this@TelefonInCallService,
                    StanPolaczenia.rozmowca,
                    StanPolaczenia.wyciszony
                )
                StanPolaczenia.Stan.Rozmowa
            }
            Call.STATE_HOLDING -> StanPolaczenia.Stan.Zawieszone
            Call.STATE_DISCONNECTED -> StanPolaczenia.Stan.Rozlaczone
            else -> StanPolaczenia.Stan.Rozmowa
        }
    }

    companion object {
        /** Instancja usługi — pozwala UI wywołać akcje (wycisz, głośnik, DTMF). */
        var instancja: TelefonInCallService? = null
            private set
    }
}
