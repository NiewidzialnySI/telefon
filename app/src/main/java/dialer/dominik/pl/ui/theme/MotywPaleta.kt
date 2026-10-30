package dialer.dominik.pl.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Tokeny aplikacji mapowane z ról Material 3 (paleta, nie jeden hex).
 *
 * primary / secondary / tertiary → akcenty
 * onPrimary → tekst/ikony NA przycisku / gradiencie
 * onSurface / onSurfaceVariant → napisy na tle i kafelkach
 * surfaceContainer* → kafelki
 *
 * @see <a href="https://m3.material.io/styles/color/roles">Color roles</a>
 */
@Immutable
data class MotywPaleta(
    val seed: Color,
    val ciemny: Boolean,
    val accent: Color,
    val accentMorski: Color,
    val przycisk: Color,
    /** Tekst/ikona na [przycisk] / gradiencie akcentu — z M3 onPrimary. */
    val naPrzycisku: Color,
    val tlo: Color,
    val naTle: Color,
    val tekstDrugorzedny: Color,
    val kafelek: Color,
    val kafelekRamka: Color,
    val aurora1: Color,
    val aurora2: Color,
    val auroraWlaczona: Boolean,
    val animacjaTla: AnimacjaTla,
    val ikona: Color,
    val blad: Color,
    /** Promień rogów kafelków (W3 = ostrzejsze panele inventory). */
    val promienKafla: Float = 16f
) {
    companion object {
        val DomyslnaCiemna = MotywPaleta(
            seed = MotywStan.SEED_DOMYSLNY,
            ciemny = true,
            accent = Color(0xFF69F0AE),
            accentMorski = Color(0xFF00E676),
            przycisk = Color(0xFF69F0AE),
            naPrzycisku = Color(0xFF06231A),
            tlo = Color(0xFF000000),
            naTle = Color.White,
            tekstDrugorzedny = Color.White.copy(alpha = 0.5f),
            kafelek = Color(0xFF131C18),
            kafelekRamka = Color(0xFF69F0AE).copy(alpha = 0.14f),
            aurora1 = Color(0xFF0B3D2E),
            aurora2 = Color(0xFF00C853),
            auroraWlaczona = true,
            animacjaTla = AnimacjaTla.Aurora,
            ikona = Color(0xFF69F0AE),
            blad = Color(0xFFE53935),
            promienKafla = 16f
        )

        fun zSchematu(
            scheme: ColorScheme,
            stan: MotywStan.Stan,
            ciemny: Boolean,
            ekran: EkranMotywu? = null
        ): MotywPaleta {
            val nad = ekran?.let { stan.ekrany[it] }
            val seed = nad?.seed ?: stan.seed
            // Role M3 — pełna paleta tonalna z seeda (+ secondary/tertiary).
            val accent = scheme.primary
            val morski = scheme.secondary
            val przycisk = nad?.przycisk ?: stan.kolorPrzycisku ?: scheme.primary
            val naPrzycisku = kontrastNa(przycisk, scheme.onPrimary)
            val kafelekAuto = if (ciemny) {
                // Lekko zabarwiony surface pod akcent (nie płaski szary).
                Color(
                    red = (scheme.surfaceContainer.red * 0.72f + accent.red * 0.10f).coerceIn(0f, 1f),
                    green = (scheme.surfaceContainer.green * 0.72f + accent.green * 0.12f).coerceIn(0f, 1f),
                    blue = (scheme.surfaceContainer.blue * 0.72f + accent.blue * 0.10f).coerceIn(0f, 1f),
                    alpha = 1f
                )
            } else {
                scheme.surfaceContainerLow
            }
            val kafelek = nad?.kafelek ?: stan.kolorKafelka ?: kafelekAuto
            val naTle = scheme.onSurface
            val tekstDrugi = scheme.onSurfaceVariant
            val aurora1 = stan.aurora1 ?: scheme.primaryContainer
            val aurora2 = stan.aurora2 ?: scheme.tertiary
            // Presety tematyczne — chrome UI jak w grze (W3: złote ramki, atrament).
            val (ramka, ikona, accentUi) = when (stan.paletaId) {
                "fallout" -> Triple(
                    Color(0xFF18FF6D).copy(alpha = 0.22f),
                    Color(0xFF18FF6D),
                    accent
                )
                else -> Triple(
                    scheme.outlineVariant.copy(alpha = if (ciemny) 0.55f else 0.85f),
                    accent,
                    accent
                )
            }
            return MotywPaleta(
                seed = seed,
                ciemny = ciemny,
                accent = accentUi,
                accentMorski = morski,
                przycisk = przycisk,
                naPrzycisku = naPrzycisku,
                tlo = if (ciemny) Color.Black else scheme.background,
                naTle = naTle,
                tekstDrugorzedny = tekstDrugi,
                kafelek = kafelek,
                kafelekRamka = ramka,
                aurora1 = aurora1,
                aurora2 = aurora2,
                auroraWlaczona = stan.auroraWlaczona && stan.animacjaTla != AnimacjaTla.Wylaczona,
                animacjaTla = stan.animacjaTla,
                ikona = ikona,
                blad = scheme.error,
                promienKafla = 16f
            )
        }

        /** Gdy użytkownik nadpisze przycisk ręcznie — dobierz czytelny kontrast. */
        private fun kontrastNa(tlo: Color, zSchematu: Color): Color {
            val lum = tlo.luminance()
            return when {
                // Jasny przycisk (róż, neon) → ciemny tekst
                lum > 0.45f -> Color(0xFF1A1C1B)
                // Ciemny przycisk → jasny tekst; preferuj onPrimary ze schematu gdy pasuje
                zSchematu.luminance() > 0.4f -> zSchematu
                else -> Color.White
            }
        }
    }
}

val LocalMotyw = staticCompositionLocalOf { MotywPaleta.DomyslnaCiemna }

/** Aktualny ekran dla Fazy 3 — ustawiany przez providery ekranów. */
val LocalEkranMotywu = staticCompositionLocalOf<EkranMotywu?> { null }

object MotywAktualny {
    @Volatile
    var paleta: MotywPaleta = MotywPaleta.DomyslnaCiemna
        private set

    val accent: Color get() = paleta.accent
    val accentMorski: Color get() = paleta.accentMorski
    val przycisk: Color get() = paleta.przycisk
    val naPrzycisku: Color get() = paleta.naPrzycisku
    val tlo: Color get() = paleta.tlo
    val naTle: Color get() = paleta.naTle
    val kafelek: Color get() = paleta.kafelek
    val aurora1: Color get() = paleta.aurora1
    val aurora2: Color get() = paleta.aurora2
    val blad: Color get() = paleta.blad
    val ciemny: Boolean get() = paleta.ciemny

    fun ustaw(p: MotywPaleta) {
        paleta = p
    }
}
