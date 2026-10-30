package dialer.dominik.pl.nagranie

import android.os.Build
import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * Odblokowuje refleksję na ukrytych API frameworka w procesie shell
 * (ActivityThread, AttributionSource itd.). Idempotentne.
 */
internal object UkryteApi {
    @Volatile
    private var wlaczono = false

    fun wlacz() {
        if (wlaczono) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            HiddenApiBypass.addHiddenApiExemptions(
                "Landroid/app/",
                "Landroid/media/",
                "Landroid/content/",
                "Landroid/os/",
            )
        }
        wlaczono = true
    }
}
