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
 * Powiadomienia przychodzące — CallStyle (Android 12+) jak natywny dialer.
 */
object PowiadomieniaPolaczen {
    private const val CHANNEL_ID = "polaczenia_przychodzace_ch"
    private const val NOTIFICATION_ID = 1001

    fun pokazBaner(context: Context, rozmowca: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Połączenia przychodzące",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Powiadomienia banerowe o przychodzących połączeniach"
                setSound(null, null)
            }
            manager?.createNotificationChannel(channel)
        }

        val fullScreenIntent = Intent(context, EkranRozmowyActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context, 0, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val odbierzPendingIntent = PendingIntent.getBroadcast(
            context, 1,
            Intent(context, AkcjaPolaczeniaReceiver::class.java).apply { action = "AKCJA_ODBIERZ" },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val odrzucPendingIntent = PendingIntent.getBroadcast(
            context, 2,
            Intent(context, AkcjaPolaczeniaReceiver::class.java).apply { action = "AKCJA_ODRZUC" },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val person = Person.Builder()
            .setName(rozmowca.ifBlank { "Nieznany numer" })
            .setImportant(true)
            .build()

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(dialer.dominik.pl.R.drawable.ic_logo_telefon)
            .setContentTitle("Połączenie przychodzące")
            .setContentText(rozmowca)
            .setColor(0xFF69F0AE.toInt())
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(fullScreenPendingIntent)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setOngoing(true)
            .setAutoCancel(false)
            .setStyle(
                NotificationCompat.CallStyle.forIncomingCall(
                    person,
                    odrzucPendingIntent,
                    odbierzPendingIntent
                )
            )

        manager?.notify(NOTIFICATION_ID, builder.build())
    }

    fun anuluj(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.cancel(NOTIFICATION_ID)
    }

    fun pokazBubble(context: Context, rozmowca: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

        val intent = Intent(context, EkranRozmowyActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val tapPendingIntent = PendingIntent.getActivity(
            context, 3, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        val powrotPendingIntent = PendingIntent.getActivity(
            context, 4, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val rozlaczPendingIntent = PendingIntent.getBroadcast(
            context, 5,
            Intent(context, AkcjaPolaczeniaReceiver::class.java).apply { action = "AKCJA_ROZLACZ" },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val ikona = androidx.core.graphics.drawable.IconCompat.createWithResource(
            context, dialer.dominik.pl.R.drawable.ic_logo_telefon
        )
        val bubbleMeta = NotificationCompat.BubbleMetadata.Builder(tapPendingIntent, ikona)
            .setDesiredHeight(1200)
            .setAutoExpandBubble(true)
            .build()

        val person = Person.Builder()
            .setName(rozmowca.ifBlank { "Nieznany numer" })
            .setImportant(true)
            .build()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(dialer.dominik.pl.R.drawable.ic_logo_telefon)
            .setContentTitle("Rozmowa trwa")
            .setContentText(rozmowca)
            .setColor(0xFF69F0AE.toInt())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(powrotPendingIntent)
            .setBubbleMetadata(bubbleMeta)
            .setOngoing(true)
            .setStyle(
                NotificationCompat.CallStyle.forOngoingCall(person, rozlaczPendingIntent)
            )
            .build()

        manager?.notify(NOTIFICATION_ID, notification)
    }

    fun aktualizujBubble(context: Context, rozmowca: String) {
        pokazBubble(context, rozmowca)
    }
}
