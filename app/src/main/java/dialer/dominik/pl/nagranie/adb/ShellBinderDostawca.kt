package dialer.dominik.pl.nagranie.adb

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import dialer.dominik.pl.nagranie.INagrywanieShell

/**
 * Shell (app_process) publikuje tu binder [INagrywanieShell] przez content.call.
 * Token jednorazowy — tylko nasz bootstrap zna wartość.
 */
class ShellBinderDostawca : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method != METODA_PUBLISH) return null
        val ctx = context ?: return null
        val token = extras?.getString(EXTRA_TOKEN) ?: return null
        val oczekiwany = ShellAdbBootstrap.oczekiwanyToken(ctx)
        if (oczekiwany.isNullOrBlank() || token != oczekiwany) {
            Log.w(TAG, "zły token")
            return null
        }
        val binder = extras.getBinder(EXTRA_BINDER)
        if (binder == null) {
            Log.w(TAG, "brak bindera")
            return null
        }
        val svc = INagrywanieShell.Stub.asInterface(binder)
        ShellAdbBootstrap.przyjmijBinder(svc)
        Log.i(TAG, "binder przyjęty v=${runCatching { svc.wersja }.getOrDefault(-1)}")
        return Bundle().apply { putBoolean("ok", true) }
    }

    override fun query(
        uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(
        uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?,
    ): Int = 0

    companion object {
        private const val TAG = "ShellBinder"
        const val METODA_PUBLISH = "publish"
        const val EXTRA_TOKEN = "token"
        const val EXTRA_BINDER = "binder"
        fun authority(pkg: String) = "$pkg.shell_binder"
    }
}
