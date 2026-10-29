package dialer.dominik.pl.call

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Odpowiada za odtwarzanie systemowego dzwonka i wibracji przy połączeniu przychodzącym.
 * Dla numerów ze spamu wibracja jest delikatniejsza, by łatwiej było je zignorować.
 */
object ZarzadcaDzwonka {
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var czySciszony: Boolean = false

    /**
     * Wycisza dzwonek i przełącza wibracje na delikatny, rzadki puls.
     * Używane, gdy użytkownik kliknie „Ścisz” na przychodzące połączenie,
     * bo nie może teraz odebrać — dzwonek cichnie, ale wibracja informuje.
     */
    @SuppressLint("MissingPermission")
    fun scisz() {
        if (czySciszony) return
        czySciszony = true
        // Zatrzymaj dzwonek i obecną wibrację
        ringtone?.stop()
        ringtone = null
        vibrator?.cancel()
        // Delikatny, rzadki puls (jak przy spamie) — łatwy do zignorowania
        if (vibrator?.hasVibrator() == true) {
            val pattern = longArrayOf(0, 150, 4000)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        }
    }

    @SuppressLint("MissingPermission")
        fun uruchom(context: Context, czySpam: Boolean = false) {
            zatrzymaj()
            czySciszony = false
            try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val ringerMode = audioManager?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL

            // Dzwonek — odtwarzamy, gdy profil dźwięku to NORMAL
            if (ringerMode == AudioManager.RINGER_MODE_NORMAL) {
                val ringtoneUri = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE)
                if (ringtoneUri != null) {
                    ringtone = RingtoneManager.getRingtone(context, ringtoneUri)
                    ringtone?.play()
                }
            }

            // Wibracje — delikatniejsze dla spamu, standardowe dla zwykłych połączeń
            if (ringerMode == AudioManager.RINGER_MODE_NORMAL || ringerMode == AudioManager.RINGER_MODE_VIBRATE) {
                vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                    vibratorManager?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }

                if (vibrator?.hasVibrator() == true) {
                    // Jeśli to spam -> delikatny, rzadki impuls (łatwo olać)
                    // Jeśli normalny -> mocniejszy, pulsujący cykl
                    val pattern = if (czySpam) {
                        longArrayOf(0, 200, 2500)
                    } else {
                        longArrayOf(0, 800, 800)
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator?.vibrate(pattern, 0)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @SuppressLint("MissingPermission")
    fun zatrzymaj() {
        try {
            ringtone?.stop()
            ringtone = null
            vibrator?.cancel()
            vibrator = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
