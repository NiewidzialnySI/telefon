package dialer.dominik.pl.nagranie

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume

/**
 * Stan połączenia z Shizuku / UserService nagrywania.
 *
 * Jedyna zewnętrzna zależność silników = Shizuku.
 * Cube ACR Helper (com.catalinagroup.callrecorder.helper) NIE jest potrzebny —
 * to helper accessibility pod Cube ACR, nie pod nasze UserService.
 */
sealed class ShizukuStan {
    data object BrakAplikacji : ShizukuStan()
    data object NieUruchomione : ShizukuStan()
    data object BrakUprawnienia : ShizukuStan()
    data object Laczenie : ShizukuStan()
    data object Gotowe : ShizukuStan()
    data class Blad(val komunikat: String) : ShizukuStan()
}

/**
 * Klient Shizuku: uprawnienie + bind UserService [ShellNagrywanieService]
 * + auto-grant runtime dla naszej aplikacji.
 */
object ShizukuKlient {
    private const val TAG = "NagrywanieShizuku"
    private const val PAKIET_SHIZUKU = "moe.shizuku.privileged.api"
    private const val REQUEST_CODE = 0xCA11
    private const val WERSJA_USLUGI = ShellNagrywanieService.WERSJA

    var stan: ShizukuStan by mutableStateOf(ShizukuStan.NieUruchomione)
        private set

    @Volatile
    var usluga: INagrywanieShell? = null
        private set

    private var appCtx: Context? = null
    private var permissionCont: kotlin.coroutines.Continuation<Boolean>? = null
    @Volatile private var bindWToku = false

    private val args: Shizuku.UserServiceArgs by lazy {
        val ctx = checkNotNull(appCtx)
        Shizuku.UserServiceArgs(
            ComponentName(ctx.packageName, ShellNagrywanieService::class.java.name),
        )
            .daemon(true)
            .processNameSuffix("nagrywanie")
            .version(WERSJA_USLUGI)
            .tag("telefon-nagrywanie")
    }

    private val polaczenie = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            bindWToku = false
            val svc = INagrywanieShell.Stub.asInterface(binder)
            val v = runCatching { svc.wersja }.getOrDefault(-1)
            Log.i(TAG, "connected wersja=$v (chcemy $WERSJA_USLUGI)")
            if (v != WERSJA_USLUGI) {
                runCatching { Shizuku.unbindUserService(args, this, true) }
                usluga = null
                stan = ShizukuStan.Laczenie
                odswiez(force = true)
                return
            }
            usluga = svc
            stan = ShizukuStan.Gotowe
            appCtx?.let {
                UprawnieniaShizuku.nadajBrakujace(it)
                UprawnieniaShizuku.nadajWriteSecure(it)
                PowiadomieniaShizuku.anulujBrak(it)
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            Log.w(TAG, "disconnected")
            bindWToku = false
            usluga = null
            odswiez(force = true)
        }
    }

    private val permissionListener =
        Shizuku.OnRequestPermissionResultListener { _, result ->
            val ok = result == PackageManager.PERMISSION_GRANTED
            permissionCont?.resume(ok)
            permissionCont = null
            if (ok) {
                appCtx?.let { UprawnieniaShizuku.nadajBrakujace(it) }
            }
            odswiez(force = true)
        }

    private val binderOk = Shizuku.OnBinderReceivedListener {
        odswiez(force = true)
    }
    private val binderDead = Shizuku.OnBinderDeadListener {
        usluga = null
        bindWToku = false
        odswiez(force = true)
        // Własne ADB wystarczy — śmierć Shizuku nie jest awarią.
        val ctx = appCtx ?: return@OnBinderDeadListener
        if (ShellKlient.czyShellGotowy()) {
            PowiadomieniaShizuku.anulujBrak(ctx)
            return@OnBinderDeadListener
        }
        ShizukuStraznik.naBinderMartwy(ctx)
    }

    fun dolacz(ctx: Context) {
        if (appCtx != null) {
            odswiez()
            return
        }
        appCtx = ctx.applicationContext
        Shizuku.addRequestPermissionResultListener(permissionListener)
        Shizuku.addBinderReceivedListenerSticky(binderOk)
        Shizuku.addBinderDeadListener(binderDead)
        odswiez(force = true)
    }

    fun odswiez(force: Boolean = false) {
        val ctx = appCtx ?: return
        if (!force && usluga != null && stan is ShizukuStan.Gotowe) return
        if (!force && bindWToku && stan is ShizukuStan.Laczenie) return

        stan = when {
            !czyZainstalowane(ctx) -> ShizukuStan.BrakAplikacji
            !Shizuku.pingBinder() -> ShizukuStan.NieUruchomione
            @Suppress("DEPRECATION") Shizuku.isPreV11() ->
                ShizukuStan.Blad("Zaktualizuj Shizuku (min. v11)")
            Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED ->
                ShizukuStan.BrakUprawnienia
            usluga != null -> {
                ShizukuStan.Gotowe
            }
            else -> {
                bindWToku = true
                val bind = runCatching { Shizuku.bindUserService(args, polaczenie) }
                if (bind.isFailure) {
                    bindWToku = false
                    Log.e(TAG, "bindUserService", bind.exceptionOrNull())
                    ShizukuStan.Blad(bind.exceptionOrNull()?.message ?: "bind fail")
                } else {
                    ShizukuStan.Laczenie
                }
            }
        }
    }

    suspend fun poprosUprawnienie(): Boolean {
        if (!Shizuku.pingBinder()) return false
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            appCtx?.let { UprawnieniaShizuku.nadajBrakujace(it) }
            odswiez(force = true)
            return true
        }
        // „Nie pytaj ponownie” — dialog API nie wystartuje; otwórz Shizuku.
        if (runCatching { Shizuku.shouldShowRequestPermissionRationale() }.getOrDefault(false)) {
            return false
        }
        return suspendCancellableCoroutine { cont ->
            permissionCont?.resume(false)
            permissionCont = cont
            runCatching { Shizuku.requestPermission(REQUEST_CODE) }
                .onFailure {
                    Log.e(TAG, "requestPermission", it)
                    permissionCont = null
                    cont.resume(false)
                }
            cont.invokeOnCancellation { permissionCont = null }
        }
    }

    fun odlacz(usun: Boolean = false) {
        runCatching { Shizuku.unbindUserService(args, polaczenie, usun) }
        usluga = null
        bindWToku = false
        odswiez(force = true)
    }

    fun czyZainstalowane(ctx: Context): Boolean = runCatching {
        ctx.packageManager.getPackageInfo(PAKIET_SHIZUKU, 0)
        true
    }.getOrDefault(false)

    /** Otwiera aplikację Shizuku (albo sklep, gdy brak instalacji). */
    fun otworzAplikacje(ctx: Context) {
        if (!czyZainstalowane(ctx)) {
            otworzSklep(ctx)
            return
        }
        val launch = ctx.packageManager.getLaunchIntentForPackage(PAKIET_SHIZUKU)
            ?: Intent(Intent.ACTION_MAIN).setPackage(PAKIET_SHIZUKU)
        runCatching {
            ctx.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { Log.w(TAG, "otworzAplikacje", it) }
    }

    fun otworzSklep(ctx: Context) {
        val market = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("market://details?id=$PAKIET_SHIZUKU")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val web = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://github.com/RikkaApps/Shizuku/releases")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(market) }
            .recoverCatching { ctx.startActivity(web) }
            .onFailure { Log.w(TAG, "otworzSklep", it) }
    }

    /**
     * Ciche połączenie — NIGDY nie otwiera Shizuku/sklepu.
     * Jeśli usługa już działa + jest zgoda → bind UserService + pm grant.
     * Używane przy starcie app / nagrywaniu / resume ustawień.
     */
    suspend fun sprobujPolaczyc(ctx: Context, timeoutMs: Long = 4_000): Boolean {
        dolacz(ctx)
        odswiez(force = true)
        return when (stan) {
            ShizukuStan.Gotowe -> {
                UprawnieniaShizuku.nadajBrakujace(ctx)
                true
            }
            ShizukuStan.Laczenie -> {
                val ok = czekajGotowe(timeoutMs)
                if (ok) UprawnieniaShizuku.nadajBrakujace(ctx)
                ok
            }
            // Brak zgody / wyłączone — zostaw status w Ustawieniach; user klika przycisk.
            else -> false
        }
    }

    /**
     * Ręczna naprawa z przycisku w Ustawieniach — tu wolno otworzyć Shizuku.
     */
    suspend fun napraw(ctx: Context): Boolean {
        dolacz(ctx)
        odswiez(force = true)
        return when (val s = stan) {
            ShizukuStan.BrakAplikacji -> {
                otworzSklep(ctx)
                false
            }
            ShizukuStan.NieUruchomione, is ShizukuStan.Blad -> {
                otworzAplikacje(ctx)
                false
            }
            ShizukuStan.BrakUprawnienia -> {
                val ok = poprosUprawnienie()
                if (!ok) {
                    otworzAplikacje(ctx)
                    false
                } else {
                    czekajGotowe(8_000)
                }
            }
            ShizukuStan.Laczenie -> czekajGotowe(8_000)
            ShizukuStan.Gotowe -> {
                UprawnieniaShizuku.nadajBrakujace(ctx)
                true
            }
        }
    }

    /** @deprecated Użyj [sprobujPolaczyc] (cicho) albo [napraw] (z UI). */
    suspend fun zapewnijGotowosc(ctx: Context, timeoutMs: Long = 12_000): Boolean =
        sprobujPolaczyc(ctx, timeoutMs)

    private suspend fun czekajGotowe(timeoutMs: Long): Boolean {
        val start = SystemClock.elapsedRealtime()
        while (SystemClock.elapsedRealtime() - start < timeoutMs) {
            when (stan) {
                ShizukuStan.Gotowe -> if (usluga != null) return true
                ShizukuStan.BrakAplikacji,
                ShizukuStan.NieUruchomione,
                ShizukuStan.BrakUprawnienia,
                is ShizukuStan.Blad -> return false
                ShizukuStan.Laczenie -> Unit
            }
            delay(250)
            // Nie wywołuj bind ponownie w trakcie Laczenie.
            if (stan !is ShizukuStan.Laczenie) odswiez()
        }
        return stan is ShizukuStan.Gotowe && usluga != null
    }

    fun krotkiOpis(s: ShizukuStan = stan): String = when (s) {
        ShizukuStan.BrakAplikacji -> "zainstaluj Shizuku"
        ShizukuStan.NieUruchomione -> "włącz Shizuku (po restarcie też)"
        ShizukuStan.BrakUprawnienia -> "nadaj uprawnienie"
        ShizukuStan.Laczenie -> "łączenie…"
        ShizukuStan.Gotowe -> "Shizuku OK — obie strony"
        is ShizukuStan.Blad -> "błąd Shizuku"
    }

    fun etykietaAkcji(s: ShizukuStan = stan): String = when (s) {
        ShizukuStan.BrakAplikacji -> "Zainstaluj Shizuku"
        ShizukuStan.NieUruchomione -> "Otwórz Shizuku"
        ShizukuStan.BrakUprawnienia -> "Nadaj uprawnienie"
        is ShizukuStan.Blad -> "Otwórz Shizuku"
        ShizukuStan.Laczenie -> "Łączenie…"
        ShizukuStan.Gotowe -> "Gotowe"
    }

    fun czyShellGotowy(): Boolean = stan is ShizukuStan.Gotowe && usluga != null

    fun opisStanu(s: ShizukuStan = stan): String = krotkiOpis(s)
}
