package dialer.dominik.pl.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dialer.dominik.pl.ui.screens.rozlaczRozmowe

class AkcjaPolaczeniaReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        PowiadomieniaPolaczen.anuluj(context)
        ZarzadcaDzwonka.zatrzymaj()

        when (intent.action) {
            "AKCJA_ODBIERZ" -> {
                StanPolaczenia.polaczenie?.answer(0)
                EkranRozmowy.pokaz(context)
            }
            "AKCJA_ODRZUC" -> {
                StanPolaczenia.polaczenie?.reject(false, null)
                StanPolaczenia.polaczenie = null
            }
            "AKCJA_ROZLACZ" -> {
                // Niezawodne rozłączenie (disconnect + fallback endCall).
                rozlaczRozmowe(context, usunLog = false)
            }
        }
    }
}
