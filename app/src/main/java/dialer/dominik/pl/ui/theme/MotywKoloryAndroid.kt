package dialer.dominik.pl.ui.theme

import android.content.Context
import androidx.compose.ui.graphics.toArgb

/** Kolory motywu poza Compose (powiadomienia, View). */
object MotywKoloryAndroid {

    fun akcentArgb(context: Context): Int {
        MotywStan.zapewnijWczytane(context)
        val stan = MotywStan.stan.value
        val kolor = stan.kolorPrzycisku ?: stan.seed
        return kolor.toArgb()
    }
}
