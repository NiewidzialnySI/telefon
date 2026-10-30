package dialer.dominik.pl.nagranie

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * W procesie Shizuku UserService nie ma Application — Context systemowy
 * bierzemy z ActivityThread przez refleksję.
 *
 * systemMain() tworzy Handler, więc musi lecieć na wątku z Looperem
 * (main procesu shell). AIDL leci na Binder-thread bez Looper → cache.
 */
internal object KontekstSystemowy {
    private const val TAG = "NagrywanieShell"

    @Volatile
    private var cache: Context? = null

    fun pobierz(): Context {
        cache?.let { return it }
        synchronized(this) {
            cache?.let { return it }
            val ctx = uzyskaj()
            cache = ctx
            return ctx
        }
    }

    fun rozgrzejNaMain() {
        if (cache != null) return
        runCatching { pobierz() }
            .onSuccess { Log.i(TAG, "KontekstSystemowy gotowy") }
            .onFailure { Log.w(TAG, "rozgrzewka kontekstu: ${it.message}") }
    }

    private fun uzyskaj(): Context {
        val cls = Class.forName("android.app.ActivityThread")

        runCatching {
            val istniejacy = cls.getMethod("currentActivityThread").invoke(null)
            if (istniejacy != null) {
                return cls.getMethod("getSystemContext").invoke(istniejacy) as Context
            }
        }

        val main = Looper.getMainLooper()
            ?: error("Brak main Looper — Shizuku UserService źle skonfigurowany")

        if (Thread.currentThread() === main.thread) {
            val at = cls.getMethod("systemMain").invoke(null)
            return cls.getMethod("getSystemContext").invoke(at) as Context
        }

        val wynik = AtomicReference<Context?>(null)
        val blad = AtomicReference<Throwable?>(null)
        val latch = CountDownLatch(1)
        Handler(main).post {
            try {
                val at = cls.getMethod("systemMain").invoke(null)
                wynik.set(cls.getMethod("getSystemContext").invoke(at) as Context)
            } catch (t: Throwable) {
                blad.set(t)
            } finally {
                latch.countDown()
            }
        }
        if (!latch.await(2, TimeUnit.SECONDS)) {
            error("systemMain() timeout 2s")
        }
        blad.get()?.let { throw it }
        return wynik.get() ?: error("systemMain() null")
    }
}
