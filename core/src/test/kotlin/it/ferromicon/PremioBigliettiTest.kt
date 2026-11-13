package it.ferromicon

import org.junit.Assert.assertEquals
import org.junit.Test

class PremioBigliettiTest {

    @Test fun regolarizzazioniDueEuroCiascuna() {
        // Febbraio 2026 nel cedolino: 3 regolarizzazioni = 6 €
        val voci = PremioBiglietti.calcola(PremioBiglietti.Dati(regolarizzazioni = 3))
        assertEquals(6.0, voci.single { it.codice == "1A28" }.importo, 1e-9)
    }

    @Test fun bigliettiEdSovrattassaAl25() {
        val voci = PremioBiglietti.calcola(PremioBiglietti.Dati(
            bigliettiSenzaEsazione = 40.0, sovrattassaRiscossa = 20.0))
        assertEquals(10.0, voci.single { it.codice == "1A86" }.importo, 1e-9)
        assertEquals(5.0, voci.single { it.codice == "1A87" }.importo, 1e-9)
    }

    @Test fun verbaliPagatiDueEuro() {
        val voci = PremioBiglietti.calcola(PremioBiglietti.Dati(verbaliPagati = 4))
        assertEquals(8.0, voci.single { it.codice == "1A99" }.importo, 1e-9)
    }

    @Test fun lungaPercorrenzaSettePercentoSullaRegolarizzazione() {
        val voci = PremioBiglietti.calcola(PremioBiglietti.Dati(regolarizzazioni = 10, lungaPercorrenza = true))
        assertEquals(20.0, voci.single { it.codice == "1A28" }.importo, 1e-9)
        assertEquals(1.4, voci.single { it.codice == "LP" }.importo, 1e-9)
    }

    @Test fun senzaDatiNessunaVoce() {
        assertEquals(0, PremioBiglietti.calcola(PremioBiglietti.Dati()).size)
        assertEquals(0.0, PremioBiglietti.totale(PremioBiglietti.Dati()), 1e-9)
    }
}
