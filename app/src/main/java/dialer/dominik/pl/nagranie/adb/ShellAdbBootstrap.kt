package dialer.dominik.pl.nagranie.adb

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import dialer.dominik.pl.nagranie.INagrywanieShell
import dialer.dominik.pl.nagranie.NagrywanieUstawienia
import dialer.dominik.pl.nagranie.ShellKlient
import dialer.dominik.pl.nagranie.ShizukuAdbNaprawa
import dialer.dominik.pl.nagranie.ShizukuStraznik
import io.github.muntashirakon.adb.AdbPairingRequiredException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume

/**
 * Parowanie Wireless debugging → connectTls → app_process [ShellAdbMain] → binder.
 * Daemon w tle (`&`) — może przeżyć wyłączenie adb_wifi po udanym starcie.
 */
object ShellAdbBootstrap {
    private const val TAG = "ShellAdbBootstrap"
    private const val PREFS = "adb_shell_nagranie"
    private const val K_TOKEN = "token"
    private const val K_PAIRED = "paired"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val binderRef = AtomicReference<INagrywanieShell?>(null)
    private val restartWToku = AtomicBoolean(false)
    /** Wielu oczekujących (uruchom + republish + ponowPoUtracie) — jeden slot gubił callback. */
    private val czekajacy = java.util.concurrent.CopyOnWriteArrayList<(INagrywanieShell) -> Unit>()
    @Volatile private var appCtx: Context? = null
    private val deathRecipient = IBinder.DeathRecipient {
        Log.w(TAG, "binder telefon_nagranie martwy")
        binderRef.set(null)
        val ctx = appCtx ?: return@DeathRecipient
        if (!NagrywanieUstawienia.czyWlaczone(ctx)) return@DeathRecipient
        // Dopiero PO utracie — nie wcześniej.
        ponowPoUtracie(ctx)
    }

    data class Diagnostyka(
        val wifi: Boolean,
        val adbWifi: Boolean,
        val writeSecure: Boolean,
        val prefsSparowane: Boolean,
        val uslugaZyje: Boolean,
    ) {
        fun linie(): String = buildString {
            append(if (wifi) "✓ Wi‑Fi" else "✗ Brak Wi‑Fi (wymagane)")
            append('\n')
            append(
                when {
                    adbWifi -> "✓ Wireless debugging włączone"
                    writeSecure -> "✗ Wireless debugging wyłączone (mogę włączyć)"
                    else -> "✗ Wireless debugging wyłączone (brak WRITE_SECURE)"
                },
            )
            append('\n')
            append(
                when {
                    uslugaZyje -> "✓ Usługa shell działa"
                    prefsSparowane -> "○ Sparowane (prefs) — usługa nie działa"
                    else -> "✗ Nie sparowano (potrzeba kodu)"
                },
            )
        }

        fun krotkiStatus(): String = when {
            uslugaZyje -> "ADB OK — obie strony"
            !wifi -> "Włącz Wi‑Fi (lub hotspot)"
            !adbWifi && !prefsSparowane -> "Włącz Wireless debugging i sparuj"
            !adbWifi -> "Wireless debugging wyłączone — uruchom włączy / otwórz ustawienia"
            !prefsSparowane -> "Wireless OK — kliknij Start i sparuj kodem"
            else -> "Sparowane — uruchom usługę"
        }
    }

    sealed class WynikUruchom {
        data object Ok : WynikUruchom()
        data class Blad(
            val komunikat: String,
            val wymagaParowania: Boolean = false,
            val otworzUstawienia: Boolean = false,
        ) : WynikUruchom()
    }

    fun usluga(): INagrywanieShell? = binderRef.get()?.takeIf {
        runCatching { it.wersja; true }.getOrDefault(false)
    }

    fun czySparowane(ctx: Context): Boolean =
        prefs(ctx).getBoolean(K_PAIRED, false)

    fun oznaczSparowane(ctx: Context, v: Boolean) {
        prefs(ctx).edit().putBoolean(K_PAIRED, v).apply()
    }

    fun oczekiwanyToken(ctx: Context): String? =
        prefs(ctx).getString(K_TOKEN, null)

    fun przyjmijBinder(svc: INagrywanieShell) {
        val poprzedni = binderRef.getAndSet(svc)
        runCatching { poprzedni?.asBinder()?.unlinkToDeath(deathRecipient, 0) }
        runCatching { svc.asBinder().linkToDeath(deathRecipient, 0) }
            .onFailure { Log.w(TAG, "linkToDeath", it) }
        val waiters = ArrayList(czekajacy)
        czekajacy.clear()
        for (cb in waiters) {
            runCatching { cb(svc) }
        }
    }

    /** Po śmierci bindera — najpierw czekaj na republish, adb_wifi tylko gdy trzeba. */
    private fun ponowPoUtracie(ctx: Context) {
        if (!restartWToku.compareAndSet(false, true)) return
        scope.launch {
            try {
                delay(800)
                if (usluga() != null) return@launch
                Log.i(TAG, "binder padł — czekam na republish…")
                if (czekajNaIstniejacyBinder(2_500L)) {
                    Log.i(TAG, "daemon wrócił bez adb_wifi")
                    return@launch
                }
                Log.i(TAG, "ponawiam usługę (adb_wifi tylko jeśli konieczne)")
                val ok = ShellKlient.zapewnij(ctx)
                Log.i(TAG, "ponowienie → $ok")
                if (!ok) ShizukuStraznik.naBinderMartwy(ctx)
            } finally {
                restartWToku.set(false)
            }
        }
    }

    /**
     * Jak Shizuku/thedjchi: po żywym daemonie wyłącz Wireless debugging.
     * Jeśli binder padnie — przywróć adb_wifi.
     */
    suspend fun sprobujWylaczycAdbWifiPoStarcie(ctx: Context): Boolean {
        val app = ctx.applicationContext
        if (usluga() == null) return false
        if (!ShizukuAdbNaprawa.maWriteSecure(app)) return false
        if (!ShizukuAdbNaprawa.czyAdbWifi(app)) return true
        // Odłącz keepalive ADB — daemon jest w tle (`&`), nie potrzebuje shell stream.
        AdbPolaczenie.ustawKeepalive(null)
        delay(400)
        Log.i(TAG, "wyłączam adb_wifi (daemon powinien żyć samodzielnie)")
        ShizukuAdbNaprawa.ustawAdbWifi(app, false)
        delay(1_800)
        val zyje = usluga() != null
        if (zyje) {
            Log.i(TAG, "adb_wifi OFF — usługa nadal OK")
            return true
        }
        Log.w(TAG, "adb_wifi OFF zabił daemon — przywracam")
        ShizukuAdbNaprawa.wlacAdbJakShizuku(app)
        delay(1_500)
        runCatching { uruchom(app) }
        return false
    }

    fun zdiagnozuj(ctx: Context): Diagnostyka {
        val app = ctx.applicationContext
        return Diagnostyka(
            wifi = czyWifi(app),
            adbWifi = ShizukuAdbNaprawa.czyAdbWifi(app),
            writeSecure = ShizukuAdbNaprawa.maWriteSecure(app),
            prefsSparowane = czySparowane(app),
            uslugaZyje = usluga() != null,
        )
    }

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun nowyToken(ctx: Context): String {
        val t = UUID.randomUUID().toString().replace("-", "")
        // commit — ContentProvider w tym samym procesie musi od razu widzieć token
        prefs(ctx).edit().putString(K_TOKEN, t).commit()
        return t
    }

    private fun czyWifi(ctx: Context): Boolean {
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    /**
     * Pairing: użytkownik podaje kod z „Paruj urządzenie z kodem”.
     * Host/port z mDNS albo ręcznie (127.0.0.1 + port z UI).
     */
    suspend fun sparuj(
        ctx: Context,
        host: String,
        port: Int,
        kod: String,
    ): Result<Unit> = runCatching {
        val adb = AdbPolaczenie.get(ctx)
        // Shizuku: najpierw 127.0.0.1 + port z mDNS.
        // ColorOS/Realme: czasem tylko IP z dialogu (Wi‑Fi / RNDIS) — stąd fallbacki.
        val hosts = linkedSetOf<String>()
        hosts += "127.0.0.1"
        if (host.isNotBlank() && host != "::" && host != "127.0.0.1") hosts += host
        hosts += lokalneIpv4()
        var last: Throwable? = null
        for (h in hosts) {
            try {
                Log.i(TAG, "pair próba $h:$port")
                adb.pair(h, port, kod)
                oznaczSparowane(ctx, true)
                Log.i(TAG, "sparowano $h:$port")
                return@runCatching
            } catch (t: Throwable) {
                last = t
                Log.w(TAG, "pair fail $h:$port — ${t.message}")
            }
        }
        throw last ?: Exception("pair failed")
    }

    private fun lokalneIpv4(): List<String> = buildList {
        runCatching {
            java.util.Collections.list(java.net.NetworkInterface.getNetworkInterfaces()).forEach { nif ->
                if (!nif.isUp || nif.isLoopback) return@forEach
                java.util.Collections.list(nif.inetAddresses).forEach { addr ->
                    if (addr is java.net.Inet4Address && !addr.isLoopbackAddress) {
                        add(addr.hostAddress ?: return@forEach)
                    }
                }
            }
        }
    }

    /**
     * Czeka na republish żyjącego daemona — **bez** włączania adb_wifi.
     * Token w prefs musi być ten sam, co przy starcie daemona.
     */
    suspend fun czekajNaIstniejacyBinder(timeoutMs: Long = 2_800L): Boolean {
        if (usluga() != null) return true
        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val juz = usluga()
                if (juz != null) {
                    cont.resume(true)
                    return@suspendCancellableCoroutine
                }
                val cb: (INagrywanieShell) -> Unit = { _ ->
                    if (cont.isActive) cont.resume(true)
                }
                czekajacy.add(cb)
                cont.invokeOnCancellation { czekajacy.remove(cb) }
            }
        } == true || usluga() != null
    }

    /** Po sparowaniu / po restarcie: sprawdź binder → dopiero potem Wi‑Fi ADB. */
    suspend fun uruchom(ctx: Context, timeoutMs: Long = 20_000L): Boolean =
        uruchomZOpisem(ctx, timeoutMs) is WynikUruchom.Ok

    suspend fun uruchomZOpisem(
        ctx: Context,
        timeoutMs: Long = 20_000L,
    ): WynikUruchom = withContext(Dispatchers.IO) {
        val app = ctx.applicationContext
        appCtx = app
        if (usluga() != null) {
            // Żyje — ewentualnie cicho wyłącz adb_wifi, NIGDY nie włączaj.
            if (ShizukuAdbNaprawa.czyAdbWifi(app)) {
                sprobujWylaczycAdbWifiPoStarcie(app)
            }
            return@withContext WynikUruchom.Ok
        }

        var d = zdiagnozuj(app)
        Log.i(
            TAG,
            "przed startem: wifi=${d.wifi} adbWifi=${d.adbWifi} " +
                "writeSecure=${d.writeSecure} paired=${d.prefsSparowane}",
        )

        if (!d.prefsSparowane) {
            return@withContext WynikUruchom.Blad(
                "Nie sparowano jeszcze. Kliknij Start i wpisz kod z „Paruj z kodem”.",
                wymagaParowania = true,
                otworzUstawienia = true,
            )
        }

        // Daemon mógł przeżyć w tle — daj mu chwilę na republish (ten sam token).
        if (!oczekiwanyToken(app).isNullOrBlank()) {
            Log.i(TAG, "sprawdzam istniejący daemon (bez adb_wifi)…")
            if (czekajNaIstniejacyBinder(2_800L)) {
                Log.i(TAG, "daemon już żył — pomijam adb_wifi")
                if (ShizukuAdbNaprawa.czyAdbWifi(app)) {
                    sprobujWylaczycAdbWifiPoStarcie(app)
                }
                return@withContext WynikUruchom.Ok
            }
            Log.i(TAG, "brak daemona — adb_wifi tylko jeśli potrzeba")
        }

        if (!d.wifi) {
            return@withContext WynikUruchom.Blad(
                "Brak Wi‑Fi. Wireless debugging wymaga sieci " +
                    "(wystarczy hotspot bez internetu).",
            )
        }

        // Włącz Wireless debugging TYLKO gdy daemon nie żyje.
        if (!d.adbWifi) {
            val wlaczono = ShizukuAdbNaprawa.wlacAdbJakShizuku(app)
            Log.i(TAG, "próba włączenia adb_wifi → $wlaczono")
            if (wlaczono) {
                // mDNS pojawia się z opóźnieniem po adb_wifi=1
                delay(2_500)
            }
            d = zdiagnozuj(app)
            if (!d.adbWifi) {
                return@withContext WynikUruchom.Blad(
                    if (d.writeSecure) {
                        "Nie udało się włączyć Wireless debugging mimo WRITE_SECURE. " +
                            "Włącz ręcznie w opcjach programisty."
                    } else {
                        "Wireless debugging wyłączone. Brak WRITE_SECURE — " +
                            "włącz ręcznie w ustawieniach (albo daj grant przez ADB)."
                    },
                    otworzUstawienia = true,
                )
            }
        }

        val adb = AdbPolaczenie.get(app)
        val polaczono = runCatching {
            // Dłuższy timeout — Realme/ColorOS czasem wolno reklamuje mDNS.
            adb.connectTls(app, 15_000)
            true
        }.getOrElse { e ->
            Log.w(TAG, "connectTls", e)
            when (e) {
                is AdbPairingRequiredException -> {
                    oznaczSparowane(app, false)
                    return@withContext WynikUruchom.Blad(
                        "Klucz ADB nieważny / nieparowany na tym telefonie. " +
                            "Sparuj ponownie kodem.",
                        wymagaParowania = true,
                        otworzUstawienia = true,
                    )
                }
                is InterruptedException -> {
                    return@withContext WynikUruchom.Blad(
                        "mDNS nie znalazł portu ADB (Wireless debugging włączone=" +
                            "${ShizukuAdbNaprawa.czyAdbWifi(app)}). " +
                            "Otwórz Wireless debugging, upewnij się że widać IP:port, " +
                            "potem spróbuj znów. USB-debug czasem psuje mDNS na ColorOS.",
                        otworzUstawienia = true,
                    )
                }
                else -> {
                    return@withContext WynikUruchom.Blad(
                        "connectTls: ${e.message ?: e.javaClass.simpleName}",
                        otworzUstawienia = true,
                    )
                }
            }
        }
        if (!polaczono) {
            return@withContext WynikUruchom.Blad(
                "Brak połączenia ADB TLS — sparuj Wireless debugging",
                wymagaParowania = true,
            )
        }

        val token = nowyToken(app)
        val apk = app.applicationInfo.sourceDir
        // app_process + CLASSPATH=*.apk ładuje często TYLKO classes.dex (bez multidex).
        // ShellAdbMain siedzi w classesN.dex → ClassNotFound. Rozpakowujemy wszystkie
        // classes*.dex do /data/local/tmp (czytelne dla UID shell).
        val cmd = komendaStartuDaemona(apk, token, app.packageName)
        Log.i(TAG, "start daemon apk=$apk")

        binderRef.set(null)
        try {
            val stream = adb.openStream("shell:")
            AdbPolaczenie.ustawKeepalive(stream)
            // Czytaj wyjście shell w tle — widać crash / ClassNotFound.
            Thread({
                runCatching {
                    val buf = ByteArray(4096)
                    val inp = stream.openInputStream()
                    while (true) {
                        val n = inp.read(buf)
                        if (n <= 0) break
                        val chunk = String(buf, 0, n, Charsets.UTF_8)
                        Log.w(TAG, "shell> ${chunk.trim()}")
                    }
                }.onFailure { Log.w(TAG, "shell-read", it) }
            }, "telefon-adb-shell-out").apply { isDaemon = true; start() }

            val out = stream.openOutputStream()
            out.write(cmd.toByteArray(Charsets.UTF_8))
            out.flush()

            val got = withTimeoutOrNull(timeoutMs) {
                suspendCancellableCoroutine { cont ->
                    val juz = usluga()
                    if (juz != null) {
                        cont.resume(juz)
                        return@suspendCancellableCoroutine
                    }
                    val cb: (INagrywanieShell) -> Unit = { svc ->
                        if (cont.isActive) cont.resume(svc)
                    }
                    czekajacy.add(cb)
                    cont.invokeOnCancellation { czekajacy.remove(cb) }
                }
            }
            if (got != null) {
                // Daemon w tle — można odciąć stream i spróbować adb_wifi=0.
                sprobujWylaczycAdbWifiPoStarcie(app)
                WynikUruchom.Ok
            } else WynikUruchom.Blad(
                "Połączono ADB, ale daemon shell nie odezwał się w ${timeoutMs / 1000}s. " +
                    "Sprawdź logcat tag ShellAdb / ShellAdbBootstrap (shell>).",
            )
        } catch (e: Throwable) {
            Log.e(TAG, "daemon", e)
            WynikUruchom.Blad("Błąd startu daemona: ${e.message}")
        }
    }

    fun otworzWirelessDebugging(ctx: Context) {
        val flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        val ok = runCatching {
            ctx.startActivity(
                Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                    .addFlags(flags)
                    .putExtra(":settings:fragment_args_key", "toggle_adb_wireless"),
            )
        }.isSuccess
        if (!ok) {
            runCatching {
                ctx.startActivity(
                    Intent("android.settings.APPLICATION_DEVELOPMENT_SETTINGS")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }

    /**
     * Unzip multidex → CLASSPATH → app_process w tle (`&`),
     * żeby przeżył zamknięcie sesji ADB / wyłączenie adb_wifi.
     */
    private fun komendaStartuDaemona(apk: String, token: String, pkg: String): String {
        val dexDir = "/data/local/tmp/telefon_shell_dex"
        val log = "/data/local/tmp/telefon_nagranie.log"
        return buildString {
            append("DEXDIR=$dexDir; APK='$apk'; ")
            append("mkdir -p \"\$DEXDIR\" && ")
            append("unzip -o -q \"\$APK\" 'classes*.dex' -d \"\$DEXDIR\" && ")
            append("CP=\$(ls \"\$DEXDIR\"/classes*.dex 2>/dev/null | tr '\\n' ':'); ")
            append("CP=\${CP%:}; ")
            append("echo \"telefon_dex CP_len=\${#CP}\"; ")
            append("export CLASSPATH=\"\$CP\"; ")
            // Nowa sesja + nohup — przeżywa zamknięcie shell / adb_wifi OFF.
            append("(setsid nohup app_process -Djava.class.path=\"\$CP\" /system/bin ")
            append("--nice-name=telefon_nagranie ")
            append("dialer.dominik.pl.nagranie.adb.ShellAdbMain ")
            append(token)
            append(' ')
            append(pkg)
            append(" >$log 2>&1 </dev/null &) || ")
            append("(nohup app_process -Djava.class.path=\"\$CP\" /system/bin ")
            append("--nice-name=telefon_nagranie ")
            append("dialer.dominik.pl.nagranie.adb.ShellAdbMain ")
            append(token)
            append(' ')
            append(pkg)
            append(" >$log 2>&1 </dev/null &); ")
            append("echo telefon_daemon_bg=ok\n")
        }
    }
}
