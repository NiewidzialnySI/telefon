package dialer.dominik.pl.nagranie

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Jedna ścieżka AudioRecord → pipe PCM.
 *
 * Konstruktor AudioRecord.Builder MUSI lecieć na main Looper procesu shell
 * (AppOps czyta thread-local ActivityThread; na Binder-thread jest pusto →
 * STATE_UNINITIALIZED). Context = [KontekstShell] — Builder.setContext
 * przekazuje AttributionSource shell.
 */
internal class PcmNagrywarka(
    private val etykieta: String,
    private val zrodlo: Int,
    private val sampleRate: Int,
    private val channelMask: Int,
    private val outFd: ParcelFileDescriptor,
    private val kontekst: Context,
) {
    private val dziala = AtomicBoolean(false)
    private val ostatniBlad = AtomicReference<String?>(null)
    private var record: AudioRecord? = null
    private var watek: Thread? = null

    val blad: String? get() = ostatniBlad.get()

    fun init(): Boolean {
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelMask, encoding)
        if (minBuf <= 0) {
            ostatniBlad.set("getMinBufferSize=$minBuf src=$zrodlo")
            return false
        }
        val kanaly = if (channelMask == AudioFormat.CHANNEL_IN_STEREO) 2 else 1
        val target = (sampleRate * kanaly * 2) / 4
        val buf = maxOf(minBuf * 2, target)

        val rec = zbudujNaMain(zrodlo, sampleRate, channelMask, encoding, buf) ?: return false
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            ostatniBlad.set("AudioRecord state=${rec.state} src=$zrodlo")
            rec.release()
            return false
        }
        record = rec
        Log.i(TAG, "OK $etykieta src=$zrodlo sr=$sampleRate ch=$channelMask")
        return true
    }

    fun start() {
        val rec = checkNotNull(record) { "init() najpierw" }
        dziala.set(true)
        watek = Thread({ pompuj(rec) }, "nagrywanie-$etykieta").apply {
            isDaemon = true
            priority = Thread.MAX_PRIORITY - 1
            start()
        }
    }

    fun stop(joinMs: Long = 1500) {
        if (!dziala.compareAndSet(true, false)) return
        runCatching { record?.stop() }
        watek?.let { runCatching { it.join(joinMs) } }
        if (watek?.isAlive == true) {
            runCatching { outFd.close() }
            watek?.let { runCatching { it.join(joinMs) } }
        }
        watek = null
        runCatching { record?.release() }
        record = null
    }

    private fun zbudujNaMain(src: Int, sr: Int, ch: Int, enc: Int, buf: Int): AudioRecord? {
        val main = Looper.getMainLooper()
        if (main.thread === Thread.currentThread()) return sprobujCtor(src, sr, ch, enc, buf)

        val wynik = AtomicReference<AudioRecord?>(null)
        val blad = AtomicReference<Throwable?>(null)
        val latch = CountDownLatch(1)
        Handler(main).post {
            try {
                wynik.set(sprobujCtor(src, sr, ch, enc, buf))
            } catch (t: Throwable) {
                blad.set(t)
            } finally {
                latch.countDown()
            }
        }
        if (!latch.await(2, TimeUnit.SECONDS)) {
            ostatniBlad.set("ctor timeout 2s")
            return null
        }
        blad.get()?.let {
            ostatniBlad.set("ctor: ${it.javaClass.simpleName}: ${it.message}")
            return null
        }
        return wynik.get()
    }

    @SuppressLint("MissingPermission")
    private fun sprobujCtor(src: Int, sr: Int, ch: Int, enc: Int, buf: Int): AudioRecord? {
        return try {
            val format = AudioFormat.Builder()
                .setEncoding(enc)
                .setSampleRate(sr)
                .setChannelMask(ch)
                .build()
            val builder = AudioRecord.Builder()
                .setContext(kontekst)
                .setAudioSource(src)
                .setAudioFormat(format)
                .setBufferSizeInBytes(buf)
            // VOICE_COMMUNICATION domyślnie privacy-sensitive → cisza przy concurrent.
            // Shell ma bypass CAPTURE_AUDIO_OUTPUT, ale flaga i tak bywa true — wymuszamy false.
            when (src) {
                android.media.MediaRecorder.AudioSource.MIC,
                android.media.MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                android.media.MediaRecorder.AudioSource.VOICE_RECOGNITION,
                android.media.MediaRecorder.AudioSource.UNPROCESSED -> {
                    runCatching { builder.setPrivacySensitive(false) }
                }
            }
            val rec = builder.build()
            // TELEPHONY tylko dla ścieżek RX — na UPLINK/MIC psuje routing (tylko własny głos).
            when (src) {
                android.media.MediaRecorder.AudioSource.VOICE_DOWNLINK,
                android.media.MediaRecorder.AudioSource.VOICE_CALL -> {
                    runCatching {
                        val am = kontekst.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
                            ?: return@runCatching
                        val tel = am.getDevices(android.media.AudioManager.GET_DEVICES_INPUTS)
                            .firstOrNull { it.type == android.media.AudioDeviceInfo.TYPE_TELEPHONY }
                        if (tel != null) rec.setPreferredDevice(tel)
                    }
                }
            }
            rec
        } catch (t: Throwable) {
            ostatniBlad.set("ctor: ${t.javaClass.simpleName}: ${t.message}")
            Log.w(TAG, "ctor $etykieta", t)
            null
        }
    }

    private fun pompuj(rec: AudioRecord) {
        val out = FileOutputStream(outFd.fileDescriptor)
        val buf = ByteArray(8 * 1024)
        try {
            rec.startRecording()
            while (dziala.get()) {
                val n = rec.read(buf, 0, buf.size, AudioRecord.READ_BLOCKING)
                when {
                    n > 0 -> out.write(buf, 0, n)
                    n == AudioRecord.ERROR_INVALID_OPERATION ||
                        n == AudioRecord.ERROR_BAD_VALUE ||
                        n == AudioRecord.ERROR_DEAD_OBJECT -> {
                        ostatniBlad.set("read=$n")
                        break
                    }
                }
            }
        } catch (t: Throwable) {
            ostatniBlad.set("pump: ${t.javaClass.simpleName}: ${t.message}")
        } finally {
            runCatching { out.flush() }
            runCatching { out.close() }
            runCatching { outFd.close() }
        }
    }

    companion object {
        private const val TAG = "NagrywanieShell"
    }
}
