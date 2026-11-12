package it.ferromicon

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StaffTest {
    private fun dt(g: Int, h: Int, m: Int): LocalDateTime = LocalDateTime.of(2026, 11, g, h, m)

    @Test
    fun flessibilitaEntroIlLimiteNonSiTocca() {
        assertEquals(120 to 0, OrarioStaff.riportoFlessibilita(120))
        assertEquals(-120 to 0, OrarioStaff.riportoFlessibilita(-120))
    }

    @Test
    fun flessibilitaPositivaOltreLimiteSiRiportaACinqueOre() {
        assertEquals(300 to 100, OrarioStaff.riportoFlessibilita(400))
    }

    @Test
    fun flessibilitaNegativaOltreLimiteSiTrattiene() {
        assertEquals(-300 to -100, OrarioStaff.riportoFlessibilita(-400))
    }

    @Test
    fun limiteUfficioEOrarioOrdinarioStaff() {
        assertEquals(LimitiOrario(456, 456), LimitiOrario.contratto(Profilo.UFFICIO))
        assertEquals(456, Competenze.limiteStraordinario(Turno(dt(2, 8, 15), dt(2, 16, 21), Profilo.UFFICIO)))
    }

    @Test
    fun ufficioOltreLOrarioOrdinarioEStraordinario() {
        val esiti = RegoleAltarail.verificaTurno(Turno(dt(2, 8, 0), dt(2, 17, 0), Profilo.UFFICIO))
        assertTrue(esiti.any { it.gravita == Gravita.INFO && it.messaggio.contains("straordinario") })
    }

    @Test
    fun ufficioNotturnoSegnalato() {
        val esiti = RegoleAltarail.verificaTurno(Turno(dt(2, 22, 0), dt(3, 4, 0), Profilo.UFFICIO))
        assertTrue(esiti.any { it.gravita == Gravita.ATTENZIONE })
    }
}
