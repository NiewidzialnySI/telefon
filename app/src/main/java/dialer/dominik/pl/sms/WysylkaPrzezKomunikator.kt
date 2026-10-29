package dialer.dominik.pl.sms

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast

/**
 * Otwiera wybrany komunikator z gotowym numerem i treścią wiadomości.
 *
 * WAŻNE — ograniczenia platformy (stan na 2026):
 * - SMS: obsługujemy sami, pełna automatyczna wysyłka (jesteśmy domyślną apką).
 * - Signal: `sgnl://signal.me/#p/<numer>` — otwiera czat z tą osobą.
 *   Signal NIE wysyła SMS-ów (usunęli obsługę w 2022).
 * - Messenger: wymaga wewnętrznego ID Facebooka, nie numeru telefonu.
 *   ID wpisuje użytkownik raz, zapisujemy przy numerze.
 *
 * Żaden z komunikatorów nie pozwala wysłać wiadomości bez potwierdzenia
 * użytkownika — Android blokuje `RESPOND_VIA_MESSAGE` dla zwykłych aplikacji.
 */
fun wyslijPrzezKomunikator(
    context: Context,
    kanal: KanalWiadomosci,
    numer: String,
    tresc: String
): Boolean {
    val czystyNumer = numer.filter { it.isDigit() || it == '+' }

    val intencja: Intent? = when (kanal) {
        KanalWiadomosci.Sms -> null

        KanalWiadomosci.Signal -> {
            // KRYTYCZNE — regex w SignalMeUtil.kt Signala to:
            //   ^(https|sgnl)://signal\.me/#p/(\+[0-9]+)$
            // Znak „+" MUSI być literalny — gdy Android zakoduje go jako %2B,
            // link nie pasuje do wzorca i Signal po cichu pokazuje listę czatów.
            // Dlatego numer budujemy BEZ Uri.encode i bez removePrefix.
            val zPlusem = if (czystyNumer.startsWith("+")) czystyNumer else "+$czystyNumer"
            // UWAGA: Uri.Builder gubi ukośnik przed fragmentem — bez .path("/")
            // powstaje „signal.me#p/..." zamiast „signal.me/#p/...",
            // a regex Signala wymaga ukośnika. Pusty path daje „/".
            val uri = Uri.Builder()
                .scheme("sgnl")
                .authority("signal.me")
                .path("/")
                .encodedFragment("p/$zPlusem")
                .build()
            // setPackage() pozwala systemowi wybrać DOWOLNĄ pasującą activity Signala
            // (zwykle MainActivity, która pokazuje listę). Wskazujemy wprost
            // DeepLinkEntryActivity — ta przepuszcza link do MainActivity,
            // ale z zachowanym fragmentem #p/<numer>, co otwiera czat.
            Intent(Intent.ACTION_VIEW, uri).setComponent(
                android.content.ComponentName(
                    "org.thoughtcrime.securesms",
                    "org.thoughtcrime.securesms.deeplinks.DeepLinkEntryActivity"
                )
            )
        }

        KanalWiadomosci.Messenger -> {
            val id = pobierzKontoKomunikatora(context, numer, KanalWiadomosci.Messenger)
            if (id.isNullOrBlank()) {
                Toast.makeText(
                    context,
                    "Najpierw wpisz ID konta Messenger dla tego numeru",
                    Toast.LENGTH_LONG
                ).show()
                return false
            }
            Intent(Intent.ACTION_VIEW, Uri.parse("fb-messenger://user/$id"))
                .setPackage("com.facebook.orca")
        }
    } ?: return false

    // Intencja jest null tylko dla SMS — sprawdzone powyżej przez ?: return false
    val gotowaIntencja: Intent = intencja!!
    // NEW_TASK jest potrzebny tylko przy starcie z kontekstu nie-activity.
    // Dla Signala psuje przekazanie linku (MainActivity nie dostaje fragmentu),
    // więc ustawiamy go wyłącznie dla pozostałych kanałów.
    if (kanal != KanalWiadomosci.Signal) {
        gotowaIntencja.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    } else {
        gotowaIntencja.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }

    return try {
        context.startActivity(gotowaIntencja)
        true
    } catch (e: ActivityNotFoundException) {
        // Aplikacja niezainstalowana — próbujemy bez wskazania pakietu
        try {
            val bezPakietu = Intent(gotowaIntencja).setPackage(null)
            context.startActivity(bezPakietu)
            true
        } catch (e2: Exception) {
            Toast.makeText(
                context,
                "${kanal.nazwa()} nie jest zainstalowany",
                Toast.LENGTH_SHORT
            ).show()
            false
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Nie udało się otworzyć ${kanal.nazwa()}", Toast.LENGTH_SHORT).show()
        false
    }
}

/** Sprawdza, czy aplikacja obsługująca kanał jest zainstalowana. */
fun czyKanalDostepny(context: Context, kanal: KanalWiadomosci): Boolean {
    val pakiet = kanal.pakiet() ?: return true
    return try {
        context.packageManager.getPackageInfo(pakiet, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}
