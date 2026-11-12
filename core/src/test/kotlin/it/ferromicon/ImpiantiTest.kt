package it.ferromicon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImpiantiTest {
    @Test
    fun elencoSenzaDoppioni() {
        assertEquals(Impianti.tutti.size, Impianti.tutti.toSet().size)
    }

    @Test
    fun elencoCompleto() {
        assertEquals(28, Impianti.tutti.size)
        assertTrue("Varese UN" in Impianti.tutti)
        assertTrue("Novara RFI" in Impianti.tutti && "Novara UN" in Impianti.tutti)
    }

    @Test
    fun etichetteQualificheConSigla() {
        assertTrue(Profilo.MACCHINISTA.etichetta.contains("PdC"))
        assertTrue(Profilo.CAPOTRENO.etichetta.contains("PdA"))
    }
}
