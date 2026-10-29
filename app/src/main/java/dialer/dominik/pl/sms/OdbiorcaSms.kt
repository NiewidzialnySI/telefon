package dialer.dominik.pl.sms

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import dialer.dominik.pl.BubbleChatActivity
import dialer.dominik.pl.FullScreenSmsActivity
import dialer.dominik.pl.MainActivity
import dialer.dominik.pl.R
import dialer.dominik.pl.ui.screens.pobierzPseudonim

/**
 * Odbiera przychodzące SMS-y, gdy aplikacja jest DOMYŚLNĄ aplikacją SMS.
 *
 * Zapisuje przychodzące wiadomości do Telephony.Sms.Inbox (wymóg dla domyślnej apki).
 */
class OdbiorcaSms : BroadcastReceiver() {

    companion object {
        private const val TAG = "OdbiorcaSms"
        private const val KANAL_ID = "nowe_sms"

        /** Ostatnia odebrana wiadomość — do wyświetlenia w UI. */
        var ostatniaWiadomosc: WiadomoscSms? = null
            private set

        /** Ostatnio opublikowany ShortcutInfo konwersacji — do przekazania do notyfikacji. */
        @Volatile
        var ostatniShortcut: ShortcutInfoCompat? = null
            private set

        /**
         * Wyciąga kod OTP z treści wiadomości albo null (gdy brak).
         * Wspólna logika: [WykrywanieOtp] (także przycisk „Wyczyść kody 2FA”).
         */
        fun wykryjOtp(tresc: String): String? = WykrywanieOtp.wykryj(tresc)

        /** Kopiuje tekst do schowka (bez powiadomienia o tym). */
        fun kopiujDoSchowka(context: Context, tekst: String) {
            try {
                val schowek = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                schowek?.setPrimaryClip(android.content.ClipData.newPlainText("kod", tekst))
            } catch (e: Exception) {
                Log.e(TAG, "Błąd kopiowania do schowka", e)
            }
        }

        /**
         * Publikuje dynamiczny skrót konwersacji (Android 11+/API 30+), jeżeli jeszcze
         * nie istnieje. System NIE traktuje powiadomienia jako rozmowy/nie pozwoli na
         * dymek (bubble), dopóki dla nadawcy nie ma opublikowanego shortcut-a. Wzorzec
         * z Signal: `ConversationUtil.ensureShortcutForRecipientSync`.
         */
        fun ensureShortcutForRecipient(context: Context, nadawca: String) {
            if (Build.VERSION.SDK_INT < 30) return
            val shortcutId = normalizujShortcutId(nadawca)
            try {
                // Usuń poprzednią wersję (mogła mieć stary intent MAIN/LAUNCHER),
                // żeby wymusić świeżą publikację z ACTION_VIEW — bez tego system
                // trzyma nieaktualny shortcut i zgłasza "found valid? false".
                ShortcutManagerCompat.removeDynamicShortcuts(context, listOf(shortcutId))

                // Intent ACTION_VIEW (nie MAIN/LAUNCHER) + jawne setActivity + LocusId:
                // tylko wtedy system uznaje shortcut za konwersację i pozwala na dymek.
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("tel:$nadawca"), context, MainActivity::class.java).apply {
                    putExtra("OTWARTY_CZAT", nadawca)
                }
                // Person z tym samym kluczem co w notyfikacji — bez tego system nie
                // wiąże shortcut-a z osobą i zgłasza "shortcut ... found valid? false".
                var persona: androidx.core.app.Person? = null
                try {
                    persona = androidx.core.app.Person.Builder()
                        .setName(nazwaNadawcy(context, nadawca))
                        .setKey(shortcutId)
                        .build()
                } catch (e: Exception) {
                    persona = null
                }
                val zdjecie = dialer.dominik.pl.call.WeryfikacjaNumeru.pobierzZdjecieKontaktu(context, nadawca)
                val firma = dialer.dominik.pl.ui.screens.czyZnanaFirma(nadawca)
                // Szybka ikona BEZ sieci (kontakt / logo apki) — powiadomienie nie czeka na favicon.
                // Logo firmy dociągamy w tle i odświeżamy shortcut (głowa dymka).
                val scIkonaSzybka = when {
                    zdjecie != null -> IconCompat.createWithAdaptiveBitmap(przycinajDoOkregu(zdjecie))
                    else -> IconCompat.createWithResource(context, R.drawable.ic_logo_nowoczesny)
                }
                fun zbudujIOpublikuj(ikona: IconCompat) {
                    val scBuilder = ShortcutInfoCompat.Builder(context, shortcutId)
                        .setShortLabel(nadawca)
                        .setLongLabel(nadawca)
                        .setIcon(ikona)
                        .setIntent(intent)
                        .setActivity(android.content.ComponentName(context, MainActivity::class.java))
                        .setLocusId(androidx.core.content.LocusIdCompat(shortcutId))
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        scBuilder.setIsConversation()
                    }
                    if (persona != null) {
                        scBuilder.setPersons(arrayOf(persona))
                    }
                    ostatniShortcut = scBuilder.build()
                    ShortcutManagerCompat.pushDynamicShortcut(context, ostatniShortcut!!)
                }
                zbudujIOpublikuj(scIkonaSzybka)
                if (firma) {
                    val appCtx = context.applicationContext
                    Thread {
                        try {
                            val logo = dialer.dominik.pl.ui.screens.pobierzLogoFirmy(appCtx, nadawca)
                            if (logo != null) {
                                zbudujIOpublikuj(
                                    IconCompat.createWithAdaptiveBitmap(przycinajDoOkregu(logo))
                                )
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Async logo shortcut: ${e.message}")
                        }
                    }.start()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Błąd publikacji skrótu konwersacji", e)
            }
        }

        /** Stabilny, bezpieczny ID skrótu z numeru telefonu (tylko cyfry). */
        private fun normalizujShortcutId(nadawca: String): String {
            val cyfry = nadawca.filter { it.isDigit() }
            return if (cyfry.isNotBlank()) "konw_$cyfry" else "konw_" + nadawca.hashCode()
        }

        /**
         * Nazwa wyświetlana nadawcy dla dymka/powiadomienia: pseudonim (ustawiony w apce)
         * → imię i nazwisko z książki → surowy numer (gdy nie zapisany).
         */
        private fun nazwaNadawcy(context: Context, nadawca: String): String {
            val zKsiazki = dialer.dominik.pl.call.WeryfikacjaNumeru.pobierzNazweKontaktu(context, nadawca)
            val pseudonim = zKsiazki?.let { pobierzPseudonim(context, it) }
            return pseudonim ?: zKsiazki ?: nadawca
        }

        /**
         * Przycina bitmapę do okręgu (transparentne rogi). Android wycina ikony dymka/
         * shortcut do maski koła, ale kwadratowa bitmapa zdjęcia zostawia białe rogi —
         * wcześniejsze przycięcie do okręgu pozbawia je tła i ikona wygląda jak buźka.
         */
        private fun przycinajDoOkregu(bmp: android.graphics.Bitmap): android.graphics.Bitmap {
            val rozmiar = minOf(bmp.width, bmp.height)
            val zuzyta = android.graphics.Bitmap.createBitmap(rozmiar, rozmiar, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(zuzyta)
            canvas.drawARGB(0, 0, 0, 0)
            // Maska okręgu (rogi transparentne).
            canvas.drawCircle(rozmiar / 2f, rozmiar / 2f, rozmiar / 2f,
                android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE })
            // Zdjęcie mocno POWIĘKSZONE — na Realme systemowy padding ikon dymka jest
            // większy niż ~11%, więc skala 1.18 zostawiała biały pierścień. 1.45 sprawia,
            // że zdjęcie po wewnętrznym insercie systemu wypełnia całe koło.
            val skala = 1.45f
            val sRozmiar = (rozmiar * skala).toInt()
            val sx = (bmp.width - sRozmiar) / 2
            val sy = (bmp.height - sRozmiar) / 2
            val farba = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            farba.setXfermode(android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN))
            canvas.drawBitmap(bmp, android.graphics.Rect(sx, sy, sx + sRozmiar, sy + sRozmiar),
                android.graphics.Rect(0, 0, rozmiar, rozmiar), farba)
            return zuzyta
        }

        /**
         * Wspólna ścieżka powiadomienia + dymka dla SMS i MMS (po zapisie do skrzynki).
         * Wywoływać z wątku IO — publikuje shortcut, OTP, heads-up / bubble.
         */
        fun powiadomOPrzychodzacej(
            context: Context,
            nadawca: String,
            tresc: String,
            czas: Long = System.currentTimeMillis()
        ) {
            ostatniaWiadomosc = WiadomoscSms(nadawca = nadawca, tresc = tresc, czas = czas)
            val otp = wykryjOtp(tresc)
            if (!otp.isNullOrBlank()) {
                kopiujDoSchowka(context, otp)
            }
            ensureShortcutForRecipient(context, nadawca)
            try { Thread.yield() } catch (_: Exception) {}
            pokazPowiadomienie(context, nadawca, tresc, czas, otp)
            try {
                val prefsUkryte = context.getSharedPreferences("sms_ukryte", Context.MODE_PRIVATE)
                val zestaw = (prefsUkryte.getStringSet("ukryte", emptySet()) ?: emptySet()).toMutableSet()
                if (nadawca in zestaw) {
                    zestaw.remove(nadawca)
                    prefsUkryte.edit().putStringSet("ukryte", zestaw).apply()
                    Log.i(TAG, "Nowa wiadomość od archiwizowanego nadawcy $nadawca — cofnięto do skrzynki")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Błąd zdejmowania z archiwum", e)
            }
        }

        /** Pokazuje powiadomienie o nowej wiadomości (z odpowiedzią, kopiowaniem OTP i dymkiem). */
        private fun pokazPowiadomienie(
            context: Context,
            nadawca: String,
            tresc: String,
            czas: Long,
            otp: String?
        ) {
        val menedzer = context.getSystemService(NotificationManager::class.java) ?: return

        // Czy nadawca jest zapisany w kontaktach? Od tego zależy: kontakt → wybudź
        // ekran (pełnoekranowe heads-up), nieznany → cichy kanał z wibracją, ekran zgaszony.
        val czyKontakt = dialer.dominik.pl.call.WeryfikacjaNumeru.pobierzNazweKontaktu(context, nadawca) != null

        // Kanał PER KONWERSACJĘ (per nadawca), nie jeden globalny — wzorzec Signal i
        // Fossify. Kanał konwersacji z setAllowBubbles(true) nie jest blokowany jako
        // "mImportanceLockedDefaultApp" (w przeciwieństwie do globalnego nowe_sms,
        // gdzie Realme trzymało mAllowBubbles=0 i dymek nie wstawał).
        // Dla nieznanego nadawcy: osobny kanał "_cichy" — LOW + wibracja, bez wybudzania.
        val channelId = if (!czyKontakt) normalizujShortcutId(nadawca) + "_cichy" else normalizujShortcutId(nadawca)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            menedzer.deleteNotificationChannel(channelId)
            val kanal = NotificationChannel(
                channelId,
                if (!czyKontakt) "Rozmowa z $nadawca (cicho)" else "Rozmowa z $nadawca",
                if (!czyKontakt) NotificationManager.IMPORTANCE_LOW else NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = if (!czyKontakt) "Ciche powiadomienia SMS — tylko wibracja" else "Powiadomienia i dymki z wiadomości SMS"
                setShowBadge(true)
                // Dla nieznanego: lekka wibracja, bez głośnego alarmu.
                if (!czyKontakt) setVibrationPattern(longArrayOf(0, 120, 80, 120))
                // Zezwól na dymek (bubble). setConversationId wymaga dodatkowego
                // kanału-rodzica i bywa kapryśny — bubble działa na zwykłym kanale,
                // o ile shortcut jest valid + setBubbleMetadata jest mutable + setAllowBubbles.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setAllowBubbles(true)
                }
            }
            menedzer.createNotificationChannel(kanal)
        }

        // Klik w powiadomienie otwiera skrzynkę SMS aplikacji
        val wejscie = PendingIntent.getActivity(
            context,
            czas.toInt(),
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Pełnoekranowe wybudzenie ekranu przy SMS od ZAPISANEGO kontaktu — jak dzwonek.
        // Prowadzimy do FullScreenSmsActivity (trampolina): wybudza ekran i natychmiast
        // wraca, zostawiając heads-up (baner z treścią) na ekranie locka. Klik w baner
        // (setContentIntent = MainActivity) otwiera czat; fullScreenIntent NIE otwiera
        // czatu od razu (wcześniej BubbleChatActivity otwierała go i kryła heads-up).
        val fullScreenPending = if (czyKontakt) {
            PendingIntent.getActivity(
                context,
                czas.toInt() + 1,
                Intent(context, FullScreenSmsActivity::class.java)
                    .putExtra(BubbleChatActivity.EXTRA_NUMER, nadawca)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        } else null

        // Osoba nadawcy — z kluczem równym shortcutId, żeby notyfikacja była uznana
        // za "konwersację" (wymóg bubble na Android 11+, wzorzec Signal).
        val nadawcaPersona = androidx.core.app.Person.Builder()
            .setName(nazwaNadawcy(context, nadawca))
            .setKey(normalizujShortcutId(nadawca))
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_logo_nowoczesny))
            .build()

        val builder = NotificationCompat.Builder(context, channelId)
            .setContentTitle(nazwaNadawcy(context, nadawca))
            .setContentText(tresc)
            // MessagingStyle (nie BigTextStyle) — to sprawia, że system traktuje
            // powiadomienie jako konwersację i pozwala na dymek (bubble).
            .setStyle(
                NotificationCompat.MessagingStyle(nadawcaPersona)
                    .setConversationTitle(nadawca)
                    .addMessage(tresc, czas, nadawcaPersona)
            )
            .setSmallIcon(R.drawable.ic_logo_telefon)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .addPerson(nadawcaPersona)
            .setColor(0xFF69F0AE.toInt())
            .setColorized(true)
            .setAutoCancel(true)
            .setContentIntent(wejscie)
            .setPriority(if (czyKontakt) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            // Wybudź ekran tylko dla zapisanego kontaktu (fullScreenIntent); dla
            // nieznanego brak — ekran zgaszony, tylko wibracja kanału "_cichy".
            .apply {
                fullScreenPending?.let { setFullScreenIntent(it, true) }
            }
            // Wiąże notyfikację z konwersacją przez PEŁNY ShortcutInfo (Kotlin:
            // setShortcutInfo(shortcut) jak w Fossify), nie sam id — to jednoznacznie
            // wyznacza shortcut-i konwersacji, w przeciwieństwie do setShortcutId(String).
            .apply {
                val sc = ostatniShortcut
                if (sc != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setShortcutInfo(sc)
                }
            }

        // Dymek (bubble) — trwająca rozmowa SMS jako przemieszczalny chat-head.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bubbleIntent = Intent(context, BubbleChatActivity::class.java).apply {
                // Otwiera sam czat w dymku — osobna aktywność BubbleChatActivity
                // (bez paska zakładek), patrz manifest allowEmbedded+resizeableActivity.
                putExtra(BubbleChatActivity.EXTRA_NUMER, nadawca)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            // Ikona dymka = zdjęcie kontaktu nadawcy (jeśli jest), inaczej logo.
            // Zdjęcie pobieramy na tym samym wątku IO co całe powiadomienie.
            val zdjecie = dialer.dominik.pl.call.WeryfikacjaNumeru.pobierzZdjecieKontaktu(context, nadawca)
            // Diagnoza: czy zdjęcie się znalazło dla ikony dymka.
            android.util.Log.i("IkonaDymka", "nadawca=$nadawca zdjecie=${zdjecie != null}")
            // Bez synchronicznego pobierania logo sieciowego — ikona z shortcutu
            // (aktualizowana async w ensureShortcut) albo zdjęcie kontaktu / logo apki.
            val ikonaDymka = when {
                zdjecie != null -> IconCompat.createWithAdaptiveBitmap(przycinajDoOkregu(zdjecie))
                else -> androidx.core.graphics.drawable.IconCompat.createWithResource(
                    context, R.drawable.ic_logo_nowoczesny
                )
            }
            val bubblePending = PendingIntent.getActivity(
                context, czas.toInt(), bubbleIntent,
                // BubbleMetadata wymaga MUTABLE PendingIntent — immutable rzuca
                // IllegalArgumentException i całe powiadomienie się nie pokazuje.
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            val bubbleMeta = androidx.core.app.NotificationCompat.BubbleMetadata.Builder(bubblePending, ikonaDymka)
                .setDesiredHeight(1000)
                // Dymek + heads-up: suppress(false) sprawia, że zwykłe powiadomienie
                // (banner z treścią dzięki MessagingStyle) jest pokazywane OPRÓCZ
                // głowy dymka. autoExpand(false) = dymek zwijany do głowy, nie przysłania
                // banneru — treść widać od razu, a dotknięcie głowy rozwija czat.
                .setAutoExpandBubble(false)
                .setSuppressNotification(false)
                .build()
            builder.setBubbleMetadata(bubbleMeta)
        }

        // Akcja: Kopiuj kod OTP (gdy wykryto).
        if (!otp.isNullOrBlank()) {
            val kopiujIntent = Intent(context, AkcjaSmsReceiver::class.java).apply {
                action = "AKCJA_KOPIUJ_OTP"
                putExtra("KOD", otp)
            }
            val kopiujPending = PendingIntent.getBroadcast(
                context, 30 + czas.toInt(), kopiujIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, "✔ Kopiuj $otp", kopiujPending)
        }

        // Akcja: Odpowiedz (RemoteInput — pole tekstowe w powiadomieniu).
        val odpowiedzIntent = Intent(context, AkcjaSmsReceiver::class.java).apply {
            action = "AKCJA_ODPOWIEDZ"
            putExtra("NADAWCA", nadawca)
        }
        val odpowiedzPending = PendingIntent.getBroadcast(
            context, 40 + czas.toInt(), odpowiedzIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        val remoteInput = androidx.core.app.RemoteInput.Builder("KLAWISZ_ODPOWIEDZ").run {
            setLabel("Odpowiedz")
            build()
        }
        val odpowiedzAkcja = androidx.core.app.NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send, "Odpowiedz", odpowiedzPending
        ).addRemoteInput(remoteInput).build()
        builder.addAction(odpowiedzAkcja)

        // Stabilny identyfikator powiadomienia per nadawca — dzięki temu druga wiadomość
        // od tej samej osoby AKTUALIZUJE istniejący dymek, zamiast tworzyć nowy.
        val notificationId = normalizujShortcutId(nadawca).hashCode()

        val powiadomienie = builder.build()

            try {
                menedzer.notify(notificationId, powiadomienie)
            } catch (e: Exception) {
                Log.e(TAG, "Błąd wyświetlania powiadomienia", e)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        // Obsługujemy SMS_DELIVER (przychodzący SMS do domyślnej apki SMS).
        // Tylko ta akcja — SMS_RECEIVED nie jest używany, żeby nie duplikować
        // zapisu i powiadomienia (system wysyła oba przy tym samym SMS-ie).
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return

        // goAsync: zapis SMS-a może trwać dłużej niż okno broadcastu.
        // Dzwonimy finish() dopiero po zakończeniu zapisu i powiadomienia.
        val pendingResult = goAsync()

        Thread {
            try {
                val wiadomosci = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return@Thread
                if (wiadomosci.isEmpty()) return@Thread

                val nadawca = wiadomosci.firstOrNull()?.displayOriginatingAddress ?: "Nieznany"
                val tresc = wiadomosci.joinToString(separator = "") { it.messageBody ?: "" }
                val czas = wiadomosci.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()

                // Obowiązkowy zapis przychodzącego SMS-a do systemowej skrzynki odbiorczej (Inbox)
                try {
                    val values = ContentValues().apply {
                        put(Telephony.Sms.Inbox.ADDRESS, nadawca)
                        put(Telephony.Sms.Inbox.BODY, tresc)
                        put(Telephony.Sms.Inbox.DATE, czas)
                        put(Telephony.Sms.Inbox.READ, 0)
                        put(Telephony.Sms.Inbox.SEEN, 0)
                    }
                    context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
                    // Bez unieważnienia cache skrzynka w UI pokazuje stare wątki do ręcznego odświeżenia.
                    dialer.dominik.pl.ui.screens.uniewaznijCacheSkrzynki()
                } catch (e: Exception) {
                    Log.e(TAG, "Błąd zapisu przychodzącego SMS do Inbox", e)
                }

                powiadomOPrzychodzacej(context, nadawca, tresc, czas)
                Log.i(TAG, "Odebrano i zapisano w skrzynce SMS od $nadawca")
            } catch (e: Exception) {
                Log.e(TAG, "Błąd odbioru SMS: ${e.javaClass.simpleName}", e)
            } finally {
                pendingResult.finish()
            }
        }.apply { priority = Thread.MAX_PRIORITY }.start()
    }
}

/** Pojedyncza odebrana wiadomość. */
data class WiadomoscSms(
    val nadawca: String,
    val tresc: String,
    val czas: Long
)
