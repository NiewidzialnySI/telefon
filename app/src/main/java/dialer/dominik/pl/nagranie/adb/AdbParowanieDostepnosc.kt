package dialer.dominik.pl.nagranie.adb

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast

/**
 * Auto‑parowanie **bez zamykania okna z kodem**:
 * klika Wireless → „Sparuj … kodem”, czyta 6 cyfr, oddaje do [AdbParowanieService].
 * Przy WRITE_SECURE usługę można włączyć programowo ([sprobujWlaczyc]).
 */
class AdbParowanieDostepnosc : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var ostatniKod: String? = null
    private var ostatniCzas = 0L
    private var ostatniKlik = 0L
    /** Gdy true — nie klikamy już nic (dialog z kodem musi zostać na wierzchu). */
    private var dialogZKodem = false

    override fun onServiceConnected() {
        serviceInfo = serviceInfo?.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = flags or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 80
        }
        dialogZKodem = false
        ostatniKod = null
        instancja = this
        Toast.makeText(
            this,
            "Auto‑ADB włączone — sama wejdę w „Paruj z kodem” i sparuję.",
            Toast.LENGTH_SHORT,
        ).show()
        // Onboarding / PermissionsScreen: wróć do aplikacji bez ręcznego „wstecz”.
        if (wrocPoWlaczeniu.compareAndSet(true, false)) {
            handler.postDelayed({ wrocDoAplikacji(applicationContext) }, 350)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!AdbParowanieService.czyAktywne()) return
        if (event == null) return

        if (resetNawigacji.compareAndSet(true, false)) {
            dialogZKodem = false
            ostatniKod = null
            ostatniKlik = 0L
        }

        val teksty = zbierzZeWszystkichOkien()
        if (teksty.isEmpty()) return

        val kontekstParowania = teksty.any { t ->
            val n = t.norm()
            FRAZY_KONTEKST_KODU.any { f -> n.contains(f.norm(), ignoreCase = true) }
        }

        if (kontekstParowania) {
            dialogZKodem = true
            val kod = znajdzKod(teksty) ?: return
            val (hostUi, portUi) = znajdzHostPort(teksty)
            val teraz = System.currentTimeMillis()
            if (kod == ostatniKod && teraz - ostatniCzas < 2_500) return
            ostatniKod = kod
            ostatniCzas = teraz
            Log.i(TAG, "odczytano kod=$kod host=$hostUi port=$portUi (dialog otwarty — bez zmiany ekranu)")
            // Tylko service w tle — żadnego startActivity (to zabija dialog / port).
            AdbParowanieService.dostarczKod(applicationContext, kod, portUi, hostUi)
            return
        }

        // Po otwarciu dialogu nie klikamy dalej (nie wolno schować okna z kodem).
        if (dialogZKodem) return

        val pkg = event.packageName?.toString().orEmpty()
        val naUstawieniach = pkg.contains("settings", ignoreCase = true)
        if (!naUstawieniach) return

        val root = rootInActiveWindow ?: return
        try {
            sprobujNawigacje(root, teksty)
        } finally {
            runCatching { root.recycle() }
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instancja === this) instancja = null
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    /** Dialog / overlay bywa osobnym oknem — rootInActiveWindow czasem go nie widzi. */
    private fun zbierzZeWszystkichOkien(): List<String> {
        val out = mutableListOf<String>()
        val wins: List<AccessibilityWindowInfo>? = runCatching { windows }.getOrNull()
        if (!wins.isNullOrEmpty()) {
            for (w in wins) {
                val r = w.root ?: continue
                try {
                    zbierzTeksty(r, out)
                } finally {
                    runCatching { r.recycle() }
                }
            }
        } else {
            val r = rootInActiveWindow
            if (r != null) {
                try {
                    zbierzTeksty(r, out)
                } finally {
                    runCatching { r.recycle() }
                }
            }
        }
        return out
    }

    private fun sprobujNawigacje(root: AccessibilityNodeInfo, teksty: List<String>) {
        val teraz = System.currentTimeMillis()
        if (teraz - ostatniKlik < 1_200) return

        if (kliknijPoSlowie(root, FRAZY_PARUJ)) {
            ostatniKlik = teraz
            Log.i(TAG, "klik: Sparuj/Paruj z kodem — zostawiam dialog na wierzchu")
            return
        }

        if (wlaczPrzelacznikWireless(root)) {
            ostatniKlik = teraz
            Log.i(TAG, "klik: włącz Wireless debugging")
            return
        }

        // Nie klikaj „Wireless” jeśli już jesteśmy na tym ekranie (widać Sparuj / IP:port).
        val juzNaWireless = teksty.any { t ->
            val n = t.norm()
            FRAZY_PARUJ.any { n.contains(it.norm(), ignoreCase = true) } ||
                RE_IP_PORT.containsMatchIn(n)
        }
        if (juzNaWireless) return

        if (teksty.any { t -> FRAZY_WIRELESS.any { f -> t.norm().contains(f.norm(), ignoreCase = true) } }) {
            if (kliknijPoSlowie(root, FRAZY_WIRELESS)) {
                ostatniKlik = teraz
                Log.i(TAG, "klik: Wireless debugging")
            }
        }
    }

    private fun wlaczPrzelacznikWireless(root: AccessibilityNodeInfo): Boolean {
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        zbierzWezly(root, nodes)
        for (n in nodes) {
            val t = (n.text?.toString().orEmpty() + " " + n.contentDescription?.toString().orEmpty()).norm()
            if (FRAZY_WIRELESS.none { t.contains(it.norm(), ignoreCase = true) }) continue
            val sw = znajdzCheckable(n) ?: continue
            if (sw.isChecked) continue
            if (sw.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            if (kliknijWgore(sw)) return true
        }
        return false
    }

    private fun znajdzCheckable(start: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (start.isCheckable) return start
        val parent = start.parent ?: return null
        try {
            for (i in 0 until parent.childCount) {
                val c = parent.getChild(i) ?: continue
                if (c.isCheckable) return c
            }
            if (parent.isCheckable) return parent
            val g = parent.parent ?: return null
            for (i in 0 until g.childCount) {
                val c = g.getChild(i) ?: continue
                val found = znajdzCheckableWPoddrzewie(c)
                if (found != null) return found
            }
        } catch (_: Throwable) {
        }
        return null
    }

    private fun znajdzCheckableWPoddrzewie(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (n.isCheckable) return n
        for (i in 0 until n.childCount) {
            val c = n.getChild(i) ?: continue
            val f = znajdzCheckableWPoddrzewie(c)
            if (f != null) return f
        }
        return null
    }

    private fun kliknijPoSlowie(root: AccessibilityNodeInfo, frazy: List<String>): Boolean {
        for (f in frazy) {
            val found = root.findAccessibilityNodeInfosByText(f) ?: continue
            for (n in found) {
                if (kliknijWgore(n)) return true
            }
        }
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        zbierzWezly(root, nodes)
        for (n in nodes) {
            val t = (n.text?.toString().orEmpty() + " " +
                n.contentDescription?.toString().orEmpty()).norm()
            if (frazy.none { t.contains(it.norm(), ignoreCase = true) }) continue
            if (kliknijWgore(n)) return true
        }
        return false
    }

    private fun kliknijWgore(node: AccessibilityNodeInfo): Boolean {
        var cur: AccessibilityNodeInfo? = node
        var depth = 0
        while (cur != null && depth < 8) {
            if (cur.isClickable || cur.isCheckable) {
                if (cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            }
            cur = cur.parent
            depth++
        }
        return false
    }

    private fun zbierzTeksty(node: AccessibilityNodeInfo, out: MutableList<String>) {
        node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { out.add(it) }
        node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { out.add(it) }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                zbierzTeksty(child, out)
            } finally {
                runCatching { child.recycle() }
            }
        }
    }

    private fun zbierzWezly(node: AccessibilityNodeInfo, out: MutableList<AccessibilityNodeInfo>) {
        out.add(node)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            zbierzWezly(child, out)
        }
    }

    companion object {
        private const val TAG = "AdbParowanieA11y"
        private val RE_KOD = Regex("""(?<!\d)\d{6}(?!\d)""")
        private val RE_IP_PORT = Regex("""(\d{1,3}(?:\.\d{1,3}){3})\s*:\s*(\d{2,5})""")
        /** Ustawiane przy starcie FGS — a11y znów może klikać ścieżkę do dialogu. */
        private val resetNawigacji = java.util.concurrent.atomic.AtomicBoolean(false)

        fun oznaczNowaSesjaParowania() {
            resetNawigacji.set(true)
        }

        private fun String.norm(): String =
            replace('\u00a0', ' ')
                .replace('\u2011', '-')
                .replace('\u2013', '-')
                .replace('\u2014', '-')

        private val FRAZY_KONTEKST_KODU = listOf(
            "Wi-Fi pairing code",
            "Wi‑Fi pairing code",
            "Kod parowania Wi-Fi",
            "Kod parowania Wi‑Fi",
            "kod parowania",
            "pairing code",
            "Sparuj z urządzeniem",
            "Pair with device",
            "ANULUJ", // dialog ColorOS ma Anuluj + kod
        )

        private val FRAZY_PARUJ = listOf(
            "Sparuj urządzenie przy pomocy kodu parowania",
            "Sparuj nowe urządzenie przy pomocy 6-cyfrowego kodu",
            "Sparuj urządzenie przy pomocy kodu",
            "Paruj urządzenie z kodem",
            "Paruj urządzenie za pomocą kodu",
            "Paruj z kodem",
            "Pair device with pairing code",
            "Pair with pairing code",
        )

        private val FRAZY_WIRELESS = listOf(
            "Debugowanie bezprzewodowe",
            "Debugowanie Wi-Fi",
            "Debugowanie Wi‑Fi",
            "Wireless debugging",
            "Wireless Debugging",
        )

        fun znajdzKod(teksty: List<String>): String? {
            for (t in teksty) {
                val bezIp = t.norm().replace(RE_IP_PORT, " ")
                val m = RE_KOD.find(bezIp) ?: continue
                return m.value
            }
            for (t in teksty) {
                if (RE_IP_PORT.containsMatchIn(t)) continue
                val tylko = t.filter { it.isDigit() }
                if (tylko.length == 6) return tylko
            }
            val pojedyncze = teksty.map { it.trim() }
                .filter { it.length == 1 && it[0].isDigit() }
            if (pojedyncze.size >= 6) return pojedyncze.takeLast(6).joinToString("")
            return null
        }

        fun znajdzHostPort(teksty: List<String>): Pair<String?, Int> {
            for (t in teksty) {
                val m = RE_IP_PORT.find(t.norm()) ?: continue
                val host = m.groupValues[1]
                val p = m.groupValues[2].toIntOrNull() ?: continue
                if (p in 1..65535) return host to p
            }
            return null to -1
        }

        fun czyWlaczona(ctx: Context): Boolean {
            val enabled = Settings.Secure.getString(
                ctx.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            val cn = ComponentName(ctx, AdbParowanieDostepnosc::class.java)
            val flat = cn.flattenToString()
            return enabled.split(':').any {
                it.equals(flat, ignoreCase = true) ||
                    (it.contains(ctx.packageName) && it.contains("AdbParowanieDostepnosc"))
            }
        }

        /**
         * Włącza usługę a11y bez UI — wymaga WRITE_SECURE_SETTINGS
         * (jak `adb shell settings put secure enabled_accessibility_services`).
         */
        fun sprobujWlaczyc(ctx: Context): Boolean {
            if (czyWlaczona(ctx)) return true
            val app = ctx.applicationContext
            if (!dialer.dominik.pl.nagranie.ShizukuAdbNaprawa.maWriteSecure(app)) {
                Log.w(TAG, "sprobujWlaczyc: brak WRITE_SECURE")
                return false
            }
            val cr = app.contentResolver
            val cn = ComponentName(app, AdbParowanieDostepnosc::class.java).flattenToString()
            val cur = Settings.Secure.getString(
                cr,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ).orEmpty()
            val set = cur.split(':').map { it.trim() }.filter { it.isNotEmpty() }.toMutableSet()
            set += cn
            val joined = set.joinToString(":")
            val putSvc = Settings.Secure.putString(
                cr,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                joined,
            )
            val putOn = Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            val ok = putSvc && putOn && czyWlaczona(app)
            Log.i(TAG, "sprobujWlaczyc putSvc=$putSvc putOn=$putOn ok=$ok")
            return ok
        }

        fun otworzUstawienia(ctx: Context) {
            runCatching {
                ctx.startActivity(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }

        /** PermissionsScreen: po włączeniu przełącznika wróć do dialera. */
        fun oznaczWrocPoWlaczeniu() {
            wrocPoWlaczeniu.set(true)
        }

        fun wrocDoAplikacji(ctx: Context) {
            val app = ctx.applicationContext
            // Wyjdź z karty usługi (ColorOS zostawia użytkownika w Settings).
            runCatching {
                // GLOBAL_ACTION_BACK — dostępne tylko z działającej usługi.
                instancja?.let { svc ->
                    svc.performGlobalAction(GLOBAL_ACTION_BACK)
                    svc.handler.postDelayed({
                        svc.performGlobalAction(GLOBAL_ACTION_BACK)
                    }, 180)
                }
            }
            val launch = app.packageManager.getLaunchIntentForPackage(app.packageName)
                ?: Intent().setClassName(app, "dialer.dominik.pl.MainActivity")
            launch.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP,
            )
            // Po krótkim BACK — podnieś aplikację.
            Handler(Looper.getMainLooper()).postDelayed({
                runCatching { app.startActivity(launch) }
                    .onFailure { Log.w(TAG, "wrocDoAplikacji", it) }
            }, 280)
        }

        @Volatile
        var instancja: AdbParowanieDostepnosc? = null
            private set
        private val wrocPoWlaczeniu = java.util.concurrent.atomic.AtomicBoolean(false)
    }
}
