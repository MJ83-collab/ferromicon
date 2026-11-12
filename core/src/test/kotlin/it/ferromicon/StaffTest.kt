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

    private fun giorno(g: Int, prima: String?, ultima: String?, flex: Double?, timbrature: Int) =
        GiornataSap(g, "LU", prima, ultima, flex, timbrature)

    @Test
    fun saldoMeseSommaFlessibilitaEConRiporto() {
        val g = listOf(giorno(1, "08:15", "16:21", 0.5, 2), giorno(2, "08:15", "16:21", -0.25, 2))
        assertEquals(15, OrarioStaff.saldoMese(g))
        assertEquals(75, OrarioStaff.saldoMese(g, 60))
    }

    @Test
    fun timbratureInsufficientiSegnalate() {
        val g = listOf(giorno(1, "08:15", "16:21", null, 4), giorno(2, "08:15", "13:00", null, 2), giorno(3, null, null, null, 0))
        assertEquals(listOf(2), OrarioStaff.giornateConTimbratureInsufficienti(g).map { it.giorno })
    }

    @Test
    fun uscitaAnticipataSenzaPermessoSegnalata() {
        val g = listOf(giorno(1, "08:15", "15:00", null, 2), giorno(2, "08:15", "15:00", null, 2), giorno(3, "08:15", "16:30", null, 2))
        assertEquals(listOf(2), OrarioStaff.usciteAnticipateSenzaPermesso(g, setOf(1)))
    }

    @Test
    fun permessoOreCostoSecondoVademecum() {
        assertEquals(60, OrarioStaff.costoPermessoOre(25))
        assertEquals(135, OrarioStaff.costoPermessoOre(135))
        assertEquals(456, OrarioStaff.costoPermessoOre(500))
        assertEquals(0, OrarioStaff.costoPermessoOre(0))
    }

    @Test
    fun minutiPeriodiServizioFuoriSede() {
        assertEquals(180 + 120, OrarioStaff.minutiPeriodi(listOf("08:30" to "11:30", "13:00" to "15:00")))
        assertEquals(0, OrarioStaff.minutiPeriodi(listOf("10:00" to "xx")))
    }

    @Test
    fun exFestivitaResiduo() {
        assertEquals(4, OrarioStaff.exFestivitaResidue(0))
        assertEquals(0, OrarioStaff.exFestivitaResidue(6))
    }

    @Test
    fun avvisoFlessibilitaOltreCinqueOre() {
        assertEquals(null, OrarioStaff.avvisoFlessibilita(300))
        assertTrue(OrarioStaff.avvisoFlessibilita(301) != null)
        assertTrue(OrarioStaff.avvisoFlessibilita(-301) != null)
    }
}
