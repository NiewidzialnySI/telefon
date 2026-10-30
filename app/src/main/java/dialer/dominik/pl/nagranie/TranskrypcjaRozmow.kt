package dialer.dominik.pl.nagranie

import android.content.Context
import android.util.Log
import dev.ffmpegkit.whisper.Whisper
import dev.ffmpegkit.whisper.WhisperConfig
import dev.ffmpegkit.whisper.WhisperModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Offline WAV → tekst (Whisper.cpp).
 *
 * Model: bardsai/whisper-medium-pl-v2 (~1,5 GB ggml f16) — wyraźnie lepszy polski
 * niż small-pl (~14% → ~8,7% WER na Common Voice). Spontaniczna mowa / przekleństwa
 * przez BPE. Ściągany raz przy pierwszym użyciu (Wi‑Fi).
 *
 * Jeszcze mocniejsze (large-v3-turbo PL ~0,8–1,6 GB) wymagałoby kwantyzacji Q8
 * albo większego f16 — free whisper-android AAR lepiej znosi f16 medium.
 */
object TranskrypcjaRozmow {
    private const val TAG = "Transkrypcja"

    private const val MODEL_URL =
        "https://huggingface.co/knightdave/whisper-polish-ggml-handy/resolve/main/ggml-medium-pl.bin"
    private const val MODEL_FILE_NAME = "ggml-medium-pl.bin"
    private const val MODEL_DIR_NAME = "whisper-pl"
    /** Pełny f16 medium ≈ 1,45–1,55 GB. */
    private const val MIN_BAJTOW = 1_400L * 1024L * 1024L

    private const val STARY_VOSK_DIR = "vosk-model-small-pl-0.22"
    private const val STARY_SMALL = "ggml-small-pl.bin"

    sealed class Stan {
        data object Bezczynny : Stan()
        data class Pobieranie(val postep: Float) : Stan()
        data object LadowanieModelu : Stan()
        data class Transkrybuje(val plik: String) : Stan()
        data class Gotowe(val tekst: String) : Stan()
        data class Blad(val komunikat: String) : Stan()
    }

    private val _stan = MutableStateFlow<Stan>(Stan.Bezczynny)
    val stan: StateFlow<Stan> = _stan

    private val mutex = Mutex()
    @Volatile private var model: WhisperModel? = null

    fun plikTranskrypcji(wav: File): File =
        File(wav.parentFile, wav.nameWithoutExtension + ".txt")

    fun odczytaj(wav: File): String? {
        val t = plikTranskrypcji(wav)
        if (!t.exists()) return null
        return runCatching { t.readText(Charsets.UTF_8).trim() }.getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    fun zapisz(wav: File, tekst: String) {
        plikTranskrypcji(wav).writeText(tekst.trim(), Charsets.UTF_8)
    }

    fun modelGotowy(ctx: Context): Boolean {
        val f = plikModelu(ctx)
        return f.isFile && f.length() >= MIN_BAJTOW
    }

    suspend fun transkrybuj(ctx: Context, wav: File): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                upewnijModel(ctx)
                _stan.value = Stan.LadowanieModelu
                val m = zaladujModel(ctx)
                _stan.value = Stan.Transkrybuje(wav.name)
                val watki = Runtime.getRuntime().availableProcessors().coerceIn(2, 6)
                val wynik = Whisper.transcribe(
                    m,
                    wav.absolutePath,
                    WhisperConfig(
                        language = "pl",
                        translate = false,
                        threads = watki,
                        maxSegmentLength = 0,
                        printTimestamps = false,
                    ),
                )
                val tekst = wynik.text.trim()
                if (tekst.isBlank()) {
                    _stan.value = Stan.Blad("Brak rozpoznanej mowy (cisza / zły język)")
                    return@withLock ""
                }
                zapisz(wav, tekst)
                _stan.value = Stan.Gotowe(tekst)
                tekst
            } catch (t: Throwable) {
                Log.e(TAG, "transkrybuj", t)
                val msg = t.message ?: "błąd transkrypcji"
                _stan.value = Stan.Blad(msg)
                throw t
            }
        }
    }

    fun resetStan() {
        _stan.value = Stan.Bezczynny
    }

    private fun plikModelu(ctx: Context): File =
        File(File(ctx.filesDir, MODEL_DIR_NAME), MODEL_FILE_NAME)

    private suspend fun zaladujModel(ctx: Context): WhisperModel {
        model?.takeIf { it.isValid }?.let { return it }
        // Po zmianie pliku modelu zwolnij stary handle.
        model?.let { runCatching { Whisper.releaseModel(it) } }
        model = null
        val path = plikModelu(ctx).absolutePath
        val m = Whisper.loadModel(ctx, path)
        model = m
        return m
    }

    private fun upewnijModel(ctx: Context) {
        usunStareModele(ctx)
        if (modelGotowy(ctx)) return
        _stan.value = Stan.Pobieranie(0f)
        val dest = plikModelu(ctx)
        dest.parentFile?.mkdirs()
        val tmp = File(dest.parentFile, "$MODEL_FILE_NAME.part")
        tmp.delete()
        pobierz(MODEL_URL, tmp) { p -> _stan.value = Stan.Pobieranie(p) }
        if (tmp.length() < MIN_BAJTOW) {
            val n = tmp.length()
            tmp.delete()
            error("Model Whisper niepełny po pobraniu ($n B, trzeba ≥ $MIN_BAJTOW)")
        }
        // Zwolnij stary model w RAM przed podmianą pliku.
        model?.let { runCatching { Whisper.releaseModel(it) } }
        model = null
        dest.delete()
        if (!tmp.renameTo(dest)) {
            tmp.copyTo(dest, overwrite = true)
            tmp.delete()
        }
        if (!modelGotowy(ctx)) error("Model Whisper niekompletny po pobraniu")
        _stan.value = Stan.Pobieranie(1f)
    }

    private fun usunStareModele(ctx: Context) {
        File(ctx.filesDir, STARY_VOSK_DIR).takeIf { it.exists() }?.let {
            runCatching { it.deleteRecursively() }
                .onFailure { Log.w(TAG, "usun vosk", it) }
        }
        File(File(ctx.filesDir, MODEL_DIR_NAME), STARY_SMALL).takeIf { it.isFile }?.let {
            runCatching { it.delete() }
                .onFailure { Log.w(TAG, "usun small-pl", it) }
        }
    }

    private fun pobierz(url: String, dest: File, onProgress: (Float) -> Unit) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 600_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "TelefonDialer/1.2")
        }
        try {
            if (conn.responseCode !in 200..299) {
                error("HTTP ${conn.responseCode} przy pobieraniu modelu")
            }
            val total = conn.contentLengthLong.coerceAtLeast(1L)
            BufferedInputStream(conn.inputStream).use { inp ->
                FileOutputStream(dest).use { out ->
                    val buf = ByteArray(256 * 1024)
                    var readTotal = 0L
                    while (true) {
                        val n = inp.read(buf)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                        readTotal += n
                        onProgress((readTotal.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
    }
}
