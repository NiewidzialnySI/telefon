package dialer.dominik.pl.ui.components

import androidx.compose.ui.graphics.vector.ImageVector
import dialer.dominik.pl.ui.components.icons.IkonyDialer
import dialer.dominik.pl.ui.components.icons.IkonyKontaktow
import dialer.dominik.pl.ui.components.icons.IkonySms
import dialer.dominik.pl.ui.components.icons.IkonySystemowe

/**
 * Zbiorczy obiekt ikon — legacy/atraktor API.
 *
 * Każda ikona deleguje do tematycznego obiektu w podpakiecie `icons`:
 * [IkonySystemowe], [IkonyDialer], [IkonyKontaktow], [IkonySms].
 *
 * Można pisać `IkonyAplikacji.Sprawdzenie` albo (nowy styl)
 * `IkonySystemowe.Sprawdzenie` — oba zwracają tę samą ikonę.
 */
object IkonyAplikacji {

    // --- Systemowe / uprawnienia ---
    val Sprawdzenie: ImageVector get() = IkonySystemowe.Sprawdzenie
    val Ustawienia: ImageVector get() = IkonySystemowe.Ustawienia
    val Tarcza: ImageVector get() = IkonySystemowe.Tarcza
    val Strzalka: ImageVector get() = IkonySystemowe.Strzalka
    val Klodka: ImageVector get() = IkonySystemowe.Klodka
    val Bluetooth: ImageVector get() = IkonySystemowe.Bluetooth
    val Lupa: ImageVector get() = IkonySystemowe.Lupa
    val Notatka: ImageVector get() = IkonySystemowe.Notatka

    // --- Dialer / rozmowa ---
    val Sluchawka: ImageVector get() = IkonyDialer.Sluchawka
    val Cofnij: ImageVector get() = IkonyDialer.Cofnij
    val Klawiatura: ImageVector get() = IkonyDialer.Klawiatura
    val MikrofonWylaczony: ImageVector get() = IkonyDialer.MikrofonWylaczony
    val Glosnik: ImageVector get() = IkonyDialer.Glosnik
    val TelefonLogo: ImageVector get() = IkonyDialer.TelefonLogo
    val Wciszenie: ImageVector get() = IkonyDialer.Wciszenie
    val Dymek: ImageVector get() = IkonyDialer.Dymek
    val Nagrywanie: ImageVector get() = IkonyDialer.Nagrywanie
    val StrzalkaWGore: ImageVector get() = IkonyDialer.StrzalkaWGore

    // --- Kontakty / formularze ---
    val NowyKontakt: ImageVector get() = IkonyKontaktow.NowyKontakt
    val Firma: ImageVector get() = IkonyKontaktow.Firma
    val Pseudonim: ImageVector get() = IkonyKontaktow.Pseudonim
    val Zamknij: ImageVector get() = IkonyKontaktow.Zamknij
    val DodajKontakt: ImageVector get() = IkonyKontaktow.DodajKontakt
    val Gwiazda: ImageVector get() = IkonyKontaktow.Gwiazda
    val GwiazdaPusta: ImageVector get() = IkonyKontaktow.GwiazdaPusta
    val Kosz: ImageVector get() = IkonyKontaktow.Kosz
    val Zlacznik: ImageVector get() = IkonyKontaktow.Zlacznik
    val Galeria: ImageVector get() = IkonyKontaktow.Galeria
    val Kamera: ImageVector get() = IkonyKontaktow.Kamera
    val Zegar: ImageVector get() = IkonyKontaktow.Zegar

    // --- Wiadomości / nagrania ---
    val Wiadomosc: ImageVector get() = IkonySms.Wiadomosc
    val Nagrania: ImageVector get() = IkonySms.Nagrania
    val Archiwum: ImageVector get() = IkonySms.Archiwum
    val WroćZArchiwum: ImageVector get() = IkonySms.WroćZArchiwum
}