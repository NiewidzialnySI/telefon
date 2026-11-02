package dialer.dominik.pl.nagranie

import android.content.Context
import android.media.AudioFormat
import android.os.ParcelFileDescriptor
import android.os.Process
import android.util.Log
import androidx.annotation.Keep
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.system.exitProcess

/**
 * Usługa w `app_process` UID 2000 (shell) — start przez Wireless ADB.
 * v5: + ustawWifi (svc wifi).
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

    override fun grantPermission(packageName: String, permission: String): Boolean {
        if (packageName.isBlank() || permission.isBlank()) return false
        // Tylko własny pakiet + znana lista uprawnień dialera (nie dowolny app).
        if (packageName != "dialer.dominik.pl") {
            Log.w(TAG, "grantPermission odrzucony — obcy pakiet=$packageName")
            return false
        }
        if (permission !in DOZWOLONE_GRANT) {
            Log.w(TAG, "grantPermission odrzucony — permission=$permission")
            return false
        }
        return runCatching {
            val p = ProcessBuilder("pm", "grant", packageName, permission)
                .redirectErrorStream(true)
                .start()
            val out = p.inputStream.bufferedReader().readText().trim()
            val code = p.waitFor()
            Log.i(TAG, "pm grant $permission → code=$code out=$out")
            code == 0
        }.onFailure { Log.e(TAG, "grantPermission $permission", it) }
            .getOrDefault(false)
    }

    /**
     * Jak adv-install / Obtainium (ścieżka shell): stage w `/data/local/tmp` + `pm install -r`.
     * FD przychodzi z UID aplikacji — shell nie czyta prywatnego cache apki.
     */
    override fun installApk(apkFd: ParcelFileDescriptor): String {
        val tmp = File("/data/local/tmp/telefon_update_${Process.myPid()}.apk")
        return runCatching {
            ParcelFileDescriptor.AutoCloseInputStream(apkFd).use { input ->
                FileOutputStream(tmp).use { output ->
                    input.copyTo(output, 64 * 1024)
                    output.fd.sync()
                }
            }
            if (!tmp.isFile || tmp.length() < 1024L) {
                return@runCatching "APK za mały po zapisie (${tmp.length()} B)"
            }
            // -r replace, -t allow test/debug, -g grant runtime perms
            val p = ProcessBuilder(
                "pm", "install", "-r", "-t", "-g", tmp.absolutePath,
            )
                .redirectErrorStream(true)
                .start()
            val out = p.inputStream.bufferedReader().use { it.readText() }.trim()
            val code = p.waitFor()
            Log.i(TAG, "pm install → code=$code out=$out size=${tmp.length()}")
            when {
                code == 0 && out.contains("Success", ignoreCase = true) -> ""
                out.isNotBlank() -> out.take(300)
                else -> "pm install exit=$code"
            }
        }.onFailure { Log.e(TAG, "installApk", it) }
            .getOrElse { it.message ?: "installApk fail" }
            .also { runCatching { tmp.delete() } }
    }

    override fun ustawWifi(on: Boolean): Boolean {
        val arg = if (on) "enable" else "disable"
        return runCatching {
            val p = ProcessBuilder("svc", "wifi", arg)
                .redirectErrorStream(true)
                .start()
            val out = p.inputStream.bufferedReader().readText().trim()
            val code = p.waitFor()
            Log.i(TAG, "svc wifi $arg → code=$code out=$out")
            code == 0
        }.onFailure { Log.e(TAG, "ustawWifi($on)", it) }
            .getOrDefault(false)
    }

    override fun destroy() {
        Log.i(TAG, "destroy")
        runCatching { stop() }
        exitProcess(0)
    }

    companion object {
        private const val TAG = "NagrywanieShell"
        const val WERSJA = 5
        private val DOZWOLONE_GRANT = setOf(
            "android.permission.RECORD_AUDIO",
            "android.permission.READ_PHONE_STATE",
            "android.permission.READ_CALL_LOG",
            "android.permission.WRITE_CALL_LOG",
            "android.permission.READ_SMS",
            "android.permission.SEND_SMS",
            "android.permission.RECEIVE_SMS",
            "android.permission.RECEIVE_MMS",
            "android.permission.READ_CONTACTS",
            "android.permission.WRITE_CONTACTS",
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.BLUETOOTH_CONNECT",
            "android.permission.READ_PHONE_NUMBERS",
            "android.permission.ANSWER_PHONE_CALLS",
            "android.permission.CALL_PHONE",
        )
        private const val STAN_IDLE = 0
        private const val STAN_STARTING = 1
        private const val STAN_DUAL = 2
        private const val STAN_SINGLE = 3
        private const val STAN_PLAYBACK = 4
    }
}
