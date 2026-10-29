package dialer.dominik.pl.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AkcjaTrwajacejRozmowyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "AKCJA_WYCISZ" -> {
                TelefonInCallService.instancja?.przelaczWyciszenie()
                val stan = StanPolaczenia
                PowiadomieniaTrwajacejRozmowy.pokazPowiadomienie(context, stan.rozmowca, stan.wyciszony)
            }
            "AKCJA_ROZLACZ" -> {
                StanPolaczenia.polaczenie?.disconnect()
                PowiadomieniaTrwajacejRozmowy.anuluj(context)
            }
        }
    }
}
