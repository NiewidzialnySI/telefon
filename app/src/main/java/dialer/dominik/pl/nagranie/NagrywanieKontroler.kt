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
 * Multi-silnik nagrywania rozmów — własna drabinka dialera + Shizuku.
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
    /** Soft-switch: trzymaj do 12 s PCM z POCZĄTKU pliku (nie tylko ring 1.5 s). */
    private const val STASH_MAX_MS = 12_000
    private const val PREROLL_MS = 8_000
    /** W trakcie rozmowy nie wolno blokować 20 s na start daemona. */
    private const val ZAPEWNIJ_W_ROZMOWIE_MS = 1_800L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    var stan: StanNagrywania by mutableStateOf(StanNagrywania.Bezczynny)
        private set

    var nagrywa: Boolean by mutableStateOf(false)
        private set

    var ostatniSilnik: String by mutableStateOf("")
        private set

    private val zatrzymajFlaga = AtomicBoolean(false)
    private var job: Job? = null
    @Volatile private var aktywnyPlik: File? = null
    @Volatile private var lokalnyRecord: AudioRecord? = null
    @Volatile private var pumpWatki: List<Thread> = emptyList()
    @Volatile private var keepalive: KeepaliveAudio? = null
    @Volatile private var preroll: PrerollBufor? = null
    /** Soft-switch: zachowany ogon z poprzedniej próby → dopisany na start nowego WAV. */
    @Volatile private var zachowanyPreroll: ByteArray = ByteArray(0)

    /**
     * Rozgrzewka przed ACTIVE (DIALING / RINGING): podnieś daemon ADB/Shizuku,
     * żeby przy odebraniu nie czekać ~10–20 s na shell.
     */
    fun rozgrzej(ctx: Context) {
        if (!NagrywanieUstawienia.czyWlaczone(ctx)) return
        if (ShellKlient.czyShellGotowy()) return
        scope.launch {
            runCatching { ShellKlient.zapewnij(ctx.applicationContext) }
                .onFailure { Log.w(TAG, "rozgrzej", it) }
                .onSuccess { Log.i(TAG, "rozgrzej shell=${ShellKlient.czyShellGotowy()}") }
        }
    }

    fun start(ctx: Context, numer: String, auto: Boolean = false) {
        if (nagrywa || job?.isActive == true) return
        if (!NagrywanieUstawienia.czyZgodaPrawna(ctx) || !NagrywanieUstawienia.czyWlaczone(ctx)) {
            stan = StanNagrywania.Blad("Włącz nagrywanie i zaakceptuj warunki w Ustawieniach")
            return
        }
        zatrzymajFlaga.set(false)
        job = scope.launch {
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
        scope.launch {
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
                    plik.delete()
                    stan = StanNagrywania.Blad("Nagranie puste")
                }
            }
            aktywnyPlik = null
            nagrywa = false
            if (stan !is StanNagrywania.Blad) stan = StanNagrywania.Bezczynny
        }
    }

    fun przelacz(ctx: Context, numer: String) {
        if (nagrywa) stop() else start(ctx, numer)
    }

    private fun drabinka(ctx: Context): List<SilnikNagrywania> {
        val shell = ShellKlient.czyShellGotowy()
        val all = SilnikNagrywania.entries.toList()
        // Hybryda/Dual: NIGDY nie wyrzucaj z cache „cicha” — inaczej zostaje vc_mono
        // (tylko oni) jak w logu: drabinka=[vc_mono, playback, voice_com, mic].
        fun dopuszczony(s: SilnikNagrywania): Boolean = when (s) {
            SilnikNagrywania.HybrydaMicDownlink,
            SilnikNagrywania.DualUpDown -> true
            else -> !ZdolnosciCache.czyFail(ctx, s) && !ZdolnosciCache.czyCicha(ctx, s)
        }
        var filtered = all.filter(::dopuszczony).ifEmpty { all }
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
        val head = listOf(
            SilnikNagrywania.HybrydaMicDownlink,
            SilnikNagrywania.DualUpDown,
            SilnikNagrywania.VoiceCallStereo,
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
        return if (pref != null && pref in head) {
            listOf(pref) + head.filter { it != pref } + tail
        } else {
            head + tail
        }
    }

    private suspend fun uruchomDrabinke(ctx: Context, numer: String) {
        // Krótko — pełne 20 s startu daemona ucina początek rozmowy.
        val shellOk = withTimeoutOrNull(ZAPEWNIJ_W_ROZMOWIE_MS) {
            runCatching { ShellKlient.zapewnij(ctx) }.getOrDefault(false)
        } == true
        Log.i(TAG, "zapewnij@${ZAPEWNIJ_W_ROZMOWIE_MS}ms → shell=$shellOk gotowy=${ShellKlient.czyShellGotowy()}")

        val ladder = drabinka(ctx)
        Log.i(TAG, "drabinka=${ladder.map { it.id }} shell=${ShellKlient.czyShellGotowy()}")

        // Keepalive ZANIM AudioRecord — trzyma mix/HAL w stanie aktywnym.
        keepalive?.stop()
        keepalive = KeepaliveAudio(tryb = KeepaliveAudio.Tryb.Ultradzwiek).also { it.start() }

        for (silnik in ladder) {
            if (zatrzymajFlaga.get()) return
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
                    if (zatrzymajFlaga.get()) return
                    // Wymaga obu stron, a jest tylko downlink (oni) / tylko uplink (ja)
                    // → NIE zostawiaj (user: „słyszę drugą osobę, siebie nie”). Soft-switch.
                    if (!slychac && silnik != SilnikNagrywania.MicOnly) {
                        val jedna =
                            wynik.wymagaObuStron && wynik.miernik.slychac(PROG_RMS)
                        Log.w(
                            TAG,
                            if (jedna) {
                                "${silnik.id} jednostronne " +
                                    "L=${wynik.miernik.rmsL()} R=${wynik.miernik.rmsR()} → soft-switch"
                            } else {
                                "${silnik.id} ciche → soft-switch"
                            },
                        )
                        // Hybryda/Dual — bez trwałego banu (krótki verify / cichy user).
                        if (silnik != SilnikNagrywania.HybrydaMicDownlink &&
                            silnik != SilnikNagrywania.DualUpDown
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
                            if (zatrzymajFlaga.get()) return
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
        stan = StanNagrywania.Blad("Żaden silnik nie oddał audio na tym urządzeniu")
        nagrywa = false
    }

    /**
     * Soft-switch: zachowaj PCM z POCZĄTKU pliku (do [STASH_MAX_MS]), nie tylko ring.
     * Dzięki temu failover 2–3 silników nie wycina pierwszych sekund rozmowy.
     */
    private fun softSwitchZachowajPreroll() {
        zatrzymajFlaga.set(true)
        runCatching { ShellKlient.usluga()?.stop() }
        runCatching { lokalnyRecord?.stop(); lokalnyRecord?.release() }
        lokalnyRecord = null
        pumpWatki.forEach { runCatching { it.join(600) } }
        pumpWatki = emptyList()

        val plik = aktywnyPlik
        zachowanyPreroll = pcmZPoczatkuWav(plik)
            ?.takeIf { it.isNotEmpty() }
            ?: (preroll?.zrzut() ?: ByteArray(0))

        plik?.let { if (it.exists()) it.delete() }
        aktywnyPlik = null
        nagrywa = false
        preroll = null
        zatrzymajFlaga.set(false)
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

    /** Silnik 1: AudioRecord w procesie dialera — bez Shizuku. */
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

    private fun wymagajShell(ctx: Context): INagrywanieShell? {
        ShizukuKlient.dolacz(ctx)
        ShizukuKlient.odswiez()
        val svc = ShellKlient.usluga()
        if (svc == null) return null
        return svc
    }

    private fun otworzShellDual(ctx: Context, numer: String): Otwarcie {
        val svc = wymagajShell(ctx) ?: return Otwarcie.Fail(ShellKlient.krotkiOpis(ctx))
        // Najpierw MIC+DOWNLINK (UPLINK na ColorOS/Realme bywa ciszą = „tylko oni”).
        val proby = listOf(
            MediaRecorder.AudioSource.MIC to "mic+dn",
            MediaRecorder.AudioSource.VOICE_UPLINK to "up+dn",
        )
        var pipeUp: Array<ParcelFileDescriptor>? = null
        var pipeDn: Array<ParcelFileDescriptor>? = null
        var mask = 0
        var ostatniBlad: String? = null
        for ((upSrc, tag) in proby) {
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
            if (mask == 0b11) {
                Log.i(TAG, "dual OK $tag")
                pipeUp = up
                pipeDn = dn
                break
            }
            ostatniBlad = svc.ostatniBlad ?: "dual mask=$mask ($tag)"
            runCatching { svc.stop() }
            runCatching { up[0].close(); up[1].close() }
            runCatching { dn[0].close(); dn[1].close() }
            mask = 0
        }
        val upOk = pipeUp
        val dnOk = pipeDn
        if (mask != 0b11 || upOk == null || dnOk == null) {
            return Otwarcie.Fail(ostatniBlad ?: "dual mask")
        }
        runCatching { upOk[1].close() }
        runCatching { dnOk[1].close() }
        // Mono mix — oba głosy słychać na mono głośniku (stereo L=ja R=oni → tylko L).
        val (plik, wav) = nowyWav(ctx, numer, 1)
        val miernik = MiernikRms()
        val t = Thread({
            mieszajDoMono(upOk[0], dnOk[0], wav, miernik)
        }, "nagrywanie-mix").apply { isDaemon = true; start() }
        pumpWatki = listOf(t)
        return Otwarcie.Ok(plik, miernik, wymagaObuStron = true)
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
        val miernik = MiernikRms()
        val t = Thread({
            mieszajMicIPipeDoMono(mic, pipeDn[0], wav, miernik)
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
        val miernik = MiernikRms()
        val t = Thread({
            kopiujStereoDoMono(pipe[0], wav, miernik)
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

    /** MIC (L/ja, podbite) + DOWNLINK (R/oni) → mono mix. */
    private fun mieszajMicIPipeDoMono(
        mic: AudioRecord,
        fdDn: ParcelFileDescriptor,
        wav: WavPisarz,
        miernik: MiernikRms,
    ) {
        val dn = FileInputStream(fdDn.fileDescriptor)
        val bufMic = ByteArray(4096)
        val bufDn = ByteArray(4096)
        val mono = ByteArray(4096)
        try {
            mic.startRecording()
            while (!zatrzymajFlaga.get()) {
                val nMic = mic.read(bufMic, 0, bufMic.size)
                val nDn = dn.read(bufDn)
                if (nMic <= 0 && nDn <= 0) break
                val samples = max(nMic.coerceAtLeast(0), nDn.coerceAtLeast(0)) / 2
                var o = 0
                for (i in 0 until samples) {
                    val surowy = if (i * 2 + 1 < nMic) {
                        ((bufMic[i * 2].toInt() and 0xff) or (bufMic[i * 2 + 1].toInt() shl 8)).toShort()
                    } else 0
                    // MIC bywa cichszy od downlinku — podbij „mnie”.
                    val lu = wzmocnij(surowy, 2.0f)
                    val rd = if (i * 2 + 1 < nDn) {
                        ((bufDn[i * 2].toInt() and 0xff) or (bufDn[i * 2 + 1].toInt() shl 8)).toShort()
                    } else 0
                    val mix = mieszajSample(lu, rd)
                    mono[o++] = (mix.toInt() and 0xff).toByte()
                    mono[o++] = ((mix.toInt() shr 8) and 0xff).toByte()
                    miernik.dodajL(lu)
                    miernik.dodajR(rd)
                }
                if (o > 0) piszPcm(wav, mono, 0, o)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "hybryda", t)
        } finally {
            runCatching { dn.close() }
            runCatching { fdDn.close() }
            runCatching { mic.stop(); mic.release() }
            if (lokalnyRecord === mic) lokalnyRecord = null
            runCatching { wav.close() }
        }
    }

    /** UPLINK + DOWNLINK pipes → mono WAV (obie strony na każdym głośniku). */
    private fun mieszajDoMono(
        fdUp: ParcelFileDescriptor,
        fdDn: ParcelFileDescriptor,
        wav: WavPisarz,
        miernik: MiernikRms,
    ) {
        val up = FileInputStream(fdUp.fileDescriptor)
        val dn = FileInputStream(fdDn.fileDescriptor)
        val bufUp = ByteArray(4096)
        val bufDn = ByteArray(4096)
        val mono = ByteArray(4096)
        try {
            while (!zatrzymajFlaga.get()) {
                val nUp = up.read(bufUp)
                val nDn = dn.read(bufDn)
                if (nUp <= 0 && nDn <= 0) break
                val samples = max(nUp.coerceAtLeast(0), nDn.coerceAtLeast(0)) / 2
                var o = 0
                for (i in 0 until samples) {
                    val lu = if (i * 2 + 1 < nUp) {
                        ((bufUp[i * 2].toInt() and 0xff) or (bufUp[i * 2 + 1].toInt() shl 8)).toShort()
                    } else 0
                    val rd = if (i * 2 + 1 < nDn) {
                        ((bufDn[i * 2].toInt() and 0xff) or (bufDn[i * 2 + 1].toInt() shl 8)).toShort()
                    } else 0
                    val mix = mieszajSample(lu, rd)
                    mono[o++] = (mix.toInt() and 0xff).toByte()
                    mono[o++] = ((mix.toInt() shr 8) and 0xff).toByte()
                    miernik.dodajL(lu)
                    miernik.dodajR(rd)
                }
                if (o > 0) piszPcm(wav, mono, 0, o)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "mix", t)
        } finally {
            runCatching { up.close() }
            runCatching { dn.close() }
            runCatching { fdUp.close() }
            runCatching { fdDn.close() }
            runCatching { wav.close() }
        }
    }

    /** Interleaved stereo PCM → mono mix + osobne mierniki L/R. */
    private fun kopiujStereoDoMono(
        fd: ParcelFileDescriptor,
        wav: WavPisarz,
        miernik: MiernikRms,
    ) {
        val inp = FileInputStream(fd.fileDescriptor)
        val buf = ByteArray(8192)
        val mono = ByteArray(4096)
        try {
            while (!zatrzymajFlaga.get()) {
                val n = inp.read(buf)
                if (n <= 0) break
                val frames = n / 4
                var o = 0
                for (i in 0 until frames) {
                    val base = i * 4
                    if (base + 3 >= n) break
                    val l = ((buf[base].toInt() and 0xff) or (buf[base + 1].toInt() shl 8)).toShort()
                    val r = ((buf[base + 2].toInt() and 0xff) or (buf[base + 3].toInt() shl 8)).toShort()
                    val mix = mieszajSample(l, r)
                    mono[o++] = (mix.toInt() and 0xff).toByte()
                    mono[o++] = ((mix.toInt() shr 8) and 0xff).toByte()
                    miernik.dodajL(l)
                    miernik.dodajR(r)
                }
                if (o > 0) piszPcm(wav, mono, 0, o)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "stereo→mono", t)
        } finally {
            runCatching { inp.close() }
            runCatching { fd.close() }
            runCatching { wav.close() }
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
            when (JakoscNagrywania.werdyktProbe(elapsed, obie, GRACE_MS, VERIFY_MS + 400L)) {
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
