package dialer.dominik.pl.nagranie

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
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
 * Przechwytywanie odtwarzania przez ukryty AudioPolicy (MIX_ROLE_PLAYERS),
 * bez dialogu MediaProjection.
 *
 * Cel: USAGE_VOICE_COMMUNICATION (Wi‑Fi calling / VoIP / IN_COMMUNICATION)
 * + USAGE_MEDIA. Shell na Android 14+ ma CAPTURE_VOICE_COMMUNICATION_OUTPUT.
 *
 * Koncept: AudioMixingRule → AudioMix(LOOP_BACK_RENDER) → registerAudioPolicyStatic
 * → createAudioRecordSink — jak w scrcpy / FOSS Shizuku-recorderach, własna
 * implementacja pod nasz dialer.
 */
internal class PlaybackPcmNagrywarka(
    private val sampleRate: Int,
    private val channelMask: Int,
    private val outFd: ParcelFileDescriptor,
    private val kontekst: Context,
) {
    private val dziala = AtomicBoolean(false)
    private val ostatniBlad = AtomicReference<String?>(null)
    private var record: AudioRecord? = null
    private var audioPolicy: Any? = null
    private var watek: Thread? = null

    val blad: String? get() = ostatniBlad.get()

    fun init(): Boolean {
        val rec = zbudujNaMain() ?: return false
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            ostatniBlad.set("playback AudioRecord state=${rec.state}")
            zwolnijPolicy()
            runCatching { rec.release() }
            return false
        }
        record = rec
        Log.i(TAG, "PlaybackCapture OK sr=$sampleRate ch=$channelMask")
        return true
    }

    fun start() {
        val rec = checkNotNull(record)
        dziala.set(true)
        watek = Thread({ pompuj(rec) }, "nagrywanie-playback").apply {
            isDaemon = true
            priority = Thread.MAX_PRIORITY - 1
            start()
        }
    }

    fun stop(joinMs: Long = 1500) {
        if (!dziala.compareAndSet(true, false)) {
            zwolnijPolicy()
            return
        }
        runCatching { record?.stop() }
        watek?.let { runCatching { it.join(joinMs) } }
        if (watek?.isAlive == true) {
            runCatching { outFd.close() }
            watek?.let { runCatching { it.join(joinMs) } }
        }
        watek = null
        runCatching { record?.release() }
        record = null
        zwolnijPolicy()
    }

    private fun zbudujNaMain(): AudioRecord? {
        val main = Looper.getMainLooper()
        if (main.thread === Thread.currentThread()) return utworzSink()

        val wynik = AtomicReference<AudioRecord?>(null)
        val blad = AtomicReference<Throwable?>(null)
        val latch = CountDownLatch(1)
        Handler(main).post {
            try {
                wynik.set(utworzSink())
            } catch (t: Throwable) {
                blad.set(t)
            } finally {
                latch.countDown()
            }
        }
        if (!latch.await(3, TimeUnit.SECONDS)) {
            ostatniBlad.set("playback ctor timeout")
            return null
        }
        blad.get()?.let {
            ostatniBlad.set("playback: ${it.javaClass.simpleName}: ${it.message}")
            return null
        }
        return wynik.get()
    }

    @SuppressLint("PrivateApi", "MissingPermission")
    private fun utworzSink(): AudioRecord? {
        return try {
            UkryteApi.wlacz()
            val mixingRuleCls = Class.forName("android.media.audiopolicy.AudioMixingRule")
            val mixingRuleBuilderCls = Class.forName("android.media.audiopolicy.AudioMixingRule\$Builder")
            val ruleBuilder = mixingRuleBuilderCls.getConstructor().newInstance()

            val mixRolePlayers = mixingRuleCls.getField("MIX_ROLE_PLAYERS").getInt(null)
            mixingRuleBuilderCls.getMethod("setTargetMixRole", Int::class.javaPrimitiveType)
                .invoke(ruleBuilder, mixRolePlayers)

            val ruleMatchUsage = mixingRuleCls.getField("RULE_MATCH_ATTRIBUTE_USAGE").getInt(null)
            val addMixRule = mixingRuleBuilderCls.getMethod(
                "addMixRule", Int::class.javaPrimitiveType, Any::class.java
            )
            val voiceAttr = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .build()
            val mediaAttr = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build()
            addMixRule.invoke(ruleBuilder, ruleMatchUsage, voiceAttr)
            addMixRule.invoke(ruleBuilder, ruleMatchUsage, mediaAttr)

            runCatching {
                mixingRuleBuilderCls.getMethod(
                    "voiceCommunicationCaptureAllowed", Boolean::class.javaPrimitiveType
                ).invoke(ruleBuilder, true)
            }
            runCatching {
                mixingRuleBuilderCls.getMethod(
                    "allowPrivilegedPlaybackCapture", Boolean::class.javaPrimitiveType
                ).invoke(ruleBuilder, true)
            }

            val mixingRule = mixingRuleBuilderCls.getMethod("build").invoke(ruleBuilder)

            val mixCls = Class.forName("android.media.audiopolicy.AudioMix")
            val mixBuilderCls = Class.forName("android.media.audiopolicy.AudioMix\$Builder")
            val mixBuilder = mixBuilderCls.getConstructor(mixingRuleCls).newInstance(mixingRule)

            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(channelMask)
                .build()
            mixBuilderCls.getMethod("setFormat", AudioFormat::class.java)
                .invoke(mixBuilder, format)

            val routeFlags = mixCls.getField("ROUTE_FLAG_LOOP_BACK_RENDER").getInt(null)
            mixBuilderCls.getMethod("setRouteFlags", Int::class.javaPrimitiveType)
                .invoke(mixBuilder, routeFlags)

            val audioMix = mixBuilderCls.getMethod("build").invoke(mixBuilder)

            val policyCls = Class.forName("android.media.audiopolicy.AudioPolicy")
            val policyBuilderCls = Class.forName("android.media.audiopolicy.AudioPolicy\$Builder")
            val policyBuilder = policyBuilderCls.getConstructor(Context::class.java)
                .newInstance(kontekst)
            policyBuilderCls.getMethod("addMix", mixCls).invoke(policyBuilder, audioMix)
            val policy = policyBuilderCls.getMethod("build").invoke(policyBuilder)

            val register = AudioManager::class.java.getDeclaredMethod(
                "registerAudioPolicyStatic", policyCls
            ).apply { isAccessible = true }
            val code = register.invoke(null, policy) as Int
            if (code != 0) {
                ostatniBlad.set("registerAudioPolicyStatic=$code")
                return null
            }
            audioPolicy = policy

            val sink = policyCls.getMethod("createAudioRecordSink", mixCls)
                .invoke(policy, audioMix) as? AudioRecord
            if (sink == null) {
                ostatniBlad.set("createAudioRecordSink=null")
                zwolnijPolicy()
                return null
            }
            sink
        } catch (t: Throwable) {
            ostatniBlad.set("playback: ${t.javaClass.simpleName}: ${t.message}")
            Log.w(TAG, "utworzSink", t)
            zwolnijPolicy()
            null
        }
    }

    @SuppressLint("PrivateApi")
    private fun zwolnijPolicy() {
        val policy = audioPolicy ?: return
        audioPolicy = null
        runCatching {
            val policyCls = Class.forName("android.media.audiopolicy.AudioPolicy")
            AudioManager::class.java.getDeclaredMethod(
                "unregisterAudioPolicyAsyncStatic", policyCls
            ).apply { isAccessible = true }.invoke(null, policy)
        }.onFailure { Log.w(TAG, "unregister policy", it) }
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
            ostatniBlad.set("pump: ${t.message}")
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
