package dialer.dominik.pl.call

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.Person

/**
 * Ongoing CallStyle — trwająca rozmowa na pasku statusu.
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
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Powiadomienie o aktywnej rozmowie w toku"
                setSound(null, null)
            }
            manager?.createNotificationChannel(channel)
        }

        val ekranIntent = Intent(context, EkranRozmowyActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, ekranIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // CallStyle wymaga FGS albo fullScreenIntent — bez tego Android rzuca IllegalArgumentException.
        val fullScreenPending = PendingIntent.getActivity(
            context, 20, ekranIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val wyciszPending = PendingIntent.getBroadcast(
            context, 10,
            Intent(context, AkcjaTrwajacejRozmowyReceiver::class.java).apply { action = "AKCJA_WYCISZ" },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val rozlaczPending = PendingIntent.getBroadcast(
            context, 11,
            Intent(context, AkcjaTrwajacejRozmowyReceiver::class.java).apply { action = "AKCJA_ROZLACZ" },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val person = Person.Builder()
            .setName(rozmowca.ifBlank { "Nieznany numer" })
            .setImportant(true)
            .build()

        val style = NotificationCompat.CallStyle.forOngoingCall(person, rozlaczPending)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(dialer.dominik.pl.R.drawable.ic_logo_telefon)
            .setContentTitle("Rozmowa w toku")
            .setContentText(rozmowca)
            .setColor(dialer.dominik.pl.ui.theme.MotywKoloryAndroid.akcentArgb(context))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(fullScreenPending, false)
            .setOngoing(true)
            .setStyle(style)
            .addAction(
                android.R.drawable.ic_lock_silent_mode_off,
                if (wyciszony) "Włącz mikrofon" else "Wycisz",
                wyciszPending
            )
            .build()

        manager?.notify(NOTIFICATION_ID, notification)
    }

    fun anuluj(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.cancel(NOTIFICATION_ID)
    }
}
