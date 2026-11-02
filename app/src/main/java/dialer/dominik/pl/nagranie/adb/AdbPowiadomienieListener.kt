package dialer.dominik.pl.nagranie.adb

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import dialer.dominik.pl.call.PowiadomieniaPolaczen
import dialer.dominik.pl.call.StanPolaczenia
import dialer.dominik.pl.nagranie.NagrywanieUstawienia

/**
 * Kasuje systemowy baner „Wireless / USB debugging”, który po [AdbNaprawa.wlacAdb]
 * potrafi zabić CallStyle (odbierz z powiadomienia) przy przychodzącym.
 *
 * Wymaga: Ustawienia → Powiadomienia → dostęp do powiadomień → ta apka.
 */
class AdbPowiadomienieListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        instancja = this
        Log.i(TAG, "listener connected")
        kasujJesliTrzeba()
    }

    override fun onListenerDisconnected() {
        if (instancja === this) instancja = null
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        if (!czyChronimyBanerPolaczenia()) return
        if (czyAdbBaner(sbn)) {
            Log.i(TAG, "kasuję ADB baner pkg=${sbn.packageName} ch=${sbn.notification.channelId}")
            runCatching { cancelNotification(sbn.key) }
            // System często zdejmuje CallStyle razem z banerem ADB — przywróć.
            przywrocCallStyleJesliDzwoni()
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return
        if (!czyChronimyBanerPolaczenia()) return
        if (czyAdbBaner(sbn)) {
            // Baner ADB zniknął (my / user / system) — upewnij się że CallStyle wrócił.
            przywrocCallStyleJesliDzwoni()
        }
    }

    fun kasujAdbBanery() {
        val aktywne = runCatching { activeNotifications }.getOrNull() ?: return
        for (sbn in aktywne) {
            if (czyAdbBaner(sbn)) {
                Log.i(TAG, "sweep kasuję ${sbn.key}")
                runCatching { cancelNotification(sbn.key) }
            }
        }
    }

    private fun kasujJesliTrzeba() {
        if (czyChronimyBanerPolaczenia()) {
            kasujAdbBanery()
            przywrocCallStyleJesliDzwoni()
        }
    }

    companion object {
        private const val TAG = "AdbBaner"

        @Volatile private var instancja: AdbPowiadomienieListener? = null

        private val mainHandler = Handler(Looper.getMainLooper())

        /** W trakcie dzwonienia / rozmowy — broń CallStyle przed banerem ADB. */
        fun czyChronimyBanerPolaczenia(): Boolean {
            val s = StanPolaczenia.stan
            return s == StanPolaczenia.Stan.Dzwoni ||
                s == StanPolaczenia.Stan.Rozmowa ||
                s == StanPolaczenia.Stan.Zawieszone
        }

        fun kasujTeraz() {
            instancja?.kasujAdbBanery()
        }

        /**
         * Po skasowaniu banera ADB przywróć CallStyle, jeśli nadal dzwoni przychodzące
         * (system potrafi ukryć nasze powiadomienie 1001 razem z banerem ADB).
         */
        fun przywrocCallStyleJesliDzwoni() {
            if (StanPolaczenia.stan != StanPolaczenia.Stan.Dzwoni) return
            if (!StanPolaczenia.czyPrzychodzace) return
            val ctx = instancja?.applicationContext ?: return
            val rozmowca = StanPolaczenia.rozmowca
            mainHandler.post {
                if (StanPolaczenia.stan != StanPolaczenia.Stan.Dzwoni) return@post
                if (!StanPolaczenia.czyPrzychodzace) return@post
                Log.i(TAG, "przywracam CallStyle rozmowca=$rozmowca")
                PowiadomieniaPolaczen.pokazBaner(ctx, rozmowca)
            }
            // Drugi raz po krótkim opóźnieniu — shade/ADB bywa wolniejszy niż cancel.
            mainHandler.postDelayed({
                if (StanPolaczenia.stan != StanPolaczenia.Stan.Dzwoni) return@postDelayed
                if (!StanPolaczenia.czyPrzychodzace) return@postDelayed
                PowiadomieniaPolaczen.pokazBaner(ctx, StanPolaczenia.rozmowca)
            }, 350L)
        }

        fun czyWlaczony(ctx: Context): Boolean {
            val cn = ComponentName(ctx, AdbPowiadomienieListener::class.java)
            val flat = Settings.Secure.getString(
                ctx.contentResolver,
                "enabled_notification_listeners",
            ).orEmpty()
            return flat.split(':').any {
                ComponentName.unflattenFromString(it)?.packageName == ctx.packageName &&
                    (ComponentName.unflattenFromString(it)?.className == cn.className ||
                        it.contains("AdbPowiadomienieListener"))
            } || flat.contains(ctx.packageName)
        }

        fun otworzUstawienia(ctx: Context) {
            val ok = runCatching {
                ctx.startActivity(
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }.isSuccess
            if (!ok) {
                runCatching {
                    ctx.startActivity(
                        Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
        }

        /** Po wlacAdb — od razu spróbuj zgasić baner (kilka prób, system czasem spóźnia). */
        fun poWlaczeniuAdb(ctx: Context) {
            if (!NagrywanieUstawienia.czyWlaczone(ctx) &&
                !NagrywanieUstawienia.czyAdbTylkoNaRozmowe(ctx)
            ) {
                return
            }
            kasujTeraz()
            przywrocCallStyleJesliDzwoni()
            mainHandler.apply {
                postDelayed({
                    kasujTeraz()
                    przywrocCallStyleJesliDzwoni()
                }, 400)
                postDelayed({
                    kasujTeraz()
                    przywrocCallStyleJesliDzwoni()
                }, 1_200)
                postDelayed({
                    kasujTeraz()
                    przywrocCallStyleJesliDzwoni()
                }, 2_500)
            }
        }

        fun czyAdbBaner(sbn: StatusBarNotification): Boolean {
            val pkg = sbn.packageName
            if (pkg != "android" &&
                pkg != "com.android.systemui" &&
                pkg != "com.android.settings" &&
                !pkg.contains("systemui")
            ) {
                return false
            }
            val ch = sbn.notification.channelId.orEmpty().uppercase()
            if (ch.contains("DEVELOPER") || ch.contains("ADB") || ch.contains("DEBUG")) {
                return true
            }
            val extras = sbn.notification.extras
            val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
            val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
            val joined = "$title $text".lowercase()
            return joined.contains("debugowan") ||
                joined.contains("debugging") ||
                joined.contains("wireless debugging") ||
                joined.contains("debugowanie usb") ||
                joined.contains("debugowanie bezprzewod") ||
                joined.contains("bezprzewodow") && joined.contains("debug") ||
                (joined.contains("adb") && (joined.contains("usb") || joined.contains("wifi") ||
                    joined.contains("wi-fi") || joined.contains("wireless")))
        }
    }
}
