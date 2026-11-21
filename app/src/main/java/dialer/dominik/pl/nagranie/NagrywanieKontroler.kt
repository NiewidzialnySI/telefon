package dialer.dominik.pl.nagranie

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

sealed class StanNagrywania {
    data object Bezczynny : StanNagrywania()
    data class Probuje(val silnik: String) : StanNagrywania()
    data class Aktywne(val plik: File, val silnik: String) : StanNagrywania()
    data class Blad(val komunikat: String) : StanNagrywania()
}

/**
 * Multi-silnik nagrywania rozmów — własna drabinka dialera + daemon ADB.
 *
 * Kolejność (po cache preferowanej, ze shellem):
 * 1. Hybryda MIC+DOWNLINK — „ja” z mikrofonu, gdy OEM blokuje UPLINK
 * 2. Shell UPLINK+DOWNLINK — klasyczny modem
 * 3. Shell VOICE_CALL stereo/mono
 * 4. Shell AudioPolicy / VOICE_COMMUNICATION / MIC
 *
 * Obie strony wymagane dla Dual/Hybryda/Stereo — jednostronne → soft-switch.
 */
object NagrywanieKontroler {
    private const val TAG = "Nagrywanie"
    private const val SAMPLE_RATE = 16_000
    private const val PROG_RMS = 0.005f
    /** HAL / modem często milczy pierwsze ~0.5 s — nie karz za to silnika. */
    private const val GRACE_MS = 500L
    /** Po grace: tyle ciszy = silnik martwy (krótko — inaczej tracimy początek rozmowy). */
    private const val VERIFY_MS = 900L
    /**
     * Dual/hybryda: downlink ColorOS bywa cichy ~1 s po ACTIVE.
     * Za długi wait = użytkownik słyszy „nagrywa od połowy” (soft-switch + ADB).
     */
    private const val GRACE_OBU_MS = 1_000L
    private const val VERIFY_OBU_MS = 1_600L
    /** Jedna strona już żyje — krótki dopinek na drugą, potem soft-switch. */
    private const val DOPINEK_OBU_MS = 900L
    /** Soft-switch: trzymaj do 12 s PCM z POCZĄTKU pliku (nie tylko ring 1.5 s). */
    private const val STASH_MAX_MS = 12_000
    private const val PREROLL_MS = 8_000
    /** Wczesny MIC podczas czekania na ADB — max bufor (ms). */
    private const val EARLY_MIC_MS = 12_000
    /** W trakcie rozmowy nie wolno blokować 20 s na start daemona. */
    private const val ZAPEWNIJ_W_ROZMOWIE_MS = 1_800L
    /** Cold-start ADB przy dzwonku (tryb firmowy: Wireless+USB były OFF). */
    private const val ZAPEWNIJ_ROZGRZEWKA_FIRM_MS = 16_000L
    private const val ZAPEWNIJ_W_ROZMOWIE_FIRM_MS = 10_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    var stan: StanNagrywania by mutableStateOf(StanNagrywania.Bezczynny)
        private set

    var nagrywa: Boolean by mutableStateOf(false)
        private set

    var ostatniSilnik: String by mutableStateOf("")
        private set

    private val zatrzymajFlaga = AtomicBoolean(false)
    private var job: Job? = null
    /** Join przed kolejnym [start] — unikaj dwóch torów PCM / ADB wyścigów. */
    private var cleanupJob: Job? = null
    /** Anuluje in-flight [rozgrzej], żeby po szybkim Rozłącz nie włączał ADB z powrotem. */
    @Volatile private var rozgrzewkaGen = 0
    private var rozgrzewkaJob: Job? = null
    @Volatile private var appCtx: Context? = null
    @Volatile private var aktywnyPlik: File? = null
    @Volatile private var lokalnyRecord: AudioRecord? = null
    @Volatile private var pumpWatki: List<Thread> = emptyList()
    @Volatile private var keepalive: KeepaliveAudio? = null
    @Volatile private var preroll: PrerollBufor? = null
    /** Soft-switch: zachowany ogon z poprzedniej próby → dopisany na start nowego WAV. */
    @Volatile private var zachowanyPreroll: ByteArray = ByteArray(0)

    /**
     * Rozgrzewka przed / przy ACTIVE (DIALING wychodzące): podnieś daemon ADB,
     * żeby przy odebraniu nie czekać ~10–20 s na shell.
     * W trybie firmowym najpierw włącza ADB (było OFF pod aplikacje firmowe).
     * Po zakończeniu sprawdza stan rozmowy — jeśli już Rozłącz, gasi ADB.
     */
    fun rozgrzej(ctx: Context) {
        if (!NagrywanieUstawienia.czyWlaczone(ctx)) return
        appCtx = ctx.applicationContext
        if (ShellKlient.czyShellGotowy()) return
        // Nie anuluj trwającej rozgrzewki (RINGING → Odbierz wołało drugi raz i psuło ADB).
        if (rozgrzewkaJob?.isActive == true) return
        val gen = ++rozgrzewkaGen
        rozgrzewkaJob = scope.launch {
            val app = ctx.applicationContext
            val firmowy = NagrywanieUstawienia.czyAdbTylkoNaRozmowe(app)
            try {
                if (firmowy) {
                    if (!czyPolaczenieWToku()) return@launch
                    AdbNaprawa.wlacDlaNagrywania(app)
                }
                if (gen != rozgrzewkaGen || !isActive) return@launch
                val limit = if (firmowy) ZAPEWNIJ_ROZGRZEWKA_FIRM_MS else 8_000L
                val ok = withTimeoutOrNull(limit) {
                    if (gen != rozgrzewkaGen || !isActive) return@withTimeoutOrNull false
                    runCatching { ShellKlient.zapewnij(app, autoWlacAdb = true) }.getOrDefault(false)
                } == true
                Log.i(TAG, "rozgrzej firmowy=$firmowy → $ok shell=${ShellKlient.czyShellGotowy()}")
            } finally {
                // Szybkie Rozłącz podczas DIALING / odliczania: nie zostawiaj ADB ON.
                if (firmowy && gen == rozgrzewkaGen && !czyPolaczenieWToku()) {
                    AdbNaprawa.wylaczDlaFirmowki(app)
                    Log.i(TAG, "rozgrzej — brak rozmowy, ADB OFF")
                }
            }
        }
    }

    /** Dzwoni / rozmowa / hold — albo żywy obiekt Call. */
    private fun czyPolaczenieWToku(): Boolean {
        val call = dialer.dominik.pl.call.StanPolaczenia.polaczenie
        if (call != null) {
            val s = runCatching { call.state }.getOrNull()
            if (s != null && s != android.telecom.Call.STATE_DISCONNECTED &&
                s != android.telecom.Call.STATE_DISCONNECTING
            ) {
                return true
            }
        }
        return when (dialer.dominik.pl.call.StanPolaczenia.stan) {
            dialer.dominik.pl.call.StanPolaczenia.Stan.Dzwoni,
            dialer.dominik.pl.call.StanPolaczenia.Stan.Rozmowa,
            dialer.dominik.pl.call.StanPolaczenia.Stan.Zawieszone -> true
            else -> false
        }
    }

    fun start(ctx: Context, numer: String, auto: Boolean = false) {
        if (nagrywa || job?.isActive == true) return
        if (!NagrywanieUstawienia.czyZgodaPrawna(ctx) || !NagrywanieUstawienia.czyWlaczone(ctx)) {
            stan = StanNagrywania.Blad("Włącz nagrywanie i zaakceptuj warunki w Ustawieniach")
            return
        }
        appCtx = ctx.applicationContext
        zatrzymajFlaga.set(false)
        job = scope.launch {
            // Poczekaj aż poprzedni stop() domknie PCM/shell — inaczej dual_up_dn → 0.
            cleanupJob?.join()
            try {
                uruchomDrabinke(ctx.applicationContext, numer)
            } catch (t: Throwable) {
                Log.e(TAG, "start", t)
                stan = StanNagrywania.Blad(t.message ?: "błąd")
                nagrywa = false
            } finally {
                keepalive?.stop()
                keepalive = null
            }
        }
    }

    fun stop() {
        zatrzymajFlaga.set(true)
        job?.cancel()
        job = null
        cleanupJob = scope.launch {
            withContext(Dispatchers.IO) {
                keepalive?.stop()
                keepalive = null
                runCatching { ShellKlient.usluga()?.stop() }
                runCatching { lokalnyRecord?.stop(); lokalnyRecord?.release() }
                lokalnyRecord = null
                pumpWatki.forEach { runCatching { it.join(800) } }
                pumpWatki = emptyList()
            }
            aktywnyPlik?.let { plik ->
                if (plik.exists() && plik.length() < 1024) {
                    NagrywaniePliki.usunZSidecar(plik)
                    stan = StanNagrywania.Blad("Nagranie puste")
                } else if (plik.exists() && OczyszczanieEchaNagran.maSidecar(plik)) {
                    // Offline: sidecar L/R → czyste mono (lepsze niż live AEC).
                    withContext(Dispatchers.IO) {
                        OczyszczanieEchaNagran.przetworzPoNagraniu(plik)
                    }
                }
            }
            aktywnyPlik = null
            nagrywa = false
            if (stan !is StanNagrywania.Blad) stan = StanNagrywania.Bezczynny
            // Daj onCallRemoved / nowemu Call chwilę — nie gaś ADB gdy rozmowa trwa.
            delay(400)
            zgasAdbPoRozmowieJesliFirmowy()
        }
    }

    /**
     * Gdy rozmowa padła bez nagrywania (anulowanie 3…2…1, nieodebrane, Rozłącz
     * przed ACTIVE) — unieważnij rozgrzewkę i zgaś ADB. Powtórne sprawdzenie
     * chroni przed wyścigiem z kończącym się [rozgrzej]/autoWlacAdb).
     */
    fun poRozlaczeniu(ctx: Context) {
        appCtx = ctx.applicationContext
        if (!NagrywanieUstawienia.czyAdbTylkoNaRozmowe(ctx)) return
        rozgrzewkaGen++
        rozgrzewkaJob?.cancel()
        rozgrzewkaJob = null
        if (nagrywa || job?.isActive == true) return // stop() sam zgasi
        val app = ctx.applicationContext
        scope.launch {
            delay(350)
            if (czyPolaczenieWToku()) return@launch
            AdbNaprawa.wylaczDlaFirmowki(app)
            Log.i(TAG, "poRozlaczeniu — ADB OFF (Portal Firmy)")
            // Late zapewnij/wlacAdb z anulowanej rozgrzewki — dogaś.
            delay(900)
            if (!czyPolaczenieWToku() &&
                (AdbNaprawa.czyAdbWifi(app) || AdbNaprawa.czyAdbUsb(app))
            ) {
                AdbNaprawa.wylaczDlaFirmowki(app)
                Log.i(TAG, "poRozlaczeniu — dogaszanie ADB")
            }
        }
    }

    private fun zgasAdbPoRozmowieJesliFirmowy() {
        val ctx = appCtx ?: return
        if (!NagrywanieUstawienia.czyAdbTylkoNaRozmowe(ctx)) return
        if (czyPolaczenieWToku()) {
            Log.i(TAG, "stop — pomijam ADB OFF (rozmowa/dzwonek trwa)")
            return
        }
        rozgrzewkaGen++
        rozgrzewkaJob?.cancel()
        rozgrzewkaJob = null
        AdbNaprawa.wylaczDlaFirmowki(ctx)
        Log.i(TAG, "stop — ADB OFF (Portal Firmy)")
    }

    fun przelacz(ctx: Context, numer: String) {
        if (nagrywa) stop() else start(ctx, numer)
    }

    private fun drabinka(ctx: Context): List<SilnikNagrywania> {
        val shell = ShellKlient.czyShellGotowy()
        val all = SilnikNagrywania.entries.toList()
        // Hybryda/Dual/VC stereo: nie wyrzucaj za „cicha” (false positive po krótkim verify).
        // Fail (otwarcie padło) nadal pomijamy.
        fun dopuszczony(s: SilnikNagrywania): Boolean = when (s) {
            SilnikNagrywania.HybrydaMicDownlink,
            SilnikNagrywania.DualUpDown,
            SilnikNagrywania.VoiceCallStereo,
            SilnikNagrywania.MicOnly -> !ZdolnosciCache.czyFail(ctx, s)
            else -> !ZdolnosciCache.czyFail(ctx, s) && !ZdolnosciCache.czyCicha(ctx, s)
        }
        var filtered = all.filter(::dopuszczony).ifEmpty { all }
        // Za krótka drabinka (same dual+hybryda) → dorzuć ciche z cache jako ostatnią deskę.
        if (filtered.size < 4) {
            val dopiski = all.filter {
                it !in filtered && !ZdolnosciCache.czyFail(ctx, it)
            }
            filtered = (filtered + dopiski).ifEmpty { all }
        }
        if (!shell) {
            return filtered.sortedBy { silnik ->
                when (silnik) {
                    SilnikNagrywania.LokalnyVoiceCall -> 0
                    SilnikNagrywania.HybrydaMicDownlink -> 1
                    SilnikNagrywania.MicOnly -> 2
                    else -> 3
                }
            }
        }
        filtered = filtered.filter { it != SilnikNagrywania.LokalnyVoiceCall }.ifEmpty { filtered }
        // Dual/VC stereo przed hybrydą — hybryda MIC łapie echo rozmówcy ze słuchawki.
        val head = listOf(
            SilnikNagrywania.DualUpDown,
            SilnikNagrywania.VoiceCallStereo,
            SilnikNagrywania.HybrydaMicDownlink,
        ).filter { it in filtered }
        val tail = filtered.filter { it !in head }.sortedBy {
            when (it) {
                // vc_mono na Realme/ColorOS = prawie zawsze tylko druga osoba.
                SilnikNagrywania.VoiceCallMono -> 20
                SilnikNagrywania.VoiceCommunication -> 21
                SilnikNagrywania.MicOnly -> 22
                else -> 10
            }
        }
        val pref = ZdolnosciCache.preferowana(ctx)
        // Nie faworyzuj hybrydy (echo rozmówcy ze słuchawki) nad Dual/VC stereo.
        val prefOk = pref != null &&
            pref in head &&
            pref != SilnikNagrywania.HybrydaMicDownlink
        return if (prefOk) {
            listOf(pref!!) + head.filter { it != pref } + tail
        } else {
            head + tail
        }
    }

    private suspend fun uruchomDrabinke(ctx: Context, numer: String) {
        val firmowy = NagrywanieUstawienia.czyAdbTylkoNaRozmowe(ctx)
        val limitMs = if (firmowy) ZAPEWNIJ_W_ROZMOWIE_FIRM_MS else ZAPEWNIJ_W_ROZMOWIE_MS
        // MIC od razu (równolegle z ADB) — inaczej pierwsze 2–10 s rozmowy giną w zapewnij().
        val early = startEarlyMic(ctx)
        val shellOk = try {
            withTimeoutOrNull(limitMs) {
                runCatching { ShellKlient.zapewnij(ctx, autoWlacAdb = true) }.getOrDefault(false)
            } == true
        } finally {
            val earlyPcm = early?.stopAndZrzut() ?: ByteArray(0)
            if (earlyPcm.isNotEmpty() && zachowanyPreroll.isEmpty()) {
                zachowanyPreroll = earlyPcm
                Log.i(TAG, "early-MIC zachowano ${earlyPcm.size} B (~${earlyPcm.size / (SAMPLE_RATE * 2)} s)")
            } else if (earlyPcm.isNotEmpty()) {
                // Soft-switch stash ma pierwszeństwo — doklej early na początek jeśli krótszy gap.
                zachowanyPreroll = earlyPcm + zachowanyPreroll
                Log.i(TAG, "early-MIC doklejony przed stash → ${zachowanyPreroll.size} B")
            }
        }
        Log.i(TAG, "zapewnij@${limitMs}ms firmowy=$firmowy → shell=$shellOk gotowy=${ShellKlient.czyShellGotowy()}")

        val ladder = drabinka(ctx)
        Log.i(TAG, "drabinka=${ladder.map { it.id }} shell=${ShellKlient.czyShellGotowy()}")

        // Keepalive ZANIM AudioRecord — trzyma mix/HAL w stanie aktywnym.
        keepalive?.stop()
        keepalive = KeepaliveAudio(tryb = KeepaliveAudio.Tryb.Ultradzwiek).also { it.start() }

        for (silnik in ladder) {
            // stop() kasuje Job — sprawdzaj isActive, nie tylko flagę (soft-switch ją czyści).
            if (zatrzymajFlaga.get() || !currentCoroutineContext().isActive) return
            if (silnik == SilnikNagrywania.PlaybackPolicy &&
                Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE
            ) {
                continue
            }
            // Shell-silniki bez działającej usługi — natychmiast dalej (bez 1.5 s Fail).
            if (silnik != SilnikNagrywania.LokalnyVoiceCall &&
                silnik != SilnikNagrywania.MicOnly &&
                silnik != SilnikNagrywania.HybrydaMicDownlink &&
                !ShellKlient.czyShellGotowy()
            ) {
                continue
            }
            stan = StanNagrywania.Probuje(silnik.etykieta)
            Log.i(TAG, "próba ${silnik.id}")

            val wynik = when (silnik) {
                SilnikNagrywania.LokalnyVoiceCall -> otworzLokalny(ctx, numer)
                SilnikNagrywania.DualUpDown -> otworzShellDual(ctx, numer)
                SilnikNagrywania.VoiceCallStereo -> otworzShellVoiceCallStereo(ctx, numer)
                SilnikNagrywania.VoiceCallMono -> otworzShellSingle(
                    ctx, numer, MediaRecorder.AudioSource.VOICE_CALL,
                    AudioFormat.CHANNEL_IN_MONO, 1
                )
                SilnikNagrywania.VoiceCommunication -> otworzShellSingle(
                    ctx, numer, MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    AudioFormat.CHANNEL_IN_MONO, 1
                )
                SilnikNagrywania.PlaybackPolicy -> otworzShellPlayback(ctx, numer)
                SilnikNagrywania.HybrydaMicDownlink -> otworzHybryda(ctx, numer)
                SilnikNagrywania.MicOnly -> otworzShellSingle(
                    ctx, numer, MediaRecorder.AudioSource.MIC,
                    AudioFormat.CHANNEL_IN_MONO, 1
                )
            }

            when (wynik) {
                is Otwarcie.Ok -> {
                    nagrywa = true
                    aktywnyPlik = wynik.plik
                    ostatniSilnik = silnik.etykieta
                    stan = StanNagrywania.Aktywne(wynik.plik, silnik.etykieta)
                    val slychac = if (wynik.wymagaObuStron) {
                        czekajNaObieStrony(wynik.miernik)
                    } else {
                        czekajNaAudioZGrace(wynik.miernik)
                    }
                    if (zatrzymajFlaga.get() || !currentCoroutineContext().isActive) return
                    // Wymaga obu stron, a jest tylko downlink (oni) / tylko uplink (ja)
                    // → soft-switch. Ale przy jednej żywej stronie daj downlinkowi jeszcze chwilę
                    // (ColorOS po ACTIVE / cold ADB często milczy 1–3 s na drugiej ścieżce).
                    if (!slychac && silnik != SilnikNagrywania.MicOnly) {
                        val jedna =
                            wynik.wymagaObuStron && wynik.miernik.slychac(PROG_RMS)
                        if (jedna) {
                            Log.i(
                                TAG,
                                "${silnik.id} jedna strona OK " +
                                    "L=${wynik.miernik.rmsL()} R=${wynik.miernik.rmsR()} " +
                                    "— dopinek ${DOPINEK_OBU_MS}ms",
                            )
                            delay(DOPINEK_OBU_MS)
                            if (zatrzymajFlaga.get() || !currentCoroutineContext().isActive) return
                            if (wynik.miernik.obieStrony(PROG_RMS)) {
                                Log.i(TAG, "${silnik.id} obie strony po dopinku")
                                zachowanyPreroll = ByteArray(0)
                                ZdolnosciCache.zapiszSukces(ctx, silnik)
                                while (currentCoroutineContext().isActive &&
                                    !zatrzymajFlaga.get() && nagrywa
                                ) {
                                    delay(400)
                                }
                                return
                            }
                        }
                        Log.w(
                            TAG,
                            if (jedna) {
                                "${silnik.id} jednostronne " +
                                    "L=${wynik.miernik.rmsL()} R=${wynik.miernik.rmsR()} → soft-switch"
                            } else {
                                "${silnik.id} ciche → soft-switch"
                            },
                        )
                        // Hybryda/Dual/VC stereo — bez trwałego banu (krótki verify / cichy user).
                        if (silnik != SilnikNagrywania.HybrydaMicDownlink &&
                            silnik != SilnikNagrywania.DualUpDown &&
                            silnik != SilnikNagrywania.VoiceCallStereo
                        ) {
                            ZdolnosciCache.oznaczCicha(ctx, silnik)
                        }
                        softSwitchZachowajPreroll()
                        continue
                    }
                    // vc_mono „działa” = często tylko oni — dorzuć MIC przez soft-switch na hybrydę.
                    if (silnik == SilnikNagrywania.VoiceCallMono ||
                        silnik == SilnikNagrywania.VoiceCommunication
                    ) {
                        Log.w(TAG, "${silnik.id} audio OK, ale bez gwarancji „mnie” → hybryda")
                        softSwitchZachowajPreroll()
                        // Wymuś hybrydę zaraz (nie czekaj na ogon drabinki).
                        val hyb = otworzHybryda(ctx, numer)
                        if (hyb is Otwarcie.Ok) {
                            nagrywa = true
                            aktywnyPlik = hyb.plik
                            ostatniSilnik = SilnikNagrywania.HybrydaMicDownlink.etykieta
                            stan = StanNagrywania.Aktywne(hyb.plik, ostatniSilnik)
                            val obie = czekajNaObieStrony(hyb.miernik)
                            if (zatrzymajFlaga.get() || !currentCoroutineContext().isActive) return
                            if (obie) {
                                zachowanyPreroll = ByteArray(0)
                                ZdolnosciCache.zapiszSukces(ctx, SilnikNagrywania.HybrydaMicDownlink)
                                while (currentCoroutineContext().isActive &&
                                    !zatrzymajFlaga.get() && nagrywa
                                ) {
                                    delay(400)
                                }
                                return
                            }
                            Log.w(TAG, "hybryda po mono też jednostronna — zostawiam mix jak jest")
                            zachowanyPreroll = ByteArray(0)
                            while (currentCoroutineContext().isActive &&
                                !zatrzymajFlaga.get() && nagrywa
                            ) {
                                delay(400)
                            }
                            return
                        }
                        // Hybryda nie wstała — wróć do drabinki (kolejne silniki).
                        continue
                    }
                    zachowanyPreroll = ByteArray(0)
                    ZdolnosciCache.zapiszSukces(ctx, silnik)
                    while (currentCoroutineContext().isActive && !zatrzymajFlaga.get() && nagrywa) {
                        delay(400)
                    }
                    return
                }
                is Otwarcie.Fail -> {
                    Log.w(TAG, "${silnik.id}: ${wynik.powod}")
                    ZdolnosciCache.oznaczFail(ctx, silnik)
                    runCatching { ShellKlient.usluga()?.stop() }
                }
            }
        }
        // Lepsze jednostronne / mic niż zero pliku po soft-switchach.
        if (zachowanyPreroll.isNotEmpty() &&
            currentCoroutineContext().isActive &&
            !zatrzymajFlaga.get()
        ) {
            Log.w(
                TAG,
                "drabinka wyczerpana — fallback z preroll ${zachowanyPreroll.size} B",
            )
            val fallbacki = listOf(
                SilnikNagrywania.DualUpDown,
                SilnikNagrywania.HybrydaMicDownlink,
                SilnikNagrywania.VoiceCallStereo,
                SilnikNagrywania.MicOnly,
            )
            for (silnik in fallbacki) {
                if (zatrzymajFlaga.get() || !currentCoroutineContext().isActive) return
                if (silnik != SilnikNagrywania.MicOnly &&
                    silnik != SilnikNagrywania.HybrydaMicDownlink &&
                    !ShellKlient.czyShellGotowy()
                ) {
                    continue
                }
                stan = StanNagrywania.Probuje(silnik.etykieta + " (fallback)")
                Log.i(TAG, "fallback ${silnik.id}")
                val wynik = when (silnik) {
                    SilnikNagrywania.DualUpDown -> otworzShellDual(ctx, numer)
                    SilnikNagrywania.HybrydaMicDownlink -> otworzHybryda(ctx, numer)
                    SilnikNagrywania.VoiceCallStereo -> otworzShellVoiceCallStereo(ctx, numer)
                    SilnikNagrywania.MicOnly -> otworzShellSingle(
                        ctx, numer, MediaRecorder.AudioSource.MIC,
                        AudioFormat.CHANNEL_IN_MONO, 1,
                    )
                    else -> Otwarcie.Fail("skip")
                }
                if (wynik is Otwarcie.Ok) {
                    nagrywa = true
                    aktywnyPlik = wynik.plik
                    ostatniSilnik = silnik.etykieta + " (fallback)"
                    stan = StanNagrywania.Aktywne(wynik.plik, ostatniSilnik)
                    // Bez soft-switch — trzymamy cokolwiek nagrywa do końca rozmowy.
                    while (currentCoroutineContext().isActive &&
                        !zatrzymajFlaga.get() && nagrywa
                    ) {
                        delay(400)
                    }
                    return
                }
            }
        }
        stan = StanNagrywania.Blad("Żaden silnik nie oddał audio na tym urządzeniu")
        nagrywa = false
        Log.e(TAG, "drabinka wyczerpana — brak nagrania")
    }

    /**
     * Soft-switch: zachowaj PCM z POCZĄTKU pliku (do [STASH_MAX_MS]), nie tylko ring.
     * Dzięki temu failover 2–3 silników nie wycina pierwszych sekund rozmowy.
     */
    private suspend fun softSwitchZachowajPreroll() {
        zatrzymajFlaga.set(true)
        try {
            runCatching { ShellKlient.usluga()?.stop() }
            runCatching { lokalnyRecord?.stop(); lokalnyRecord?.release() }
            lokalnyRecord = null
            pumpWatki.forEach { runCatching { it.join(600) } }
            pumpWatki = emptyList()

            val plik = aktywnyPlik
            zachowanyPreroll = pcmZPoczatkuWav(plik)
                ?.takeIf { it.isNotEmpty() }
                ?: (preroll?.zrzut() ?: ByteArray(0))

            plik?.let { if (it.exists()) NagrywaniePliki.usunZSidecar(it) }
            aktywnyPlik = null
            nagrywa = false
            preroll = null
        } finally {
            // Nie kasuj flagi, jeśli stop()/cancel w trakcie soft-switch —
            // inaczej drabinka leci dalej po końcu rozmowy.
            if (currentCoroutineContext().isActive) {
                zatrzymajFlaga.set(false)
            }
        }
        Log.i(TAG, "soft-switch zachowano ${zachowanyPreroll.size} B (~${zachowanyPreroll.size / (SAMPLE_RATE * 2)} s)")
    }

    /** PCM po 44 B nagłówku WAV — od początku, max [STASH_MAX_MS]. */
    private fun pcmZPoczatkuWav(plik: File?): ByteArray? {
        if (plik == null || !plik.exists() || plik.length() <= 44L) return null
        return runCatching {
            val all = plik.readBytes()
            if (all.size <= 44) return@runCatching null
            val pcm = all.copyOfRange(44, all.size)
            val maxB = (SAMPLE_RATE * 2L * STASH_MAX_MS) / 1000L
            if (pcm.size > maxB) pcm.copyOfRange(0, maxB.toInt()) else pcm
        }.getOrNull()
    }

    private fun nowyWav(ctx: Context, numer: String, kanaly: Int): Pair<File, WavPisarz> {
        val plik = NagrywaniePliki.nowyPlik(ctx, numer, "wav")
        val wav = WavPisarz(plik, SAMPLE_RATE, kanaly)
        val stash = zachowanyPreroll
        if (stash.isNotEmpty()) {
            // Soft-switch: początek rozmowy z poprzedniej próby nie ginie.
            wav.write(stash, 0, stash.size)
            zachowanyPreroll = ByteArray(0)
            Log.i(TAG, "dopisano preroll ${stash.size} B → ${plik.name}")
        }
        preroll = PrerollBufor(SAMPLE_RATE, kanaly, PREROLL_MS)
        return plik to wav
    }

    private fun piszPcm(wav: WavPisarz, data: ByteArray, offset: Int, len: Int) {
        if (len <= 0) return
        wav.write(data, offset, len)
        preroll?.dodaj(data, offset, len)
    }

    private sealed class Otwarcie {
        data class Ok(
            val plik: File,
            val miernik: MiernikRms,
            /** Dual/hybryda: wymagamy uplink + downlink, nie samego „siebie”. */
            val wymagaObuStron: Boolean = false,
        ) : Otwarcie()
        data class Fail(val powod: String) : Otwarcie()
    }

    /**
     * Lokalny MIC w trakcie czekania na ADB/shell — bez tego giną pierwsze sekundy rozmowy.
     * Bufor trafia do [zachowanyPreroll] i jest dopisywany na początek pierwszego WAV.
     */
    @android.annotation.SuppressLint("MissingPermission")
    private fun startEarlyMic(ctx: Context): EarlyMic? {
        if (ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) return null
        val zrodla = intArrayOf(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
        )
        var rec: AudioRecord? = null
        for (src in zrodla) {
            val r = try {
                AudioRecord.Builder()
                    .setAudioSource(src)
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(minBuf * 2)
                    .build()
            } catch (_: Throwable) {
                null
            }
            if (r != null && r.state == AudioRecord.STATE_INITIALIZED) {
                rec = r
                break
            }
            runCatching { r?.release() }
        }
        val audio = rec ?: return null
        val stop = AtomicBoolean(false)
        val buf = PrerollBufor(SAMPLE_RATE, 1, EARLY_MIC_MS)
        val t = Thread({
            val chunk = ByteArray(4096)
            try {
                audio.startRecording()
                while (!stop.get() && !zatrzymajFlaga.get()) {
                    val n = audio.read(chunk, 0, chunk.size)
                    if (n > 0) buf.dodaj(chunk, 0, n)
                    else if (n < 0) break
                }
            } catch (t: Throwable) {
                Log.w(TAG, "early-MIC", t)
            } finally {
                runCatching { audio.stop() }
                runCatching { audio.release() }
            }
        }, "nagrywanie-early-mic").apply { isDaemon = true; start() }
        Log.i(TAG, "early-MIC start")
        return EarlyMic(stop, t, buf)
    }

    private class EarlyMic(
        private val stop: AtomicBoolean,
        private val thread: Thread,
        private val buf: PrerollBufor,
    ) {
        fun stopAndZrzut(): ByteArray {
            stop.set(true)
            runCatching { thread.join(800) }
            return buf.zrzut()
        }
    }

    /** Silnik 1: AudioRecord w procesie dialera — bez UID shell. */
    @android.annotation.SuppressLint("MissingPermission")
    private fun otworzLokalny(ctx: Context, numer: String): Otwarcie {
        if (ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return Otwarcie.Fail("brak RECORD_AUDIO")
        }
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) return Otwarcie.Fail("minBuf=$minBuf")
        val rec = try {
            AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.VOICE_CALL)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBuf * 2)
                .build()
        } catch (t: Throwable) {
            return Otwarcie.Fail("lokalny ctor: ${t.message}")
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            return Otwarcie.Fail("lokalny state=${rec.state}")
        }
        lokalnyRecord = rec
        val (plik, wav) = nowyWav(ctx, numer, 1)
        val miernik = MiernikRms()
        val t = Thread({
            val buf = ByteArray(8192)
            try {
                rec.startRecording()
                while (!zatrzymajFlaga.get()) {
                    val n = rec.read(buf, 0, buf.size)
                    if (n <= 0) break
                    piszPcm(wav, buf, 0, n)
                    var i = 0
                    while (i + 1 < n) {
                        val s = ((buf[i].toInt() and 0xff) or (buf[i + 1].toInt() shl 8)).toShort()
                        miernik.dodaj(s)
                        i += 2
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "lokalny pump", t)
            } finally {
                runCatching { wav.close() }
                runCatching { rec.stop(); rec.release() }
                if (lokalnyRecord === rec) lokalnyRecord = null
            }
        }, "nagrywanie-lokalny").apply { isDaemon = true; start() }
        pumpWatki = listOf(t)
        return Otwarcie.Ok(plik, miernik)
    }

    private fun wymagajShell(ctx: Context): INagrywanieShell? =
        ShellKlient.usluga()

    private fun otworzShellDual(ctx: Context, numer: String): Otwarcie {
        val svc = wymagajShell(ctx) ?: return Otwarcie.Fail(ShellKlient.krotkiOpis(ctx))
        // UP bez echa akustycznego — ale na ColorOS często cisza („tylko oni”).
        // Sonda ~350 ms: jeśli near martwy przy żywym DN → pad na MIC (żeby było „mnie”).
        val proby = listOf(
            MediaRecorder.AudioSource.VOICE_UPLINK to ("up+dn" to AnulatorEcha.Tryb.Uplink),
            MediaRecorder.AudioSource.MIC to ("mic+dn" to AnulatorEcha.Tryb.Mic),
        )
        var pipeUp: Array<ParcelFileDescriptor>? = null
        var pipeDn: Array<ParcelFileDescriptor>? = null
        var trybNear = AnulatorEcha.Tryb.Mic
        var mask = 0
        var ostatniBlad: String? = null
        for ((upSrc, meta) in proby) {
            val (tag, tryb) = meta
            val up = ParcelFileDescriptor.createPipe()
            val dn = ParcelFileDescriptor.createPipe()
            mask = try {
                svc.startDual(upSrc, MediaRecorder.AudioSource.VOICE_DOWNLINK, SAMPLE_RATE, up[1], dn[1])
            } catch (t: Throwable) {
                runCatching { up[0].close(); up[1].close() }
                runCatching { dn[0].close(); dn[1].close() }
                ostatniBlad = t.message
                continue
            }
            if (mask != 0b11) {
                ostatniBlad = svc.ostatniBlad ?: "dual mask=$mask ($tag)"
                runCatching { svc.stop() }
                runCatching { up[0].close(); up[1].close() }
                runCatching { dn[0].close(); dn[1].close() }
                mask = 0
                continue
            }
            runCatching { up[1].close() }
            runCatching { dn[1].close() }
            // UPLINK: odrzuć ciszę near zanim zostaniemy z samym rozmówcą.
            if (tryb == AnulatorEcha.Tryb.Uplink) {
                val (eUp, eDn) = sondaEnergiiPcm(up[0], dn[0], 350L)
                Log.i(TAG, "dual sonda $tag nearPeak=$eUp dnPeak=$eDn")
                val nearMartwy = eUp < 350 && eDn > 500
                if (nearMartwy) {
                    Log.w(TAG, "dual UP cichy → próbuję MIC")
                    runCatching { svc.stop() }
                    runCatching { up[0].close() }
                    runCatching { dn[0].close() }
                    mask = 0
                    continue
                }
            }
            Log.i(TAG, "dual OK $tag tryb=$tryb")
            pipeUp = up
            pipeDn = dn
            trybNear = tryb
            break
        }
        val upOk = pipeUp
        val dnOk = pipeDn
        if (mask != 0b11 || upOk == null || dnOk == null) {
            return Otwarcie.Fail(ostatniBlad ?: "dual mask")
        }
        // Mono mix + AEC; sidecar stereo (L=near R=DN).
        val (plik, wav) = nowyWav(ctx, numer, 1)
        val stereo = otworzStereoSidecar(plik)
        val miernik = MiernikRms()
        val tryb = trybNear
        val t = Thread({
            mieszajDoMono(upOk[0], dnOk[0], wav, stereo, miernik, tryb)
        }, "nagrywanie-mix").apply { isDaemon = true; start() }
        pumpWatki = listOf(t)
        return Otwarcie.Ok(plik, miernik, wymagaObuStron = true)
    }

    /**
     * Krótki odczyt obu pipe'ów — peak |sample|. Nie zamyka FD.
     * Zużyte bajty giną (przed startem WAV) — OK vs całe nagranie bez „mnie”.
     */
    private fun sondaEnergiiPcm(
        fdUp: ParcelFileDescriptor,
        fdDn: ParcelFileDescriptor,
        ms: Long,
    ): Pair<Int, Int> {
        val up = FileInputStream(fdUp.fileDescriptor)
        val dn = FileInputStream(fdDn.fileDescriptor)
        val buf = ByteArray(4096)
        var peakUp = 0
        var peakDn = 0
        val deadline = System.nanoTime() + ms * 1_000_000L
        try {
            while (System.nanoTime() < deadline) {
                val nUp = runCatching { if (up.available() > 0) up.read(buf) else 0 }.getOrDefault(0)
                if (nUp > 0) {
                    var i = 0
                    while (i + 1 < nUp) {
                        val s = abs(
                            ((buf[i].toInt() and 0xff) or (buf[i + 1].toInt() shl 8)).toShort().toInt(),
                        )
                        if (s > peakUp) peakUp = s
                        i += 2
                    }
                }
                val nDn = runCatching { if (dn.available() > 0) dn.read(buf) else 0 }.getOrDefault(0)
                if (nDn > 0) {
                    var i = 0
                    while (i + 1 < nDn) {
                        val s = abs(
                            ((buf[i].toInt() and 0xff) or (buf[i + 1].toInt() shl 8)).toShort().toInt(),
                        )
                        if (s > peakDn) peakDn = s
                        i += 2
                    }
                }
                if (nUp <= 0 && nDn <= 0) Thread.sleep(15)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "sonda dual", t)
        }
        return peakUp to peakDn
    }

    private fun otworzShellSingle(
        ctx: Context,
        numer: String,
        zrodlo: Int,
        channelMask: Int,
        kanaly: Int,
    ): Otwarcie {
        val svc = wymagajShell(ctx) ?: return Otwarcie.Fail(ShellKlient.krotkiOpis(ctx))
        val pipe = ParcelFileDescriptor.createPipe()
        val ok = try {
            svc.startSingle(zrodlo, SAMPLE_RATE, channelMask, pipe[1])
        } catch (t: Throwable) {
            runCatching { pipe[0].close(); pipe[1].close() }
            return Otwarcie.Fail(t.message ?: "single RemoteException")
        }
        if (ok == 0) {
            runCatching { pipe[0].close() }
            return Otwarcie.Fail(svc.ostatniBlad ?: "single=0")
        }
        runCatching { pipe[1].close() }
        val (plik, wav) = nowyWav(ctx, numer, kanaly)
        val miernik = MiernikRms()
        val t = Thread({
            kopiujPcm(pipe[0], wav, miernik)
        }, "nagrywanie-single").apply { isDaemon = true; start() }
        pumpWatki = listOf(t)
        return Otwarcie.Ok(plik, miernik)
    }

    private fun otworzShellPlayback(ctx: Context, numer: String): Otwarcie {
        val svc = wymagajShell(ctx) ?: return Otwarcie.Fail(ShellKlient.krotkiOpis(ctx))
        val pipe = ParcelFileDescriptor.createPipe()
        val ok = try {
            svc.startPlayback(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, pipe[1])
        } catch (t: Throwable) {
            runCatching { pipe[0].close(); pipe[1].close() }
            return Otwarcie.Fail(t.message ?: "playback RemoteException")
        }
        if (ok == 0) {
            runCatching { pipe[0].close() }
            return Otwarcie.Fail(svc.ostatniBlad ?: "playback=0")
        }
        runCatching { pipe[1].close() }
        val (plik, wav) = nowyWav(ctx, numer, 1)
        val miernik = MiernikRms()
        val t = Thread({
            kopiujPcm(pipe[0], wav, miernik)
        }, "nagrywanie-playback").apply { isDaemon = true; start() }
        pumpWatki = listOf(t)
        return Otwarcie.Ok(plik, miernik)
    }

    /**
     * Unikalny silnik dialera: MIC u nas (uplink / nasz głos) + DOWNLINK w shell.
     * Działa gdy UPLINK jest zablokowany przez OEM, a głośnik psuje VOICE_CALL.
     */
    @android.annotation.SuppressLint("MissingPermission")
    private fun otworzHybryda(ctx: Context, numer: String): Otwarcie {
        if (ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return Otwarcie.Fail("brak RECORD_AUDIO")
        }
        val svc = wymagajShell(ctx) ?: return Otwarcie.Fail(ShellKlient.krotkiOpis(ctx))

        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) return Otwarcie.Fail("hybryda minBuf")
        // MIC w procesie dialera — keepalive potrafi zablokować tor; wyłącz na czas MIC.
        keepalive?.stop()
        keepalive = null
        runCatching {
            val am = ctx.getSystemService(android.media.AudioManager::class.java)
            if (am != null && am.mode == android.media.AudioManager.MODE_NORMAL) {
                am.mode = android.media.AudioManager.MODE_IN_COMMUNICATION
            }
        }

        // MIC / UNPROCESSED — nie VOICE_COMMUNICATION (AEC OEM często wycina „mnie”).
        val mic = zbudujMicHybrydy(minBuf)
            ?: return Otwarcie.Fail("hybryda mic init")

        val pipeDn = ParcelFileDescriptor.createPipe()
        val ok = try {
            svc.startSingle(
                MediaRecorder.AudioSource.VOICE_DOWNLINK,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                pipeDn[1],
            )
        } catch (t: Throwable) {
            mic.release()
            runCatching { pipeDn[0].close(); pipeDn[1].close() }
            return Otwarcie.Fail(t.message ?: "hybryda dn")
        }
        if (ok == 0) {
            mic.release()
            runCatching { pipeDn[0].close() }
            return Otwarcie.Fail(svc.ostatniBlad ?: "hybryda dn=0")
        }
        runCatching { pipeDn[1].close() }

        lokalnyRecord = mic
        val (plik, wav) = nowyWav(ctx, numer, 1)
        val stereo = otworzStereoSidecar(plik)
        val miernik = MiernikRms()
        val t = Thread({
            mieszajMicIPipeDoMono(mic, pipeDn[0], wav, stereo, miernik)
        }, "nagrywanie-hybryda").apply { isDaemon = true; start() }
        pumpWatki = listOf(t)
        return Otwarcie.Ok(plik, miernik, wymagaObuStron = true)
    }

    private fun otworzShellVoiceCallStereo(ctx: Context, numer: String): Otwarcie {
        val svc = wymagajShell(ctx) ?: return Otwarcie.Fail(ShellKlient.krotkiOpis(ctx))
        val pipe = ParcelFileDescriptor.createPipe()
        val ok = try {
            svc.startSingle(
                MediaRecorder.AudioSource.VOICE_CALL,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_STEREO,
                pipe[1],
            )
        } catch (t: Throwable) {
            runCatching { pipe[0].close(); pipe[1].close() }
            return Otwarcie.Fail(t.message ?: "vc stereo")
        }
        if (ok == 0) {
            runCatching { pipe[0].close() }
            return Otwarcie.Fail(svc.ostatniBlad ?: "vc stereo=0")
        }
        runCatching { pipe[1].close() }
        val (plik, wav) = nowyWav(ctx, numer, 1)
        val stereo = otworzStereoSidecar(plik)
        val miernik = MiernikRms()
        val t = Thread({
            // L=UP/near, R=DN — ten sam AEC+XOR co Dual/hybryda (wcześniej surowa suma = echo).
            kopiujStereoDoMono(pipe[0], wav, stereo, miernik)
        }, "nagrywanie-vc-stereo").apply { isDaemon = true; start() }
        pumpWatki = listOf(t)
        return Otwarcie.Ok(plik, miernik, wymagaObuStron = true)
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun zbudujMicHybrydy(minBuf: Int): AudioRecord? {
        val zrodla = intArrayOf(
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.UNPROCESSED,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
        )
        for (src in zrodla) {
            val rec = try {
                AudioRecord.Builder()
                    .setAudioSource(src)
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(minBuf * 2)
                    .setPrivacySensitive(false)
                    .build()
            } catch (_: Throwable) {
                null
            }
            if (rec != null && rec.state == AudioRecord.STATE_INITIALIZED) {
                Log.i(TAG, "hybryda mic source=$src")
                return rec
            }
            runCatching { rec?.release() }
        }
        return null
    }

    private fun otworzStereoSidecar(mono: File): WavPisarz? =
        runCatching {
            WavPisarz(NagrywaniePliki.plikStereoObok(mono), SAMPLE_RATE, 2)
        }.onFailure { Log.w(TAG, "stereo sidecar", it) }.getOrNull()

    /**
     * MIC (ja) + DOWNLINK (oni) → mono.
     * Half-duplex + AEC + sync kolejek (bez gubienia próbek = desync echa).
     */
    private fun mieszajMicIPipeDoMono(
        mic: AudioRecord,
        fdDn: ParcelFileDescriptor,
        wav: WavPisarz,
        stereo: WavPisarz?,
        miernik: MiernikRms,
    ) {
        val dn = FileInputStream(fdDn.fileDescriptor)
        val bufMic = ByteArray(4096)
        val bufDn = ByteArray(4096)
        val mono = ByteArray(4096)
        val stereoBuf = ByteArray(8192)
        val qMic = KolejkaPcm(SAMPLE_RATE * 2)
        val qDn = KolejkaPcm(SAMPLE_RATE * 2)
        val aec = AnulatorEcha(SAMPLE_RATE)
        try {
            mic.startRecording()
            while (!zatrzymajFlaga.get()) {
                val nMic = mic.read(bufMic, 0, bufMic.size)
                val nDn = dn.read(bufDn)
                if (nMic <= 0 && nDn <= 0 && qMic.isEmpty && qDn.isEmpty) break
                if (nMic > 0) qMic.dodajBajty(bufMic, nMic)
                if (nDn > 0) qDn.dodajBajty(bufDn, nDn)
                var o = 0
                var so = 0
                while (qMic.size > 0 && qDn.size > 0) {
                    val surowy = qMic.pobierz()
                    val rd = qDn.pobierz()
                    val bezEcha = aec.oczysc(surowy, rd)
                    val mix = aec.mixXor(bezEcha, rd, AnulatorEcha.Tryb.Mic, ::mieszajSample, ::wzmocnij)
                    if (o + 1 >= mono.size) {
                        piszPcm(wav, mono, 0, o)
                        o = 0
                    }
                    mono[o++] = (mix.toInt() and 0xff).toByte()
                    mono[o++] = ((mix.toInt() shr 8) and 0xff).toByte()
                    if (stereo != null) {
                        if (so + 3 >= stereoBuf.size) {
                            runCatching { stereo.write(stereoBuf, 0, so) }
                            so = 0
                        }
                        stereoBuf[so++] = (surowy.toInt() and 0xff).toByte()
                        stereoBuf[so++] = ((surowy.toInt() shr 8) and 0xff).toByte()
                        stereoBuf[so++] = (rd.toInt() and 0xff).toByte()
                        stereoBuf[so++] = ((rd.toInt() shr 8) and 0xff).toByte()
                    }
                    // Surowy near — soft-switch ma widzieć „mnie”, nie post-duck.
                    miernik.dodajL(surowy)
                    miernik.dodajR(rd)
                }
                if (o > 0) piszPcm(wav, mono, 0, o)
                if (stereo != null && so > 0) runCatching { stereo.write(stereoBuf, 0, so) }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "hybryda", t)
        } finally {
            runCatching { dn.close() }
            runCatching { fdDn.close() }
            runCatching { mic.stop(); mic.release() }
            if (lokalnyRecord === mic) lokalnyRecord = null
            runCatching { wav.close() }
            runCatching { stereo?.close() }
        }
    }

    /**
     * Near (UP lub MIC) + DOWNLINK → mono.
     * Mix XOR: przy MIC gdy oni mówią → sam DN (koniec podwójnego echa).
     */
    private fun mieszajDoMono(
        fdUp: ParcelFileDescriptor,
        fdDn: ParcelFileDescriptor,
        wav: WavPisarz,
        stereo: WavPisarz?,
        miernik: MiernikRms,
        trybNear: AnulatorEcha.Tryb,
    ) {
        val up = FileInputStream(fdUp.fileDescriptor)
        val dn = FileInputStream(fdDn.fileDescriptor)
        val bufUp = ByteArray(4096)
        val bufDn = ByteArray(4096)
        val mono = ByteArray(4096)
        val stereoBuf = ByteArray(8192)
        val qUp = KolejkaPcm(SAMPLE_RATE * 2)
        val qDn = KolejkaPcm(SAMPLE_RATE * 2)
        val aec = AnulatorEcha(SAMPLE_RATE)
        try {
            while (!zatrzymajFlaga.get()) {
                val nUp = up.read(bufUp)
                val nDn = dn.read(bufDn)
                if (nUp <= 0 && nDn <= 0 && qUp.isEmpty && qDn.isEmpty) break
                if (nUp > 0) qUp.dodajBajty(bufUp, nUp)
                if (nDn > 0) qDn.dodajBajty(bufDn, nDn)
                var o = 0
                var so = 0
                while (qUp.size > 0 && qDn.size > 0) {
                    val surowy = qUp.pobierz()
                    val rd = qDn.pobierz()
                    val bezEcha = aec.oczysc(surowy, rd)
                    val mix = aec.mixXor(bezEcha, rd, trybNear, ::mieszajSample, ::wzmocnij)
                    if (o + 1 >= mono.size) {
                        piszPcm(wav, mono, 0, o)
                        o = 0
                    }
                    mono[o++] = (mix.toInt() and 0xff).toByte()
                    mono[o++] = ((mix.toInt() shr 8) and 0xff).toByte()
                    if (stereo != null) {
                        if (so + 3 >= stereoBuf.size) {
                            runCatching { stereo.write(stereoBuf, 0, so) }
                            so = 0
                        }
                        stereoBuf[so++] = (surowy.toInt() and 0xff).toByte()
                        stereoBuf[so++] = ((surowy.toInt() shr 8) and 0xff).toByte()
                        stereoBuf[so++] = (rd.toInt() and 0xff).toByte()
                        stereoBuf[so++] = ((rd.toInt() shr 8) and 0xff).toByte()
                    }
                    miernik.dodajL(surowy)
                    miernik.dodajR(rd)
                }
                if (o > 0) piszPcm(wav, mono, 0, o)
                if (stereo != null && so > 0) runCatching { stereo.write(stereoBuf, 0, so) }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "mix", t)
        } finally {
            runCatching { up.close() }
            runCatching { dn.close() }
            runCatching { fdUp.close() }
            runCatching { fdDn.close() }
            runCatching { wav.close() }
            runCatching { stereo?.close() }
        }
    }

    /**
     * VOICE_CALL stereo (L=near/UP, R=DOWNLINK) → mono z AEC+XOR + sidecar.
     * Wcześniej surowa suma L+R dawała podwójne echo bez szans na offline.
     */
    private fun kopiujStereoDoMono(
        fd: ParcelFileDescriptor,
        wav: WavPisarz,
        stereo: WavPisarz?,
        miernik: MiernikRms,
    ) {
        val inp = FileInputStream(fd.fileDescriptor)
        val buf = ByteArray(8192)
        val mono = ByteArray(4096)
        val stereoBuf = ByteArray(8192)
        val aec = AnulatorEcha(SAMPLE_RATE)
        try {
            while (!zatrzymajFlaga.get()) {
                val n = inp.read(buf)
                if (n <= 0) break
                val frames = n / 4
                var o = 0
                var so = 0
                for (i in 0 until frames) {
                    val base = i * 4
                    if (base + 3 >= n) break
                    val near = ((buf[base].toInt() and 0xff) or (buf[base + 1].toInt() shl 8)).toShort()
                    val dn = ((buf[base + 2].toInt() and 0xff) or (buf[base + 3].toInt() shl 8)).toShort()
                    val bezEcha = aec.oczysc(near, dn)
                    val mix = aec.mixXor(bezEcha, dn, AnulatorEcha.Tryb.Mic, ::mieszajSample, ::wzmocnij)
                    if (o + 1 >= mono.size) {
                        piszPcm(wav, mono, 0, o)
                        o = 0
                    }
                    mono[o++] = (mix.toInt() and 0xff).toByte()
                    mono[o++] = ((mix.toInt() shr 8) and 0xff).toByte()
                    if (stereo != null) {
                        if (so + 3 >= stereoBuf.size) {
                            runCatching { stereo.write(stereoBuf, 0, so) }
                            so = 0
                        }
                        stereoBuf[so++] = (near.toInt() and 0xff).toByte()
                        stereoBuf[so++] = ((near.toInt() shr 8) and 0xff).toByte()
                        stereoBuf[so++] = (dn.toInt() and 0xff).toByte()
                        stereoBuf[so++] = ((dn.toInt() shr 8) and 0xff).toByte()
                    }
                    miernik.dodajL(near)
                    miernik.dodajR(dn)
                }
                if (o > 0) piszPcm(wav, mono, 0, o)
                if (stereo != null && so > 0) runCatching { stereo.write(stereoBuf, 0, so) }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "stereo→mono", t)
        } finally {
            runCatching { inp.close() }
            runCatching { fd.close() }
            runCatching { wav.close() }
            runCatching { stereo?.close() }
        }
    }

    /** Suma z saturacją (bez /2 — średnia ściszała obie strony). */
    private fun mieszajSample(a: Short, b: Short): Short {
        val sum = a.toInt() + b.toInt()
        return sum.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
    }

    private fun wzmocnij(s: Short, razy: Float): Short {
        val v = (s.toInt() * razy).toInt()
        return v.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
    }

    private fun kopiujPcm(fd: ParcelFileDescriptor, wav: WavPisarz, miernik: MiernikRms) {
        val inp = FileInputStream(fd.fileDescriptor)
        val buf = ByteArray(8192)
        try {
            while (!zatrzymajFlaga.get()) {
                val n = inp.read(buf)
                if (n <= 0) break
                piszPcm(wav, buf, 0, n)
                var i = 0
                while (i + 1 < n) {
                    val s = ((buf[i].toInt() and 0xff) or (buf[i + 1].toInt() shl 8)).toShort()
                    miernik.dodaj(s)
                    i += 2
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "kopiuj", t)
        } finally {
            runCatching { inp.close() }
            runCatching { fd.close() }
            runCatching { wav.close() }
        }
    }

    /**
     * Detekcja audybilności z grace period (warmup HAL).
     * Logika wyodrębniona też w [JakoscNagrywania] do symulacji JVM.
     */
    private suspend fun czekajNaAudioZGrace(miernik: MiernikRms): Boolean {
        val start = System.currentTimeMillis()
        while (!zatrzymajFlaga.get()) {
            val elapsed = System.currentTimeMillis() - start
            val slychac = miernik.slychac(PROG_RMS)
            when (JakoscNagrywania.werdyktProbe(elapsed, slychac, GRACE_MS, VERIFY_MS)) {
                JakoscNagrywania.Werdykt.Czekaj -> delay(150)
                JakoscNagrywania.Werdykt.Slychac -> return true
                JakoscNagrywania.Werdykt.Ciche -> return false
            }
        }
        return false
    }

    /** Dual/hybryda: obie ścieżki muszą mieć sygnał (inaczej słychać tylko siebie). */
    private suspend fun czekajNaObieStrony(miernik: MiernikRms): Boolean {
        val start = System.currentTimeMillis()
        while (!zatrzymajFlaga.get()) {
            val elapsed = System.currentTimeMillis() - start
            val obie = miernik.obieStrony(PROG_RMS)
            when (JakoscNagrywania.werdyktProbe(elapsed, obie, GRACE_OBU_MS, VERIFY_OBU_MS)) {
                JakoscNagrywania.Werdykt.Czekaj -> delay(150)
                JakoscNagrywania.Werdykt.Slychac -> return true
                JakoscNagrywania.Werdykt.Ciche -> {
                    Log.w(
                        TAG,
                        "jednostronne: Lrms=${miernik.rmsL()} Rrms=${miernik.rmsR()} " +
                            "Lp=${miernik.peakL()} Rp=${miernik.peakR()}"
                    )
                    return false
                }
            }
        }
        return false
    }

    private class MiernikRms {
        @Volatile private var sumSqL = 0.0
        @Volatile private var sumSqR = 0.0
        @Volatile private var nL = 0L
        @Volatile private var nR = 0L
        @Volatile private var peakL = 0
        @Volatile private var peakR = 0

        @Synchronized
        fun dodaj(sample: Short) {
            dodajL(sample)
            dodajR(sample)
        }

        @Synchronized
        fun dodajL(sample: Short) {
            val v = sample.toInt()
            sumSqL += v.toDouble() * v
            nL++
            peakL = max(peakL, abs(v))
        }

        @Synchronized
        fun dodajR(sample: Short) {
            val v = sample.toInt()
            sumSqR += v.toDouble() * v
            nR++
            peakR = max(peakR, abs(v))
        }

        fun rmsL(): Float = rmsOf(sumSqL, nL)
        fun rmsR(): Float = rmsOf(sumSqR, nR)
        fun peakL(): Int = peakL
        fun peakR(): Int = peakR

        fun slychac(prog: Float): Boolean =
            rmsL() >= prog || rmsR() >= prog || peakL > 500 || peakR > 500

        fun obieStrony(prog: Float): Boolean {
            val lOk = rmsL() >= prog || peakL > 400
            val rOk = rmsR() >= prog || peakR > 400
            return lOk && rOk
        }

        private fun rmsOf(sumSq: Double, n: Long): Float {
            if (n == 0L) return 0f
            return (sqrt(sumSq / n) / 32768.0).toFloat()
        }
    }
}
