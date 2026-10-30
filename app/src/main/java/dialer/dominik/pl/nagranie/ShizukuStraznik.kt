package dialer.dominik.pl.nagranie

import android.content.Context
import android.util.Log
import dialer.dominik.pl.nagranie.adb.ShellAdbBootstrap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Alert tylko gdy **żaden** silnik shell nie działa (własne ADB ani Shizuku).
 * Własna usługa ADB ma pierwszeństwo — Shizuku nie jest wymagane.
 */
object ShizukuStraznik {
    private const val TAG = "ShizukuStraznik"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var ostatniAlertMs = 0L
    @Volatile private var sprawdzWToku = false

    fun sprawdz(ctx: Context, forceAlert: Boolean = false) {
        val app = ctx.applicationContext
        if (!NagrywanieUstawienia.czyWlaczone(app) ||
            !NagrywanieUstawienia.czyPowiadomieniaShizuku(app)
        ) {
            PowiadomieniaShizuku.anulujBrak(app)
            return
        }
        if (ShellKlient.czyShellGotowy()) {
            PowiadomieniaShizuku.anulujBrak(app)
            return
        }
        if (sprawdzWToku) return
        sprawdzWToku = true
        scope.launch {
            try {
                // Najpierw własna usługa — bez krzyczenia o Shizuku.
                if (ShellAdbBootstrap.czySparowane(app) || ShellAdbBootstrap.usluga() != null) {
                    runCatching { ShellKlient.zapewnij(app) }
                        .onFailure { Log.w(TAG, "zapewnij", it) }
                } else {
                    // Brak parowania ADB — cichy zapas Shizuku (bez otwierania UI).
                    ShizukuKlient.dolacz(app)
                    runCatching { ShizukuKlient.sprobujPolaczyc(app) }
                }
                if (ShellKlient.czyShellGotowy()) {
                    PowiadomieniaShizuku.anulujBrak(app)
                    return@launch
                }
                val teraz = System.currentTimeMillis()
                if (!forceAlert && teraz - ostatniAlertMs < 30 * 60_000L) return@launch
                ostatniAlertMs = teraz
                val opis = ShellKlient.krotkiOpis(app)
                Log.i(TAG, "alert: $opis")
                PowiadomieniaShizuku.pokazBrak(app, opis)
            } finally {
                sprawdzWToku = false
            }
        }
    }

    /**
     * Śmierć bindera Shizuku — **nie** alarmuj, jeśli własna ADB żyje / jest sparowana.
     */
    fun naBinderMartwy(ctx: Context) {
        if (!NagrywanieUstawienia.czyWlaczone(ctx)) return
        if (ShellKlient.czyShellGotowy()) {
            PowiadomieniaShizuku.anulujBrak(ctx)
            return
        }
        // Własna ścieżka: cicho podnieś daemon, bez „Shizuku nie działa”.
        if (ShellAdbBootstrap.czySparowane(ctx)) {
            sprawdz(ctx, forceAlert = false)
            return
        }
        if (!NagrywanieUstawienia.czyPowiadomieniaShizuku(ctx)) return
        ostatniAlertMs = 0L
        sprawdz(ctx, forceAlert = true)
    }
}
