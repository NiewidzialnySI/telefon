package dialer.dominik.pl.nagranie

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log

/**
 * Nadaje brakujące uprawnienia runtime przez daemon shell (`pm grant`).
 */
object UprawnieniaShell {
    private const val TAG = "UprawnieniaShell"

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
        val shell = ShellKlient.usluga() ?: run {
            Log.w(TAG, "brak daemona shell — pomijam pm grant")
            return 0
        }
        val pkg = ctx.packageName
        var nadane = 0
        for (perm in UPRAWNIENIA) {
            if (ctx.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED) continue
            val ok = runCatching { shell.grantPermission(pkg, perm) }.getOrDefault(false)
            if (ok) {
                nadane++
                Log.i(TAG, "nadano $perm")
            } else {
                Log.w(TAG, "nie udało się nadać $perm")
            }
        }
        return nadane
    }

    fun nadajWriteSecure(ctx: Context): Boolean {
        if (ctx.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return true
        }
        val shell = ShellKlient.usluga() ?: return false
        val ok = runCatching {
            shell.grantPermission(ctx.packageName, Manifest.permission.WRITE_SECURE_SETTINGS)
        }.getOrDefault(false)
        Log.i(TAG, "WRITE_SECURE_SETTINGS=$ok")
        return ok
    }
}
