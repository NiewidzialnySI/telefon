package dialer.dominik.pl.nagranie

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
import dialer.dominik.pl.AppNavigationState
import dialer.dominik.pl.MainActivity
import dialer.dominik.pl.R
import dialer.dominik.pl.Zakladka
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Transkrypcja / pobieranie modelu w tle (FGS).
 * Po zakończeniu — powiadomienie z wynikiem (klik → zakładka Nagrania).
 */
class TranskrypcjaForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var observeJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val sciezka = intent?.getStringExtra(EXTRA_WAV).orEmpty()
        val wav = File(sciezka)
        if (!wav.isFile) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!trwa.compareAndSet(false, true)) {
            // Już trwa inna — nie gasić bieżącej.
            return START_NOT_STICKY
        }

        upewnijKanal()
        startFg(zbudujPostep("Start…", indeterminate = true))

        observeJob?.cancel()
        observeJob = scope.launch {
            TranskrypcjaRozmow.stan.collectLatest { s ->
                when (s) {
                    is TranskrypcjaRozmow.Stan.Pobieranie -> {
                        val pct = (s.postep * 100).toInt().coerceIn(0, 100)
                        aktualizujPostep(
                            "Pobieranie modelu PL… $pct%",
                            progress = pct,
                            indeterminate = false,
                        )
                    }
                    is TranskrypcjaRozmow.Stan.LadowanieModelu ->
                        aktualizujPostep("Ładowanie modelu…", indeterminate = true)
                    is TranskrypcjaRozmow.Stan.Transkrybuje ->
                        aktualizujPostep("Transkrypcja…", indeterminate = true)
                    else -> Unit
                }
            }
        }

        job?.cancel()
        job = scope.launch {
            val wynik = runCatching { TranskrypcjaRozmow.transkrybuj(applicationContext, wav) }
            observeJob?.cancel()
            ServiceCompat.stopForeground(
                this@TranskrypcjaForegroundService,
                ServiceCompat.STOP_FOREGROUND_REMOVE,
            )
            getSystemService(NotificationManager::class.java)?.cancel(NOTIF_POSTEP)
            wynik.fold(
                onSuccess = { tekst ->
                    if (tekst.isBlank()) {
                        pokazKoniec(
                            ok = false,
                            tytul = "Transkrypcja",
                            tekst = "Brak rozpoznanej mowy",
                        )
                    } else {
                        val skrot = tekst.replace('\n', ' ').trim()
                            .let { if (it.length > 120) it.take(117) + "…" else it }
                        pokazKoniec(
                            ok = true,
                            tytul = "Transkrypcja gotowa",
                            tekst = skrot,
                        )
                    }
                    TranskrypcjaRozmow.resetStan()
                },
                onFailure = { e ->
                    pokazKoniec(
                        ok = false,
                        tytul = "Transkrypcja — błąd",
                        tekst = e.message ?: "Nie udało się",
                    )
                    TranskrypcjaRozmow.resetStan()
                },
            )
            trwa.set(false)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        job?.cancel()
        observeJob?.cancel()
        scope.cancel()
        if (trwa.get()) {
            // Anulowane w trakcie — zostaw stan błędu / wyczyść.
            trwa.set(false)
            TranskrypcjaRozmow.resetStan()
        }
        super.onDestroy()
    }

    private fun startFg(n: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_POSTEP, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            @Suppress("DEPRECATION")
            startForeground(NOTIF_POSTEP, n)
        }
    }

    private fun aktualizujPostep(
        tekst: String,
        progress: Int = 0,
        indeterminate: Boolean = false,
    ) {
        getSystemService(NotificationManager::class.java)
            ?.notify(NOTIF_POSTEP, zbudujPostep(tekst, progress, indeterminate))
    }

    private fun upewnijKanal() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(
                KANAL_POSTEP,
                "Transkrypcja — postęp",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "Pobieranie modelu i rozpoznawanie mowy" },
        )
        nm.createNotificationChannel(
            NotificationChannel(
                KANAL_KONIEC,
                "Transkrypcja — gotowe",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Powiadomienie po zakończeniu transkrypcji" },
        )
    }

    private fun pendingNagrania(req: Int): PendingIntent {
        val i = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(EXTRA_OTWORZ_NAGRANIA, true)
        }
        return PendingIntent.getActivity(
            this,
            req,
            i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun zbudujPostep(
        tekst: String,
        progress: Int = 0,
        indeterminate: Boolean = true,
    ): Notification =
        NotificationCompat.Builder(this, KANAL_POSTEP)
            .setSmallIcon(R.drawable.ic_logo_telefon)
            .setContentTitle("Transkrypcja")
            .setContentText(tekst)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress.coerceIn(0, 100), indeterminate)
            .setContentIntent(pendingNagrania(0))
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()

    private fun pokazKoniec(ok: Boolean, tytul: String, tekst: String) {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        nm.notify(
            NOTIF_KONIEC,
            NotificationCompat.Builder(this, KANAL_KONIEC)
                .setSmallIcon(R.drawable.ic_logo_telefon)
                .setContentTitle(tytul)
                .setContentText(tekst)
                .setStyle(NotificationCompat.BigTextStyle().bigText(tekst))
                .setAutoCancel(true)
                .setContentIntent(pendingNagrania(1))
                .build(),
        )
    }

    companion object {
        private const val EXTRA_WAV = "wav"
        const val EXTRA_OTWORZ_NAGRANIA = "otworz_nagrania"
        private const val KANAL_POSTEP = "transkrypcja_postep"
        private const val KANAL_KONIEC = "transkrypcja_koniec"
        private const val NOTIF_POSTEP = 3301
        private const val NOTIF_KONIEC = 3302

        private val trwa = AtomicBoolean(false)

        fun czyTrwa(): Boolean = trwa.get()

        fun start(ctx: Context, wav: File) {
            val i = Intent(ctx, TranskrypcjaForegroundService::class.java)
                .putExtra(EXTRA_WAV, wav.absolutePath)
            ctx.startForegroundService(i)
        }

        /** MainActivity: intent z powiadomienia → zakładka Nagrania. */
        fun obsluzIntent(intent: Intent?) {
            if (intent?.getBooleanExtra(EXTRA_OTWORZ_NAGRANIA, false) == true) {
                AppNavigationState.docelowaZakladka = Zakladka.Nagrania
                intent.removeExtra(EXTRA_OTWORZ_NAGRANIA)
            }
        }
    }
}
