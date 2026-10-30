package dialer.dominik.pl.nagranie

import android.content.Context
import android.provider.Settings
import android.util.Log

/**
 * Flagi z chińskich / XDA guide’ów (ColorOS / realme / OnePlus):
 * włączają **natywny** przycisk nagrywania w dialerze Oplus/ODialer/InCallUI.
 *
 * **Nie da się** „przelać” CAPTURE_AUDIO_OUTPUT z `com.oplus.dialer` do naszej apki —
 * to permission signature|privileged tylko dla aplikacji systemowej.
 * Flagi są przydatne gdy ktoś równolegle używa OEM dialera albo testuje sideload
 * Contacts/InCallUI z regionu IN (Telegram @realmesystemapplication, V2EX, XDA).
 *
 * Trwałość: Settings.Global **przetrwa reboot** (w przeciwieństwie do sesji ADB).
 */
object OemNagrywanieFlagi {
    private const val TAG = "OemNagrywanieFlagi"

    private val FLAGI = listOf(
        "op_voice_recording_supported_by_mcc" to 1,
        "oplus_call_recording_auto" to 1,
        "oplus_customize_all_call_audio_record" to 1,
        "oplus_customize_all_stranger_call_audio_record" to 1,
    )

    fun ustawWszystkie(ctx: Context): Int {
        if (!ShizukuAdbNaprawa.maWriteSecure(ctx) &&
            ctx.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            // Bez WRITE_SECURE i tak spróbuj — czasem Settings.Global.putInt przejdzie
            // tylko z tym permission.
        }
        var n = 0
        val cr = ctx.contentResolver
        for ((k, v) in FLAGI) {
            val ok = runCatching {
                Settings.Global.putInt(cr, k, v)
            }.isSuccess
            if (ok) {
                n++
                Log.i(TAG, "$k=$v")
            } else {
                Log.w(TAG, "fail $k")
            }
        }
        return n
    }
}
