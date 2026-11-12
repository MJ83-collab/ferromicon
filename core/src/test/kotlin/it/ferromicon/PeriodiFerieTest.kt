package it.ferromicon

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PeriodiFerieTest {
    @Test
    fun gruppoDInvernoDueMilaVentisei() {
        val p = PeriodiFerie.invernale('D', 2026)!!
        assertEquals(LocalDate.of(2027, 4, 25), p.da)
        assertEquals(LocalDate.of(2027, 5, 1), p.a)
    }

    @Test
    fun gruppoEImmacolata() {
        val p = PeriodiFerie.invernale('e', 2026)!!
        assertEquals("Immacolata", p.nome)
        assertEquals(LocalDate.of(2026, 12, 5), p.da)
    }

    @Test
    fun annoSenzaTabellaNonInventaDate() {
        assertNull(PeriodiFerie.invernale('D', 2029))
    }
}
