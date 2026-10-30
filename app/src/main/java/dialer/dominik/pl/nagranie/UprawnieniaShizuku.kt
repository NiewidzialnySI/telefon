package dialer.dominik.pl.nagranie

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

/**
 * Po uzyskaniu zgody Shizuku — nadaje naszej aplikacji brakujące uprawnienia runtime
 * przez IPackageManager (jak shell / `pm grant`).
 *
 * Nie dotyczy Cube ACR Helper — nasze silniki działają w UserService (UID shell),
 * nie przez helpery accessibility.
 */
object UprawnieniaShizuku {
    private const val TAG = "UprawnieniaShizuku"

    private val UPRAWNIENIA = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        add(Manifest.permission.READ_PHONE_STATE)
        add(Manifest.permission.READ_CALL_LOG)
        add(Manifest.permission.READ_CONTACTS)
        if (Build.VERSION.SDK_INT >= 33) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= 31) {
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }

    fun nadajBrakujace(ctx: Context): Int {
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "brak uprawnienia Shizuku — pomijam pm grant")
            return 0
        }
        val pkg = ctx.packageName
        val userId = android.os.Process.myUid() / 100_000
        var nadane = 0
        for (perm in UPRAWNIENIA) {
            if (ctx.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED) continue
            if (grantRuntime(pkg, perm, userId)) {
                nadane++
                Log.i(TAG, "nadano $perm")
            } else {
                Log.w(TAG, "nie udało się nadać $perm")
            }
        }
        return nadane
    }

    /** Do przełączania Wireless debugging z poziomu apki (naprawa z powiadomienia). */
    fun nadajWriteSecure(ctx: Context): Boolean {
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) return false
        if (ctx.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return true
        }
        val pkg = ctx.packageName
        val userId = android.os.Process.myUid() / 100_000
        val ok = grantRuntime(pkg, Manifest.permission.WRITE_SECURE_SETTINGS, userId)
        Log.i(TAG, "WRITE_SECURE_SETTINGS=$ok")
        return ok
    }

    private fun grantRuntime(pkg: String, permission: String, userId: Int): Boolean {
        return runCatching {
            val raw = SystemServiceHelper.getSystemService("package")
                ?: error("brak service package")
            val wrapped: IBinder = ShizukuBinderWrapper(raw)
            val stub = Class.forName("android.content.pm.IPackageManager\$Stub")
            val pm = stub.getMethod("asInterface", IBinder::class.java).invoke(null, wrapped)
                ?: error("asInterface null")
            val grants = pm.javaClass.methods.filter { it.name == "grantRuntimePermission" }
            var ok = false
            for (grant in grants) {
                ok = runCatching {
                    when (grant.parameterTypes.size) {
                        2 -> grant.invoke(pm, pkg, permission)
                        3 -> grant.invoke(pm, pkg, permission, userId)
                        4 -> grant.invoke(pm, pkg, permission, userId, 0)
                        else -> return@runCatching false
                    }
                    true
                }.getOrDefault(false)
                if (ok) break
            }
            ok
        }.onFailure { Log.e(TAG, "grantRuntime $permission", it) }
            .getOrDefault(false)
    }
}
