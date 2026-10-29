package dialer.dominik.pl.call

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import dialer.dominik.pl.MainActivity
import dialer.dominik.pl.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Nagrywanie rozmów przez mikrofon.
 *
 * WAŻNE OGRANICZENIE SYSTEMU: od Androida 14 usługi typu `microphone`
 * NIE MOŻNA utworzyć, gdy aplikacja jest w tle — RECORD_AUDIO jest uprawnieniem
 * „while-in-use”, więc w tle apka formalnie go nie ma i system rzuca
 * SecurityException. Dlatego usługę trzeba uruchomić, GDY APKA JEST WIDOCZNA
 * (np. przy kliknięciu „Zadzwoń”), a nie po wykryciu rozmowy.
 *
 * Bez uprawnienia CAPTURE_AUDIO_OUTPUT (dostępne tylko dla aplikacji
 * systemowych) nagrywana jest wyłącznie strona użytkownika. Obie strony
 * słychać tylko przy włączonym głośniku — to ograniczenie sprzętowe.
 */
class NagrywanieRozmowService : Service() {

    companion object {
        private const val TAG = "NagrywanieRozmow"
        private const val KANAL_ID = "nagrywanie_rozmow"
        private const val POWIADOMIENIE_ID = 4201

        const val AKCJA_START = "dialer.dominik.pl.NAGRYWANIE_START"
        const val AKCJA_STOP = "dialer.dominik.pl.NAGRYWANIE_STOP"
        const val EXTRA_KATALOG = "katalog"

        /**
         * Uruchamia nagrywanie. Zwraca true, jeśli udało się wystartować.
         *
         * Musi być wołane z widocznej aktywności — patrz komentarz klasy.
         */
        fun start(context: Context, katalog: File): Boolean {
            return try {
                val intencja = Intent(context, NagrywanieRozmowService::class.java).apply {
                    action = AKCJA_START
                    putExtra(EXTRA_KATALOG, katalog.absolutePath)
                }
                context.startForegroundService(intencja)
                true
            } catch (e: Exception) {
                // ForegroundServiceStartNotAllowedException lub SecurityException
                Log.e(TAG, "Nie udało się uruchomić nagrywania: ${e.javaClass.simpleName}", e)
                false
            }
        }

        fun stop(context: Context) {
            try {
                context.startService(
                    Intent(context, NagrywanieRozmowService::class.java).apply {
                        action = AKCJA_STOP
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Nie udało się zatrzymać nagrywania", e)
            }
        }
    }

    private var recorder: MediaRecorder? = null
    private var plik: File? = null
    private var trwa = false

    override fun onBind(intencja: Intent?): IBinder? = null

    override fun onStartCommand(intencja: Intent?, flagi: Int, idStartu: Int): Int {
        when (intencja?.action) {
            AKCJA_START -> {
                val katalog = intencja.getStringExtra(EXTRA_KATALOG)
                    ?.let { File(it) }
                    ?: File(filesDir, "recordings")
                rozpocznij(katalog)
            }
            AKCJA_STOP -> {
                zakoncz()
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_NOT_STICKY
    }

    private fun rozpocznij(katalog: File) {
        if (trwa) return

        try {
            katalog.mkdirs()

            // Nazwa pliku: data, godzina i kierunek rozmowy
            val znacznik = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
                .format(Date())
            val nowy = File(katalog, "rozmowa_$znacznik.m4a")

            val nagrywacz = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            nagrywacz.apply {
                // VOICE_COMMUNICATION: optymalizowane pod rozmowę, mniej agresywne
                // niż MIC. Nadal nagrywa tylko stronę użytkownika — bez
                // CAPTURE_AUDIO_OUTPUT nie ma dostępu do strumienia rozmowy.
                setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128_000)
                setAudioSamplingRate(44_100)
                setOutputFile(nowy.absolutePath)
                prepare()
                start()
            }

            recorder = nagrywacz
            plik = nowy
            trwa = true

            startForeground(
                POWIADOMIENIE_ID,
                zbudujPowiadomienie("Nagrywanie rozmowy", "Trwa nagrywanie — włącz głośnik, aby nagrać obie strony"),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                } else 0
            )

            Log.i(TAG, "Nagrywanie rozpoczęte: ${nowy.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Błąd nagrywania: ${e.javaClass.simpleName}", e)
            zakoncz()
            stopSelf()
        }
    }

    private fun zakoncz() {
        try {
            recorder?.let {
                it.stop()
                it.release()
            }
        } catch (e: Exception) {
            // stop() rzuca, jeśli nagranie było zbyt krótkie (np. < 1 s)
            Log.w(TAG, "Zatrzymanie nagrywania: ${e.message}")
            try {
                recorder?.release()
            } catch (ignored: Exception) { }
        } finally {
            recorder = null
            trwa = false
            plik?.let { Log.i(TAG, "Nagranie zapisane: ${it.absolutePath}") }
            plik = null
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
    }

    private fun zbudujPowiadomienie(tytul: String, tresc: String): Notification {
        val menedzer = getSystemService(NotificationManager::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val kanal = NotificationChannel(
                KANAL_ID,
                "Nagrywanie rozmów",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Informacja o trwającym nagrywaniu rozmowy"
                setShowBadge(false)
            }
            menedzer?.createNotificationChannel(kanal)
        }

        val wejscie = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, KANAL_ID)
            .setContentTitle(tytul)
            .setContentText(tresc)
            .setSmallIcon(R.drawable.ic_logo_nowoczesny)
            .setColor(0xFF69F0AE.toInt())
            .setColorized(true)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(wejscie)
            .build()
    }

    override fun onDestroy() {
        zakoncz()
        super.onDestroy()
    }
}
