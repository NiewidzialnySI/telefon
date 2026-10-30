package dialer.dominik.pl.call

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import dialer.dominik.pl.MainActivity
import dialer.dominik.pl.R
import dialer.dominik.pl.ui.theme.MotywKoloryAndroid
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Wpis o pojedynczym nieodebranym połączeniu. */
data class WpisNieodebrany(
    val numer: String,
    val rozmowca: String,
    val czasMs: Long = System.currentTimeMillis()
)

/**
 * Zbiorcze powiadomienia o nieodebranych połączeniach.
 * Pamięta nieodebrane połączenia tylko do momentu odrzucenia/usunięcia z paska.
 */
object PowiadomieniaNieodebranych {
    private const val CHANNEL_ID = "nieodebrane_polaczenia_ch"
    private const val NOTIFICATION_ID = 1003
    const val EXTRA_NUMER = "extra_numer_nieodebrany"
    const val ACTION_OTWORZ_SMS = "dialer.dominik.pl.OTWORZ_SMS"

    /** Lista aktywnych nieodebranych połączeń od ostatniego czyszczenia. */
    private val listaNieodebranych = mutableListOf<WpisNieodebrany>()

    @Synchronized
    fun dodajIPokaz(context: Context, rozmowca: String, numer: String) {
        val czasMs = System.currentTimeMillis()
        listaNieodebranych.add(WpisNieodebrany(numer = numer, rozmowca = rozmowca, czasMs = czasMs))
        odswiezPowiadomienie(context)
    }

    @Synchronized
    fun wyczysc(context: Context) {
        listaNieodebranych.clear()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.cancel(NOTIFICATION_ID)
    }

    private fun odswiezPowiadomienie(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        if (listaNieodebranych.isEmpty()) {
            manager.cancel(NOTIFICATION_ID)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Nieodebrane połączenia",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Powiadomienia o nieodebranych połączeniach przychodzących"
            }
            manager.createNotificationChannel(channel)
        }

        val df = SimpleDateFormat("HH:mm", Locale.getDefault())

        // Kliknięcie w powiadomienie -> otwiera historię połączeń w MainActivity i czyści listę
        val glownaIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val glownaPendingIntent = PendingIntent.getActivity(
            context, 101, glownaIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Intencja usunięcia (przesunięcie w lewo/prawo) -> czyszczenie pamięci
        val odrzucIntent = Intent(context, AkcjaPolaczeniaReceiver::class.java).apply {
            action = "AKCJA_ANULUJ_NIEODEBRANE"
        }
        val odrzucPendingIntent = PendingIntent.getBroadcast(
            context, 104, odrzucIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_logo_telefon)
            .setColor(MotywKoloryAndroid.akcentArgb(context))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
            .setContentIntent(glownaPendingIntent)
            .setDeleteIntent(odrzucPendingIntent)
            .setAutoCancel(true)

        if (listaNieodebranych.size == 1) {
            // Pojedyncze nieodebrane połączenie
            val wpis = listaNieodebranych.first()
            val godzinaFmt = df.format(Date(wpis.czasMs))

            val nazwaKontaktu = WeryfikacjaNumeru.pobierzNazweKontaktu(context, wpis.numer)
            val pseudonim = nazwaKontaktu?.let { dialer.dominik.pl.ui.screens.pobierzPseudonim(context, it) }

            val etykietaOsoby = when {
                !pseudonim.isNullOrBlank() && pseudonim != nazwaKontaktu -> "$pseudonim ($nazwaKontaktu)"
                !nazwaKontaktu.isNullOrBlank() -> nazwaKontaktu
                wpis.rozmowca.isNotBlank() && wpis.rozmowca != wpis.numer -> wpis.rozmowca
                else -> null
            }

            val liniaGlowne = etykietaOsoby ?: wpis.numer.ifBlank { "Nieznany numer" }
            val trescKrotka = if (etykietaOsoby != null && wpis.numer.isNotBlank()) {
                "$etykietaOsoby • ${wpis.numer}"
            } else {
                liniaGlowne
            }
            val liniaOpisowa = if (etykietaOsoby != null && wpis.numer.isNotBlank()) {
                "$etykietaOsoby\n${wpis.numer} • godz. $godzinaFmt"
            } else {
                "$liniaGlowne • godz. $godzinaFmt"
            }

            // Akcje dla 1 połączenia
            val oddzwonIntent = Intent(context, AkcjaPolaczeniaReceiver::class.java).apply {
                action = "AKCJA_ODDZWON"
                putExtra(EXTRA_NUMER, wpis.numer)
            }
            val oddzwonPendingIntent = PendingIntent.getBroadcast(
                context, 102, oddzwonIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val smsIntent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_OTWORZ_SMS
                putExtra(EXTRA_NUMER, wpis.numer)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val smsPendingIntent = PendingIntent.getActivity(
                context, 103, smsIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            builder.setContentTitle("Nieodebrane połączenie")
                .setContentText(trescKrotka)
                .setSubText(godzinaFmt)
                .setStyle(NotificationCompat.BigTextStyle().bigText(liniaOpisowa))
                .setWhen(wpis.czasMs)
                .setShowWhen(true)
                .addAction(R.drawable.ic_logo_telefon, "Oddzwoń", oddzwonPendingIntent)
                .addAction(android.R.drawable.ic_menu_send, "Wyślij wiadomość", smsPendingIntent)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Oznacz jako przeczytane", odrzucPendingIntent)

        } else {
            // Zbiorcze nieodebrane połączenia (> 1)
            val ilosc = listaNieodebranych.size
            val najnowszy = listaNieodebranych.last()

            val inboxStyle = NotificationCompat.InboxStyle()
                .setBigContentTitle("Nieodebrane połączenia ($ilosc)")

            // Pokazujemy do 5 najnowszych nieodebranych w stylu InboxStyle
            val skroconaLista = listaNieodebranych.takeLast(5).reversed()
            val unikalniRozmowcy = mutableListOf<String>()

            for (wpis in skroconaLista) {
                val godz = df.format(Date(wpis.czasMs))
                val nazwa = WeryfikacjaNumeru.pobierzNazweKontaktu(context, wpis.numer)
                val ps = nazwa?.let { dialer.dominik.pl.ui.screens.pobierzPseudonim(context, it) }
                val etykieta = when {
                    !ps.isNullOrBlank() && ps != nazwa -> "$ps ($nazwa)"
                    !nazwa.isNullOrBlank() -> nazwa
                    wpis.rozmowca.isNotBlank() -> wpis.rozmowca
                    else -> wpis.numer
                }
                inboxStyle.addLine("$etykieta • $godz")
                if (etykieta !in unikalniRozmowcy) unikalniRozmowcy.add(etykieta)
            }

            val trescZbiorcza = unikalniRozmowcy.joinToString(", ")

            // Akcja oddzwonienia na OSTATNI nieodebrany numer
            val oddzwonIntent = Intent(context, AkcjaPolaczeniaReceiver::class.java).apply {
                action = "AKCJA_ODDZWON"
                putExtra(EXTRA_NUMER, najnowszy.numer)
            }
            val oddzwonPendingIntent = PendingIntent.getBroadcast(
                context, 102, oddzwonIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            builder.setContentTitle("Nieodebrane połączenia ($ilosc)")
                .setContentText(trescZbiorcza)
                .setSubText("$ilosc nieodebrane")
                .setStyle(inboxStyle)
                .setWhen(najnowszy.czasMs)
                .setShowWhen(true)
                .addAction(R.drawable.ic_logo_telefon, "Oddzwoń do ostatniego", oddzwonPendingIntent)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Oznacz jako przeczytane", odrzucPendingIntent)
        }

        manager.notify(NOTIFICATION_ID, builder.build())
    }
}
