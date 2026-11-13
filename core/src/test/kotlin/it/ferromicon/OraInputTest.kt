package it.ferromicon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalTime

class OraInputTest {
    @Test
    fun accettaHHmmEHHColon() {
        assertEquals(LocalTime.of(8, 40), OraInput.parse("0840"))
        assertEquals(LocalTime.of(8, 40), OraInput.parse("840"))
        assertEquals(LocalTime.of(8, 40), OraInput.parse("08:40"))
        assertEquals(LocalTime.of(23, 5), OraInput.parse(" 2305 "))
    }

    @Test
    fun rifiutaValoriNonValidi() {
        assertNull(OraInput.parse("2460"))
        assertNull(OraInput.parse("08:4"))
        assertNull(OraInput.parse("ab"))
        assertNull(OraInput.parse(""))
    }

    @Test
    fun formatoHHmm() {
        assertEquals("0840", OraInput.formato(LocalTime.of(8, 40)))
    }
}
