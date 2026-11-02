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
    /** Legacy key — wcześniej „powiadomienia_shizuku”. */
    private const val K_POWIADOMIENIA = "powiadomienia_shizuku"
    /**
     * ADB (Wireless+USB) tylko na czas rozmowy — między połączeniami oba OFF
     * (aplikacje firmowe / bankowe). Daemon startuje przy dzwonku, ginie po rozłączeniu.
     */
    private const val K_ADB_TYLKO_ROZMOWA = "adb_tylko_na_rozmowe"
    /** Synchronizuj tryb Portal Firmy ze stanem systemowego work profile. */
    private const val K_PORTAL_FIRMY_AUTO = "portal_firmy_auto"

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

    /** Automatycznie startuj przy ACTIVE (wymaga wlaczone + usługa ADB). */
    fun czyAuto(ctx: Context): Boolean = prefs(ctx).getBoolean(K_AUTO, false)

    fun ustawAuto(ctx: Context, v: Boolean) {
        prefs(ctx).edit().putBoolean(K_AUTO, v).apply()
    }

    fun czyZgodaPrawna(ctx: Context): Boolean = prefs(ctx).getBoolean(K_ZGODA_PRAWNA, false)

    fun ustawZgodaPrawna(ctx: Context, v: Boolean) {
        prefs(ctx).edit().putBoolean(K_ZGODA_PRAWNA, v).apply()
    }

    /** Powiadom gdy usługa ADB nie działa (klik → auto-naprawa Wi‑Fi/Wireless). */
    fun czyPowiadomieniaNagrywania(ctx: Context): Boolean =
        prefs(ctx).getBoolean(K_POWIADOMIENIA, false)

    fun ustawPowiadomieniaNagrywania(ctx: Context, v: Boolean) {
        prefs(ctx).edit().putBoolean(K_POWIADOMIENIA, v).apply()
    }

    /** Domyślnie ON — aplikacje firmowe wymagają wyłączonego debugowania. */
    fun czyAdbTylkoNaRozmowe(ctx: Context): Boolean =
        prefs(ctx).getBoolean(K_ADB_TYLKO_ROZMOWA, true)

    fun ustawAdbTylkoNaRozmowe(ctx: Context, v: Boolean) {
        prefs(ctx).edit().putBoolean(K_ADB_TYLKO_ROZMOWA, v).apply()
    }

    fun czyPortalFirmyAuto(ctx: Context): Boolean =
        prefs(ctx).getBoolean(K_PORTAL_FIRMY_AUTO, false)

    fun ustawPortalFirmyAuto(ctx: Context, v: Boolean) {
        prefs(ctx).edit().putBoolean(K_PORTAL_FIRMY_AUTO, v).apply()
    }
}
