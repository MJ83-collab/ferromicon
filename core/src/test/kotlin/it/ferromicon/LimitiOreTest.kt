package it.ferromicon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class LimitiOreTest {

    @Test fun limiteMensileSecondoFormula() {
        assertEquals(162.857, LimitiOre.limiteMensile(30), 0.01)
        assertEquals(152.0, LimitiOre.limiteMensile(28), 1e-9)
        assertEquals(168.286, LimitiOre.limiteMensile(31), 0.01)
    }

    @Test fun settimanaOltreIlLimite() {
        // Lunedì 2 novembre 2026: 45 ore in una settimana, 10 nella successiva
        val lunedi = LocalDate.of(2026, 11, 2)
        val intervalli = listOf(
            LocalDateTime.of(lunedi, java.time.LocalTime.of(5, 0)) to LocalDateTime.of(lunedi, java.time.LocalTime.of(5, 0)).plusHours(45),
            LocalDateTime.of(lunedi.plusDays(8), java.time.LocalTime.of(5, 0)) to LocalDateTime.of(lunedi.plusDays(8), java.time.LocalTime.of(5, 0)).plusHours(10)
        )
        val oltre = LimitiOre.settimaneOltre(intervalli, LimitiOre.LIMITE_SETTIMANALE_PROGRAMMAZIONE)
        assertEquals(1, oltre.size)
        assertEquals(lunedi, oltre.single().first)
        assertEquals(45.0, oltre.single().second, 1e-9)
    }

    @Test fun nessunaSettimanaOltreSeSottoLimite() {
        val d = LocalDateTime.of(2026, 11, 3, 6, 0)
        assertTrue(LimitiOre.settimaneOltre(listOf(d to d.plusHours(8)), 44.0).isEmpty())
    }
}
