package dialer.dominik.pl.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dialer.dominik.pl.ui.screens.rozlaczRozmowe
import dialer.dominik.pl.ui.screens.wykonajPolaczenie

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
                // REJECT_REASON_DECLINED → DisconnectCause.REJECTED (nie BUSY / disconnect).
                odrzucPrzychodzace(StanPolaczenia.polaczenie)
                StanPolaczenia.polaczenie = null
            }
            "AKCJA_ROZLACZ" -> {
                // Niezawodne rozłączenie (disconnect + fallback endCall).
                rozlaczRozmowe(context, usunLog = false)
            }
            "AKCJA_ODDZWON" -> {
                PowiadomieniaNieodebranych.wyczysc(context)
                val numer = intent.getStringExtra(PowiadomieniaNieodebranych.EXTRA_NUMER).orEmpty()
                if (numer.isNotBlank()) {
                    wykonajPolaczenie(context, numer)
                }
            }
            "AKCJA_ANULUJ_NIEODEBRANE" -> {
                PowiadomieniaNieodebranych.wyczysc(context)
            }
        }
    }
}
