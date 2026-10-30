package dialer.dominik.pl.nagranie

import android.annotation.SuppressLint
import android.app.Application
import android.app.Instrumentation
import android.content.AttributionSource
import android.content.Context
import android.content.ContextParams
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.os.Process
import android.util.Log
import android.view.Display

/**
 * Context, który AudioFlinger / AppOps widzą jako prawdziwy pakiet systemowy
 * `com.android.shell` (UID 2000) — ten sam UID, w którym działa Shizuku UserService.
 *
 * Samo bycie UID 2000 nie wystarcza: bramka sprawdza parę (uid, package)
 * względem package DB. `com.android.shell` ma signature-level RECORD_AUDIO /
 * CAPTURE_AUDIO_OUTPUT, więc VOICE_* źródła się otwierają.
 *
 * Dodatkowo patchujemy ActivityThread (łańcuch AttributionSource → Application),
 * bo część ścieżek frameworka omija Context przekazany do Buildera.
 */
@SuppressLint("PrivateApi", "DiscouragedPrivateApi", "SoonBlockedPrivateApi")
internal class KontekstShell(baza: Context) : ContextWrapper(baza) {

    private val pakiet = PAKIET_SHELL

    init {
        val fakeApp: Application? = runCatching {
            Instrumentation.newApplication(Application::class.java, this)
        }.onFailure { Log.w(TAG, "newApplication: ${it.message}") }.getOrNull()

        var patchy = 0
        runCatching {
            val atCls = Class.forName("android.app.ActivityThread")
            val at = atCls.getMethod("currentActivityThread").invoke(null)
                ?: atCls.getMethod("systemMain").invoke(null)

            runCatching {
                atCls.getDeclaredField("sCurrentActivityThread").apply { isAccessible = true }
                    .set(null, at)
                patchy++
            }
            runCatching {
                atCls.getDeclaredField("mSystemThread").apply { isAccessible = true }
                    .setBoolean(at, true)
                patchy++
            }
            if (fakeApp != null) {
                runCatching {
                    atCls.getDeclaredField("mInitialApplication").apply { isAccessible = true }
                        .set(at, fakeApp)
                    patchy++
                }
            }
            runCatching {
                val mBound = atCls.getDeclaredField("mBoundApplication").apply { isAccessible = true }
                val bound = mBound.get(at) ?: run {
                    val bindCls = Class.forName("android.app.ActivityThread\$AppBindData")
                    val ctor = bindCls.getDeclaredConstructor().apply { isAccessible = true }
                    ctor.newInstance().also { mBound.set(at, it) }
                }
                val appInfo = ApplicationInfo().apply {
                    packageName = pakiet
                    uid = Process.myUid()
                }
                Class.forName("android.app.ActivityThread\$AppBindData")
                    .getDeclaredField("appInfo")
                    .apply { isAccessible = true }
                    .set(bound, appInfo)
                patchy++
            }
        }.onFailure { Log.e(TAG, "patch ActivityThread", it) }

        Log.i(TAG, "KontekstShell uid=${Process.myUid()} patchy=$patchy/4")
    }

    override fun getPackageName(): String = pakiet
    override fun getOpPackageName(): String = pakiet

    override fun getAttributionSource(): AttributionSource =
        AttributionSource.Builder(Process.myUid())
            .setPackageName(pakiet)
            .build()

    override fun getDeviceId(): Int = 0

    override fun getSystemService(name: String): Any? {
        val svc = super.getSystemService(name) ?: return null
        if (name in USLUGI_Z_KONTEKSTEM) {
            runCatching {
                svc.javaClass.getDeclaredField("mContext").apply { isAccessible = true }
                    .set(svc, this)
            }
        }
        return svc
    }

    override fun getApplicationContext(): Context = this
    override fun createAttributionContext(attributionTag: String?): Context = this
    override fun createConfigurationContext(overrideConfiguration: Configuration): Context = this
    override fun createContext(contextParams: ContextParams): Context = this
    override fun createDeviceContext(deviceId: Int): Context = this
    override fun createDisplayContext(display: Display): Context = this
    override fun createPackageContext(packageName: String?, flags: Int): Context = this

    companion object {
        private const val TAG = "NagrywanieShell"
        const val PAKIET_SHELL = "com.android.shell"
        private val USLUGI_Z_KONTEKSTEM = setOf(
            Context.AUDIO_SERVICE,
            Context.TELEPHONY_SERVICE,
            Context.APP_OPS_SERVICE,
            Context.MEDIA_SESSION_SERVICE,
            Context.MEDIA_ROUTER_SERVICE,
        )
    }
}
