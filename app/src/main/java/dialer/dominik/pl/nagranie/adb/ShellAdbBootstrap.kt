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
import dialer.dominik.pl.nagranie.AdbNaprawa
import dialer.dominik.pl.nagranie.NagrywanieStraznik
import io.github.muntashirakon.adb.AdbPairingRequiredException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    /** rozgrzej + start naraz psuły shell (nakładające się komendy / connectTls). */
    private val uruchomMutex = Mutex()
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
        val adbUsb: Boolean,
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
            append(if (adbUsb) "○ USB debugging włączone" else "✓ USB debugging wyłączone")
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
            uslugaZyje && !adbUsb && !adbWifi -> "ADB OK — do rebootu (bez debug)"
            uslugaZyje && !adbUsb && adbWifi -> "ADB OK — Wireless ON, USB OFF"
            uslugaZyje && adbUsb && !adbWifi ->
                "ADB OK — Wireless możesz wyłączyć; nie ruszaj USB"
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
                // Nie włączaj ADB samo — użytkownik mógł celowo wyłączyć debug.
                Log.i(TAG, "ponawiam usługę (bez auto wlacAdb)")
                val ok = ShellKlient.zapewnij(ctx, autoWlacAdb = false)
                Log.i(TAG, "ponowienie → $ok")
                if (!ok) NagrywanieStraznik.naBinderMartwy(ctx)
            } finally {
                restartWToku.set(false)
            }
        }
    }

    /**
     * Po żywym daemonie: odłącz keepalive i ustaw najbezpieczniejszy tryb ADB.
     * Nie gasimy już „ślepo” samego Wireless przy włączonym USB — to dawało
     * fałszywy sukces i ginęło przy wyłączeniu USB debugging.
     */
    suspend fun sprobujWylaczycAdbWifiPoStarcie(ctx: Context): Boolean {
        val app = ctx.applicationContext
        if (usluga() == null) return false
        // Odłącz keepalive ADB — daemon jest w tle (`&`), nie potrzebuje shell stream.
        AdbPolaczenie.ustawKeepalive(null)
        delay(400)
        // Tryb firmowy: ADB zostaje ON tylko na rozmowę — nie gasimy tu (zrobi to stop()).
        if (NagrywanieUstawienia.czyAdbTylkoNaRozmowe(app)) {
            Log.i(TAG, "tryb firmowy — pomijam stabilizację (ADB zgaśnie po rozmowie)")
            return usluga() != null
        }
        val tryb = AdbNaprawa.ustabilizujPoStarcieDaemona(app) { usluga() != null }
        Log.i(TAG, "stabilizacja ADB → $tryb")
        return when (tryb) {
            AdbNaprawa.TrybAdbPoStarcie.TylkoUsb,
            AdbNaprawa.TrybAdbPoStarcie.TylkoWireless,
            AdbNaprawa.TrybAdbPoStarcie.ObaWlaczone,
            AdbNaprawa.TrybAdbPoStarcie.BezUprawnien,
            -> usluga() != null

            // Legacy / awaryjne — wcześniej test USB OFF; teraz cel to TylkoUsb.
            AdbNaprawa.TrybAdbPoStarcie.ObaWylaczone -> {
                AdbNaprawa.ustawAdbUsb(app, true)
                delay(800)
                usluga() != null
            }

            AdbNaprawa.TrybAdbPoStarcie.WymagaRestartuTylkoUsb ->
                wznowWTrybieTylkoUsb(app)

            AdbNaprawa.TrybAdbPoStarcie.Martwy -> {
                Log.w(TAG, "stabilizacja zabiła daemon — przywracam ADB (start osobno)")
                AdbNaprawa.wlacAdb(app)
                false
            }
        }
    }

    /**
     * Oba OFF zabija daemon na tym ROM-ie. Restart: chwilowo Wireless (TLS),
     * potem **Wireless OFF + USB ON** — użytkownik może wyłączyć Wireless bez utraty nagrywania.
     */
    private suspend fun wznowWTrybieTylkoUsb(app: Context): Boolean {
        Log.i(TAG, "wznowienie: start daemona, potem TylkoUsb (Wireless OFF, USB ON)")
        AdbNaprawa.wlacAdb(app)
        delay(2_500)
        val ok = uruchomZOpisem(app, stabilizujPo = false) is WynikUruchom.Ok
        if (!ok || usluga() == null) {
            Log.w(TAG, "wznowienie: nie udało się ponownie wystartować daemona")
            return false
        }
        AdbPolaczenie.ustawKeepalive(null)
        delay(400)
        AdbNaprawa.ustawAdbUsb(app, true)
        AdbNaprawa.ustawAdbWifi(app, false)
        delay(1_800)
        if (usluga() != null) {
            Log.i(TAG, "wznowienie OK: TylkoUsb — możesz wyłączyć Wireless, nie wyłączaj USB")
            return true
        }
        // Awaryjnie: zostaw oba ON żeby daemon żył.
        Log.w(TAG, "wznowienie: TylkoUsb nie utrzymał daemona — zostawiam oba ON")
        AdbNaprawa.wlacAdb(app)
        delay(1_500)
        val okOba = uruchomZOpisem(app, stabilizujPo = false) is WynikUruchom.Ok
        if (okOba && usluga() != null) {
            AdbPolaczenie.ustawKeepalive(null)
            return true
        }
        return false
    }

    fun zdiagnozuj(ctx: Context): Diagnostyka {
        val app = ctx.applicationContext
        return Diagnostyka(
            wifi = czyWifi(app),
            adbWifi = AdbNaprawa.czyAdbWifi(app),
            adbUsb = AdbNaprawa.czyAdbUsb(app),
            writeSecure = AdbNaprawa.maWriteSecure(app),
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
    suspend fun uruchom(
        ctx: Context,
        timeoutMs: Long = 20_000L,
        stabilizujPo: Boolean = true,
        autoWlacAdb: Boolean = true,
    ): Boolean = uruchomZOpisem(ctx, timeoutMs, stabilizujPo, autoWlacAdb) is WynikUruchom.Ok

    /**
     * @param autoWlacAdb gdy false — tylko republish / istniejące ADB; nie wołaj [AdbNaprawa.wlacAdb]
     *   (powrót do apki / strażnik nie mają włączać debugu za użytkownika).
     */
    suspend fun uruchomZOpisem(
        ctx: Context,
        timeoutMs: Long = 20_000L,
        stabilizujPo: Boolean = true,
        autoWlacAdb: Boolean = true,
    ): WynikUruchom = withContext(Dispatchers.IO) {
        // Poza mutexem: szybka ścieżka gdy daemon już żyje.
        if (usluga() != null && !stabilizujPo) return@withContext WynikUruchom.Ok
        uruchomMutex.withLock {
            uruchomZOpisemZablokowane(ctx, timeoutMs, stabilizujPo, autoWlacAdb)
        }
    }

    private suspend fun uruchomZOpisemZablokowane(
        ctx: Context,
        timeoutMs: Long,
        stabilizujPo: Boolean,
        autoWlacAdb: Boolean,
    ): WynikUruchom {
        val app = ctx.applicationContext
        appCtx = app
        if (usluga() != null) {
            // Żyje — dociśnij tryb ADB (nie zostawiaj „Wireless OFF + USB ON” bez testu).
            if (stabilizujPo &&
                (AdbNaprawa.czyAdbWifi(app) || AdbNaprawa.czyAdbUsb(app))
            ) {
                sprobujWylaczycAdbWifiPoStarcie(app)
            }
            return if (usluga() != null) WynikUruchom.Ok
            else WynikUruchom.Blad("Daemon padł przy stabilizacji ADB — uruchom ponownie.")
        }

        var d = zdiagnozuj(app)
        Log.i(
            TAG,
            "przed startem: wifi=${d.wifi} adbWifi=${d.adbWifi} adbUsb=${d.adbUsb} " +
                "writeSecure=${d.writeSecure} paired=${d.prefsSparowane}",
        )

        if (!d.prefsSparowane) {
            return WynikUruchom.Blad(
                "Nie sparowano jeszcze. Kliknij Start i wpisz kod z „Paruj z kodem”.",
                wymagaParowania = true,
                otworzUstawienia = true,
            )
        }

        // Daemon mógł przeżyć w tle — daj mu chwilę na republish (ten sam token).
        if (!oczekiwanyToken(app).isNullOrBlank()) {
            Log.i(TAG, "sprawdzam istniejący daemon (bez adb_wifi)…")
            if (czekajNaIstniejacyBinder(2_800L)) {
                Log.i(TAG, "daemon już żył — stabilizuję ADB (stabilizujPo=$stabilizujPo)")
                if (stabilizujPo &&
                    (AdbNaprawa.czyAdbWifi(app) || AdbNaprawa.czyAdbUsb(app))
                ) {
                    sprobujWylaczycAdbWifiPoStarcie(app)
                }
                return if (usluga() != null) WynikUruchom.Ok
                else WynikUruchom.Blad("Daemon padł przy stabilizacji ADB — uruchom ponownie.")
            }
            Log.i(TAG, "brak daemona — adb_wifi tylko jeśli potrzeba")
        }

        if (!d.wifi) {
            if (!autoWlacAdb) {
                return WynikUruchom.Blad(
                    "Brak Wi‑Fi. Wireless debugging wymaga sieci " +
                        "(wystarczy hotspot bez internetu).",
                )
            }
            Log.i(TAG, "brak Wi‑Fi — próbuję włączyć / otworzyć panel")
            if (!AdbNaprawa.zapewnijWifi(app, timeoutMs = 18_000L)) {
                return WynikUruchom.Blad(
                    "Brak Wi‑Fi. Włącz Wi‑Fi lub hotspot, potem uruchom ponownie.",
                )
            }
            d = zdiagnozuj(app)
        }

        // Włącz Wireless debugging TYLKO gdy daemon nie żyje i wolno auto-ON.
        var wlasnieWlaczonoWifi = false
        if (!d.adbWifi) {
            if (!autoWlacAdb) {
                Log.i(TAG, "adb_wifi OFF — pomijam wlacAdb (autoWlacAdb=false)")
                return WynikUruchom.Blad(
                    "Wireless debugging wyłączone. Usługa nie włączy go sama — " +
                        "włącz w opcjach programisty albo „Napraw” z powiadomienia.",
                    otworzUstawienia = true,
                )
            }
            val wlaczono = AdbNaprawa.wlacAdb(app)
            Log.i(TAG, "próba włączenia adb_wifi → $wlaczono")
            if (wlaczono) {
                wlasnieWlaczonoWifi = true
                // ColorOS/Realme: mDNS po cold-start firmowym bywa >2.5 s.
                delay(4_000)
            }
            d = zdiagnozuj(app)
            if (!d.adbWifi) {
                return WynikUruchom.Blad(
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
        suspend fun probujConnect(timeoutMs: Long): Throwable? {
            return runCatching {
                adb.connectTls(app, timeoutMs)
                null
            }.exceptionOrNull()
        }
        var connectBlad = probujConnect(15_000L)
        if (connectBlad is InterruptedException) {
            // Po świeżym adb_wifi mDNS często spóźnia się — jedna dopinka.
            Log.w(TAG, "connectTls timeout — retry mDNS (wlasnie=$wlasnieWlaczonoWifi)")
            delay(if (wlasnieWlaczonoWifi) 3_000 else 1_500)
            if (!AdbNaprawa.czyAdbWifi(app) && autoWlacAdb) {
                AdbNaprawa.wlacAdb(app)
                delay(2_500)
            }
            connectBlad = probujConnect(20_000L)
        }
        if (connectBlad != null) {
            val e = connectBlad
            Log.w(TAG, "connectTls", e)
            return when (e) {
                is AdbPairingRequiredException -> {
                    oznaczSparowane(app, false)
                    WynikUruchom.Blad(
                        "Klucz ADB nieważny / nieparowany na tym telefonie. " +
                            "Sparuj ponownie kodem.",
                        wymagaParowania = true,
                        otworzUstawienia = true,
                    )
                }
                is InterruptedException -> WynikUruchom.Blad(
                    "mDNS nie znalazł portu ADB (Wireless debugging włączone=" +
                        "${AdbNaprawa.czyAdbWifi(app)}). " +
                        "Otwórz Wireless debugging, upewnij się że widać IP:port, " +
                        "potem spróbuj znów. USB-debug czasem psuje mDNS na ColorOS.",
                    otworzUstawienia = true,
                )
                else -> WynikUruchom.Blad(
                    "connectTls: ${e.message ?: e.javaClass.simpleName}",
                    otworzUstawienia = true,
                )
            }
        }

        val token = nowyToken(app)
        val apk = app.applicationInfo.sourceDir
        // app_process + CLASSPATH=*.apk ładuje często TYLKO classes.dex (bez multidex).
        // ShellAdbMain siedzi w classesN.dex → ClassNotFound. Rozpakowujemy wszystkie
        // classes*.dex do /data/local/tmp (czytelne dla UID shell).
        val cmd = komendaStartuDaemona(apk, token, app.packageName)
        Log.i(TAG, "start daemon apk=$apk")

        binderRef.set(null)
        return try {
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
                // Daemon w tle — ustabilizuj ADB (USB nie może zostać jedyną „podpórką”).
                if (stabilizujPo) {
                    sprobujWylaczycAdbWifiPoStarcie(app)
                }
                if (usluga() != null) {
                    dialer.dominik.pl.nagranie.UprawnieniaShell.nadajBrakujace(app)
                    dialer.dominik.pl.nagranie.UprawnieniaShell.nadajWriteSecure(app)
                    WynikUruchom.Ok
                } else WynikUruchom.Blad(
                    "Daemon wystartował, ale padł przy stabilizacji ADB. Uruchom ponownie.",
                )
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
            // trap HUP + setsid + nohup — lepiej przeżywa zamknięcie sesji adbd / Wireless OFF.
            append("trap '' HUP; ")
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
