package dialer.dominik.pl.backup

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import dialer.dominik.pl.MainActivity
import dialer.dominik.pl.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Backup / przywracanie w tle — działa też po wyjściu z aplikacji (foreground + powiadomienie).
 */
class BackupForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val akcja = intent?.action ?: ACTION_BACKUP
        val token = BackupStan.tokenTymczasowy
        if (token.isNullOrBlank()) {
            BackupStan.blad("Brak tokenu — połącz konto Google ponownie")
            usunPowiadomieniePostepu()
            stopSelf()
            return START_NOT_STICKY
        }

        val tryb = if (akcja == ACTION_PRZYWROC) BackupStan.Tryb.Przywroc else BackupStan.Tryb.Backup
        utworzKanal()
        val notif = zbudujPowiadomienie(
            if (tryb == BackupStan.Tryb.Backup) "Tworzenie kopii…" else "Przywracanie kopii…",
            0
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_ID,
                notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            @Suppress("DEPRECATION")
            startForeground(NOTIF_ID, notif)
        }

        BackupStan.start(tryb)
        job?.cancel()
        job = scope.launch {
            val wynik = when (tryb) {
                BackupStan.Tryb.Backup -> BackupDyskGoogle.wykonajBackup(applicationContext, token)
                BackupStan.Tryb.Przywroc -> BackupDyskGoogle.wykonajPrzywroc(applicationContext, token)
                else -> BackupDyskGoogle.Wynik.Blad("Nieznany tryb")
            }
            BackupStan.tokenTymczasowy = null
            when (wynik) {
                is BackupDyskGoogle.Wynik.Sukces -> {
                    BackupStan.sukces(wynik.komunikat)
                    pokazanKoniec(wynik.komunikat, ok = true)
                }
                is BackupDyskGoogle.Wynik.Blad -> {
                    BackupStan.blad(wynik.komunikat)
                    pokazanKoniec(wynik.komunikat, ok = false)
                }
            }
            // REMOVE — DETACH zostawiał „Tworzenie kopii…” jako wiszące ongoing.
            ServiceCompat.stopForeground(
                this@BackupForegroundService,
                ServiceCompat.STOP_FOREGROUND_REMOVE
            )
            usunPowiadomieniePostepu()
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        job?.cancel()
        usunPowiadomieniePostepu()
        scope.cancel()
        super.onDestroy()
    }

    private fun usunPowiadomieniePostepu() {
        getSystemService(NotificationManager::class.java)?.cancel(NOTIF_ID)
    }

    private fun utworzKanal() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        val kanal = NotificationChannel(
            CHANNEL_ID,
            "Backup Dysku Google",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Postęp kopii zapasowej"
        }
        nm.createNotificationChannel(kanal)
    }

    private fun zbudujPowiadomienie(tekst: String, postep01: Int): Notification {
        val otworz = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Telefon — backup")
            .setContentText(tekst)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, postep01.coerceIn(0, 100), postep01 <= 0)
            .setContentIntent(otworz)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
    }

    private fun pokazanKoniec(tekst: String, ok: Boolean) {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        val otworz = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(if (ok) "Backup gotowy" else "Backup — błąd")
            .setContentText(tekst)
            .setAutoCancel(true)
            .setContentIntent(otworz)
            .build()
        nm.notify(NOTIF_ID_KONIEC, n)
    }

    companion object {
        const val ACTION_BACKUP = "dialer.dominik.pl.backup.BACKUP"
        const val ACTION_PRZYWROC = "dialer.dominik.pl.backup.PRZYWROC"
        private const val CHANNEL_ID = "backup_dysk"
        private const val NOTIF_ID = 7101
        private const val NOTIF_ID_KONIEC = 7102

        fun start(context: Context, przywroc: Boolean) {
            val i = Intent(context, BackupForegroundService::class.java).apply {
                action = if (przywroc) ACTION_PRZYWROC else ACTION_BACKUP
            }
            context.startForegroundService(i)
        }

        /** Usuwa wiszące „Tworzenie kopii…”, gdy serwis już nie działa. */
        fun usunZaleglyPostep(context: Context) {
            if (BackupStan.czyTrwa()) return
            context.getSystemService(NotificationManager::class.java)?.cancel(NOTIF_ID)
        }
    }
}
