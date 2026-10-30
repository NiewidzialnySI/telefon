package dialer.dominik.pl.nagranie

import android.content.Context
import android.os.Build

/**
 * Pamięć „co działa na tym telefonie” — po udanej strategii następna rozmowa
 * startuje od razu z niej, bez spalania drabinki.
 */
object ZdolnosciCache {
    /** v4 — hybryda/dual zawsze w drabince; vc_mono nie jest preferencją. */
    private const val PREFS = "nagrywanie_zdolnosci_v4"
    private const val K_FINGERPRINT = "fp"
    private const val K_PREFEROWANA = "preferowana"
    private const val K_CISZE = "cisze"
    private const val K_FAIL = "fail"

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun upewnijFp(ctx: Context) {
        val p = prefs(ctx)
        val fp = Build.FINGERPRINT
        if (p.getString(K_FINGERPRINT, null) != fp) {
            p.edit()
                .putString(K_FINGERPRINT, fp)
                .remove(K_PREFEROWANA)
                .remove(K_CISZE)
                .remove(K_FAIL)
                .apply()
        }
    }

    fun preferowana(ctx: Context): SilnikNagrywania? {
        upewnijFp(ctx)
        val id = prefs(ctx).getString(K_PREFEROWANA, null) ?: return null
        return SilnikNagrywania.entries.find { it.id == id }
    }

    fun zapiszSukces(ctx: Context, silnik: SilnikNagrywania) {
        upewnijFp(ctx)
        // Mono VC / voice_com / mic — jednostronne albo tylko Ty; nie preferuj.
        if (silnik == SilnikNagrywania.MicOnly ||
            silnik == SilnikNagrywania.VoiceCommunication ||
            silnik == SilnikNagrywania.VoiceCallMono
        ) return
        prefs(ctx).edit().putString(K_PREFEROWANA, silnik.id).apply()
    }

    fun oznaczCicha(ctx: Context, silnik: SilnikNagrywania) {
        upewnijFp(ctx)
        val p = prefs(ctx)
        val set = p.getStringSet(K_CISZE, emptySet())!!.toMutableSet()
        set.add(silnik.id)
        p.edit().putStringSet(K_CISZE, set).apply()
    }

    fun oznaczFail(ctx: Context, silnik: SilnikNagrywania) {
        upewnijFp(ctx)
        val p = prefs(ctx)
        val set = p.getStringSet(K_FAIL, emptySet())!!.toMutableSet()
        set.add(silnik.id)
        p.edit().putStringSet(K_FAIL, set).apply()
    }

    fun czyCicha(ctx: Context, silnik: SilnikNagrywania): Boolean {
        upewnijFp(ctx)
        return prefs(ctx).getStringSet(K_CISZE, emptySet())!!.contains(silnik.id)
    }

    fun czyFail(ctx: Context, silnik: SilnikNagrywania): Boolean {
        upewnijFp(ctx)
        return prefs(ctx).getStringSet(K_FAIL, emptySet())!!.contains(silnik.id)
    }

    fun reset(ctx: Context) {
        prefs(ctx).edit().clear().apply()
    }

    fun opis(ctx: Context): String {
        upewnijFp(ctx)
        val pref = preferowana(ctx)?.etykieta ?: "—"
        val cisze = prefs(ctx).getStringSet(K_CISZE, emptySet())!!.size
        val fail = prefs(ctx).getStringSet(K_FAIL, emptySet())!!.size
        return "Preferowany: $pref · ciche:$cisze · fail:$fail"
    }
}

/**
 * Silniki — kolejność = naturalna drabinka (preferowany wskakuje na początek).
 *
 * Przewaga dialera: [HybrydaMicDownlink] miesza MIC w naszym procesie
 * (mamy RECORD_AUDIO + timing InCallService) z DOWNLINK w shell.
 */
enum class SilnikNagrywania(val id: String, val etykieta: String) {
    // Hybryda pierwsza: MIC łapie „mnie”, gdy OEM blokuje VOICE_UPLINK (tylko oni w Dual).
    HybrydaMicDownlink("hybryda", "Hybryda MIC+DOWNLINK"),
    DualUpDown("dual_up_dn", "Shell UPLINK+DOWNLINK"),
    VoiceCallStereo("vc_stereo", "Shell VOICE_CALL stereo"),
    VoiceCallMono("vc_mono", "Shell VOICE_CALL mono"),
    PlaybackPolicy("playback", "Shell AudioPolicy (VoIP)"),
    LokalnyVoiceCall("lokalny_vc", "Lokalny VOICE_CALL"),
    VoiceCommunication("voice_com", "Shell VOICE_COMMUNICATION"),
    MicOnly("mic", "Tylko mikrofon"),
}
