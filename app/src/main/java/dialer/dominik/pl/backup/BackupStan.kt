package dialer.dominik.pl.backup

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Globalny stan backupu — UI i foreground service. */
object BackupStan {

    private const val PREFS = "backup_meta"
    private const val K_OSTATNI = "ostatni_backup_ms"
    private const val K_IMIE = "profil_imie"
    private const val K_EMAIL = "profil_email"
    private const val K_ZDJECIE = "profil_zdjecie"

    data class Profil(
        val imie: String,
        val email: String,
        val zdjecieUrl: String?
    )

    enum class Tryb { Bezczynny, Backup, Przywroc }

    data class StanUi(
        val tryb: Tryb = Tryb.Bezczynny,
        /** 0f..1f — wypełnienie przycisku. */
        val postep: Float = 0f,
        val etykieta: String = "",
        val blad: String? = null,
        val sukces: String? = null,
        val ostatniBackupMs: Long = 0L,
        val profil: Profil? = null
    )

    private val _stan = MutableStateFlow(StanUi())
    val stan: StateFlow<StanUi> = _stan.asStateFlow()

    /** Krótkotrwały token dla serwisu (nie w Intent). */
    @Volatile
    var tokenTymczasowy: String? = null

    fun wczytajCache(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val imie = p.getString(K_IMIE, null)
        val email = p.getString(K_EMAIL, null)
        val foto = p.getString(K_ZDJECIE, null)
        val ostatni = p.getLong(K_OSTATNI, 0L)
        _stan.value = _stan.value.copy(
            ostatniBackupMs = ostatni,
            profil = if (!imie.isNullOrBlank()) {
                Profil(imie, email.orEmpty(), foto)
            } else null
        )
    }

    fun zapiszProfil(context: Context, profil: Profil) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(K_IMIE, profil.imie)
            .putString(K_EMAIL, profil.email)
            .putString(K_ZDJECIE, profil.zdjecieUrl)
            .apply()
        _stan.value = _stan.value.copy(profil = profil)
    }

    fun zapiszOstatniBackup(context: Context, ms: Long = System.currentTimeMillis()) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(K_OSTATNI, ms)
            .apply()
        _stan.value = _stan.value.copy(ostatniBackupMs = ms)
    }

    fun ustawOstatniZDysku(ms: Long) {
        if (ms <= 0L) return
        val biezacy = _stan.value.ostatniBackupMs
        if (ms > biezacy) {
            _stan.value = _stan.value.copy(ostatniBackupMs = ms)
        }
    }

    fun start(tryb: Tryb) {
        _stan.value = _stan.value.copy(
            tryb = tryb,
            postep = 0.02f,
            etykieta = if (tryb == Tryb.Backup) "Przygotowanie…" else "Przywracanie…",
            blad = null,
            sukces = null
        )
    }

    fun postep(wartosc: Float, etykieta: String) {
        _stan.value = _stan.value.copy(
            postep = wartosc.coerceIn(0f, 1f),
            etykieta = etykieta
        )
    }

    fun sukces(komunikat: String) {
        _stan.value = _stan.value.copy(
            tryb = Tryb.Bezczynny,
            postep = 1f,
            etykieta = "",
            sukces = komunikat,
            blad = null
        )
    }

    fun blad(komunikat: String) {
        _stan.value = _stan.value.copy(
            tryb = Tryb.Bezczynny,
            postep = 0f,
            etykieta = "",
            blad = komunikat,
            sukces = null
        )
    }

    fun czyTrwa(): Boolean = _stan.value.tryb != Tryb.Bezczynny
}
