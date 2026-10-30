package dialer.dominik.pl.nagranie

import android.content.Context
import android.util.Log
import dialer.dominik.pl.nagranie.adb.ShellAdbBootstrap

/**
 * Fasada: najpierw własna usługa ADB (shell), potem Shizuku.
 */
object ShellKlient {
    private const val TAG = "ShellKlient"

    fun usluga(): INagrywanieShell? =
        ShellAdbBootstrap.usluga() ?: ShizukuKlient.usluga

    fun czyShellGotowy(): Boolean = usluga() != null

    fun krotkiOpis(ctx: Context): String = when {
        ShellAdbBootstrap.usluga() != null -> "ADB OK — obie strony"
        ShizukuKlient.czyShellGotowy() -> ShizukuKlient.krotkiOpis()
        else -> ShellAdbBootstrap.zdiagnozuj(ctx).krotkiStatus()
    }

    suspend fun zapewnij(ctx: Context): Boolean {
        if (czyShellGotowy()) return true
        if (ShellAdbBootstrap.czySparowane(ctx)) {
            // uruchom najpierw czeka na republish; adb_wifi tylko gdy daemon martwy.
            val ok = runCatching { ShellAdbBootstrap.uruchom(ctx) }
                .onFailure { Log.w(TAG, "adb uruchom", it) }
                .getOrDefault(false)
            if (ok) return true
        }
        ShizukuKlient.dolacz(ctx)
        return ShizukuKlient.sprobujPolaczyc(ctx)
    }
}
