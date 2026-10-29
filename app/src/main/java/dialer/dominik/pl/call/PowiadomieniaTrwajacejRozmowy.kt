package dialer.dominik.pl.call

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * Zarządza powiadomieniem trwającej rozmowy (Ongoing Notification) na pasku statusu,
 * pozwalając użytkownikowi wyciszyć mikrofon, rozłączyć się lub wrócić do ekranu rozmowy.
 */
object PowiadomieniaTrwajacejRozmowy {
    private const val CHANNEL_ID = "trwajaca_rozmowa_ch"
    private const val NOTIFICATION_ID = 2002

    fun pokazPowiadomienie(context: Context, rozmowca: String, wyciszony: Boolean) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Trwające połączenie",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Powiadomienie o aktywnej rozmowie w toku"
            }
            manager?.createNotificationChannel(channel)
        }

        // Kliknięcie w powiadomienie otwiera pełny ekran rozmowy
        val intent = Intent(context, EkranRozmowyActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Akcja Wycisz / Włącz mikrofon
        val wyciszIntent = Intent(context, AkcjaTrwajacejRozmowyReceiver::class.java).apply {
            action = "AKCJA_WYCISZ"
        }
        val wyciszPending = PendingIntent.getBroadcast(
            context, 10, wyciszIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Akcja Rozłącz
        val rozlaczIntent = Intent(context, AkcjaTrwajacejRozmowyReceiver::class.java).apply {
            action = "AKCJA_ROZLACZ"
        }
        val rozlaczPending = PendingIntent.getBroadcast(
            context, 11, rozlaczIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val akcjaWyciszTekst = if (wyciszony) "Włącz mikrofon" else "Wycisz"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(dialer.dominik.pl.R.drawable.ic_logo_telefon)
                    .setContentTitle("Rozmowa w toku")
                    .setContentText(rozmowca)
                    .setColor(0xFF69F0AE.toInt())
                    .setColorized(true)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setCategory(NotificationCompat.CATEGORY_CALL)
                    .setContentIntent(pendingIntent)
                    .setOngoing(true)
                    .addAction(android.R.drawable.ic_lock_silent_mode_off, akcjaWyciszTekst, wyciszPending)
                    .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Rozłącz", rozlaczPending)
                    .build()

        manager?.notify(NOTIFICATION_ID, notification)
    }

    fun anuluj(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.cancel(NOTIFICATION_ID)
    }
}
