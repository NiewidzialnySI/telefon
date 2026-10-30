package dialer.dominik.pl.nagranie

/**
 * Pierścień PCM: trzyma ostatnie [pojemnoscMs] ms audio.
 * Przy „commit” strategii wypisujemy preroll NA POCZĄTEK pliku WAV
 * (przez tymczasowy staging), żeby nie uciąć ciepłego startu HAL.
 *
 * W praktyce pump pisze równolegle do [WavPisarz] i do tego bufora;
 * przy soft-switch zachowujemy ostatnie N ms z poprzedniej próby.
 */
internal class PrerollBufor(
    sampleRate: Int,
    kanaly: Int,
    pojemnoscMs: Int = 1500,
) {
    private val bajtowNaSample = 2 * kanaly
    private val pojemnosc = ((sampleRate * pojemnoscMs) / 1000) * bajtowNaSample
    private val buf = ByteArray(pojemnosc.coerceAtLeast(bajtowNaSample * 64))
    private var writePos = 0
    private var wypelnione = 0

    @Synchronized
    fun dodaj(data: ByteArray, offset: Int, len: Int) {
        if (len <= 0 || buf.isEmpty()) return
        var left = len
        var off = offset
        while (left > 0) {
            val chunk = minOf(left, buf.size - writePos)
            System.arraycopy(data, off, buf, writePos, chunk)
            writePos = (writePos + chunk) % buf.size
            left -= chunk
            off += chunk
            wypelnione = minOf(buf.size, wypelnione + chunk)
        }
    }

    /** Zwraca chronologiczną kopię (najstarsze → najnowsze). */
    @Synchronized
    fun zrzut(): ByteArray {
        if (wypelnione == 0) return ByteArray(0)
        val out = ByteArray(wypelnione)
        val start = if (wypelnione < buf.size) 0 else writePos
        if (start + wypelnione <= buf.size) {
            System.arraycopy(buf, start, out, 0, wypelnione)
        } else {
            val first = buf.size - start
            System.arraycopy(buf, start, out, 0, first)
            System.arraycopy(buf, 0, out, first, wypelnione - first)
        }
        return out
    }

    @Synchronized
    fun wyczysc() {
        writePos = 0
        wypelnione = 0
    }
}
