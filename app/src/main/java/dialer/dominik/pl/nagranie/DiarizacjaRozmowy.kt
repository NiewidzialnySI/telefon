package dialer.dominik.pl.nagranie

/**
 * Składa segmenty Whisper z torów Ja / Rozmówca w dialog z etykietami.
 */
internal object DiarizacjaRozmowy {

    data class Segment(
        val startMs: Long,
        val endMs: Long,
        val kto: String,
        val tekst: String,
    )

    const val ETYKIETA_JA = "Ja"
    const val ETYKIETA_ROZMOWCA = "Rozmówca"

    fun scal(
        ja: List<Segment>,
        rozmowca: List<Segment>,
    ): String {
        val all = (ja + rozmowca)
            .map { it.copy(tekst = it.tekst.trim()) }
            .filter { it.tekst.isNotBlank() }
            .sortedWith(compareBy({ it.startMs }, { it.kto }))
        if (all.isEmpty()) return ""

        val out = StringBuilder()
        var ostatniKto: String? = null
        val buf = StringBuilder()

        fun flush() {
            if (ostatniKto == null || buf.isEmpty()) return
            if (out.isNotEmpty()) out.append('\n')
            out.append(ostatniKto).append(": ").append(buf.toString().trim())
            buf.clear()
        }

        for (s in all) {
            if (s.kto != ostatniKto) {
                flush()
                ostatniKto = s.kto
            }
            if (buf.isNotEmpty()) buf.append(' ')
            buf.append(s.tekst)
        }
        flush()
        return out.toString()
    }
}
