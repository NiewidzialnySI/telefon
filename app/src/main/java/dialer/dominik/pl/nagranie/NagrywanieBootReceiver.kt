package dialer.dominik.pl.nagranie

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper

/** Po restarcie — daj Wi‑Fi chwilę, potem sprawdź usługę ADB. */
class NagrywanieBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val a = intent?.action ?: return
        if (a != Intent.ACTION_BOOT_COMPLETED && a != Intent.ACTION_LOCKED_BOOT_COMPLETED) return
        if (!NagrywanieUstawienia.czyWlaczone(context)) return
        if (!NagrywanieUstawienia.czyPowiadomieniaNagrywania(context)) return
        val app = context.applicationContext
        Handler(Looper.getMainLooper()).postDelayed({
            NagrywanieStraznik.sprawdz(app, forceAlert = true)
        }, 45_000L)
        Handler(Looper.getMainLooper()).postDelayed({
            NagrywanieStraznik.sprawdz(app, forceAlert = true)
        }, 120_000L)
    }
}
