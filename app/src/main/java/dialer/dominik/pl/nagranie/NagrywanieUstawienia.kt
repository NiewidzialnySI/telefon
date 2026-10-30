package dialer.dominik.pl.nagranie

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Preferencje nagrywania rozmów (SharedPreferences). */
object NagrywanieUstawienia {
    private const val PREFS = "nagrywanie_rozmow"
    private const val K_WLACZONE = "wlaczone"
    private const val K_AUTO = "auto"
    private const val K_ZGODA_PRAWNA = "zgoda_prawna"
    private const val K_POWIADOMIENIA_SHIZUKU = "powiadomienia_shizuku"

    private val _wlaczoneUi = MutableStateFlow(false)
    /** Do UI (pasek zakładek) — odświeżane przez [odswiezUi] / [ustawWlaczone]. */
    val wlaczoneUi: StateFlow<Boolean> = _wlaczoneUi.asStateFlow()

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun odswiezUi(ctx: Context) {
        _wlaczoneUi.value = czyWlaczone(ctx)
    }

    fun czyWlaczone(ctx: Context): Boolean = prefs(ctx).getBoolean(K_WLACZONE, false)

    fun ustawWlaczone(ctx: Context, v: Boolean) {
        prefs(ctx).edit().putBoolean(K_WLACZONE, v).apply()
        _wlaczoneUi.value = v
    }

    /** Automatycznie startuj przy ACTIVE (wymaga wlaczone + Shizuku). */
    fun czyAuto(ctx: Context): Boolean = prefs(ctx).getBoolean(K_AUTO, false)

    fun ustawAuto(ctx: Context, v: Boolean) {
        prefs(ctx).edit().putBoolean(K_AUTO, v).apply()
    }

    fun czyZgodaPrawna(ctx: Context): Boolean = prefs(ctx).getBoolean(K_ZGODA_PRAWNA, false)

    fun ustawZgodaPrawna(ctx: Context, v: Boolean) {
        prefs(ctx).edit().putBoolean(K_ZGODA_PRAWNA, v).apply()
    }

    /** Powiadom gdy Shizuku nie działa (klik → auto-naprawa Wi‑Fi/ADB). */
    fun czyPowiadomieniaShizuku(ctx: Context): Boolean =
        prefs(ctx).getBoolean(K_POWIADOMIENIA_SHIZUKU, false)

    fun ustawPowiadomieniaShizuku(ctx: Context, v: Boolean) {
        prefs(ctx).edit().putBoolean(K_POWIADOMIENIA_SHIZUKU, v).apply()
    }
}
