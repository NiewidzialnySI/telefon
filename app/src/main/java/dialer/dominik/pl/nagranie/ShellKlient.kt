package dialer.dominik.pl.nagranie

import android.content.Context
import android.util.Log
import dialer.dominik.pl.nagranie.adb.ShellAdbBootstrap

/** Fasada: własna usługa ADB (shell). */
object ShellKlient {
    private const val TAG = "ShellKlient"

    fun usluga(): INagrywanieShell? = ShellAdbBootstrap.usluga()

    fun czyShellGotowy(): Boolean = usluga() != null

    fun krotkiOpis(ctx: Context): String =
        if (ShellAdbBootstrap.usluga() != null) "ADB OK — obie strony"
        else ShellAdbBootstrap.zdiagnozuj(ctx).krotkiStatus()

    suspend fun zapewnij(ctx: Context, autoWlacAdb: Boolean = true): Boolean {
        if (czyShellGotowy()) return true
        if (!ShellAdbBootstrap.czySparowane(ctx)) return false
        // Nie używaj runCatching na suspend — wyjątek po resume obchodzi try (crash apki).
        return try {
            ShellAdbBootstrap.uruchom(ctx, autoWlacAdb = autoWlacAdb)
        } catch (t: Throwable) {
            Log.w(TAG, "adb uruchom", t)
            false
        }
    }
}
