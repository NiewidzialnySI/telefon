package dialer.dominik.pl.call

import android.media.AudioManager
import android.os.Build
import android.os.OutcomeReceiver
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.CallEndpoint
import android.telecom.CallEndpointException
import android.telecom.InCallService
import android.content.Context
import dialer.dominik.pl.ui.screens.wykonajFaktycznePolaczenie
import dialer.dominik.pl.ui.screens.pobierzPseudonim
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executor

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

    /** Czy klawiatura DTMF jest otwarta w UI. */
    var klawiatura: Boolean by mutableStateOf(false)

    /** Cyfry DTMF wpisane w tej rozmowie (podgląd nad klawiaturą). */
    var buforDtmf: String by mutableStateOf("")

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
 * Odrzuca przychodzące połączenie z właściwym przyczyną sieciową (decline / REJECTED),
 * a nie „zajęty / busy”.
 *
 * Na ścieżce InCallService: [Call.reject] → ConnectionService → [DisconnectCause.REJECTED].
 * [Call.disconnect] na RINGING na wielu modemach mapuje się na USER_BUSY („w rozmowie”).
 */
internal fun odrzucPrzychodzace(call: Call? = StanPolaczenia.polaczenie) {
    val c = call ?: return
    try {
        val stan = c.state
        val dzwoni = stan == Call.STATE_RINGING ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                stan == Call.STATE_SIMULATED_RINGING)
        if (dzwoni) {
            // API 30+: jawny powód DECLINED → DisconnectCause.REJECTED (nie BUSY).
            c.reject(Call.REJECT_REASON_DECLINED)
        }
        // Poza RINGING reject jest no-op — nie zastępujemy disconnect()-em (to dałoby BUSY).
    } catch (e: Exception) {
        e.printStackTrace()
        try {
            @Suppress("DEPRECATION")
            c.reject(false, null)
        } catch (e2: Exception) {
            e2.printStackTrace()
        }
    }
}

/** Kierunek przychodzący — preferuj [Call.Details.callDirection], nie tylko STATE_RINGING. */
internal fun czyPolaczeniePrzychodzace(call: Call): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        when (call.details.callDirection) {
            Call.Details.DIRECTION_INCOMING -> return true
            Call.Details.DIRECTION_OUTGOING -> return false
        }
    }
    return call.state == Call.STATE_RINGING ||
        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            call.state == Call.STATE_SIMULATED_RINGING)
}

/**
 * Jedna kolejka akcji audio/mute na połączenie dialing → active.
 * Nowe żądanie anuluje poprzednie oczekiwanie (ostatni wybór usera wygrywa).
 */
private val scopeGdyGotowa = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
private var jobGdyGotowa: Job? = null

/** Anuluje poprzedni Job i czeka na ACTIVE, potem wykonuje [akcja] (max ~15 s). */
internal fun kolejkujGdyGotowa(akcja: TelefonInCallService.() -> Unit) {
    jobGdyGotowa?.cancel()
    jobGdyGotowa = scopeGdyGotowa.launch {
        zAkcjamiGdyGotowa(akcja)
    }
}

/**
 * Wykonuje akcję, gdy usługa jest dostępna ORAZ trwa `Rozmowa`.
 * Preferuj [kolejkujGdyGotowa] z UI — gwarantuje jeden Job.
 */
internal suspend fun zAkcjamiGdyGotowa(akcja: TelefonInCallService.() -> Unit) {
    var proba = 0
    while (proba < 150) {
        val usluga = TelefonInCallService.instancja
        val wRozmowie = StanPolaczenia.stan == StanPolaczenia.Stan.Rozmowa
        if (usluga != null && wRozmowie) {
            try {
                usluga.akcja()
            } catch (_: Exception) {
            }
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

    /** API 34+: lista endpointów z [onAvailableCallEndpointsChanged]. */
    private var dostepneEndpointy: List<CallEndpoint> = emptyList()

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
        jobGdyGotowa?.cancel()
        jobGdyGotowa = null
        instancja = null
        super.onDestroy()
    }

    override fun onAvailableCallEndpointsChanged(availableEndpoints: MutableList<CallEndpoint>) {
        super.onAvailableCallEndpointsChanged(availableEndpoints)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            dostepneEndpointy = availableEndpoints.toList()
        }
    }

    override fun onCallEndpointChanged(callEndpoint: CallEndpoint) {
        super.onCallEndpointChanged(callEndpoint)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        // Podczas dzwonienia UI = wybór usera; system jeszcze raportuje EARPIECE.
        if (StanPolaczenia.stan == StanPolaczenia.Stan.Dzwoni) return
        zastosujEndpointDoPrzyciskow(callEndpoint.endpointType)
    }

    override fun onMuteStateChanged(isMuted: Boolean) {
        super.onMuteStateChanged(isMuted)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        // Nie gas mute wybrane w countdown — system zgłasza false aż do ACTIVE.
        if (StanPolaczenia.stan == StanPolaczenia.Stan.Dzwoni) return
        StanPolaczenia.wyciszony = isMuted
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        call.registerCallback(sluchaczPolaczen)

        StanPolaczenia.polaczenie = call
        val numer = call.details.handle?.schemeSpecificPart ?: ""
        StanPolaczenia.numerRozmowy = numer
        // Zachowaj mute/głośnik/BT wybrane podczas countdown (zadanaTrasa / wyciszony).
        // Reset tylko gdy to zupełnie nowe połączenie bez wcześniejszego wyboru na ekranie.
        val zachowajTrase = StanPolaczenia.zadanaTrasa != null
        val zachowajMute = StanPolaczenia.wyciszony
        StanPolaczenia.klawiatura = false
        StanPolaczenia.buforDtmf = ""
        if (!zachowajMute) StanPolaczenia.wyciszony = false
        if (!zachowajTrase) {
            StanPolaczenia.glosnik = false
            StanPolaczenia.bluetoothAktywny = false
        }
        // Każda rozmowa zaczyna z pustą notatką — zapis trafia do konkretnego wpisu CallLog.
        StanPolaczenia.notatkaRozmowy = ""
        val nazwa = WeryfikacjaNumeru.pobierzNazweKontaktu(this, numer)
        val pseudonim = nazwa?.let { pobierzPseudonim(this, it) }
        StanPolaczenia.rozmowca = pseudonim
            ?.takeIf { it.isNotBlank() }
            ?: nazwa
            ?: numer.ifBlank { "Nieznany numer" }
        @Suppress("DEPRECATION")
        aktualizujStan(call, call.state)

        // Nie ruszaj trasy audio przy RINGING — setAudioRoute na przychodzącym bywa
        // interpretowane przez OEM jak „zajęcie” linii; trasa i tak przy ACTIVE.
        // Nie resetuj do EARPIECE, gdy user już wybrał głośnik/BT podczas dzwonienia.
        if (!zachowajTrase && call.state != Call.STATE_RINGING) {
            try {
                ustawTrase(CallAudioState.ROUTE_WIRED_OR_EARPIECE)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        // Nie kasuj kolejki dialing — kolejkujGdyGotowa ma wykonać mute/trasę po ACTIVE.
        // (Jeśli nie było kolejki, przy ACTIVE zastosujemy zadanaTrasa poniżej.)

        PowiadomieniaPolaczen.anuluj(this)
        pokazPowiadomienieLubEkran(this, call)
    }

    /** Rozróżnia: zablokowany ekran → pełny ekran rozmowy; odblokowany → baner. */
    private fun pokazPowiadomienieLubEkran(context: Context, call: Call) {
        val tylkoDzwoni = call.state == Call.STATE_RINGING
        val keyguard = context.getSystemService(android.app.KeyguardManager::class.java)
        val ekranZablokowany = keyguard?.isKeyguardLocked == true

        val rozmowca = StanPolaczenia.rozmowca

        // IN_CALL_SERVICE_RINGING=true → system nie dzwoni; zawsze startujemy ZarzadcaDzwonka
        // (także przy samym banerze, gdy pełny ekran nie jest zamontowany).
        if (tylkoDzwoni) {
            ZarzadcaDzwonka.uruchom(context, czySpam = false)
        }

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
        ZarzadcaDzwonka.zatrzymaj()
        PowiadomieniaPolaczen.anuluj(this)
        PowiadomieniaTrwajacejRozmowy.anuluj(this)

        val czyPrzychodzace = StanPolaczenia.czyPrzychodzace
        val czasStartu = StanPolaczenia.czasStartu
        val rozmowca = StanPolaczenia.rozmowca
        val numer = StanPolaczenia.numerRozmowy

        // Powiadomienie o nieodebranym połączeniu
        if (czyPrzychodzace && czasStartu == 0L && numer.isNotBlank()) {
            PowiadomieniaNieodebranych.dodajIPokaz(this, rozmowca, numer)
        }

        // Zatrzymaj nagrywanie Shizuku przy końcu rozmowy.
        dialer.dominik.pl.nagranie.NagrywanieKontroler.stop()
        // Rozmowa się skończyła — zamknij własny dymek (nakładka), jeśli jest.
        DymekRozmowyService.stop(this)
        // Notatka → konkretny wpis CallLog (nie kafelek numeru). CallLog bywa
        // zapisany z opóźnieniem, więc próbujemy kilka razy w tle.
        val numerNotatki = StanPolaczenia.numerRozmowy
        val trescNotatki = StanPolaczenia.notatkaRozmowy
        val startNotatki = StanPolaczenia.czasStartu
        StanPolaczenia.notatkaRozmowy = ""
        if (numerNotatki.isNotBlank() && trescNotatki.isNotBlank()) {
            Thread {
                repeat(6) { proba ->
                    NotatkiRozmow.zapiszPoRozmowie(
                        this@TelefonInCallService,
                        numerNotatki,
                        startNotatki,
                        trescNotatki
                    )
                    val id = NotatkiRozmow.znajdzIdPolaczenia(
                        this@TelefonInCallService,
                        numerNotatki,
                        startNotatki
                    )
                    if (id != null && id > 0L &&
                        !NotatkiRozmow.pobierzNotatke(this@TelefonInCallService, id).isNullOrBlank()
                    ) {
                        return@Thread
                    }
                    try {
                        Thread.sleep(250L * (proba + 1))
                    } catch (_: InterruptedException) {
                        return@Thread
                    }
                }
            }.start()
        }
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
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            StanPolaczenia.urzadzeniaBluetooth =
                audioState.supportedBluetoothDevices?.toList() ?: emptyList()
        }
        // Countdown / DIALING: przyciski mute/głośnik trzymają optymistyczny stan usera.
        if (StanPolaczenia.stan == StanPolaczenia.Stan.Dzwoni) return

        StanPolaczenia.wyciszony = audioState.isMuted
        // route bywa bitmaską na OEM — nie porównuj na ==.
        val route = audioState.route
        val bt = (route and CallAudioState.ROUTE_BLUETOOTH) != 0
        val speaker = (route and CallAudioState.ROUTE_SPEAKER) != 0
        zastosujTraseDoPrzyciskow(
            glosnik = speaker && !bt,
            bluetooth = bt,
            routeMask = route,
        )
    }

    /**
     * Podświetlenie = rzeczywista trasa Telecom, ALE gdy user właśnie kliknął
     * (zadanaTrasa) — trzymaj optymistyczny stan, aż system dogoni (toggle BT↔ucho).
     */
    private fun zastosujEndpointDoPrzyciskow(typ: Int) {
        val bt = typ == CallEndpoint.TYPE_BLUETOOTH
        val speaker = typ == CallEndpoint.TYPE_SPEAKER
        val routeMask = when (typ) {
            CallEndpoint.TYPE_SPEAKER -> CallAudioState.ROUTE_SPEAKER
            CallEndpoint.TYPE_BLUETOOTH -> CallAudioState.ROUTE_BLUETOOTH
            CallEndpoint.TYPE_WIRED_HEADSET -> CallAudioState.ROUTE_WIRED_HEADSET
            else -> CallAudioState.ROUTE_EARPIECE
        }
        zastosujTraseDoPrzyciskow(glosnik = speaker, bluetooth = bt, routeMask = routeMask)
    }

    private fun zastosujTraseDoPrzyciskow(glosnik: Boolean, bluetooth: Boolean, routeMask: Int) {
        val zadana = StanPolaczenia.zadanaTrasa
        if (zadana != null) {
            val pasuje = when (zadana) {
                CallAudioState.ROUTE_SPEAKER ->
                    (routeMask and CallAudioState.ROUTE_SPEAKER) != 0
                CallAudioState.ROUTE_BLUETOOTH ->
                    (routeMask and CallAudioState.ROUTE_BLUETOOTH) != 0
                CallAudioState.ROUTE_WIRED_OR_EARPIECE,
                CallAudioState.ROUTE_EARPIECE,
                CallAudioState.ROUTE_WIRED_HEADSET ->
                    (routeMask and CallAudioState.ROUTE_BLUETOOTH) == 0 &&
                        (routeMask and CallAudioState.ROUTE_SPEAKER) == 0
                else -> false
            }
            if (!pasuje) {
                // System jeszcze na starej trasie — UI zostaje przy wyborze usera.
                StanPolaczenia.bluetoothAktywny = zadana == CallAudioState.ROUTE_BLUETOOTH
                StanPolaczenia.glosnik = zadana == CallAudioState.ROUTE_SPEAKER
                return
            }
            StanPolaczenia.zadanaTrasa = null
        }
        StanPolaczenia.bluetoothAktywny = bluetooth
        StanPolaczenia.glosnik = glosnik && !bluetooth
    }

    /** Wybiera konkretne urządzenie Bluetooth (lub domyślną trasę BT). */
    fun wybierzBluetooth(device: android.bluetooth.BluetoothDevice?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val bt = dostepneEndpointy.firstOrNull { it.endpointType == CallEndpoint.TYPE_BLUETOOTH }
            if (bt != null) {
                zadajEndpoint(bt)
                return
            }
        }
        if (device != null) {
            try {
                requestBluetoothAudio(device)
                return
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        setAudioRoute(CallAudioState.ROUTE_BLUETOOTH)
    }

    /** Przełącza wyciszenie mikrofonu i synchronizuje `StanPolaczenia.wyciszony`. */
    fun przelaczWyciszenie() {
        StanPolaczenia.wyciszony = !StanPolaczenia.wyciszony
        setMuted(StanPolaczenia.wyciszony)
    }

    /**
     * Stosuje już ustawiony (np. optymistycznie w Dialing) stan `wyciszony`
     * bez ponownego flipu — używane z `kolejkujGdyGotowa`.
     */
    fun zastosujWyciszenie() {
        setMuted(StanPolaczenia.wyciszony)
    }

    /** Przełącza między słuchawką, głośnikiem i Bluetooth (CallEndpoint na API 34+). */
    fun ustawTrase(trasa: Int) {
        // Przy zejściu z BT wyłącz SCO — inaczej OEM często zostaje na słuchawkach.
        if (trasa != CallAudioState.ROUTE_BLUETOOTH) {
            runCatching {
                val am = getSystemService(AudioManager::class.java) ?: return@runCatching
                if (am.isBluetoothScoOn) {
                    am.stopBluetoothSco()
                    @Suppress("DEPRECATION")
                    am.isBluetoothScoOn = false
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val typ = when (trasa) {
                CallAudioState.ROUTE_SPEAKER -> CallEndpoint.TYPE_SPEAKER
                CallAudioState.ROUTE_BLUETOOTH -> CallEndpoint.TYPE_BLUETOOTH
                CallAudioState.ROUTE_WIRED_HEADSET -> CallEndpoint.TYPE_WIRED_HEADSET
                else -> CallEndpoint.TYPE_EARPIECE
            }
            val ep = dostepneEndpointy.firstOrNull { it.endpointType == typ }
                ?: if (typ == CallEndpoint.TYPE_EARPIECE) {
                    dostepneEndpointy.firstOrNull {
                        it.endpointType == CallEndpoint.TYPE_WIRED_HEADSET
                    }
                } else null
            if (ep != null) {
                zadajEndpoint(ep)
                return
            }
        }
        setAudioRoute(trasa)
    }

    private fun zadajEndpoint(endpoint: CallEndpoint) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        val executor: Executor = mainExecutor
        requestCallEndpointChange(
            endpoint,
            executor,
            object : OutcomeReceiver<Void?, CallEndpointException> {
                override fun onResult(result: Void?) {}
                override fun onError(error: CallEndpointException) {
                    // Fallback do starego API, gdy endpoint nie przyjął zmiany.
                    try {
                        val trasa = when (endpoint.endpointType) {
                            CallEndpoint.TYPE_SPEAKER -> CallAudioState.ROUTE_SPEAKER
                            CallEndpoint.TYPE_BLUETOOTH -> CallAudioState.ROUTE_BLUETOOTH
                            CallEndpoint.TYPE_WIRED_HEADSET -> CallAudioState.ROUTE_WIRED_HEADSET
                            else -> CallAudioState.ROUTE_WIRED_OR_EARPIECE
                        }
                        setAudioRoute(trasa)
                    } catch (_: Exception) {
                    }
                }
            }
        )
    }

    /** Wysyła ton DTMF i dopisuje cyfrę do podglądu. */
    fun wyslijTon(cyfra: Char) {
        val call = StanPolaczenia.polaczenie ?: return
        StanPolaczenia.buforDtmf = (StanPolaczenia.buforDtmf + cyfra).takeLast(32)
        call.playDtmfTone(cyfra)
        // Natychmiastowy stop = niesłyszalny ton; Fossify trzyma ~DIALPAD_TONE_LENGTH_MS.
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            try {
                call.stopDtmfTone()
            } catch (_: Exception) {
            }
        }, 180L)
    }

    /** Mute + trasa z countdown — egzekwuj przy ACTIVE (niezależnie od kolejki Job). */
    private fun zastosujWyboryPoOdebraniu() {
        try {
            setMuted(StanPolaczenia.wyciszony)
            val trasa = StanPolaczenia.zadanaTrasa
            if (trasa != null) {
                ustawTrase(trasa)
            } else if (StanPolaczenia.glosnik) {
                ustawTrase(CallAudioState.ROUTE_SPEAKER)
            } else if (StanPolaczenia.bluetoothAktywny) {
                ustawTrase(CallAudioState.ROUTE_BLUETOOTH)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        // Po ACTIVE dociągnij realną trasę (auto-BT często już gra, a callback jeszcze nie).
        mainExecutor.execute { odswiezPrzyciskiZBiezacejTrasy() }
        mainExecutor.execute {
            android.os.Handler(mainLooper).postDelayed({
                odswiezPrzyciskiZBiezacejTrasy()
            }, 600L)
        }
    }

    private fun odswiezPrzyciskiZBiezacejTrasy() {
        if (StanPolaczenia.stan == StanPolaczenia.Stan.Dzwoni) return
        // Nie nadpisuj wyboru usera w trakcie przełączania.
        if (StanPolaczenia.zadanaTrasa != null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val ep = runCatching { currentCallEndpoint }.getOrNull() ?: return
            zastosujEndpointDoPrzyciskow(ep.endpointType)
        }
    }

    private fun aktualizujStan(call: Call, stan: Int) {
        // Kierunek z Details (stabilny); STATE_RINGING tylko jako fallback.
        // Wcześniej NEW/CONNECTING przychodzącego miało czyPrzychodzace=false → UI
        // pokazywał Dialing + disconnect() → busy tone u dzwoniącego.
        StanPolaczenia.czyPrzychodzace = czyPolaczeniePrzychodzace(call)
        StanPolaczenia.stan = when (stan) {
            Call.STATE_NEW, Call.STATE_CONNECTING, Call.STATE_DIALING -> {
                // Podnieś daemon ADB zanim rozmowa będzie ACTIVE (~11 s delay).
                dialer.dominik.pl.nagranie.NagrywanieKontroler.rozgrzej(this@TelefonInCallService)
                StanPolaczenia.Stan.Dzwoni
            }
            Call.STATE_RINGING -> {
                dialer.dominik.pl.nagranie.NagrywanieKontroler.rozgrzej(this@TelefonInCallService)
                StanPolaczenia.Stan.Dzwoni
            }
            Call.STATE_ACTIVE -> {
                ZarzadcaDzwonka.zatrzymaj()
                if (StanPolaczenia.czasStartu == 0L) {
                    StanPolaczenia.czasStartu = System.currentTimeMillis()
                }
                // Zastosuj wybory z fazy dzwonienia (mute / głośnik / BT).
                zastosujWyboryPoOdebraniu()
                PowiadomieniaTrwajacejRozmowy.pokazPowiadomienie(
                    this@TelefonInCallService,
                    StanPolaczenia.rozmowca,
                    StanPolaczenia.wyciszony
                )
                // Auto-nagrywanie — tylko gdy włączone w ustawieniach.
                dialer.dominik.pl.nagranie.NagrywanieKontroler.let { nag ->
                    val ctx = this@TelefonInCallService
                    if (dialer.dominik.pl.nagranie.NagrywanieUstawienia.czyWlaczone(ctx) &&
                        dialer.dominik.pl.nagranie.NagrywanieUstawienia.czyAuto(ctx) &&
                        !nag.nagrywa
                    ) {
                        nag.start(ctx, StanPolaczenia.numerRozmowy, auto = true)
                    }
                }
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
