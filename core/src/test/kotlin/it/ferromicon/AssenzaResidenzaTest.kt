package it.ferromicon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class AssenzaResidenzaTest {

    private val dopo = LocalDate.of(2025, 9, 1)
    private val prima = LocalDate.of(2025, 6, 1)

    @Test fun italiaSenzaERiposo() {
        assertEquals(1.60, AssenzaResidenza.tariffaOraria(false, false, false, dopo)!!, 1e-9)
        assertEquals(2.60, AssenzaResidenza.tariffaOraria(true, false, false, dopo)!!, 1e-9)
    }

    @Test fun esteroTariffe() {
        assertEquals(2.00, AssenzaResidenza.tariffaOraria(false, true, false, dopo)!!, 1e-9)
        assertEquals(3.20, AssenzaResidenza.tariffaOraria(true, true, false, dopo)!!, 1e-9)
    }

    @Test fun accompagnamentoNotte() {
        assertEquals(1.00, AssenzaResidenza.tariffaOraria(false, false, true, dopo)!!, 1e-9)
        assertEquals(1.60, AssenzaResidenza.tariffaOraria(false, true, true, dopo)!!, 1e-9)
    }

    @Test fun prima_dellAgostoNonVerificata() {
        assertNull(AssenzaResidenza.tariffaOraria(true, false, false, prima))
    }
}
