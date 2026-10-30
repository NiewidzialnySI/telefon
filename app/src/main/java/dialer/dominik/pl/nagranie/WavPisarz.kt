package dialer.dominik.pl.nagranie

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Prosty writer WAV PCM 16-bit LE. Nagłówek uzupełniany przy close().
 */
internal class WavPisarz(
    private val plik: File,
    private val sampleRate: Int,
    private val kanaly: Int,
) {
    private val raf = RandomAccessFile(plik, "rw")
    private var bajtyDanych = 0L

    init {
        raf.setLength(0)
        // Placeholder 44 B — przepiszemy przy close
        raf.write(ByteArray(44))
    }

    fun write(buf: ByteArray, offset: Int, len: Int) {
        raf.write(buf, offset, len)
        bajtyDanych += len
    }

    fun close() {
        try {
            raf.seek(0)
            val bb = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            val bits = 16
            val byteRate = sampleRate * kanaly * bits / 8
            val blockAlign = (kanaly * bits / 8).toShort()
            bb.put("RIFF".toByteArray())
            bb.putInt((36 + bajtyDanych).toInt())
            bb.put("WAVE".toByteArray())
            bb.put("fmt ".toByteArray())
            bb.putInt(16)
            bb.putShort(1) // PCM
            bb.putShort(kanaly.toShort())
            bb.putInt(sampleRate)
            bb.putInt(byteRate)
            bb.putShort(blockAlign)
            bb.putShort(bits.toShort())
            bb.put("data".toByteArray())
            bb.putInt(bajtyDanych.toInt())
            raf.write(bb.array())
        } finally {
            raf.close()
        }
    }
}
