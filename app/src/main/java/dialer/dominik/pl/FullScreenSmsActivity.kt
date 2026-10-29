package dialer.dominik.pl

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper

/**
 * Wybudza ekran przy SMS od ZAPISANEGO kontaktu i pozwala systemowi pokazać
 * heads-up (baner z treścią) na pełnym ekranie lock screen — jak dzwonek.
 * Ta aktywność jest tylko "trampoliną": po wybudzeniu wraca (finish), a baner
 * heads-up przejmuje ekran (klik w niego otwiera czat przez setContentIntent).
 */
class FullScreenSmsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Ciemne tło AMOLED spójne z aplikacją, żeby ekran nie błysnął na biało.
        window.setBackgroundDrawableResource(android.R.color.black)
        // Po wybudzeniu zwolnij aktywność — heads-up (baner powiadomienia) wyświetla
        // się nad ekranem i czeka na klika, a nie zasłania go pusta pełnoekranowa Activity.
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isFinishing) finish()
        }, 600)
    }
}