package dialer.dominik.pl.call

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.platform.LocalContext
import dialer.dominik.pl.ui.theme.TelefonTheme

/**
 * Aktywność ekranu rozmowy.
 *
 * Pokazuje się nad blokadą ekranu i wyłącza gaszenie — tak jak systemowa
 * aplikacja telefoniczna. Zamyka się sama, gdy połączenie się kończy.
 */
class EkranRozmowyActivity : ComponentActivity() {

    // === Czujnik zbliżeniowy ===
    // Przy uchu: blokada dotyku + PROXIMITY_SCREEN_OFF_WAKE_LOCK (ekran gaśnie).
    private var czujnikZblizenia: Sensor? = null
    private var menedzerCzujnikow: SensorManager? = null
    private var zblizenieAktywne = false
    private var proximityWakeLock: PowerManager.WakeLock? = null

    private val sluchaczZblizenia = object : SensorEventListener {
        override fun onSensorChanged(zdarzenie: SensorEvent?) {
            if (StanPolaczenia.stan != StanPolaczenia.Stan.Rozmowa) {
                if (zblizenieAktywne) {
                    zblizenieAktywne = false
                    ustawZblizenie(false)
                }
                return
            }
            // Przy głośniku nie gasimy ekranu (rozmowa głośnomówiąca).
            if (StanPolaczenia.glosnik) {
                if (zblizenieAktywne) {
                    zblizenieAktywne = false
                    ustawZblizenie(false)
                }
                return
            }
            val wartosc = zdarzenie?.values?.firstOrNull() ?: return
            val blisko = wartosc < (czujnikZblizenia?.maximumRange ?: 0f) / 2f
            if (blisko != zblizenieAktywne) {
                zblizenieAktywne = blisko
                ustawZblizenie(blisko)
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, dokladnosc: Int) {}
    }

    private fun ustawZblizenie(blisko: Boolean) {
        if (blisko) {
            window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
            nabyjProximityWakeLock()
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
            zwolnijProximityWakeLock()
        }
    }

    @Suppress("DEPRECATION")
    private fun nabyjProximityWakeLock() {
        if (proximityWakeLock?.isHeld == true) return
        try {
            val pm = getSystemService(PowerManager::class.java) ?: return
            if (!pm.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) return
            proximityWakeLock = pm.newWakeLock(
                PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                "telefon:proximity"
            ).also {
                it.setReferenceCounted(false)
                it.acquire(30 * 60 * 1000L)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun zwolnijProximityWakeLock() {
        try {
            if (proximityWakeLock?.isHeld == true) {
                proximityWakeLock?.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        proximityWakeLock = null
    }

    override fun onNewIntent(intencja: Intent) {
        super.onNewIntent(intencja)
        if (intencja.action == EkranRozmowy.AKCJA_ZAMKNIJ) {
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.action == EkranRozmowy.AKCJA_ZAMKNIJ) {
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
            return
        }
        // Powrót z dymka: zero systemowej animacji Activity — inaczej „miga / skacze”.
        if (StanPolaczenia.wracaZDymka) {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        @Suppress("UNUSED_VARIABLE")
        val menedzerBlokady = getSystemService(KeyguardManager::class.java)

        setContent {
            TelefonTheme(dynamicColor = false) {
                EkranRozmowy(
                    context = LocalContext.current,
                    onZakoncz = { finish() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        zarejestrujCzujnikZblizenia()
    }

    override fun onPause() {
        super.onPause()
        wyrejestrujCzujnikZblizenia()
        window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        zwolnijProximityWakeLock()
    }

    override fun onDestroy() {
        zwolnijProximityWakeLock()
        super.onDestroy()
    }

    private fun zarejestrujCzujnikZblizenia() {
        if (czujnikZblizenia != null) return
        val menedzer = getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        menedzerCzujnikow = menedzer
        val sensor = menedzer.getDefaultSensor(Sensor.TYPE_PROXIMITY) ?: return
        czujnikZblizenia = sensor
        zblizenieAktywne = false
        menedzer.registerListener(
            sluchaczZblizenia,
            sensor,
            SensorManager.SENSOR_DELAY_NORMAL
        )
    }

    private fun wyrejestrujCzujnikZblizenia() {
        menedzerCzujnikow?.unregisterListener(sluchaczZblizenia)
        menedzerCzujnikow = null
        czujnikZblizenia = null
        zblizenieAktywne = false
    }
}
