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
        val app = context.applicationContext
        if (NagrywanieUstawienia.czyPortalFirmyAuto(app)) {
            Handler(Looper.getMainLooper()).postDelayed({
                PortalFirmyDetektor.zsynchronizujJesliAuto(app)
            }, 8_000L)
        }
        if (!NagrywanieUstawienia.czyWlaczone(context)) return
        if (!NagrywanieUstawienia.czyPowiadomieniaNagrywania(context)) return
        Handler(Looper.getMainLooper()).postDelayed({
            NagrywanieStraznik.sprawdz(app, forceAlert = true)
        }, 45_000L)
        Handler(Looper.getMainLooper()).postDelayed({
            NagrywanieStraznik.sprawdz(app, forceAlert = true)
        }, 120_000L)
    }
}
