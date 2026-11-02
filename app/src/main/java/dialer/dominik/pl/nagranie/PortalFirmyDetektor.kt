package dialer.dominik.pl.nagranie

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import dialer.dominik.pl.nagranie.adb.ShellAdbBootstrap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Odczyt systemowego profilu firmowego (Portal / Work Profile)
 * i synchronizacja z trybem ADB tylko-na-rozmowę.
 */
object PortalFirmyDetektor {
    private const val TAG = "PortalFirmy"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Czy na urządzeniu jest profil firmowy (nawet uśpiony). */
    fun czyPortalIstnieje(ctx: Context): Boolean = handlFirmowy(ctx) != null

    /**
     * Profil firmowy jest **włączony** (aplikacje pracy dostępne).
     * Quiet mode = wyłączony / uśpiony Portal.
     */
    fun czyPortalWlaczony(ctx: Context): Boolean {
        val um = ctx.getSystemService(UserManager::class.java) ?: return false
        val h = handlFirmowy(ctx) ?: return false
        return !um.isQuietModeEnabled(h)
    }

    fun handlFirmowy(ctx: Context): UserHandle? {
        val um = ctx.getSystemService(UserManager::class.java) ?: return null
        val ja = Process.myUserHandle()
        val kandydaci = um.userProfiles.filter { it != ja }
        if (kandydaci.isEmpty()) return null
        return kandydaci.firstOrNull { czyManagedProfile(um, it) } ?: kandydaci.firstOrNull()
    }

    private fun czyManagedProfile(um: UserManager, handle: UserHandle): Boolean {
        return runCatching {
            val id = UserHandle::class.java
                .getMethod("getIdentifier")
                .invoke(handle) as Int
            UserManager::class.java
                .getMethod("isManagedProfile", Int::class.javaPrimitiveType)
                .invoke(um, id) as Boolean
        }.getOrDefault(false)
    }

    /**
     * Gdy Auto ON: ustaw [NagrywanieUstawienia.czyAdbTylkoNaRozmowe] wg Portalu
     * i włącz/wyłącz ADB. Zwraca nowy stan trybu firmowego albo null gdy Auto OFF /
     * brak profilu.
     */
    fun zsynchronizujJesliAuto(ctx: Context): Boolean? {
        val app = ctx.applicationContext
        if (!NagrywanieUstawienia.czyPortalFirmyAuto(app)) return null
        if (!czyPortalIstnieje(app)) {
            Log.i(TAG, "auto: brak profilu firmowego — bez zmian")
            return null
        }
        val portalOn = czyPortalWlaczony(app)
        val bylo = NagrywanieUstawienia.czyAdbTylkoNaRozmowe(app)
        if (bylo == portalOn) {
            Log.i(TAG, "auto: bez zmian portalOn=$portalOn")
            return portalOn
        }
        Log.i(TAG, "auto: Portal ${if (portalOn) "ON" else "OFF"} → tryb firmowy=$portalOn")
        NagrywanieUstawienia.ustawAdbTylkoNaRozmowe(app, portalOn)
        if (portalOn) {
            AdbNaprawa.wylaczDlaFirmowki(app)
        } else {
            scope.launch {
                runCatching {
                    ShellAdbBootstrap.uruchomZOpisem(app)
                    ShellAdbBootstrap.sprobujWylaczycAdbWifiPoStarcie(app)
                }.onFailure { Log.w(TAG, "auto start usługi", it) }
            }
        }
        return portalOn
    }

    fun filtrBroadcastow(): IntentFilter = IntentFilter().apply {
        addAction(Intent.ACTION_MANAGED_PROFILE_AVAILABLE)
        addAction(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE)
        if (Build.VERSION.SDK_INT >= 30) {
            addAction(Intent.ACTION_MANAGED_PROFILE_UNLOCKED)
        }
    }
}

/** Reaguje na włączenie / uśpienie profilu firmowego. */
class PortalFirmyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val a = intent?.action ?: return
        Log.i("PortalFirmy", "broadcast $a")
        PortalFirmyDetektor.zsynchronizujJesliAuto(context)
    }
}
