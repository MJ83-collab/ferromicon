package it.ferromicon

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarioRiposiTest {
    @Test
    fun primoGiornoDelCalendario() {
        assertEquals("4", CalendarioRiposi.codice('A', LocalDate.of(2025, 12, 21)))
        assertEquals("5", CalendarioRiposi.codice('B', LocalDate.of(2025, 12, 21)))
    }

    @Test
    fun colonnaBIlGiornoDopo() {
        assertEquals("GRS", CalendarioRiposi.codice('B', LocalDate.of(2025, 12, 22)))
    }

    @Test
    fun cambioDiBloccoDiVentottoGiorni() {
        assertEquals("3", CalendarioRiposi.codice('A', LocalDate.of(2026, 1, 18)))
        assertEquals("4", CalendarioRiposi.codice('B', LocalDate.of(2026, 1, 18)))
    }

    @Test
    fun ultimoGiornoVerificato() {
        assertEquals("4", CalendarioRiposi.codice('A', LocalDate.of(2028, 1, 7)))
        assertEquals("5", CalendarioRiposi.codice('B', LocalDate.of(2028, 1, 7)))
    }
}
