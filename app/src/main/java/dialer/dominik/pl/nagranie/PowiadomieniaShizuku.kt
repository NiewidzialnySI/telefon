package dialer.dominik.pl.nagranie

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import dialer.dominik.pl.R
import dialer.dominik.pl.nagranie.adb.ShellAdbBootstrap

/** Powiadomienie: brak silnika shell (własne ADB / zapas Shizuku) → [ShizukuNaprawaReceiver]. */
object PowiadomieniaShizuku {
    private const val KANAL = "shizuku_nagrywanie"
    private const val ID = 3101
    private const val ID_POSTEP = 3102

    fun upewnijKanal(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(
                KANAL,
                "Nagrywanie rozmów",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Gdy usługa nagrywania obu stron jest niedostępna"
            },
        )
    }

    fun pokazBrak(ctx: Context, opis: String) {
        if (!NagrywanieUstawienia.czyPowiadomieniaShizuku(ctx)) return
        if (!NagrywanieUstawienia.czyWlaczone(ctx)) return
        // Jeszcze raz — mogło się podnieść w międzyczasie.
        if (ShellKlient.czyShellGotowy()) {
            anulujBrak(ctx)
            return
        }
        upewnijKanal(ctx)
        val napraw = PendingIntent.getBroadcast(
            ctx,
            0,
            Intent(ctx, ShizukuNaprawaReceiver::class.java).setAction(ShizukuNaprawaReceiver.AKCJA_NAPRAW),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val wlasne = ShellAdbBootstrap.czySparowane(ctx)
        val tytul = if (wlasne) "Usługa nagrywania nie działa" else "Nagrywanie obu stron niedostępne"
        val hint = if (wlasne) {
            "Dotknij → włącz Wireless debugging i podnieś usługę ADB."
        } else {
            "Dotknij → automatyczna naprawa (ADB / opcjonalnie Shizuku)."
        }
        val n = NotificationCompat.Builder(ctx, KANAL)
            .setSmallIcon(R.drawable.ic_logo_telefon)
            .setContentTitle(tytul)
            .setContentText(opis.ifBlank { hint })
            .setStyle(
                NotificationCompat.BigTextStyle().bigText("$opis\n\n$hint"),
            )
            .setContentIntent(napraw)
            .addAction(0, "Napraw", napraw)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        ctx.getSystemService(NotificationManager::class.java)?.notify(ID, n)
    }

    fun pokazPostep(ctx: Context, tekst: String) {
        upewnijKanal(ctx)
        val n = NotificationCompat.Builder(ctx, KANAL)
            .setSmallIcon(R.drawable.ic_logo_telefon)
            .setContentTitle("Uruchamianie nagrywania")
            .setContentText(tekst)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(0, 0, true)
            .build()
        ctx.getSystemService(NotificationManager::class.java)?.notify(ID_POSTEP, n)
    }

    fun pokazWynik(ctx: Context, ok: Boolean, tekst: String) {
        upewnijKanal(ctx)
        anulujPostep(ctx)
        if (ok) {
            anulujBrak(ctx)
            return
        }
        val n = NotificationCompat.Builder(ctx, KANAL)
            .setSmallIcon(R.drawable.ic_logo_telefon)
            .setContentTitle("Nagrywanie — naprawa")
            .setContentText(tekst)
            .setStyle(NotificationCompat.BigTextStyle().bigText(tekst))
            .setAutoCancel(true)
            .build()
        ctx.getSystemService(NotificationManager::class.java)?.notify(ID, n)
    }

    fun anulujBrak(ctx: Context) {
        ctx.getSystemService(NotificationManager::class.java)?.cancel(ID)
    }

    fun anulujPostep(ctx: Context) {
        ctx.getSystemService(NotificationManager::class.java)?.cancel(ID_POSTEP)
    }
}
