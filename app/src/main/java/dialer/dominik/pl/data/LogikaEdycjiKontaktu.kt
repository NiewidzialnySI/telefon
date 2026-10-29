package dialer.dominik.pl.data
import dialer.dominik.pl.ui.screens.KontaktItem
import dialer.dominik.pl.ui.screens.NumerKontaktu

import android.Manifest
import android.content.ContentProviderOperation
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast

/** Dane kontaktu do edycji — wczytane z książki telefonicznej (nie zmyślone). */
internal data class DaneEdycjiKontaktu(
    val imie: String,
    val nazwisko: String,
    val firma: String,
    /** Czy kontakt ma już wiersz Organization w książce (rzutować na update vs insert). */
    val czyMaFirme: Boolean,
    /** Identyfikator RawContact — wymagany przy INSERT do Data (firma, zdjęcie, numer). */
    val rawContactId: Long,
    val numery: List<NumerKontaktu>
)

/** Pełna nazwa (Imię Nazwisko) do wyświetlenia i jako klucz pseudonimu aplikacyjnego. */
internal fun pelnaNazwa(imie: String, nazwisko: String): String = "$imie $nazwisko".trim()

/**
 * Wczytuje realne dane kontaktu (imię, nazwisko, firma, numery) po CONTACT_ID.
 * Dla edytowanego kontaktu podajemy też bieżącą nazwę i numery z listy,
 * żeby pokazać co jest, zanim użytkownik coś zmieni.
 */
internal fun pobierzDaneDoEdycji(
    context: Context,
    kontakt: KontaktItem
): DaneEdycjiKontaktu {
    var imie = ""
    var nazwisko = ""
    var firma = ""
    var czyMaFirme = false
    var rawContactId = -1L
    val numery = mutableListOf<NumerKontaktu>()

    try {
        // StructuredName + Organization po CONTACT_ID
        val uri = ContactsContract.Data.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.Data.RAW_CONTACT_ID,
            ContactsContract.Data.MIMETYPE,
            ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME,
            ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME,
            ContactsContract.CommonDataKinds.Organization.COMPANY
        )
        val selection = "${ContactsContract.Data.CONTACT_ID} = ?"
        context.contentResolver.query(uri, projection, selection, arrayOf(kontakt.contactId.toString()), null)
            ?.use { cursor ->
                val rawIdx = cursor.getColumnIndex(ContactsContract.Data.RAW_CONTACT_ID)
                val mimetypeIdx = cursor.getColumnIndex(ContactsContract.Data.MIMETYPE)
                val givenIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME)
                val familyIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME)
                val firmaIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Organization.COMPANY)
                while (cursor.moveToNext()) {
                    if (rawContactId < 0 && rawIdx != -1) {
                        rawContactId = cursor.getLong(rawIdx)
                    }
                    when (cursor.getString(mimetypeIdx)) {
                        ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE -> {
                            imie = cursor.getString(givenIdx) ?: ""
                            nazwisko = cursor.getString(familyIdx) ?: ""
                        }
                        ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE -> {
                            firma = cursor.getString(firmaIdx) ?: ""
                            czyMaFirme = true
                        }
                    }
                }
            }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    // Numery bierzemy z listy w KontaktItem (aktualne z książki).
    numery.addAll(kontakt.numery)

    // Fallback: jeśli query nie zwróciło imienia, użyj tego co mamy w liście.
    if (imie.isBlank() && nazwisko.isBlank()) {
        val rozbite = kontakt.name.trim().split(" ")
        if (rozbite.isNotEmpty()) imie = rozbite.first()
        if (rozbite.size > 1) nazwisko = rozbite.drop(1).joinToString(" ")
    }

    return DaneEdycjiKontaktu(imie, nazwisko, firma, czyMaFirme, rawContactId, numery)
}

/**
 * Zapisuje pełną edycję kontaktu przez ContactsContract (WRITE_CONTACTS).
 *
 * Aktualizuje StructuredName (imię/nazwisko), numer (istniejący) i firmę,
 * dodaje nowe numery oraz usuwa wskazane. Zwraca true gdy sukces.
 */
internal fun zapiszEdycjeKontaktu(
    context: Context,
    kontakt: KontaktItem,
    imie: String,
    nazwisko: String,
    firma: String,
    czyMaFirme: Boolean,
    rawContactId: Long,
    numery: List<NumerKontaktu>, // finalna lista numerów (po dodaniu/usunięciu)
        zdjecieBytes: ByteArray? = null,
        usunZdjecie: Boolean = false
    ): Boolean {
    val operations = ArrayList<ContentProviderOperation>()
    val pelne = pelnaNazwa(imie, nazwisko)

    // 1. StructuredName — aktualizacja imienia/nazwiska. Robimy update przez
    //    CONTACT_ID + MIMETYPE (nie trzeba znać Data._ID).
    if (pelne.isNotBlank()) {
        operations.add(
            ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                .withSelection(
                    "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                    arrayOf(kontakt.contactId.toString(), ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                )
                .withValue(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME, imie)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME, nazwisko)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, pelne)
                .build()
        )
    }

    // 2. Firma — aktualizacja lub dodanie wiersza Organization.
    //    UWAGA: newUpdate na nieistniejącym wierszu rzuca błąd w applyBatch,
    //    więc gdy kontakt nie ma firmy musimy zrobić INSERT (albo pominąć przy pustej).
    if (czyMaFirme) {
        operations.add(
            ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                .withSelection(
                    "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                    arrayOf(kontakt.contactId.toString(), ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE)
                )
                .withValue(ContactsContract.CommonDataKinds.Organization.COMPANY, firma)
                .build()
        )
    } else if (firma.isNotBlank()) {
        operations.add(
            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Organization.COMPANY, firma)
                .build()
        )
    }
    // Gdy !czyMaFirme && firma pusty — nic nie dodajemy (brak wiersza do aktualizacji i nic do zapisania).

    // 2b. Zdjęcie kontaktu. Gdy user chce USUNĄĆ zdjęcie (long-press → kosz) → tylko delete.
        //     Gdy są nowe bytes po kadrowaniu → usuń istniejące i wgraj nowe.
        //     Obie operacje przez CONTACT_ID + MIMETYPE (Photo).
        if (usunZdjecie) {
            // Usunięcie całkowite: powiązanie zdjęcia z kontaktem oraz plikowe zdjęcie pierwotne.
            operations.add(
                ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
                    .withSelection(
                        "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                        arrayOf(
                            kontakt.contactId.toString(),
                            ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE
                        )
                    )
                    .build()
            )
            // W pełni usuń też zdjęcie zdjęciowe pierwotne (photo) — dzięki temu zdjęcie
            // kontaktu nie zostaje „przelinkowane" do asynchronu.
            operations.add(
                ContentProviderOperation.newDelete(ContactsContract.Contacts.CONTENT_URI)
                    .withSelection(
                        "${ContactsContract.Contacts._ID} = ? AND ${ContactsContract.Contacts.PHOTO_ID} IS NOT NULL",
                        arrayOf(kontakt.contactId.toString())
                    )
                    .build()
            )
        } else if (zdjecieBytes != null && zdjecieBytes.isNotEmpty()) {
            operations.add(
                ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
                    .withSelection(
                        "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                        arrayOf(
                            kontakt.contactId.toString(),
                            ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE
                        )
                    )
                    .build()
            )
            operations.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Photo.PHOTO, zdjecieBytes)
                    .build()
            )
        }

    // 3. Numery: istniejące aktualizujemy (jeśli numer się zmienił), nowe dodajemy,
    //    te których zabrakło — usuwamy po Phone._ID.
    val istniejaceId = kontakt.numery.map { it.id }.toSet()
    val finalneId = numery.map { it.id }.toSet()

    // 3a. Usuń numery, które zostały usunięte w oknie.
    for (numer in kontakt.numery) {
        if (numer.id !in finalneId) {
            operations.add(
                ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
                    .withSelection(
                        "${ContactsContract.Data._ID} = ?",
                        arrayOf(numer.id)
                    )
                    .build()
            )
        }
    }

    // 3b. Numer główny — aktualizacja istniejącego (pierwszy wg listy) lub dodanie.
    for ((indeks, numer) in numery.withIndex()) {
        val pelnyNumer = numer.surowy
        if (numer.id in istniejaceId && numer.id.isNotBlank()) {
            // Numer już istnieje w książce — update treści.
            operations.add(
                ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                    .withSelection(
                        "${ContactsContract.Data._ID} = ?",
                        arrayOf(numer.id)
                    )
                    .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, pelnyNumer)
                    .build()
            )
        } else if (pelnyNumer.isNotBlank()) {
            // Nowy numer — insert z przypisaniem do CONTACT_ID (nowa wartość dla istniejącego
            // kontaktu wymaga podania CONTACT_ID i MIMETYPE w Data).
            operations.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, pelnyNumer)
                    .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                    .build()
            )
        }
    }

    return try {
        context.contentResolver.applyBatch(ContactsContract.AUTHORITY, operations)
        true
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Nie udało się zapisać zmian", Toast.LENGTH_SHORT).show()
        false
    }
}

/** Uprawnienie zapisu kontaktów przyznane? */
internal fun maUprawnienieZapisu(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.WRITE_CONTACTS) ==
        PackageManager.PERMISSION_GRANTED

/**
 * Usuwa cały kontakt z książki telefonicznej po CONTACT_ID (WRITE_CONTACTS).
 * Usunięcie wiersza RawContacts o danym CONTACT_ID usuwa powiązane numery,
 * zdjęcie i inne dane. Zwraca true gdy kontakt został usunięty.
 */
internal fun usunKontakt(context: Context, contactId: Long): Boolean {
    return try {
        val usuniete = context.contentResolver.delete(
            ContactsContract.RawContacts.CONTENT_URI,
            "${ContactsContract.RawContacts.CONTACT_ID} = ?",
            arrayOf(contactId.toString())
        )
        // apply: delete zwraca liczbę usuniętych wierszy.
        if (usuniete > 0) {
            true
        } else {
            // Fallback — brak RAW wierszy: usuń z głównej tabeli po CONTACT_ID.
            context.contentResolver.delete(
                ContactsContract.Contacts.CONTENT_URI,
                "${ContactsContract.Contacts._ID} = ?",
                arrayOf(contactId.toString())
            ) > 0
        }
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}
