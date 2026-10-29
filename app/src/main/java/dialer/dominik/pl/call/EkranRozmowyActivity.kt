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
    // Gdy trwa rozmowa i telefon jest przy uchu, wyłączamy dotyk na ekranie,
    // żeby przypadkowo nie kliknąć "Rozłącz"/"Głośnik" policzkiem albo uchem.
    private var czujnikZblizenia: Sensor? = null
    private var menedzerCzujnikow: SensorManager? = null
    private var zblizenieAktywne = false

    private val sluchaczZblizenia = object : SensorEventListener {
        override fun onSensorChanged(zdarzenie: SensorEvent?) {
            // Czujnik działa tylko podczas TRWAJĄCEJ rozmowy (nie przy dzwonieniu,
            // wybieraniu numeru itd.) — przy uchu ma blokować dotyk przycisków.
            if (StanPolaczenia.stan != StanPolaczenia.Stan.Rozmowa) {
                if (zblizenieAktywne) {
                    zblizenieAktywne = false
                    ustawDotykZgodnieZZblizeniem()
                }
                return
            }
            // TYPE_PROXIMITY zwraca wartość bliską 0 gdy coś jest blisko sensora
            // (np. ucho/twarz), a wartość zbliżoną do maxRange gdy jest daleko.
            val wartosc = zdarzenie?.values?.firstOrNull() ?: return
            val blisko = wartosc < (czujnikZblizenia?.maximumRange ?: 0f) / 2f
            // Zmiana tylko w jedną stronę — unikamy ciągłego przeliczania przy szumie sensora.
            if (blisko != zblizenieAktywne) {
                zblizenieAktywne = blisko
                ustawDotykZgodnieZZblizeniem()
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, dokladnosc: Int) {}
    }

    /** Wg. czujnika: blisko = bez dotyku na ekranie; daleko = dotyk dostępny. */
    private fun ustawDotykZgodnieZZblizeniem() {
        if (zblizenieAktywne) {
            window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        }
    }

    override fun onNewIntent(intencja: Intent) {
        super.onNewIntent(intencja)
        // Przyszła prośba o zamknięcie ekranu — połączenie się skończyło
        if (intencja.action == EkranRozmowy.AKCJA_ZAMKNIJ) {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.action == EkranRozmowy.AKCJA_ZAMKNIJ) {
            finish()
            return
        }
        enableEdgeToEdge()

        // Ekran rozmowy musi być widoczny nad blokadą i nie może zgasnąć
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

        val menedzerBlokady = getSystemService(KeyguardManager::class.java)
        if (menedzerBlokady?.isKeyguardLocked == true) {
            // Odblokowanie nie jest wymagane — ekran rozmowy ma działać nad blokadą
        }

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
        // Zarejestruj czujnik zbliżeniowy. Handler w onSensorChanged decyduje,
        // czy aktywować blokadę dotyku (tylko podczas trwającej rozmowy).
        zarejestrujCzujnikZblizenia()
    }

    override fun onPause() {
        super.onPause()
        // Zawsze wyrejestruj i włącz dotyk z powrotem, gdy aktywność traci fokus.
        wyrejestrujCzujnikZblizenia()
        window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
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
