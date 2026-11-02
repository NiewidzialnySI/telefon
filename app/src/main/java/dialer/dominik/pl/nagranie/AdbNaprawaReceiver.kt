package dialer.dominik.pl.nagranie

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Klik w powiadomienie „usługa nagrywania nie działa”. */
class AdbNaprawaReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != AKCJA_NAPRAW) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val w = AdbNaprawa.naprawZPowiadomienia(context.applicationContext)
                if (w.ok) {
                    PowiadomieniaNagrywania.anulujPostep(context)
                    PowiadomieniaNagrywania.anulujBrak(context)
                    if (!w.adbWifiWylaczone) {
                        PowiadomieniaNagrywania.pokazWynik(context, false, w.komunikat)
                    }
                } else {
                    PowiadomieniaNagrywania.pokazWynik(context, false, w.komunikat)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "napraw", t)
                PowiadomieniaNagrywania.pokazWynik(
                    context,
                    false,
                    t.message ?: "Błąd naprawy ADB",
                )
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val AKCJA_NAPRAW = "dialer.dominik.pl.NAPRAW_ADB"
        private const val TAG = "AdbNaprawaReceiver"
    }
}
