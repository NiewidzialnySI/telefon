package dialer.dominik.pl.call

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * Zarządza powiadomieniami typu Heads-up (baner) dla połączeń przychodzących,
 * gdy telefon jest odblokowany przez użytkownika.
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
                setSound(null, null) // Dzwonek odtwarza dedykowany ZarzadcaDzwonka
            }
            manager?.createNotificationChannel(channel)
        }

        // Kliknięcie w baner otwiera pełny ekran rozmowy
        val fullScreenIntent = Intent(context, EkranRozmowyActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context, 0, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Akcja Odbierz z banera
        val odbierzIntent = Intent(context, AkcjaPolaczeniaReceiver::class.java).apply {
            action = "AKCJA_ODBIERZ"
        }
        val odbierzPendingIntent = PendingIntent.getBroadcast(
            context, 1, odbierzIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Akcja Odrzuć z banera
        val odrzucIntent = Intent(context, AkcjaPolaczeniaReceiver::class.java).apply {
            action = "AKCJA_ODRZUC"
        }
        val odrzucPendingIntent = PendingIntent.getBroadcast(
            context, 2, odrzucIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(dialer.dominik.pl.R.drawable.ic_logo_telefon)
                    .setContentTitle("📞 Połączenie przychodzące")
                    .setContentText(rozmowca)
                    .setColor(0xFF69F0AE.toInt())
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_CALL)
                    .setContentIntent(fullScreenPendingIntent)
                    .setFullScreenIntent(fullScreenPendingIntent, true)
                    .setColorized(true)
                    .addAction(android.R.drawable.ic_menu_call, "Odbierz", odbierzPendingIntent)
                    .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Odrzuć", odrzucPendingIntent)
                    .setAutoCancel(true)
                    .setOngoing(true)
                    .build()

        manager?.notify(NOTIFICATION_ID, notification)
    }

    fun anuluj(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.cancel(NOTIFICATION_ID)
    }

    /**
     * Pokazuje trwającą rozmowę jako przemieszczalny dymek (Bubble, jak Messenger).
     */
    fun pokazBubble(context: Context, rozmowca: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

        val intent = Intent(context, EkranRozmowyActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        // BubbleMetadata wymaga MUTABLE PendingIntent (immutable rzuca
                // IllegalArgumentException:"PendingIntents attached to bubbles must be mutable").
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

        val bubbleMeta = androidx.core.app.NotificationCompat.BubbleMetadata.Builder(tapPendingIntent, ikona)
                    .setDesiredHeight(1200)
                    .setAutoExpandBubble(true)
                    .build()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(dialer.dominik.pl.R.drawable.ic_logo_telefon)
            .setContentTitle("📞 Rozmowa trwa")
            .setContentText(rozmowca)
            .setColor(0xFF69F0AE.toInt())
            .setColorized(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(powrotPendingIntent)
            .setBubbleMetadata(bubbleMeta)
            .addAction(android.R.drawable.ic_menu_call, "Pokaż rozmowę", powrotPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Rozłącz", rozlaczPendingIntent)
            .setAutoCancel(false)
            .setOngoing(true)
            .build()

        manager?.notify(NOTIFICATION_ID, notification)
    }

    /** Odświeża numer/tytuł istniejącego bubble (bez zamykania powiadomienia). */
    fun aktualizujBubble(context: Context, rozmowca: String) {
        pokazBubble(context, rozmowca)
    }
}
