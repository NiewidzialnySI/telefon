package dialer.dominik.pl.nagranie

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper

/** Po restarcie — daj Wi‑Fi/Shizuku chwilę, potem sprawdź i ewentualnie powiadom. */
class ShizukuBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val a = intent?.action ?: return
        if (a != Intent.ACTION_BOOT_COMPLETED && a != Intent.ACTION_LOCKED_BOOT_COMPLETED) return
        if (!NagrywanieUstawienia.czyWlaczone(context)) return
        if (!NagrywanieUstawienia.czyPowiadomieniaShizuku(context)) return
        val app = context.applicationContext
        // Shizuku Start-on-boot zwykle startuje po Wi‑Fi — sprawdzamy z opóźnieniem.
        Handler(Looper.getMainLooper()).postDelayed({
            ShizukuStraznik.sprawdz(app, forceAlert = true)
        }, 45_000L)
        Handler(Looper.getMainLooper()).postDelayed({
            ShizukuStraznik.sprawdz(app, forceAlert = true)
        }, 120_000L)
    }
}
