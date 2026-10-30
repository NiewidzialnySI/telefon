package dialer.dominik.pl.nagranie

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.provider.Settings
import android.util.Log
import dialer.dominik.pl.nagranie.adb.ShellAdbBootstrap
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume

/**
 * Naprawa jak oficjalne Shizuku 13.6+ / thedjchi (BootCompleteReceiver / AdbStartService):
 *
 * 1. Czekaj na Wi‑Fi (NetworkCallback — nie „ślepe” delay)
 * 2. `adb_wifi_enabled=1`, `adb_enabled=1`, `adb_allowed_connection_time=0`
 * 3. Shizuku (Start on boot / otwarcie) podnosi usługę przez **swój** klucz ADB + mDNS
 * 4. Bind UserService + pm grant (+ WRITE_SECURE na przyszłość)
 * 5. Jak thedjchi: spróbuj `adb_wifi=0`; jeśli binder padnie → włącz z powrotem
 *
 * Nie da się „lepiej niż Shizuku” bez własnego libadb (CallVault): klucz SPAKE2
 * jest w keystore Shizuku — my tylko przygotowujemy warunki i podpinamy się pod binder.
 */
object ShizukuAdbNaprawa {
    private const val TAG = "ShizukuAdbNaprawa"
    private const val ADB_WIFI = "adb_wifi_enabled"
    private const val ADB_ALLOWED_TIME = "adb_allowed_connection_time"

    data class Wynik(
        val ok: Boolean,
        val komunikat: String,
        val adbWifiWylaczone: Boolean = false,
    )

    suspend fun naprawZPowiadomienia(ctx: Context): Wynik {
        val app = ctx.applicationContext

        PowiadomieniaShizuku.pokazPostep(app, "Czekam na Wi‑Fi…")
        wlacWifi(app)
        if (!czekajWifi(app, timeoutMs = 25_000L)) {
            return Wynik(
                false,
                "Brak Wi‑Fi. Wireless debugging / auto-start Shizuku wymaga sieci " +
                    "(wystarczy hotspot bez internetu).",
            )
        }

        PowiadomieniaShizuku.pokazPostep(app, "Włączam ADB jak Shizuku…")
        val mialAdbWifi = czyAdbWifi(app)
        val wlaczonoAdb = wlacAdbJakShizuku(app)
        if (!wlaczonoAdb && !mialAdbWifi) {
            Log.w(TAG, "brak WRITE_SECURE_SETTINGS")
            return Wynik(
                false,
                "Brak WRITE_SECURE_SETTINGS — nie włączę Wireless debugging. " +
                    "Raz: adb shell pm grant ${app.packageName} " +
                    "android.permission.WRITE_SECURE_SETTINGS " +
                    "(albo włącz Shizuku ręcznie; potem nadamy to sami).",
            )
        }

        // Shizuku BootComplete / AdbStartService: mDNS ~ kilka sekund po adb_wifi.
        // Najpierw własna usługa ADB (bez Shizuku), jeśli już sparowana.
        if (ShellAdbBootstrap.czySparowane(app)) {
            PowiadomieniaShizuku.pokazPostep(app, "Uruchamiam własną usługę ADB…")
            val wlasne = runCatching { ShellAdbBootstrap.uruchom(app) }.getOrDefault(false)
            if (wlasne && ShellKlient.czyShellGotowy()) {
                UprawnieniaShizuku.nadajBrakujace(app)
                UprawnieniaShizuku.nadajWriteSecure(app)
                OemNagrywanieFlagi.ustawWszystkie(app)
                return Wynik(true, "Własne ADB OK — obie strony (bez Shizuku).", false)
            }
        }

        PowiadomieniaShizuku.pokazPostep(app, "Czekam na auto-start Shizuku…")
        ShizukuKlient.dolacz(app)
        var podniesione = czekajBinder(app, 8_000L)

        if (!podniesione) {
            PowiadomieniaShizuku.pokazPostep(app, "Otwieram Shizuku (Start)…")
            ShizukuKlient.otworzAplikacje(app)
            podniesione = czekajBinder(app, 25_000L)
        }

        if (!podniesione) {
            return Wynik(
                false,
                "Shizuku nie wystartowało. W Shizuku: Start on boot + zaufane Wi‑Fi " +
                    "+ Disable permission monitoring (ColorOS). Potem znów „Napraw”.",
            )
        }

        PowiadomieniaShizuku.pokazPostep(app, "Łączę i nadaję uprawnienia…")
        val ok = when (ShizukuKlient.stan) {
            ShizukuStan.BrakUprawnienia -> {
                val zgoda = ShizukuKlient.poprosUprawnienie()
                if (zgoda) ShizukuKlient.sprobujPolaczyc(app, 8_000) else false
            }
            else -> {
                ShizukuKlient.sprobujPolaczyc(app, 8_000) ||
                    ShizukuKlient.napraw(app)
            }
        }

        if (!ok || !ShizukuKlient.czyShellGotowy()) {
            return Wynik(
                false,
                "Binder Shizuku jest, ale UserService niegotowy (${ShizukuKlient.krotkiOpis()}).",
            )
        }

        UprawnieniaShizuku.nadajBrakujace(app)
        UprawnieniaShizuku.nadajWriteSecure(app)
        // Flagi OEM (na wypadek równoległego dialera Oplus) — nieszkodliwe dla nas.
        OemNagrywanieFlagi.ustawWszystkie(app)

        // thedjchi: po udanym starcie wyłącza adb_wifi (sesja Shizuku już żyje).
        var wylaczone = false
        PowiadomieniaShizuku.pokazPostep(app, "Wyłączam ADB Wi‑Fi (test jak Shizuku)…")
        ustawAdbWifi(app, false)
        delay(2_000)
        ShizukuKlient.odswiez(force = true)
        if (Shizuku.pingBinder() && ShizukuKlient.czyShellGotowy()) {
            wylaczone = true
            Log.i(TAG, "adb_wifi OFF — Shizuku żyje (jak thedjchi)")
        } else {
            Log.w(TAG, "adb_wifi OFF zabił binder — przywracam")
            wlacAdbJakShizuku(app)
            delay(1_500)
            ShizukuKlient.odswiez(force = true)
            ShizukuKlient.sprobujPolaczyc(app, 6_000)
            wylaczone = false
        }

        val gotowe = ShizukuKlient.czyShellGotowy()
        return Wynik(
            ok = gotowe,
            komunikat = when {
                !gotowe -> "Połączenie utracone po przełączeniu ADB Wi‑Fi."
                wylaczone ->
                    "Shizuku OK (jak oficjalny start). ADB Wi‑Fi off — usługa do restartu."
                else ->
                    "Shizuku OK. Na tym ROM-ie zostaw Wireless debugging włączone."
            },
            adbWifiWylaczone = wylaczone,
        )
    }

    /** Identycznie jak BootCompleteReceiver.adbStart w Shizuku. */
    fun wlacAdbJakShizuku(ctx: Context): Boolean {
        if (!maWriteSecure(ctx)) return false
        val cr = ctx.contentResolver
        return runCatching {
            Settings.Global.putInt(cr, ADB_WIFI, 1)
            Settings.Global.putInt(cr, Settings.Global.ADB_ENABLED, 1)
            Settings.Global.putLong(cr, ADB_ALLOWED_TIME, 0L)
            true
        }.getOrDefault(false)
    }

    private suspend fun czekajWifi(ctx: Context, timeoutMs: Long): Boolean {
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return false
        val aktywna = cm.getNetworkCapabilities(cm.activeNetwork)
        if (aktywna?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) return true

        val wynik = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val req = NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .build()
                val cb = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        if (cont.isActive) cont.resume(true)
                        runCatching { cm.unregisterNetworkCallback(this) }
                    }
                }
                runCatching { cm.registerNetworkCallback(req, cb) }
                    .onFailure {
                        if (cont.isActive) cont.resume(false)
                    }
                cont.invokeOnCancellation {
                    runCatching { cm.unregisterNetworkCallback(cb) }
                }
            }
        }
        return wynik == true
    }

    private suspend fun czekajBinder(ctx: Context, timeoutMs: Long): Boolean {
        val start = android.os.SystemClock.elapsedRealtime()
        while (android.os.SystemClock.elapsedRealtime() - start < timeoutMs) {
            ShizukuKlient.dolacz(ctx)
            ShizukuKlient.odswiez(force = true)
            if (Shizuku.pingBinder()) return true
            delay(400)
        }
        return Shizuku.pingBinder()
    }

    private fun wlacWifi(ctx: Context) {
        val wm = ctx.applicationContext.getSystemService(WifiManager::class.java) ?: return
        @Suppress("DEPRECATION")
        if (!wm.isWifiEnabled) {
            val ok = runCatching {
                @Suppress("DEPRECATION")
                wm.isWifiEnabled = true
            }.isSuccess
            if (!ok || !wm.isWifiEnabled) {
                runCatching {
                    ctx.startActivity(
                        Intent(Settings.Panel.ACTION_WIFI)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
        }
    }

    fun czyAdbWifi(ctx: Context): Boolean =
        runCatching {
            Settings.Global.getInt(ctx.contentResolver, ADB_WIFI, 0) == 1
        }.getOrDefault(false)

    fun ustawAdbWifi(ctx: Context, on: Boolean): Boolean {
        if (!maWriteSecure(ctx)) return false
        return runCatching {
            Settings.Global.putInt(ctx.contentResolver, ADB_WIFI, if (on) 1 else 0)
        }.getOrDefault(false)
    }

    fun maWriteSecure(ctx: Context): Boolean =
        ctx.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
}
