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
import kotlin.coroutines.resume

/**
 * Naprawa / start własnej usługi nagrywania (Wireless ADB → daemon shell).
 *
 * 1. Czekaj na Wi‑Fi
 * 2. Włącz `adb_wifi` + `adb_enabled`
 * 3. Uruchom [ShellAdbBootstrap]
 * 4. Nadaj uprawnienia z UID shell + stabilizuj ADB
 */
object AdbNaprawa {
    private const val TAG = "AdbNaprawa"
    private const val ADB_WIFI = "adb_wifi_enabled"
    private const val ADB_ALLOWED_TIME = "adb_allowed_connection_time"

    data class Wynik(
        val ok: Boolean,
        val komunikat: String,
        val adbWifiWylaczone: Boolean = false,
    )

    suspend fun naprawZPowiadomienia(ctx: Context): Wynik {
        val app = ctx.applicationContext

        PowiadomieniaNagrywania.pokazPostep(app, "Czekam na Wi‑Fi…")
        wlacWifi(app)
        if (!czekajWifi(app, timeoutMs = 25_000L)) {
            return Wynik(
                false,
                "Brak Wi‑Fi. Wireless debugging wymaga sieci " +
                    "(wystarczy hotspot bez internetu).",
            )
        }

        PowiadomieniaNagrywania.pokazPostep(app, "Włączam Wireless debugging…")
        val mialAdbWifi = czyAdbWifi(app)
        val wlaczonoAdb = wlacAdb(app)
        if (!wlaczonoAdb && !mialAdbWifi) {
            Log.w(TAG, "brak WRITE_SECURE_SETTINGS")
            return Wynik(
                false,
                "Brak WRITE_SECURE_SETTINGS — nie włączę Wireless debugging. " +
                    "Raz: adb shell pm grant ${app.packageName} " +
                    "android.permission.WRITE_SECURE_SETTINGS",
            )
        }

        if (!ShellAdbBootstrap.czySparowane(app)) {
            return Wynik(
                false,
                "Nie sparowano jeszcze Wireless debugging. Włącz nagrywanie w ustawieniach i sparuj kodem.",
            )
        }

        PowiadomieniaNagrywania.pokazPostep(app, "Uruchamiam usługę ADB…")
        val wlasne = runCatching { ShellAdbBootstrap.uruchom(app) }.getOrDefault(false)
        if (!wlasne || !ShellKlient.czyShellGotowy()) {
            return Wynik(
                false,
                "Nie udało się uruchomić usługi nagrywania. Sprawdź Wi‑Fi i Wireless debugging.",
            )
        }

        UprawnieniaShell.nadajBrakujace(app)
        UprawnieniaShell.nadajWriteSecure(app)
        OemNagrywanieFlagi.ustawWszystkie(app)

        val tryb = when {
            !czyAdbUsb(app) && !czyAdbWifi(app) -> TrybAdbPoStarcie.ObaWylaczone
            czyAdbWifi(app) && !czyAdbUsb(app) -> TrybAdbPoStarcie.TylkoWireless
            !czyAdbWifi(app) && czyAdbUsb(app) -> TrybAdbPoStarcie.TylkoUsb
            else -> TrybAdbPoStarcie.ObaWlaczone
        }

        return Wynik(
            ok = true,
            komunikat = when (tryb) {
                TrybAdbPoStarcie.ObaWylaczone ->
                    "ADB OK — usługa do rebootu (bez debug)."
                TrybAdbPoStarcie.TylkoWireless ->
                    "ADB OK — Wireless ON, USB możesz wyłączyć."
                TrybAdbPoStarcie.TylkoUsb ->
                    "ADB OK — nie wyłączaj USB debugging do rebootu."
                else -> "ADB OK — obie strony."
            },
            adbWifiWylaczone = tryb == TrybAdbPoStarcie.ObaWylaczone ||
                tryb == TrybAdbPoStarcie.TylkoUsb,
        )
    }

    /** Włącza Wireless + USB debugging (potrzebne do mDNS / adbd). */
    fun wlacAdb(ctx: Context): Boolean {
        if (!maWriteSecure(ctx)) return false
        val cr = ctx.contentResolver
        return runCatching {
            Settings.Global.putInt(cr, ADB_WIFI, 1)
            Settings.Global.putInt(cr, Settings.Global.ADB_ENABLED, 1)
            Settings.Global.putLong(cr, ADB_ALLOWED_TIME, 0L)
            true
        }.getOrDefault(false)
    }

    fun czyAdbUsb(ctx: Context): Boolean =
        runCatching {
            Settings.Global.getInt(ctx.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
        }.getOrDefault(false)

    fun ustawAdbUsb(ctx: Context, on: Boolean): Boolean {
        if (!maWriteSecure(ctx)) return false
        return runCatching {
            Settings.Global.putInt(
                ctx.contentResolver,
                Settings.Global.ADB_ENABLED,
                if (on) 1 else 0,
            )
            true
        }.getOrDefault(false)
    }

    /**
     * Test przeżycia daemona po odcięciu ADB:
     * 1) Wireless OFF (USB ON)
     * 2) USB OFF → detach / restart Wireless-only
     */
    suspend fun ustabilizujPoStarcieDaemona(
        ctx: Context,
        zyje: () -> Boolean,
    ): TrybAdbPoStarcie {
        val app = ctx.applicationContext
        if (!zyje()) return TrybAdbPoStarcie.Martwy
        if (!maWriteSecure(app)) return TrybAdbPoStarcie.BezUprawnien

        val wifiOn = czyAdbWifi(app)
        val usbOn = czyAdbUsb(app)
        when {
            !wifiOn && !usbOn -> {
                Log.i(TAG, "stabilizacja: już Oba OFF")
                return TrybAdbPoStarcie.ObaWylaczone
            }
            wifiOn && !usbOn -> {
                Log.i(TAG, "stabilizacja: już TylkoWireless — zostawiam")
                return TrybAdbPoStarcie.TylkoWireless
            }
        }

        if (wifiOn) {
            Log.i(TAG, "stabilizacja: wyłączam Wireless (USB na razie ON)")
            ustawAdbWifi(app, false)
            delay(1_800)
        }
        if (!zyje()) {
            Log.w(TAG, "stabilizacja: Wireless OFF zabił daemon — przywracam oba")
            wlacAdb(app)
            return TrybAdbPoStarcie.Martwy
        }

        if (czyAdbUsb(app)) {
            Log.i(TAG, "stabilizacja: test USB OFF (Wireless już OFF)")
            ustawAdbUsb(app, false)
            delay(1_800)
        }
        if (zyje()) {
            Log.i(TAG, "stabilizacja: Oba OFF — detach do rebootu OK")
            return TrybAdbPoStarcie.ObaWylaczone
        }

        Log.w(TAG, "stabilizacja: USB OFF zabił daemon → restart w trybie Wireless")
        return TrybAdbPoStarcie.WymagaRestartuTylkoWireless
    }

    enum class TrybAdbPoStarcie {
        ObaWylaczone,
        TylkoWireless,
        TylkoUsb,
        ObaWlaczone,
        WymagaRestartuTylkoWireless,
        BezUprawnien,
        Martwy,
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
