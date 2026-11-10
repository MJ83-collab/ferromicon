package it.ferromicon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CedolinoTest {

    private fun p(s: String, x: Double, y: Double, w: Double, h: Double = 8.0) = Pezzo(s, x, y, w, h)

    /** Una pagina di cedolino costruita a mano con la stessa disposizione del modulo SAP. */
    private fun paginaCedolino(): List<Pezzo> = listOf(
        // intestazione: "Stipendio di" + "novembre" + "2025" in pezzi vicini
        p("Stipendio", 30.0, 50.0, 45.0), p("di", 78.0, 50.0, 8.0), p("novembre", 89.0, 50.0, 45.0), p("2025", 137.0, 50.0, 25.0),
        p("Valuta : 30.11.2025", 300.0, 50.0, 90.0),
        p("Livello : B3", 30.0, 70.0, 60.0),
        p("Profilo PAM AREA: CONDUZIONE", 30.0, 80.0, 150.0),
        // presenze
        p("PRESENZE", 30.0, 90.0, 60.0), p("ferie", 300.0, 90.0, 30.0), p("malattia", 360.0, 90.0, 40.0),
        p("2,00", 300.0, 100.0, 30.0), p("1,00", 370.0, 100.0, 30.0),
        // componenti della retribuzione
        p("COMPONENTI DELLA RETRIBUZIONE", 30.0, 120.0, 160.0),
        p("MINIMO", 30.0, 130.0, 40.0), p("1.600,00", 150.0, 130.0, 50.0),
        p("SCATTI", 30.0, 140.0, 40.0), p("100,00", 150.0, 140.0, 50.0),
        p("TOTALE RETRIBUZIONE", 30.0, 150.0, 120.0), p("1.700,00", 300.0, 150.0, 50.0),
        // tabella delle voci
        p("VAL.FIGURAT", 200.0, 200.0, 60.0), p("PARAMETRI", 270.0, 200.0, 50.0), p("COMP.UNITARI", 330.0, 200.0, 60.0),
        p("TRATTENUTE", 400.0, 200.0, 60.0), p("COMPETENZE", 470.0, 200.0, 60.0),
        p("0001", 30.0, 210.0, 25.0), p("STIPENDIO BASE", 60.0, 210.0, 100.0), p("1.700,00", 480.0, 210.0, 50.0),
        p("0452", 30.0, 220.0, 25.0), p("STRAORDINARIO DIURNO", 60.0, 220.0, 110.0),
        p("10,00", 290.0, 220.0, 30.0), p("15,00", 350.0, 220.0, 40.0), p("150,00", 480.0, 220.0, 50.0),
        p("0900", 30.0, 230.0, 25.0), p("TRATT. SINDACALE", 60.0, 230.0, 100.0), p("12,00", 420.0, 230.0, 40.0),
        // blocchi in basso
        p("PREVIDEN", 30.0, 300.0, 50.0),
        p("1.900,00", 60.0, 310.0, 50.0), p("170,00", 440.0, 310.0, 40.0),
        p("Anno", 20.0, 320.0, 25.0),
        p("21.000,00", 60.0, 330.0, 55.0),
        p("IRPEF", 30.0, 340.0, 40.0),
        p("1.500,00", 60.0, 350.0, 50.0), p("280,00", 440.0, 350.0, 40.0),
        p("Anno", 20.0, 360.0, 25.0),
        p("16.500,00", 60.0, 370.0, 55.0),
        p("TOT.TRATTENUTE", 400.0, 400.0, 80.0), p("TOT.COMPETENZE", 500.0, 400.0, 80.0),
        p("462,00", 420.0, 410.0, 60.0), p("1.850,00", 520.0, 410.0, 60.0),
        p("NETTO A PAGARE", 400.0, 450.0, 100.0), p("1.388,00", 550.0, 460.0, 60.0)
    )

    @Test fun numeriItaliani() {
        assertEquals(1234.56, CedolinoParser.numero("1.234,56")!!, 0.0001)
        assertEquals(-12.5, CedolinoParser.numero("12,50-")!!, 0.0001)
        assertEquals(-3.0, CedolinoParser.numero("-3,00")!!, 0.0001)
        assertNull(CedolinoParser.numero("abc"))
        assertTrue(CedolinoParser.èNumero("1.234,56"))
        assertFalse(CedolinoParser.èNumero("2025"))
    }

    @Test fun leggeUnCedolino() {
        val c = CedolinoParser.parse(listOf(paginaCedolino()), "cedolino.pdf")
        assertEquals(2025, c.anno)
        assertEquals(11, c.mese)
        assertEquals("30.11.2025", c.valuta)
        assertEquals("B3", c.livello)
        assertEquals("PAM", c.profilo.substringBefore(" "))
        assertEquals(2.0, c.presenze["ferie"]!!, 0.0)
        assertEquals(1600.0, c.componenti["MINIMO"]!!, 0.0)
        assertEquals(1700.0, c.n("totaleRetribuzione")!!, 0.0)

        assertEquals(3, c.voci.size)
        val straordinario = c.voci.first { it.codice == "0452" }
        assertTrue(straordinario.flag.contains("STRAORDINARIO"))
        assertEquals(10.0, straordinario.param!!, 0.0)
        assertEquals(15.0, straordinario.compUnit!!, 0.0)
        assertEquals(150.0, straordinario.competenza!!, 0.0)
        assertEquals(12.0, c.voci.first { it.codice == "0900" }.trattenuta!!, 0.0)

        assertEquals(170.0, c.n("inps")!!, 0.0)
        assertEquals(280.0, c.n("irpef")!!, 0.0)
        assertEquals(1900.0, c.n("imponibile_inps")!!, 0.0)
        assertEquals(1900.0, c.n("imponibileMese_inps")!!, 0.0)
        assertEquals(21000.0, c.n("imponibileAnno_inps")!!, 0.0)
        assertEquals(16500.0, c.n("imponibileAnno_irpef")!!, 0.0)
        assertEquals(1850.0, c.n("totCompetenze")!!, 0.0)
        assertEquals(462.0, c.n("totTrattenute")!!, 0.0)
        assertEquals(1388.0, c.netto!!, 0.0)

        // somme delle voci = totali stampati, e competenze - trattenute = netto
        assertEquals(1850.0, c.sommaCompetenze, 0.001)
        assertEquals(462.0, c.sommaTrattenute, 0.001)
        assertTrue(c.controlloOk)
        assertTrue(c.ordinario)
    }

    @Test fun controlloSegnalaSommeCheNonTornano() {
        val pagina = paginaCedolino().map { if (it.str == "1.388,00") it.copy(str = "1.300,00") else it }
        val c = CedolinoParser.parse(listOf(pagina))
        assertEquals(1300.0, c.netto!!, 0.0)
        assertFalse(c.controlloOk)
    }

    @Test fun annoEMeseDalNomeDelFileSeMancanoNelTesto() {
        val senzaTitolo = paginaCedolino().filter { it.y != 50.0 || it.str.startsWith("Valuta") }
        val c = CedolinoParser.parse(listOf(senzaTitolo), "cedolino_2024_07.pdf")
        assertEquals(2024, c.anno)
        assertEquals(7, c.mese)
    }

    @Test fun paginaVuotaNonDaErrori() {
        val c = CedolinoParser.parse(listOf(emptyList()), "x.pdf")
        assertNull(c.anno)
        assertTrue(c.voci.isEmpty())
        assertFalse(c.controlloOk)
    }

    // ---------- CU ----------

    private fun paginaCu(): List<Pezzo> = listOf(
        p("CERTIFICAZIONE UNICA 2026", 30.0, 20.0, 150.0),
        p("RELATIVA ALL'ANNO 2025", 30.0, 30.0, 150.0),
        p("CERTIFICAZIONE LAVORO DIPENDENTE", 30.0, 40.0, 200.0),
        p("Ritenute Irpef", 30.0, 50.0, 80.0),
        p("1", 100.0, 200.0, 5.0, h = 5.0), p("32.500,00", 130.0, 208.0, 60.0, h = 7.0),
        p("21", 100.0, 240.0, 8.0, h = 5.0), p("4.100,00", 130.0, 248.0, 60.0, h = 7.0),
        p("26", 100.0, 280.0, 8.0, h = 5.0), p("100,00", 130.0, 288.0, 40.0, h = 7.0),
        p("27", 100.0, 320.0, 8.0, h = 5.0), p("160,00", 130.0, 328.0, 40.0, h = 7.0)
    )

    @Test fun leggeUnaCu() {
        val pagine = listOf(paginaCu())
        assertTrue(CuParser.èCu(pagine))
        val cu = CuParser.parse(pagine, "CU_2026.pdf")
        assertEquals(2025, cu.anno)
        assertEquals(2026, cu.annoCu)
        assertEquals(32500.0, cu.reddito!!, 0.001)
        assertEquals(4100.0, cu.numeri["ritenuteIrpef"]!!, 0.001)
        assertEquals(260.0, cu.addComunale!!, 0.001)
        assertTrue(cu.valida)
        assertFalse(CuParser.èCu(listOf(paginaCedolino())))
    }

    // ---------- dati per la stima del netto ----------

    private fun ced(anno: Int, mese: Int, vararg n: Pair<String, Double>, componenti: Map<String, Double> = emptyMap()) =
        Cedolino("", anno, mese, "", "", "", "", emptyMap(), componenti, emptyList(), n.toMap(),
            emptyMap(), emptyMap(), emptyMap(), 0.0, 0.0, true)

    @Test fun proponeIDatiFiscaliDallaCu() {
        val cedolini = listOf(
            ced(2025, 10, "inps" to 170.0, "imponibileMese_inps" to 1850.0),
            ced(2025, 11, "inps" to 170.0, "imponibileMese_inps" to 1850.0)
        )
        val cu = listOf(CuParser.parse(listOf(paginaCu()), "CU_2026.pdf"))
        val prop = DatiFiscali.daCedolini(cedolini, cu)!!
        assertEquals(2025, prop.anno)
        assertEquals(32500.0, prop.reddito!!, 0.001)
        assertEquals("CU 2025", prop.fonteReddito)
        assertEquals(9.19, prop.inpsPct!!, 0.01)
        assertEquals(0.8, prop.addComunalePct!!, 0.01)       // 260 / 32.500
    }

    @Test fun stimaIlRedditoDaiProgressiviSenzaCu() {
        val base = mapOf("MINIMO" to 2000.0)
        val cedolini = (1..6).map {
            ced(2026, it, "imponibileAnno_irpef" to 2000.0 * it, "imponibileMese_irpef" to 2000.0,
                "inps" to 183.8, "imponibileMese_inps" to 2000.0, componenti = base)
        }
        val prop = DatiFiscali.daCedolini(cedolini, emptyList())!!
        assertEquals(2026, prop.anno)
        // 12.000 già maturati + 6 mesi x 2.000 + tredicesima + quattordicesima (mese < 7)
        val baseC = 2000.0 * (1 - 0.0919)
        assertEquals(12000.0 + 6 * 2000.0 + baseC + baseC, prop.reddito!!, 1.0)
        assertTrue(prop.fonteReddito.startsWith("stima"))
        assertNotNull(prop.inpsPct)
    }

    @Test fun senzaDatiNiente() {
        assertNull(DatiFiscali.daCedolini(emptyList(), emptyList()))
    }
}
