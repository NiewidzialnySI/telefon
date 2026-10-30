package dialer.dominik.pl.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dialer.dominik.pl.ui.screens.rozlaczRozmowe

class AkcjaTrwajacejRozmowyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "AKCJA_WYCISZ" -> {
                TelefonInCallService.instancja?.przelaczWyciszenie()
                val stan = StanPolaczenia
                PowiadomieniaTrwajacejRozmowy.pokazPowiadomienie(context, stan.rozmowca, stan.wyciszony)
            }
            "AKCJA_ROZLACZ" -> {
                // Jak AkcjaPolaczeniaReceiver — reject przy RINGING, disconnect + fallback.
                rozlaczRozmowe(context, usunLog = false)
                PowiadomieniaTrwajacejRozmowy.anuluj(context)
            }
        }
    }
}
