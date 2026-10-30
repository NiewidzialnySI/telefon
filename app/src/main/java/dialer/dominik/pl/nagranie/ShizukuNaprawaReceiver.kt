package dialer.dominik.pl.nagranie

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Klik w powiadomienie „Shizuku nie działa”. */
class ShizukuNaprawaReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != AKCJA_NAPRAW) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val w = ShizukuAdbNaprawa.naprawZPowiadomienia(context.applicationContext)
                if (w.ok) {
                    PowiadomieniaShizuku.anulujPostep(context)
                    PowiadomieniaShizuku.anulujBrak(context)
                    // Krótki wynik tylko gdy trzeba coś wiedzieć o ADB Wi‑Fi.
                    if (!w.adbWifiWylaczone) {
                        PowiadomieniaShizuku.pokazWynik(context, false, w.komunikat)
                    }
                } else {
                    PowiadomieniaShizuku.pokazWynik(context, false, w.komunikat)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "napraw", t)
                PowiadomieniaShizuku.pokazWynik(
                    context,
                    false,
                    t.message ?: "Błąd naprawy Shizuku",
                )
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val AKCJA_NAPRAW = "dialer.dominik.pl.NAPRAW_SHIZUKU"
        private const val TAG = "ShizukuNaprawa"
    }
}
