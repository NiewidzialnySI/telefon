package dialer.dominik.pl.nagranie

import android.content.Context
import android.util.Log
import dialer.dominik.pl.nagranie.adb.ShellAdbBootstrap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Alert gdy własna usługa ADB nie działa. */
object NagrywanieStraznik {
    private const val TAG = "NagrywanieStraznik"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var ostatniAlertMs = 0L
    @Volatile private var sprawdzWToku = false

    fun sprawdz(ctx: Context, forceAlert: Boolean = false) {
        val app = ctx.applicationContext
        if (!NagrywanieUstawienia.czyWlaczone(app) ||
            !NagrywanieUstawienia.czyPowiadomieniaNagrywania(app)
        ) {
            PowiadomieniaNagrywania.anulujBrak(app)
            return
        }
        if (ShellKlient.czyShellGotowy()) {
            PowiadomieniaNagrywania.anulujBrak(app)
            return
        }
        if (sprawdzWToku) return
        sprawdzWToku = true
        scope.launch {
            try {
                if (ShellAdbBootstrap.czySparowane(app) || ShellAdbBootstrap.usluga() != null) {
                    runCatching { ShellKlient.zapewnij(app) }
                        .onFailure { Log.w(TAG, "zapewnij", it) }
                }
                if (ShellKlient.czyShellGotowy()) {
                    PowiadomieniaNagrywania.anulujBrak(app)
                    return@launch
                }
                val teraz = System.currentTimeMillis()
                if (!forceAlert && teraz - ostatniAlertMs < 30 * 60_000L) return@launch
                ostatniAlertMs = teraz
                val opis = ShellKlient.krotkiOpis(app)
                Log.i(TAG, "alert: $opis")
                PowiadomieniaNagrywania.pokazBrak(app, opis)
            } finally {
                sprawdzWToku = false
            }
        }
    }

    fun naBinderMartwy(ctx: Context) {
        if (!NagrywanieUstawienia.czyWlaczone(ctx)) return
        if (ShellKlient.czyShellGotowy()) {
            PowiadomieniaNagrywania.anulujBrak(ctx)
            return
        }
        if (ShellAdbBootstrap.czySparowane(ctx)) {
            sprawdz(ctx, forceAlert = false)
            return
        }
        if (!NagrywanieUstawienia.czyPowiadomieniaNagrywania(ctx)) return
        ostatniAlertMs = 0L
        sprawdz(ctx, forceAlert = true)
    }
}
