package dialer.dominik.pl.nagranie

import org.junit.Assert.assertEquals
import org.junit.Test

class DiarizacjaRozmowyTest {

    @Test
    fun scalPrzeplataJaIRozmowce() {
        val ja = listOf(
            DiarizacjaRozmowy.Segment(0, 1000, "Ja", "cześć"),
            DiarizacjaRozmowy.Segment(3000, 4000, "Ja", "co słychać"),
        )
        val oni = listOf(
            DiarizacjaRozmowy.Segment(1200, 2500, "Rozmówca", "hej"),
            DiarizacjaRozmowy.Segment(4500, 5000, "Rozmówca", "spoko"),
        )
        val out = DiarizacjaRozmowy.scal(ja, oni)
        assertEquals(
            """
            Ja: cześć
            Rozmówca: hej
            Ja: co słychać
            Rozmówca: spoko
            """.trimIndent(),
            out,
        )
    }

    @Test
    fun scalLaczyKolejneSegmentyTegoSamego() {
        val ja = listOf(
            DiarizacjaRozmowy.Segment(0, 500, "Ja", "raz"),
            DiarizacjaRozmowy.Segment(600, 900, "Ja", "dwa"),
        )
        val out = DiarizacjaRozmowy.scal(ja, emptyList())
        assertEquals("Ja: raz dwa", out)
    }

    @Test
    fun scalPuste() {
        assertEquals("", DiarizacjaRozmowy.scal(emptyList(), emptyList()))
    }
}
