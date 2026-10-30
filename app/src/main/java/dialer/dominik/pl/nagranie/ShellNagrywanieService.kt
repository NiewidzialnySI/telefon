package dialer.dominik.pl.nagranie

import android.content.Context
import android.media.AudioFormat
import android.os.ParcelFileDescriptor
import android.os.Process
import android.util.Log
import androidx.annotation.Keep
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.system.exitProcess

/**
 * UserService Shizuku — `app_process` UID 2000 (shell).
 * v2: + startPlayback (AudioPolicy loopback).
 */
@Keep
class ShellNagrywanieService : INagrywanieShell.Stub {

    @Keep
    constructor() : this(null)

    @Keep
    constructor(@Suppress("UNUSED_PARAMETER") context: Context?) {
        Log.i(TAG, "init uid=${Process.myUid()} v=$WERSJA")
        UkryteApi.wlacz()
        KontekstSystemowy.rozgrzejNaMain()
    }

    private val kontekstCapture: Context = run {
        val raw = KontekstSystemowy.pobierz()
        if (Process.myUid() == 0) {
            raw
        } else {
            runCatching { KontekstShell(raw) }
                .onFailure { Log.e(TAG, "KontekstShell fail", it) }
                .getOrDefault(raw)
        }
    }

    private val stan = AtomicInteger(STAN_IDLE)
    @Volatile private var jobUp: PcmNagrywarka? = null
    @Volatile private var jobDn: PcmNagrywarka? = null
    @Volatile private var jobPlayback: PlaybackPcmNagrywarka? = null
    private val ostatniBlad = AtomicReference<String?>(null)

    override fun getWersja(): Int = WERSJA

    override fun startDual(
        zrodloUplink: Int,
        zrodloDownlink: Int,
        sampleRate: Int,
        fdUplink: ParcelFileDescriptor,
        fdDownlink: ParcelFileDescriptor,
    ): Int {
        Log.i(TAG, "startDual up=$zrodloUplink dn=$zrodloDownlink sr=$sampleRate")
        if (!stan.compareAndSet(STAN_IDLE, STAN_STARTING)) {
            runCatching { fdUplink.close() }
            runCatching { fdDownlink.close() }
            return 0
        }
        val up = PcmNagrywarka(
            "up", zrodloUplink, sampleRate, AudioFormat.CHANNEL_IN_MONO, fdUplink, kontekstCapture
        )
        val dn = PcmNagrywarka(
            "dn", zrodloDownlink, sampleRate, AudioFormat.CHANNEL_IN_MONO, fdDownlink, kontekstCapture
        )
        val upOk = up.init()
        val dnOk = dn.init()
        if (!upOk && !dnOk) {
            ostatniBlad.set("dual: up=${up.blad}; dn=${dn.blad}")
            runCatching { fdUplink.close() }
            runCatching { fdDownlink.close() }
            stan.set(STAN_IDLE)
            return 0
        }
        var mask = 0
        if (upOk) {
            jobUp = up
            mask = mask or 0b01
            up.start()
        } else {
            runCatching { fdUplink.close() }
        }
        if (dnOk) {
            jobDn = dn
            mask = mask or 0b10
            dn.start()
        } else {
            runCatching { fdDownlink.close() }
        }
        stan.set(STAN_DUAL)
        return mask
    }

    override fun startSingle(
        zrodlo: Int,
        sampleRate: Int,
        channelMask: Int,
        fdPcm: ParcelFileDescriptor,
    ): Int {
        Log.i(TAG, "startSingle src=$zrodlo sr=$sampleRate ch=$channelMask")
        if (!stan.compareAndSet(STAN_IDLE, STAN_STARTING)) {
            runCatching { fdPcm.close() }
            return 0
        }
        val job = PcmNagrywarka(
            "single", zrodlo, sampleRate, channelMask, fdPcm, kontekstCapture
        )
        if (!job.init()) {
            ostatniBlad.set("single: ${job.blad}")
            runCatching { fdPcm.close() }
            stan.set(STAN_IDLE)
            return 0
        }
        jobUp = job
        job.start()
        stan.set(STAN_SINGLE)
        return 1
    }

    override fun startPlayback(
        sampleRate: Int,
        channelMask: Int,
        fdPcm: ParcelFileDescriptor,
    ): Int {
        Log.i(TAG, "startPlayback sr=$sampleRate ch=$channelMask")
        if (!stan.compareAndSet(STAN_IDLE, STAN_STARTING)) {
            runCatching { fdPcm.close() }
            return 0
        }
        val job = PlaybackPcmNagrywarka(sampleRate, channelMask, fdPcm, kontekstCapture)
        if (!job.init()) {
            ostatniBlad.set("playback: ${job.blad}")
            runCatching { fdPcm.close() }
            stan.set(STAN_IDLE)
            return 0
        }
        jobPlayback = job
        job.start()
        stan.set(STAN_PLAYBACK)
        return 1
    }

    override fun stop() {
        Log.i(TAG, "stop")
        runCatching { jobUp?.stop() }
        runCatching { jobDn?.stop() }
        runCatching { jobPlayback?.stop() }
        jobUp = null
        jobDn = null
        jobPlayback = null
        stan.set(STAN_IDLE)
    }

    override fun getStan(): Int = stan.get()

    override fun getOstatniBlad(): String? = ostatniBlad.get()

    override fun destroy() {
        Log.i(TAG, "destroy")
        runCatching { stop() }
        exitProcess(0)
    }

    companion object {
        private const val TAG = "NagrywanieShell"
        const val WERSJA = 2
        private const val STAN_IDLE = 0
        private const val STAN_STARTING = 1
        private const val STAN_DUAL = 2
        private const val STAN_SINGLE = 3
        private const val STAN_PLAYBACK = 4
    }
}
