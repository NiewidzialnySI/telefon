package dialer.dominik.pl.nagranie

import android.content.Context
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
        if (!zapewnijWifi(app, timeoutMs = 25_000L)) {
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
                    "ADB OK — Wireless możesz wyłączyć; nie wyłączaj USB debugging."
                else -> "ADB OK — obie strony."
            },
            adbWifiWylaczone = tryb == TrybAdbPoStarcie.ObaWylaczone ||
                tryb == TrybAdbPoStarcie.TylkoUsb,
        )
    }

    /** Włącza Wireless + USB debugging (potrzebne do mDNS / adbd). */
    fun wlacAdb(ctx: Context): Boolean {
        if (!maWriteSecure(ctx)) return false
        // Stack: kto włącza ADB (Wireless+USB) — do śledzenia niechcianego auto-ON.
        Log.w(TAG, "wlacAdb(WIFI+USB) wywołane", Throwable("wlacAdb-caller"))
        val cr = ctx.contentResolver
        val ok = runCatching {
            Settings.Global.putInt(cr, ADB_WIFI, 1)
            Settings.Global.putInt(cr, Settings.Global.ADB_ENABLED, 1)
            Settings.Global.putLong(cr, ADB_ALLOWED_TIME, 0L)
            true
        }.getOrDefault(false)
        if (ok) {
            // Baner systemowy ADB potrafi zabić CallStyle — kasuj od razu.
            dialer.dominik.pl.nagranie.adb.AdbPowiadomienieListener.poWlaczeniuAdb(ctx)
        }
        return ok
    }

    fun czyAdbUsb(ctx: Context): Boolean =
        runCatching {
            Settings.Global.getInt(ctx.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
        }.getOrDefault(false)

    fun ustawAdbUsb(ctx: Context, on: Boolean): Boolean {
        if (!maWriteSecure(ctx)) return false
        Log.w(TAG, "ustawAdbUsb($on)", Throwable("ustawAdbUsb-caller"))
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
     * Po starcie daemona — tryb bez Portalu Firmy: **Wireless OFF + USB ON**.
     *
     * Na ColorOS wyłączenie obu kanałów zabija shell/daemon. USB debugging
     * utrzymuje usługę bez Wi‑Fi (nie trzeba ciągle włączać sieci).
     * Oba OFF robi wyłącznie tryb Portal Firmy ([wylaczDlaFirmowki]).
     */
    suspend fun ustabilizujPoStarcieDaemona(
        ctx: Context,
        zyje: () -> Boolean,
    ): TrybAdbPoStarcie {
        val app = ctx.applicationContext
        if (!zyje()) return TrybAdbPoStarcie.Martwy
        if (!maWriteSecure(app)) return TrybAdbPoStarcie.BezUprawnien

        if (!czyAdbWifi(app) && !czyAdbUsb(app)) {
            // Już oba OFF — w trybie bez firmówki włącz USB, żeby daemon przeżył.
            Log.i(TAG, "stabilizacja: Oba OFF → włączam USB (keepalive)")
            ustawAdbUsb(app, true)
            delay(1_200)
            return if (zyje()) TrybAdbPoStarcie.TylkoUsb else TrybAdbPoStarcie.Martwy
        }

        if (!czyAdbUsb(app)) {
            Log.i(TAG, "stabilizacja: włączam USB (podpora daemona)")
            ustawAdbUsb(app, true)
            delay(1_200)
            if (!zyje()) {
                Log.w(TAG, "stabilizacja: pad po włączeniu USB")
                wlacAdb(app)
                return TrybAdbPoStarcie.Martwy
            }
        }

        if (czyAdbWifi(app)) {
            Log.i(TAG, "stabilizacja: wyłączam Wireless (USB zostaje ON)")
            ustawAdbWifi(app, false)
            delay(1_800)
        }
        if (!zyje()) {
            Log.w(TAG, "stabilizacja: Wireless OFF zabił daemon — przywracam oba")
            wlacAdb(app)
            return TrybAdbPoStarcie.Martwy
        }

        Log.i(TAG, "stabilizacja: TylkoUsb — nie gaszę USB (daemon / bez Wi‑Fi)")
        return TrybAdbPoStarcie.TylkoUsb
    }

    enum class TrybAdbPoStarcie {
        ObaWylaczone,
        TylkoWireless,
        TylkoUsb,
        ObaWlaczone,
        /** Oba OFF zabija — wznów z USB ON / Wireless OFF. */
        WymagaRestartuTylkoUsb,
        BezUprawnien,
        Martwy,
    }

    /** Włącz Wi‑Fi; bez shella → a11y klika switch w ustawieniach (nie panel „włącz sam”). */
    suspend fun zapewnijWifi(ctx: Context, timeoutMs: Long = 20_000L): Boolean {
        val app = ctx.applicationContext
        if (czyWifi(app) || czyWifiManagerOn(app)) return true
        wlacWifi(app)
        delay(500)
        if (czyWifi(app) || czyWifiManagerOn(app)) return true
        wlacWifi(app)
        if (czekajWifi(app, 2_500L)) return true
        // ColorOS ignoruje wifi_on / setWifiEnabled — klik przez dostępność.
        Log.i(TAG, "zapewnijWifi: fallback a11y")
        return wlacWifiPrzezA11y(app, timeoutMs = timeoutMs.coerceAtLeast(8_000L))
    }

    /** Sieć Wi‑Fi aktywna (połączona). */
    fun czyWifi(ctx: Context): Boolean {
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    /** Radio Wi‑Fi włączone (nawet bez asocjacji z AP). */
    fun czyWifiManagerOn(ctx: Context): Boolean {
        val wm = ctx.applicationContext.getSystemService(WifiManager::class.java) ?: return false
        @Suppress("DEPRECATION")
        return wm.isWifiEnabled
    }

    private suspend fun wlacWifiPrzezA11y(ctx: Context, timeoutMs: Long): Boolean {
        val wynik = withTimeoutOrNull(timeoutMs + 2_000L) {
            suspendCancellableCoroutine { cont ->
                dialer.dominik.pl.nagranie.adb.AdbParowanieDostepnosc.poprosWlacWifi(
                    ctx,
                    timeoutMs = timeoutMs,
                ) { ok ->
                    if (cont.isActive) cont.resume(ok)
                }
            }
        }
        return wynik == true || czyWifi(ctx) || czyWifiManagerOn(ctx)
    }

    suspend fun czekajWifi(ctx: Context, timeoutMs: Long): Boolean {
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

    /**
     * Włącza Wi‑Fi **w miejscu** (bez panelu ustawień).
     * 1) WifiManager  2) Global `wifi_on` (WRITE_SECURE)  3) shell `svc wifi`
     * (daemon na USB ADB działa bez Wi‑Fi).
     */
    fun wlacWifi(ctx: Context) {
        val app = ctx.applicationContext
        val wm = app.getSystemService(WifiManager::class.java) ?: return
        @Suppress("DEPRECATION")
        if (wm.isWifiEnabled) return

        @Suppress("DEPRECATION")
        runCatching { wm.isWifiEnabled = true }
            .onFailure { Log.w(TAG, "setWifiEnabled", it) }
        @Suppress("DEPRECATION")
        if (wm.isWifiEnabled) {
            Log.i(TAG, "wlacWifi: WifiManager OK")
            return
        }

        if (maWriteSecure(app)) {
            val cr = app.contentResolver
            val ok = runCatching {
                Settings.Global.putInt(cr, "wifi_on", 1)
                runCatching { Settings.Global.putInt(cr, Settings.Global.WIFI_ON, 1) }
                true
            }.getOrDefault(false)
            Log.i(TAG, "wlacWifi: wifi_on put=$ok")
            @Suppress("DEPRECATION")
            runCatching { wm.isWifiEnabled = true }
            @Suppress("DEPRECATION")
            if (wm.isWifiEnabled) {
                Log.i(TAG, "wlacWifi: OK po wifi_on")
                return
            }
        }

        // UID shell — niezawodne gdy daemon już żyje (USB debugging).
        val shell = ShellKlient.usluga()
        if (shell != null) {
            val ok = runCatching { shell.ustawWifi(true) }.getOrDefault(false)
            Log.i(TAG, "wlacWifi: shell svc wifi enable → $ok")
        } else {
            Log.w(TAG, "wlacWifi: brak shell — a11y w zapewnijWifi()")
        }
    }

    fun czyAdbWifi(ctx: Context): Boolean =
        runCatching {
            Settings.Global.getInt(ctx.contentResolver, ADB_WIFI, 0) == 1
        }.getOrDefault(false)

    fun ustawAdbWifi(ctx: Context, on: Boolean): Boolean {
        if (!maWriteSecure(ctx)) return false
        Log.w(TAG, "ustawAdbWifi($on)", Throwable("ustawAdbWifi-caller"))
        return runCatching {
            Settings.Global.putInt(ctx.contentResolver, ADB_WIFI, if (on) 1 else 0)
        }.getOrDefault(false)
    }

    /** Oba kanały OFF — pod aplikacje firmowe / bankowe. Zabija daemon shell. */
    fun wylaczObaAdb(ctx: Context): Boolean {
        if (!maWriteSecure(ctx)) return false
        Log.w(TAG, "wylaczObaAdb", Throwable("wylaczObaAdb-caller"))
        return runCatching {
            val cr = ctx.contentResolver
            Settings.Global.putInt(cr, ADB_WIFI, 0)
            Settings.Global.putInt(cr, Settings.Global.ADB_ENABLED, 0)
            true
        }.getOrDefault(false)
    }

    /**
     * Stan pod Portal Firmy: oba ADB OFF.
     * (Portal czepia się ADB, nie Opcji programisty — tych nie ruszamy.)
     */
    fun wylaczDlaFirmowki(ctx: Context): Boolean = wylaczObaAdb(ctx)

    /** Przygotuj ADB przed rozmową (tryb firmowy). */
    fun wlacDlaNagrywania(ctx: Context): Boolean = wlacAdb(ctx)

    fun maWriteSecure(ctx: Context): Boolean =
        ctx.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
}
