package dialer.dominik.pl.ui.theme

import androidx.compose.ui.graphics.Color

// --- Stara paleta (niebieska) ---
val PrimaryLight = Color(0xFF0D47A1)
val PrimaryDark = Color(0xFF00BCD4)
val SurfaceLight = Color(0xFFF8F9FA)
val SurfaceDark = Color(0xFF121212)
val OnSurface = Color(0xFF1C1B1F)

// --- Stałe marki (fallback / seed) ---
val ZielonyGlebokiSeed = Color(0xFF0B3D2E)
val ZielonyJasnySeed = Color(0xFF00C853)
val ZielonySredniSeed = Color(0xFF2E7D32)
val CzarnyGlebokiSeed = Color(0xFF121212)
val BialyCzysty = Color(0xFFF8F9FA)

/** @deprecated Używaj MotywAktualny — zostaje jako alias kompatybilności. */
val ZielonyGleboki: Color get() = MotywAktualny.aurora1
/** @deprecated */
val ZielonyJasny: Color get() = MotywAktualny.aurora2
/** @deprecated */
val ZielonySredni: Color get() = MotywAktualny.accentMorski
/** @deprecated */
val CzarnyGleboki: Color get() = if (MotywAktualny.ciemny) Color(0xFF121212) else Color(0xFFF0F2F1)

/**
 * Czyste tło — w trybie ciemnym AMOLED (#000), w jasnym z palety.
 * Property (nie const), żeby żywo brać [MotywAktualny].
 */
val CzarnyAmoled: Color get() = MotywAktualny.tlo

/** Akcent wiodący (dawniej miętowy neon). */
val ZielonyNeon: Color get() = MotywAktualny.accent

/** Kolor przycisków / gradientów (zaawansowane → kolorPrzycisku, inaczej accent). */
val ZielonyMorski: Color get() = MotywAktualny.przycisk

/**
 * Tekst/ikona NA przycisku lub gradiencie akcentu (dawniej sztywne #06231A).
 * Z roli M3 onPrimary — działa dla różu, fioletu, neonu itd.
 */
val NaPrzycisku: Color get() = MotywAktualny.naPrzycisku

/** @deprecated Używaj [NaPrzycisku] — zostawione pod stare wywołania. */
val TekstNaZielonym: Color get() = MotywAktualny.naPrzycisku

/** Czerwień połączenia / błędu — może iść z schematu. */
val CzerwonyPolaczenia: Color get() = MotywAktualny.blad

// --- Stan uprawnień (statyczne, mało zależne od motywu) ---
val ZielonyKontenerJasny = Color(0xFFC8E6C9)
val ZielonyTekstJasny = Color(0xFF1B5E20)
val ZielonyKontenerCiemny = Color(0xFF1B5E20)
val ZielonyTekstCiemny = Color(0xFFC8F7DA)
val SzaryKontenerJasny = Color(0xFFE3E5E8)
val SzaryTekstJasny = Color(0xFF4A4D52)
val SzaryKontenerCiemny = Color(0xFF2E3136)
val SzaryTekstCiemny = Color(0xFFB9BCC2)
