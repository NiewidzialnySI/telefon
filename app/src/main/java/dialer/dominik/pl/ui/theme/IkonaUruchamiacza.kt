package dialer.dominik.pl.ui.theme

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager

/**
 * Dynamiczna ikona launchera — `activity-alias` + [PackageManager.setComponentEnabledSetting].
 *
 * **Ważne:** wyłączenie aliasu, z którego aktualnie działa aktywność, niszczy tę aktywność
 * (nawet przy [PackageManager.DONT_KILL_APP]). Dlatego przy zmianie motywu w foreground
 * tylko włączamy nowy alias, a stary wyłączamy dopiero w [zastosujPoWyjsciu] (onStop).
 */
object IkonaUruchamiacza {

    private const val DOMYSLNY = "neon_zielony"

    @Volatile
    private var oczekujacaPaletaId: String? = null

    /** Alias w manifeście: `.launcher.Ikona_<id>` */
    private fun komponent(context: Context, paletaId: String): ComponentName {
        val bezpieczny = paletaId.ifBlank { DOMYSLNY }
        return ComponentName(context, "${context.packageName}.launcher.Ikona_$bezpieczny")
    }

    private fun znormalizuj(paletaId: String?): String = when {
        paletaId.isNullOrBlank() -> DOMYSLNY
        MotywPaletyPresety.poId(paletaId) != null -> paletaId
        else -> DOMYSLNY
    }

    private fun activityZ(context: Context): Activity? {
        var c: Context? = context
        while (c is ContextWrapper) {
            if (c is Activity) return c
            c = c.baseContext
        }
        return null
    }

    /**
     * Synchronizacja z UI / startu. W foreground nie wyłącza komponentu bieżącej aktywności.
     */
    fun synchronizuj(context: Context, paletaId: String?) {
        val docelowy = znormalizuj(paletaId)
        oczekujacaPaletaId = docelowy
        val chroniony = activityZ(context)
            ?.takeUnless { it.isFinishing }
            ?.componentName
        zastosuj(context, docelowy, nieWylaczaj = chroniony)
    }

    /**
     * Pełne przełączenie (tylko jeden alias włączony) — wołać z [Activity.onStop],
     * gdy użytkownik już wyszedł z aplikacji.
     */
    fun zastosujPoWyjsciu(context: Context) {
        val docelowy = oczekujacaPaletaId ?: return
        zastosuj(context, docelowy, nieWylaczaj = null)
    }

    private fun zastosuj(
        context: Context,
        docelowy: String,
        nieWylaczaj: ComponentName?
    ) {
        val pm = context.packageManager
        val docelowyCn = komponent(context, docelowy)

        // Najpierw włącz nowy — żeby launcher zawsze miał choć jeden MAIN/LAUNCHER.
        runCatching {
            pm.setComponentEnabledSetting(
                docelowyCn,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
        }

        for (id in MotywPaletyPresety.Wszystkie.map { it.id }) {
            if (id == docelowy) continue
            val cn = komponent(context, id)
            if (nieWylaczaj != null && cn == nieWylaczaj) continue
            runCatching {
                pm.setComponentEnabledSetting(
                    cn,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
        }
    }
}
