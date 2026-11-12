package it.ferromicon

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class PeriodiFerieTest {
    @Test
    fun gruppoDInvernoDueMilaVentisei() {
        val p = FerieInvernali.invernale('D', 2026)!!
        assertEquals(LocalDate.of(2027, 4, 25), p.da)
        assertEquals(LocalDate.of(2027, 5, 1), p.a)
    }

    @Test
    fun gruppoEImmacolata() {
        val p = FerieInvernali.invernale('e', 2026)!!
        assertEquals("Immacolata", p.nome)
        assertEquals(LocalDate.of(2026, 12, 5), p.da)
    }

    @Test
    fun annoSenzaTabellaNonInventaDate() {
        assertNull(FerieInvernali.invernale('D', 2029))
    }

    @Test
    fun sequenzaDelGruppoDSegueIlCiclo() {
        assertEquals('D', FerieInvernali.letteraPerAnno('D', 2026))
        assertEquals('A', FerieInvernali.letteraPerAnno('D', 2027))
        assertEquals('C', FerieInvernali.letteraPerAnno('D', 2028))
        assertEquals('E', FerieInvernali.letteraPerAnno('D', 2029))
        assertEquals('F', FerieInvernali.letteraPerAnno('D', 2030))
        assertEquals('B', FerieInvernali.letteraPerAnno('D', 2031))
        assertEquals('D', FerieInvernali.letteraPerAnno('D', 2032))
    }

    @Test
    fun sequenzaPrimaDelDueMilaVentisei() {
        assertEquals('B', FerieInvernali.letteraPerAnno('D', 2025))
    }

    @Test
    fun pianoDel2026GruppoDConRiposiB() {
        val p = FerieInvernali.piano(2026, 'D', 'B')
        assertEquals('D', p.lettera)
        assertEquals(LocalDate.of(2027, 4, 25), p.periodo!!.da)
        assertEquals(2, p.riposiNelPeriodo)
        assertTrue(p.riposiVerificati)
    }

    @Test
    fun pianoDel2029SenzaTabella() {
        val p = FerieInvernali.piano(2029, 'D', 'B')
        assertEquals('E', p.lettera)
        assertNull(p.periodo)
        assertNull(p.riposiNelPeriodo)
        assertFalse(p.riposiVerificati)
    }
}
