package dialer.dominik.pl.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.PaletteStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/** Tryb motywu zapamiętany w prefs. */
enum class TrybMotywu { System, Jasny, Ciemny }

/** Nazwy stylów MaterialKolor trzymane w prefs. */
object PaletteStyleName {
    const val TonalSpot = "TonalSpot"
    const val Vibrant = "Vibrant"
    const val Expressive = "Expressive"
    const val Fidelity = "Fidelity"

    fun doMaterial(nazwa: String): PaletteStyle = when (nazwa) {
        Vibrant -> PaletteStyle.Vibrant
        Expressive -> PaletteStyle.Expressive
        Fidelity -> PaletteStyle.Fidelity
        else -> PaletteStyle.TonalSpot
    }

    fun zMaterial(styl: PaletteStyle): String = when (styl) {
        PaletteStyle.Vibrant -> Vibrant
        PaletteStyle.Expressive -> Expressive
        PaletteStyle.Fidelity -> Fidelity
        else -> TonalSpot
    }
}

/** Identyfikatory ekranów z nadpisaniami Fazy 3. */
enum class EkranMotywu(val klucz: String, val etykieta: String) {
    Glowny("glowny", "Główny"),
    Kontakty("kontakty", "Kontakty"),
    Sms("sms", "Wiadomości"),
    Rozmowa("rozmowa", "Rozmowa")
}

/** Opcjonalne nadpisania dla jednego ekranu (null = dziedzicz globalne). */
data class NadpisaniaEkranu(
    val seed: Color? = null,
    val kafelek: Color? = null,
    val przycisk: Color? = null
)

/** Wariant animowanego tła (poświaty). */
enum class AnimacjaTla(val klucz: String, val etykieta: String, val opis: String) {
    Wylaczona("off", "Wyłączona", "Płaskie tło bez ruchu"),
    Aurora("aurora", "Aurora", "Dwie wędrujące poświaty"),
    Orbity("orbity", "Orbity", "Trzy krążące plamy"),
    Fale("fale", "Fale", "Poziome fale światła"),
    Siatka("siatka", "Siatka", "Delikatna siatka z pulsem"),
    Iskry("iskry", "Iskry", "Rozproszone punkty światła"),
    PipBoy("pipboy", "Pip-Boy", "CRT STATUS — sweep, fosfor, wireframe");

    companion object {
        fun zKlucza(klucz: String?): AnimacjaTla =
            entries.firstOrNull { it.klucz == klucz } ?: Aurora

        /** Animacje „tematyczne” — przy zmianie na zwykłą paletę wracamy do Aurory. */
        val Tematyczne: Set<AnimacjaTla> = setOf(PipBoy)
    }
}

/**
 * Globalny stan motywu — SharedPreferences `motyw_prefs` (wchodzi w backup prefs).
 */
object MotywStan {

    private const val PREFS = "motyw_prefs"
    private const val K_TRYB = "tryb"
    private const val K_SEED = "seed_argb"
    private const val K_SECONDARY = "secondary_argb"
    private const val K_TERTIARY = "tertiary_argb"
    private const val K_STYL = "palette_style"
    private const val K_PALETA_ID = "paleta_id"
    private const val K_PRZYCISK = "przycisk_argb"
    private const val K_KAFELEK = "kafelek_argb"
    private const val K_AURORA_ON = "aurora_on"
    private const val K_ANIMACJA_TLA = "animacja_tla"
    private const val K_AURORA1 = "aurora1_argb"
    private const val K_AURORA2 = "aurora2_argb"
    private const val K_EKRANY = "ekrany_json"

    val SEED_DOMYSLNY: Color = Color(0xFF69F0AE)

    /** @deprecated Używaj [MotywPaletyPresety.Wszystkie] — pojedyncze swatche seed. */
    val PRESETY: List<Color> = MotywPaletyPresety.Wszystkie.map { it.seed }

    data class Stan(
        val tryb: TrybMotywu = TrybMotywu.System,
        val seed: Color = SEED_DOMYSLNY,
        /** Drugi kolor palety (M3 secondary) — null = wygeneruj z seeda. */
        val secondary: Color? = MotywPaletyPresety.Wszystkie.first().secondary,
        /** Trzeci kolor palety (M3 tertiary). */
        val tertiary: Color? = MotywPaletyPresety.Wszystkie.first().tertiary,
        val stylPalety: String = PaletteStyleName.TonalSpot,
        val paletaId: String = MotywPaletyPresety.Wszystkie.first().id,
        /** null = użyj seed / accent z schematu */
        val kolorPrzycisku: Color? = null,
        /** null = wylicz z schematu */
        val kolorKafelka: Color? = null,
        val auroraWlaczona: Boolean = true,
        val animacjaTla: AnimacjaTla = AnimacjaTla.Aurora,
        /** null = wylicz z seeda */
        val aurora1: Color? = null,
        val aurora2: Color? = null,
        val ekrany: Map<EkranMotywu, NadpisaniaEkranu> = emptyMap()
    )

    private val _stan = MutableStateFlow(Stan())
    val stan: StateFlow<Stan> = _stan.asStateFlow()

    @Volatile
    private var zaladowane = false

    fun wczytaj(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val tryb = when (p.getString(K_TRYB, TrybMotywu.System.name)) {
            TrybMotywu.Jasny.name -> TrybMotywu.Jasny
            TrybMotywu.Ciemny.name -> TrybMotywu.Ciemny
            else -> TrybMotywu.System
        }
        fun kolorLubNull(klucz: String): Color? =
            if (p.contains(klucz)) Color(p.getInt(klucz, 0)) else null

        val domyslna = MotywPaletyPresety.Wszystkie.first()
        val surowyPaletaId = p.getString(K_PALETA_ID, domyslna.id) ?: domyslna.id
        // Usunięte motywy (gow / wiedźmin / cyber / igrzyska / pokemon) → domyślna paleta.
        val paletaId = when {
            MotywPaletyPresety.poId(surowyPaletaId) != null -> surowyPaletaId
            surowyPaletaId == "custom" -> surowyPaletaId
            else -> domyslna.id
        }
        _stan.value = Stan(
            tryb = tryb,
            seed = Color(p.getInt(K_SEED, SEED_DOMYSLNY.toArgb())),
            secondary = kolorLubNull(K_SECONDARY) ?: domyslna.secondary,
            tertiary = kolorLubNull(K_TERTIARY) ?: domyslna.tertiary,
            stylPalety = p.getString(K_STYL, PaletteStyleName.TonalSpot) ?: PaletteStyleName.TonalSpot,
            paletaId = paletaId,
            kolorPrzycisku = kolorLubNull(K_PRZYCISK),
            kolorKafelka = kolorLubNull(K_KAFELEK),
            auroraWlaczona = p.getBoolean(K_AURORA_ON, true),
            animacjaTla = run {
                val zapisana = p.getString(K_ANIMACJA_TLA, null)
                when {
                    zapisana != null -> AnimacjaTla.zKlucza(zapisana)
                    !p.getBoolean(K_AURORA_ON, true) -> AnimacjaTla.Wylaczona
                    else -> AnimacjaTla.Aurora
                }
            },
            aurora1 = kolorLubNull(K_AURORA1),
            aurora2 = kolorLubNull(K_AURORA2),
            ekrany = odczytajEkrany(p.getString(K_EKRANY, null))
        )
        zaladowane = true
        IkonaUruchamiacza.synchronizuj(context, _stan.value.paletaId)
    }

    fun zapewnijWczytane(context: Context) {
        if (!zaladowane) wczytaj(context)
    }

    private fun zapisz(context: Context, nowy: Stan) {
        val ed = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(K_TRYB, nowy.tryb.name)
            .putInt(K_SEED, nowy.seed.toArgb())
            .putString(K_STYL, nowy.stylPalety)
            .putString(K_PALETA_ID, nowy.paletaId)
            .putBoolean(K_AURORA_ON, nowy.animacjaTla != AnimacjaTla.Wylaczona)
            .putString(K_ANIMACJA_TLA, nowy.animacjaTla.klucz)
        fun putKolor(klucz: String, c: Color?) {
            if (c == null) ed.remove(klucz) else ed.putInt(klucz, c.toArgb())
        }
        putKolor(K_SECONDARY, nowy.secondary)
        putKolor(K_TERTIARY, nowy.tertiary)
        putKolor(K_PRZYCISK, nowy.kolorPrzycisku)
        putKolor(K_KAFELEK, nowy.kolorKafelka)
        putKolor(K_AURORA1, nowy.aurora1)
        putKolor(K_AURORA2, nowy.aurora2)
        ed.putString(K_EKRANY, zapiszEkrany(nowy.ekrany))
        ed.apply()
        _stan.value = nowy
    }

    fun ustawTryb(context: Context, tryb: TrybMotywu) {
        zapisz(context, _stan.value.copy(tryb = tryb))
    }

    /** Wybór nazwanej palety (seed + secondary + tertiary + styl). */
    fun ustawPalete(context: Context, paleta: MotywPaletaNazwana) {
        val vault = paleta.id == "fallout"
        val poprzednia = _stan.value.animacjaTla
        val animacja = when {
            vault -> AnimacjaTla.PipBoy
            poprzednia in AnimacjaTla.Tematyczne -> AnimacjaTla.Aurora
            else -> poprzednia
        }
        val tryb = when {
            vault -> TrybMotywu.Ciemny
            else -> _stan.value.tryb
        }
        zapisz(
            context,
            _stan.value.copy(
                tryb = tryb,
                seed = paleta.seed,
                secondary = paleta.secondary,
                tertiary = paleta.tertiary,
                stylPalety = PaletteStyleName.zMaterial(paleta.styl),
                paletaId = paleta.id,
                animacjaTla = animacja,
                auroraWlaczona = animacja != AnimacjaTla.Wylaczona,
                kolorPrzycisku = if (vault) paleta.seed else null,
                kolorKafelka = if (vault) Color(0xFF0A1A12) else null,
                aurora1 = if (vault) Color(0xFF003E7E) else null,
                aurora2 = if (vault) Color(0xFF18FF6D) else null
            )
        )
        IkonaUruchamiacza.synchronizuj(context, paleta.id)
    }

    fun ustawSeed(context: Context, seed: Color) {
        val znana = MotywPaletyPresety.dopasujDoSeeda(seed)
        if (znana != null) {
            ustawPalete(context, znana)
        } else {
            zapisz(
                context,
                _stan.value.copy(
                    seed = seed,
                    secondary = null,
                    tertiary = null,
                    paletaId = "custom",
                    stylPalety = PaletteStyleName.TonalSpot
                )
            )
        }
    }

    fun ustawKolorPrzycisku(context: Context, kolor: Color?) {
        zapisz(context, _stan.value.copy(kolorPrzycisku = kolor))
    }

    fun ustawKolorKafelka(context: Context, kolor: Color?) {
        zapisz(context, _stan.value.copy(kolorKafelka = kolor))
    }

    fun ustawAurora(context: Context, wlaczona: Boolean) {
        zapisz(
            context,
            _stan.value.copy(
                auroraWlaczona = wlaczona,
                animacjaTla = if (wlaczona) {
                    if (_stan.value.animacjaTla == AnimacjaTla.Wylaczona) AnimacjaTla.Aurora
                    else _stan.value.animacjaTla
                } else {
                    AnimacjaTla.Wylaczona
                }
            )
        )
    }

    fun ustawAnimacjeTla(context: Context, animacja: AnimacjaTla) {
        zapisz(
            context,
            _stan.value.copy(
                animacjaTla = animacja,
                auroraWlaczona = animacja != AnimacjaTla.Wylaczona
            )
        )
    }

    fun ustawAuroraKolory(context: Context, a1: Color?, a2: Color?) {
        zapisz(context, _stan.value.copy(aurora1 = a1, aurora2 = a2))
    }

    fun ustawNadpisanieEkranu(context: Context, ekran: EkranMotywu, nad: NadpisaniaEkranu?) {
        val mapa = _stan.value.ekrany.toMutableMap()
        if (nad == null || (nad.seed == null && nad.kafelek == null && nad.przycisk == null)) {
            mapa.remove(ekran)
        } else {
            mapa[ekran] = nad
        }
        zapisz(context, _stan.value.copy(ekrany = mapa))
    }

    fun resetZaawansowane(context: Context) {
        zapisz(
            context,
            _stan.value.copy(
                kolorPrzycisku = null,
                kolorKafelka = null,
                auroraWlaczona = true,
                animacjaTla = AnimacjaTla.Aurora,
                aurora1 = null,
                aurora2 = null
            )
        )
    }

    /** Pełny reset: tryb System, domyślna paleta, aurora, bez nadpisań. */
    fun resetMotywu(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        _stan.value = Stan()
        zaladowane = true
        IkonaUruchamiacza.synchronizuj(context, _stan.value.paletaId)
    }

    fun resetEkrany(context: Context) {
        zapisz(context, _stan.value.copy(ekrany = emptyMap()))
    }

    @Composable
    fun czyCiemny(): Boolean {
        val s = _stan.value
        return when (s.tryb) {
            TrybMotywu.Jasny -> false
            TrybMotywu.Ciemny -> true
            TrybMotywu.System -> isSystemInDarkTheme()
        }
    }

    private fun zapiszEkrany(mapa: Map<EkranMotywu, NadpisaniaEkranu>): String {
        val root = JSONObject()
        for ((ekran, nad) in mapa) {
            val o = JSONObject()
            nad.seed?.let { o.put("seed", it.toArgb()) }
            nad.kafelek?.let { o.put("kafelek", it.toArgb()) }
            nad.przycisk?.let { o.put("przycisk", it.toArgb()) }
            if (o.length() > 0) root.put(ekran.klucz, o)
        }
        return root.toString()
    }

    private fun odczytajEkrany(json: String?): Map<EkranMotywu, NadpisaniaEkranu> {
        if (json.isNullOrBlank()) return emptyMap()
        return try {
            val root = JSONObject(json)
            val wynik = mutableMapOf<EkranMotywu, NadpisaniaEkranu>()
            for (ekran in EkranMotywu.entries) {
                if (!root.has(ekran.klucz)) continue
                val o = root.getJSONObject(ekran.klucz)
                wynik[ekran] = NadpisaniaEkranu(
                    seed = if (o.has("seed")) Color(o.getInt("seed")) else null,
                    kafelek = if (o.has("kafelek")) Color(o.getInt("kafelek")) else null,
                    przycisk = if (o.has("przycisk")) Color(o.getInt("przycisk")) else null
                )
            }
            wynik
        } catch (_: Exception) {
            emptyMap()
        }
    }
}
