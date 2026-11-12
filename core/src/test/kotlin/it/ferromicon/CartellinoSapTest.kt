package it.ferromicon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CartellinoSapTest {

    private val righe = listOf(
        "Cartellino orologio con selezione data 01.11.2026-30.11.2026",
        "01 DO RS Ognissanti",
        "02 LU G118 Timbrature 08:19 16:28 7,36 0,00 0,03 0,00 0,03",
        "03 MA G118 Timbrature 08:22 16:31 7,36 0,00 0,03 0,00 0,03",
        "Maggior presenza 16:28 7,36 0,00",
        "09 LU G118 Timbrature 08:12 15:36 6,51 0,00 0,00 0,00 0,45-",
        "6,51 0,00 0,00 0,00 0,45-",
        "Flessibilità mese precedente 5,00"
    )

    @Test fun leggePeriodoEGiornate() {
        val c = LettoreCartellino.analizza(righe)!!
        assertEquals(2026, c.anno)
        assertEquals(11, c.mese)
        assertEquals(4, c.giornate.size)
        assertEquals(1, c.giornate[0].giorno)
        assertEquals("DO", c.giornate[0].sigla)
        assertNull(c.giornate[0].prima)
    }

    @Test fun orariEFlexGiornaliero() {
        val c = LettoreCartellino.analizza(righe)!!
        val due = c.giornate.first { it.giorno == 2 }
        assertEquals("08:19", due.prima)
        assertEquals("16:28", due.ultima)
        assertEquals(0.03, due.flex!!, 0.0001)
    }

    @Test fun flexNegativoConSegnoFinale() {
        val c = LettoreCartellino.analizza(righe)!!
        assertEquals(-0.45, c.giornate.first { it.giorno == 9 }.flex!!, 0.0001)
    }

    @Test fun flexMesePrecedenteNonEFlexDiGiornata() {
        val c = LettoreCartellino.analizza(righe)!!
        assertEquals(5.0, c.flexMesePrecedente!!, 0.0001)
        // La riga «Flessibilità mese precedente» non deve diventare la flex del giorno 9.
        assertEquals(-0.45, c.giornate.first { it.giorno == 9 }.flex!!, 0.0001)
    }

    @Test fun senzaPeriodoNonAnalizza() {
        assertNull(LettoreCartellino.analizza(listOf("01 DO RS Ognissanti")))
    }

    @Test fun valoreConSegnoDaTrattinoFinaleOIniziale() {
        assertEquals(-1.5, LettoreCartellino.valore("1,50-"), 0.0001)
        assertEquals(-1.5, LettoreCartellino.valore("-1,50"), 0.0001)
        assertEquals(1.02, LettoreCartellino.valore("1,02"), 0.0001)
    }
}
