package dialer.dominik.pl.nagranie.adb

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.annotation.Keep
import dialer.dominik.pl.nagranie.KontekstSystemowy
import dialer.dominik.pl.nagranie.ShellNagrywanieService
import dialer.dominik.pl.nagranie.UkryteApi

/**
 * Entry-point `app_process` (UID shell) — startowany przez [ShellAdbBootstrap].
 * Args: token pakiet
 *
 * Publikacja bindera jak Shizuku: [ActivityManager.getContentProviderExternal],
 * nie ContentResolver (SecurityException: package android ≠ uid 2000).
 */
@Keep
object ShellAdbMain {
    private const val TAG = "ShellAdbMain"

    @JvmStatic
    @Keep
    fun main(args: Array<String>) {
        err("start args=${args.size} uid=${android.os.Process.myUid()}")
        if (args.size < 2) {
            err("usage: ShellAdbMain <token> <package>")
            return
        }
        val token = args[0]
        val pkg = args[1]
        try {
            // Nowa sesja procesowa — nie ginij przy SIGHUP gdy adb_wifi OFF / shell padnie.
            runCatching { android.system.Os.setsid() }
                .onFailure { err("setsid: ${it.message}") }
            if (Looper.getMainLooper() == null) {
                Looper.prepareMainLooper()
            }
            UkryteApi.wlacz()
            // ActivityThread + system context — potrzebne do AM binderów.
            KontekstSystemowy.pobierz()
            err("UkryteApi+AT OK, tworzę ShellNagrywanieService…")
            val svc = ShellNagrywanieService()
            err("service OK, publikuję binder…")
            opublikuj(token, pkg, svc)
            val looper = Looper.getMainLooper()
            val h = Handler(looper)
            // Republish co 2 s — app po restarcie łapie binder BEZ włączania adb_wifi.
            val republish = object : Runnable {
                override fun run() {
                    runCatching { opublikuj(token, pkg, svc) }
                        .onFailure { err("re-publish: ${it.message}") }
                    h.postDelayed(this, 2_000L)
                }
            }
            h.postDelayed(republish, 800L)
            err("Looper.loop()")
            Looper.loop()
        } catch (t: Throwable) {
            err("CRASH: ${t.javaClass.name}: ${t.message}")
            t.printStackTrace(System.err)
            Log.e(TAG, "main", t)
        }
    }

    private fun err(msg: String) {
        System.err.println("ShellAdbMain: $msg")
        System.err.flush()
        Log.i(TAG, msg)
    }

    @SuppressLint("PrivateApi", "DiscouragedPrivateApi")
    private fun opublikuj(token: String, pkg: String, svc: ShellNagrywanieService) {
        val auth = ShellBinderDostawca.authority(pkg)
        val extras = Bundle().apply {
            putString(ShellBinderDostawca.EXTRA_TOKEN, token)
            putBinder(ShellBinderDostawca.EXTRA_BINDER, svc as IBinder)
        }

        val am = activityManager()
        val holder = getContentProviderExternal(am, auth)
            ?: error("getContentProviderExternal($auth) = null")
        val provider = providerZHoldera(holder)
            ?: error("brak IContentProvider w $holder")

        try {
            val reply = callProvider(provider, auth, extras)
            err("publish reply=$reply")
            if (reply == null) {
                err("reply null — token / provider odrzucił")
            }
        } finally {
            runCatching { removeContentProviderExternal(am, auth) }
        }
    }

    @SuppressLint("PrivateApi")
    private fun activityManager(): Any {
        val cls = Class.forName("android.app.ActivityManager")
        return runCatching {
            cls.getDeclaredMethod("getService").invoke(null)
        }.getOrElse {
            val native = Class.forName("android.app.ActivityManagerNative")
            val m = native.getDeclaredMethod("getDefault")
            m.isAccessible = true
            m.invoke(null)
        } ?: error("IActivityManager null")
    }

    @SuppressLint("PrivateApi")
    private fun getContentProviderExternal(am: Any, auth: String): Any? {
        val userId = 0
        val methods = am.javaClass.methods.filter { it.name == "getContentProviderExternal" }
        var last: Throwable? = null
        for (m in methods.sortedByDescending { it.parameterTypes.size }) {
            try {
                val n = m.parameterTypes.size
                val r = when (n) {
                    // (String auth, int userId, IBinder token, String tag) — API 29+
                    4 -> m.invoke(am, auth, userId, null, auth)
                    // (String auth, int userId, IBinder token)
                    3 -> m.invoke(am, auth, userId, null)
                    2 -> m.invoke(am, auth, userId)
                    else -> continue
                }
                err("getCPE arity=$n → ${r?.javaClass?.simpleName}")
                return r
            } catch (t: Throwable) {
                last = t
                err("getCPE arity=${m.parameterTypes.size} fail: ${t.message}")
            }
        }
        throw last ?: IllegalStateException("brak getContentProviderExternal")
    }

    private fun providerZHoldera(holder: Any): Any? {
        runCatching {
            val f = holder.javaClass.getDeclaredField("provider")
            f.isAccessible = true
            return f.get(holder)
        }
        for (m in holder.javaClass.methods) {
            if (m.parameterCount == 0 &&
                (m.name == "getProvider" || m.returnType.name.contains("IContentProvider"))
            ) {
                runCatching { return m.invoke(holder) }.getOrNull()?.let { return it }
            }
        }
        // ContentProviderHolder może być Parcelable z polem info + provider
        for (f in holder.javaClass.declaredFields) {
            f.isAccessible = true
            val v = runCatching { f.get(holder) }.getOrNull() ?: continue
            if (v.javaClass.name.contains("IContentProvider") ||
                v.javaClass.interfaces.any { it.name.contains("IContentProvider") }
            ) {
                return v
            }
        }
        err("holder fields=${holder.javaClass.declaredFields.joinToString { it.name }}")
        return null
    }

    /**
     * IContentProvider.call — różne sygnatury API 28–35.
     * callingPkg = com.android.shell (uid 2000).
     */
    @SuppressLint("PrivateApi")
    private fun callProvider(provider: Any, auth: String, extras: Bundle): Bundle? {
        val shellPkg = "com.android.shell"
        val method = ShellBinderDostawca.METODA_PUBLISH
        val attribution = zbudujAttribution(shellPkg)
        val candidates = provider.javaClass.methods
            .filter { it.name == "call" }
            .sortedByDescending { it.parameterTypes.size }

        var last: Throwable? = null
        for (m in candidates) {
            try {
                val args = zbudujArgsCall(m.parameterTypes, shellPkg, auth, method, extras, attribution)
                    ?: continue
                @Suppress("UNCHECKED_CAST")
                val reply = m.invoke(provider, *args) as Bundle?
                err("call OK arity=${m.parameterTypes.size} types=${m.parameterTypes.joinToString { it.simpleName }}")
                return reply
            } catch (t: Throwable) {
                last = t.cause ?: t
                err("call arity=${m.parameterTypes.size} fail: ${last?.javaClass?.simpleName}: ${last?.message}")
            }
        }
        throw last ?: IllegalStateException("żadne call() nie zadziałało")
    }

    private fun zbudujArgsCall(
        types: Array<Class<*>>,
        shellPkg: String,
        auth: String,
        method: String,
        extras: Bundle,
        attribution: Any?,
    ): Array<Any?>? {
        val args = arrayOfNulls<Any?>(types.size)
        // Kolejność stringów zależnie od API:
        // API 29: (callingPkg, uri/authority, method, arg, extras)
        // API 30: (callingPkg, featureId, authority, method, arg, extras)
        // API 31+: (AttributionSource, authority, method, arg, extras)
        var stringSlot = 0
        val stringSeq = when {
            types.any { it.name.contains("AttributionSource") } ->
                listOf(auth, method, null)
            types.count { it == String::class.java } >= 5 ->
                listOf(shellPkg, null, auth, method, null) // featureId
            else ->
                listOf(shellPkg, auth, method, null)
        }
        for (i in types.indices) {
            val t = types[i]
            when {
                t == Bundle::class.java -> args[i] = extras
                t.name.contains("AttributionSource") -> {
                    if (attribution == null) return null
                    args[i] = attribution
                }
                t == String::class.java -> {
                    if (stringSlot >= stringSeq.size) return null
                    args[i] = stringSeq[stringSlot++]
                }
                t == Boolean::class.javaPrimitiveType || t == java.lang.Boolean::class.java ->
                    args[i] = false
                IBinder::class.java.isAssignableFrom(t) -> args[i] = null
                else -> {
                    // Nieznany typ — pomiń tę sygnaturę
                    return null
                }
            }
        }
        return args
    }

    @SuppressLint("PrivateApi")
    private fun zbudujAttribution(pkg: String): Any? = runCatching {
        val builderCls = Class.forName("android.content.AttributionSource\$Builder")
        val b = builderCls.getConstructor(Int::class.javaPrimitiveType)
            .newInstance(android.os.Process.myUid())
        builderCls.getMethod("setPackageName", String::class.java).invoke(b, pkg)
        builderCls.getMethod("build").invoke(b)
    }.onFailure { err("AttributionSource: ${it.message}") }.getOrNull()

    private fun removeContentProviderExternal(am: Any, auth: String) {
        val methods = am.javaClass.methods.filter { it.name.startsWith("removeContentProviderExternal") }
        for (m in methods) {
            runCatching {
                when (m.parameterTypes.size) {
                    2 -> m.invoke(am, auth, null)
                    3 -> m.invoke(am, auth, null, 0)
                    else -> return@runCatching
                }
            }
        }
    }
}
