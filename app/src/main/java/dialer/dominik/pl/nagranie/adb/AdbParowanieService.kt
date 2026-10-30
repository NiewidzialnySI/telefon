package dialer.dominik.pl.nagranie.adb

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import androidx.core.app.ServiceCompat
import dialer.dominik.pl.MainActivity
import dialer.dominik.pl.R
import dialer.dominik.pl.nagranie.ShizukuAdbNaprawa
import io.github.muntashirakon.adb.android.AdbMdns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.ConnectException
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Stałe powiadomienie jak Shizuku:
 * 1) zawsze widoczne (ongoing) przy otwartym oknie z kodem
 * 2) a11y / RemoteInput → wpisuje kod w powiadomieniu
 * 3) przycisk „Sparuj” (auto‑klik po odczycie) → pair w tle, bez zamykania dialogu
 */
class AdbParowanieService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var mdns: AdbMdns? = null
    private var szuka = false
    @Volatile private var host: String = "127.0.0.1"
    @Volatile private var port: Int = -1
    @Volatile private var pendingKod: String? = null
    private val paruje = AtomicBoolean(false)
    private var autoSparujRunnable: Runnable? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        upewnijKanal()
        aktywne.set(true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notif = when (intent?.action) {
            AKCJA_START -> onStartSzukania()

            AKCJA_AUTO_KOD, AKCJA_REPLY -> {
                val kod = when (intent.action) {
                    AKCJA_AUTO_KOD -> intent.getStringExtra(KLUCZ_KOD).orEmpty()
                    else -> RemoteInput.getResultsFromIntent(intent)
                        ?.getCharSequence(KLUCZ_KOD)
                        ?.toString()
                        .orEmpty()
                }.filter { !it.isWhitespace() }.filter { it.isDigit() }.take(6)

                val h = intent.getStringExtra(KLUCZ_HOST)?.takeIf { it.isNotBlank() } ?: host
                val pExtra = intent.getIntExtra(KLUCZ_PORT, -1)
                val p = when {
                    pExtra > 0 -> pExtra
                    port > 0 -> port
                    else -> -1
                }
                when {
                    kod.isEmpty() && p > 0 -> notifCzekam(h, p)
                    kod.isEmpty() -> onStartSzukania()
                    p <= 0 -> {
                        pendingKod = kod
                        host = h
                        // Pokazujemy kod w powiadomieniu — czekamy na mDNS / „Sparuj”.
                        notifKodGotowy(kod, h, 0, czekamNaPort = true)
                    }
                    kod.length != 6 -> notifCzekam(h, p)
                    else -> {
                        // Wpisz kod w powiadomieniu + przycisk Sparuj + auto‑klik.
                        pokazKodISparuj(kod, h, p)
                    }
                }
            }

            AKCJA_SPARUJ -> {
                val kod = (intent.getStringExtra(KLUCZ_KOD) ?: pendingKod).orEmpty()
                    .filter { it.isDigit() }.take(6)
                val h = intent.getStringExtra(KLUCZ_HOST) ?: host
                val p = intent.getIntExtra(KLUCZ_PORT, -1).let { if (it > 0) it else port }
                when {
                    kod.length != 6 -> notifCzekam(h, p)
                    p <= 0 -> {
                        pendingKod = kod
                        notifKodGotowy(kod, h, 0, czekamNaPort = true)
                    }
                    else -> onKod(kod, h, p)
                }
            }

            AKCJA_STOP -> {
                anulujAutoSparuj()
                aktywne.set(false)
                zatrzymajSzukanie()
                stopForegroundRemove()
                stopSelf()
                null
            }
            else -> return START_NOT_STICKY
        }
        if (notif != null) startFg(notif)
        return START_REDELIVER_INTENT
    }

    override fun onDestroy() {
        anulujAutoSparuj()
        aktywne.set(false)
        zatrzymajSzukanie()
        scope.cancel()
        super.onDestroy()
    }

    private fun onStartSzukania(): Notification {
        startSzukanie()
        return if (port > 0) notifCzekam(host, port) else notifCzekam(host, 0)
    }

    /** Kod już znany — pokaż w powiadomieniu i „kliknij” Sparuj (auto). */
    private fun pokazKodISparuj(kod: String, h: String, p: Int): Notification {
        pendingKod = kod
        host = h
        port = p
        ostatniHost = h
        ostatniPort = p
        zaplanujAutoSparuj(kod, h, p)
        return notifKodGotowy(kod, h, p, czekamNaPort = false)
    }

    private fun zaplanujAutoSparuj(kod: String, h: String, p: Int) {
        anulujAutoSparuj()
        // Jak Shizuku: w momencie kliknięcia bierzemy NAJNOWSZY port/host
        // (mDNS/a11y mogą zaktualizować je po zaplanowaniu — stary port = ECONNREFUSED).
        val r = Runnable {
            if (!aktywne.get() || paruje.get()) return@Runnable
            val hNow = ostatniHost.takeIf { it.isNotBlank() } ?: h
            val pNow = if (ostatniPort > 0) ostatniPort else p
            Log.i(TAG, "auto‑klik Sparuj kod=$kod $hNow:$pNow (planowano $h:$p)")
            startService(
                Intent(this, AdbParowanieService::class.java)
                    .setAction(AKCJA_SPARUJ)
                    .putExtra(KLUCZ_KOD, kod)
                    .putExtra(KLUCZ_HOST, hNow)
                    .putExtra(KLUCZ_PORT, pNow),
            )
        }
        autoSparujRunnable = r
        // Krótka pauza — użytkownik widzi kod w powiadomieniu, dialog zostaje otwarty.
        mainHandler.postDelayed(r, 700)
    }

    private fun anulujAutoSparuj() {
        autoSparujRunnable?.let { mainHandler.removeCallbacks(it) }
        autoSparujRunnable = null
    }

    private fun onKod(kod: String, h: String, p: Int): Notification {
        anulujAutoSparuj()
        if (!paruje.compareAndSet(false, true)) return notifPraca(kod)
        pendingKod = null
        scope.launch {
            val wynik = ShellAdbBootstrap.sparuj(applicationContext, h, p, kod)
            if (wynik.isFailure) {
                paruje.set(false)
                handleWynik(false, wynik.exceptionOrNull())
                return@launch
            }
            val ok = runCatching { ShellAdbBootstrap.uruchom(applicationContext) }
                .getOrDefault(false)
            handleWynik(
                ok,
                if (ok) null else Exception("Daemon nie wstał — sprawdź Wireless debugging"),
            )
            if (ok) {
                delay(500)
                wrocDoAplikacji()
            }
        }
        return notifPraca(kod)
    }

    private fun handleWynik(ok: Boolean, blad: Throwable?) {
        stopForegroundRemove()
        zatrzymajSzukanie()
        aktywne.set(false)
        val tytul = if (ok) "ADB sparowane" else "Parowanie nieudane"
        val tekst = when {
            ok -> "Usługa nagrywania działa. Wireless debugging można wyłączyć."
            blad is ConnectException ->
                "Port niedostępny — zostań na oknie z kodem i spróbuj Ponów (nie wychodź z dialogu)."
            else -> blad?.message ?: "Spróbuj ponownie"
        }
        if (ok) Log.i(TAG, "parowanie OK") else Log.w(TAG, "parowanie fail", blad)

        getSystemService(NotificationManager::class.java)?.notify(
            NOTIF_ID,
            NotificationCompat.Builder(this, KANAL)
                .setSmallIcon(R.drawable.ic_logo_telefon)
                .setContentTitle(tytul)
                .setContentText(tekst)
                .setStyle(NotificationCompat.BigTextStyle().bigText(tekst))
                .setAutoCancel(true)
                .setContentIntent(pendingOtworzApp())
                .apply {
                    if (!ok) {
                        addAction(
                            0,
                            "Ponów",
                            PendingIntent.getForegroundService(
                                this@AdbParowanieService,
                                REQ_RETRY,
                                startIntent(this@AdbParowanieService),
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                            ),
                        )
                    }
                }
                .build(),
        )
        stopSelf()
    }

    private fun wrocDoAplikacji() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
        )
    }

    private fun startSzukanie() {
        if (szuka) return
        szuka = true
        mdns = AdbMdns(
            this,
            AdbMdns.SERVICE_TYPE_TLS_PAIRING,
        ) { addr: InetAddress?, p: Int ->
            if (p <= 0) return@AdbMdns
            host = addr?.hostAddress?.takeIf { it.isNotBlank() && it != "::" } ?: host
            port = p
            ostatniHost = host
            ostatniPort = p
            Log.i(TAG, "znaleziono pairing $host:$p")
            val oczekujacy = pendingKod
            if (oczekujacy != null && oczekujacy.length == 6 && !paruje.get()) {
                startFg(pokazKodISparuj(oczekujacy, host, p))
            } else if (!paruje.get()) {
                getSystemService(NotificationManager::class.java)
                    ?.notify(NOTIF_ID, notifCzekam(host, p))
            }
        }.also { it.start() }
    }

    private fun zatrzymajSzukanie() {
        if (!szuka) return
        szuka = false
        runCatching { mdns?.stop() }
        mdns = null
    }

    private fun startFg(n: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                @Suppress("DEPRECATION")
                startForeground(NOTIF_ID, n)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "startForeground", e)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                e is ForegroundServiceStartNotAllowedException
            ) {
                getSystemService(NotificationManager::class.java)?.notify(NOTIF_ID, n)
            }
        }
    }

    private fun stopForegroundRemove() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    private fun upewnijKanal() {
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(
                KANAL,
                "Parowanie ADB",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description =
                    "Stałe powiadomienie: kod + przycisk Sparuj. Nie wychodź z okna parowania."
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
    }

    private fun bazaNotif(tytul: String, tekst: String): NotificationCompat.Builder {
        val b = NotificationCompat.Builder(this, KANAL)
            .setSmallIcon(R.drawable.ic_logo_telefon)
            .setContentTitle(tytul)
            .setContentText(tekst)
            .setStyle(NotificationCompat.BigTextStyle().bigText(tekst))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(false)
            .addAction(0, "Anuluj", pendingStop())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            b.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        }
        return b
    }

    /** Czekamy na kod — pole RemoteInput + instrukcja. */
    private fun notifCzekam(h: String, p: Int): Notification {
        val remote = RemoteInput.Builder(KLUCZ_KOD).setLabel("Kod 6 cyfr").build()
        val replyPi = PendingIntent.getForegroundService(
            this,
            REQ_REPLY,
            replyIntent(this, h, if (p > 0) p else -1),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        val reply = NotificationCompat.Action.Builder(0, "Wpisz kod", replyPi)
            .addRemoteInput(remote)
            .setAllowGeneratedReplies(false)
            .build()
        val tekst = if (p > 0) {
            "Port $p. Zostań na oknie z kodem. Pasek w dół NAD oknem → Wpisz kod " +
                "(albo czekaj — odczytamy sami i naciśniemy Sparuj)."
        } else {
            "Otwórz „Sparuj … kodem”. NIE wychodź z tego okna. " +
                "Powiadomienie zostanie — wpiszemy kod i klikniemy Sparuj."
        }
        return bazaNotif("Parowanie ADB — czekam na kod", tekst)
            .addAction(reply)
            .build()
    }

    /** Kod już w powiadomieniu + przycisk Sparuj (jak „kliknij by sparowało”). */
    private fun notifKodGotowy(
        kod: String,
        h: String,
        p: Int,
        czekamNaPort: Boolean,
    ): Notification {
        val remote = RemoteInput.Builder(KLUCZ_KOD).setLabel("Kod 6 cyfr").build()
        val replyPi = PendingIntent.getForegroundService(
            this,
            REQ_REPLY,
            replyIntent(this, h, if (p > 0) p else -1),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        val reply = NotificationCompat.Action.Builder(0, "Popraw kod", replyPi)
            .addRemoteInput(remote)
            .build()

        val sparujPi = PendingIntent.getForegroundService(
            this,
            REQ_SPARUJ,
            Intent(this, AdbParowanieService::class.java)
                .setAction(AKCJA_SPARUJ)
                .putExtra(KLUCZ_KOD, kod)
                .putExtra(KLUCZ_HOST, h)
                .putExtra(KLUCZ_PORT, p),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val sparuj = NotificationCompat.Action.Builder(0, "Sparuj", sparujPi).build()

        val tekst = if (czekamNaPort) {
            "Kod $kod — czekam na port mDNS. Zostań na oknie z kodem."
        } else {
            "Kod $kod (port $p). Zostań na oknie — za chwilę sami naciśniemy Sparuj. " +
                "Możesz też kliknąć „Sparuj” poniżej."
        }
        return bazaNotif("Kod ADB: $kod", tekst)
            .addAction(sparuj)
            .addAction(reply)
            .build()
    }

    private fun notifPraca(kod: String): Notification =
        bazaNotif("Parowanie…", "Kod $kod — wymiana kluczy. Nie zamykaj okna z kodem.")
            .setProgress(0, 0, true)
            .build()

    private fun pendingStop(): PendingIntent =
        PendingIntent.getService(
            this,
            REQ_STOP,
            stopIntent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun pendingOtworzApp(): PendingIntent =
        PendingIntent.getActivity(
            this,
            REQ_OPEN,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    companion object {
        private const val TAG = "AdbParowanie"
        const val KANAL = "adb_parowanie"
        private const val NOTIF_ID = 3201
        private const val REQ_REPLY = 1
        private const val REQ_STOP = 2
        private const val REQ_RETRY = 3
        private const val REQ_OPEN = 4
        private const val REQ_SPARUJ = 5
        private const val AKCJA_START = "dialer.dominik.pl.adb.START"
        private const val AKCJA_STOP = "dialer.dominik.pl.adb.STOP"
        private const val AKCJA_REPLY = "dialer.dominik.pl.adb.REPLY"
        private const val AKCJA_AUTO_KOD = "dialer.dominik.pl.adb.AUTO_KOD"
        private const val AKCJA_SPARUJ = "dialer.dominik.pl.adb.SPARUJ"
        private const val KLUCZ_KOD = "pairing_code"
        private const val KLUCZ_PORT = "port"
        private const val KLUCZ_HOST = "host"

        private val aktywne = AtomicBoolean(false)
        @Volatile private var ostatniHost = "127.0.0.1"
        @Volatile private var ostatniPort = -1

        fun czyAktywne(): Boolean = aktywne.get()

        fun startIntent(ctx: Context): Intent =
            Intent(ctx, AdbParowanieService::class.java).setAction(AKCJA_START)

        private fun stopIntent(ctx: Context): Intent =
            Intent(ctx, AdbParowanieService::class.java).setAction(AKCJA_STOP)

        private fun replyIntent(ctx: Context, host: String, port: Int): Intent =
            Intent(ctx, AdbParowanieService::class.java)
                .setAction(AKCJA_REPLY)
                .putExtra(KLUCZ_HOST, host)
                .putExtra(KLUCZ_PORT, port)

        fun dostarczKod(
            ctx: Context,
            kod: String,
            portUi: Int = -1,
            hostUi: String? = null,
        ) {
            if (!aktywne.get()) return
            val czysty = kod.filter { it.isDigit() }.take(6)
            if (czysty.length != 6) return
            val p = if (portUi > 0) portUi else ostatniPort
            val h = hostUi?.takeIf { it.isNotBlank() && it != "::" }
                ?: ostatniHost.takeIf { it != "127.0.0.1" }
                ?: hostUi
                ?: ostatniHost
            if (hostUi != null) ostatniHost = hostUi
            if (portUi > 0) ostatniPort = portUi
            Log.i(TAG, "dostarczKod → powiadomienie kod=$czysty $h:$p")
            val i = Intent(ctx, AdbParowanieService::class.java)
                .setAction(AKCJA_AUTO_KOD)
                .putExtra(KLUCZ_KOD, czysty)
                .putExtra(KLUCZ_HOST, h)
                .putExtra(KLUCZ_PORT, p)
            try {
                ctx.startForegroundService(i)
            } catch (_: Throwable) {
                runCatching { ctx.startService(i) }
            }
        }

        fun startujIOtworzUstawienia(ctx: Context) {
            val app = ctx.applicationContext
            val wlaczono = ShizukuAdbNaprawa.wlacAdbJakShizuku(app)
            Log.i(TAG, "auto adb_wifi put=$wlaczono teraz=${ShizukuAdbNaprawa.czyAdbWifi(app)}")

            AdbParowanieDostepnosc.oznaczNowaSesjaParowania()
            val a11y = AdbParowanieDostepnosc.czyWlaczona(app) ||
                AdbParowanieDostepnosc.sprobujWlaczyc(app)
            if (!a11y) {
                Toast.makeText(
                    app,
                    "Włącz „Telefon — odczyt kodu ADB” (raz) — potem sparuję sama.",
                    Toast.LENGTH_LONG,
                ).show()
                AdbParowanieDostepnosc.otworzUstawienia(app)
            } else {
                Toast.makeText(
                    app,
                    "Auto‑parowanie: włączam Wireless i okno z kodem…",
                    Toast.LENGTH_SHORT,
                ).show()
            }

            try {
                app.startForegroundService(startIntent(app))
            } catch (e: Throwable) {
                Log.e(TAG, "startForegroundService", e)
                runCatching { app.startService(startIntent(app)) }
            }
            // Chwila na podpięcie a11y po sprobujWlaczyc, potem Wireless → klik „Paruj z kodem”.
            val delayMs = if (a11y) 700L else 400L
            Handler(Looper.getMainLooper()).postDelayed({
                ShellAdbBootstrap.otworzWirelessDebugging(app)
            }, delayMs)
        }
    }
}
