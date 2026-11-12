package it.ferromicon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class InvernaliTest {
    @Test fun letteraUgualeTraEstivoEInvernale() {
        val estD = PeriodiFerie.estivi(2026).first { it.nome == "Estivo D" }
        val inv = PeriodiFerie.invernalePer(estD)!!
        assertEquals("Invernale D (25 aprile)", inv.nome)
        assertEquals(LocalDate.of(2027, 4, 25), inv.dal)
        assertEquals(LocalDate.of(2027, 5, 1), inv.al)
    }

    @Test fun stagione2026Corrisponde() {
        val s = PeriodiFerie.invernali(2026)
        assertEquals(LocalDate.of(2027, 3, 25), s.first { it.nome.contains("Pasqua") }.dal)
        assertEquals(LocalDate.of(2027, 3, 31), s.first { it.nome.contains("Pasqua") }.al)
        assertEquals(LocalDate.of(2026, 12, 28), s.first { it.nome.contains("Capodanno") }.dal)
        assertEquals(LocalDate.of(2027, 1, 3), s.first { it.nome.contains("Capodanno") }.al)
    }

    @Test fun pasquaCalcolata() {
        assertEquals(LocalDate.of(2026, 4, 5), PeriodiFerie.pasqua(2026))
        assertEquals(LocalDate.of(2027, 3, 28), PeriodiFerie.pasqua(2027))
        assertEquals(LocalDate.of(2028, 4, 16), PeriodiFerie.pasqua(2028))
    }

    @Test fun sequenzaScorreOgniAnno() {
        assertEquals("D", PeriodiFerie.letteraPerAnno("D", 2026, 2026))
        assertEquals("A", PeriodiFerie.letteraPerAnno("D", 2026, 2027))
        assertEquals("C", PeriodiFerie.letteraPerAnno("D", 2026, 2028))
        assertEquals("B", PeriodiFerie.letteraPerAnno("D", 2026, 2031))
        assertEquals("D", PeriodiFerie.letteraPerAnno("D", 2026, 2032))
    }

    @Test fun invernoSuccessivoConPasquaNuova() {
        assertEquals("Invernale A (Natale)", PeriodiFerie.invernalePerAnno("D", 2027)!!.nome)
        assertEquals(LocalDate.of(2028, 4, 13), PeriodiFerie.invernali(2027).first { it.nome.contains("Pasqua") }.dal)
        assertEquals(LocalDate.of(2028, 4, 19), PeriodiFerie.invernali(2027).first { it.nome.contains("Pasqua") }.al)
    }

    @Test fun seiPeriodiInvernali() {
        assertEquals(6, PeriodiFerie.invernali(2026).size)
    }

    @Test fun letteraSconosciutaNonDaPeriodo() {
        assertNull(PeriodiFerie.invernalePer(PeriodoFerie("Estivo Z", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 2))))
    }
}
