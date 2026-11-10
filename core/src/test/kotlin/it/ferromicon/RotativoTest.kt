package it.ferromicon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Dati sintetici: nessun nome o matricola reale. Il rotativo ha sette colonne con un ciclo di 28 giorni. */
class RotativoTest {

    private val inizio: LocalDate = LocalDate.of(2026, 11, 14)

    /** Codici di una colonna (0..6) al giorno k dall'inizio: GRS, RIP oppure un numero di lavoro 1..5. */
    private fun codiceColonna(colonna: Int, k: Int): String {
        val g = (7 - colonna) % 7
        val o = Math.floorMod(k - g, 7)
        return when (o) {
            0 -> "GRS"
            1 -> "RIP"
            else -> (o - 1).toString()
        }
    }

    private fun rotativoSintetico(giorni: Int = 400): Rotativo {
        val mappa = LinkedHashMap<LocalDate, List<String>>()
        for (k in 0 until giorni) mappa[inizio.plusDays(k.toLong())] = List(7) { codiceColonna(it, k) }
        return Rotativo(mappa)
    }

    /** Riga di agente della settimana letta, con lo stesso andamento della colonna indicata. */
    private fun rigaSettimana(colonna: Int, nome: String, cid: String): RigaAgente {
        val codici = List(7) { k ->
            when (codiceColonna(colonna, k)) {
                "GRS" -> "GRS"
                "RIP" -> "R"
                else -> if (k == 2) "D" else "TURNO1"
            }
        }
        return RigaAgente(1, nome, cid, codici, 0)
    }

    private fun calendarioSintetico(vararg righe: RigaAgente) = CalendarioTurnoAgenti(
        turno = "PROVA", profilo = "Scorta", dal = inizio, al = inizio.plusDays(6),
        righe = righe.toList(), periodoCiclo = 0
    )

    @Test
    fun trovaLaPosizioneDallaSettimana() {
        val rot = rotativoSintetico()
        val cal = calendarioSintetico(rigaSettimana(3, "PROVA TRE", "1000003"), rigaSettimana(5, "PROVA CINQUE", "1000005"))
        assertEquals(3, RotativoRiposi.posizioneDi(cal.righe[0], cal, rot))
        assertEquals(5, RotativoRiposi.posizioneDi(cal.righe[1], cal, rot))
    }

    @Test
    fun leggeIlPdfDaiPezzi() {
        val pezzi = ArrayList<Pezzo>()
        for (k in 0 until 60) {
            val y = k * 10.0
            pezzi += Pezzo(inizio.plusDays(k.toLong()).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")), 0.0, y, 60.0)
            for (c in 0 until 7) pezzi += Pezzo(codiceColonna(c, k), 80.0 + c * 40.0, y, 30.0)
            pezzi += Pezzo("${k % 28 + 1}", 400.0, y, 10.0)
        }
        val letto = RotativoRiposi.leggi(listOf(pezzi))
        assertNotNull(letto)
        assertEquals(60, letto!!.giorni.size)
        assertEquals(inizio, letto.primo)
        assertEquals(codiceColonna(3, 10), letto.giorni[inizio.plusDays(10)]!![3])
    }

    @Test
    fun codiceDelGiornoPerPosizione() {
        val rot = rotativoSintetico()
        assertEquals("GRS", RotativoRiposi.codice(rot, 3, inizio.plusDays(4)))
        assertEquals("R", RotativoRiposi.codice(rot, 3, inizio.plusDays(5)))
        assertEquals("L", RotativoRiposi.codice(rot, 3, inizio.plusDays(6)))
        assertNull(RotativoRiposi.codice(rot, 3, inizio.minusDays(1)))
    }

    @Test
    fun generaRiposiOltreLaSettimana() {
        val rot = rotativoSintetico()
        val riga = rigaSettimana(3, "PROVA TRE", "1000003")
        val cal = calendarioSintetico(riga)
        val da = LocalDate.of(2026, 11, 21)
        val fino = LocalDate.of(2027, 1, 31)
        val gen = CalendarioAgente.genera(cal, riga, emptyList(), da, fino, emptyList(), 1L, rot)

        val attesi = generateSequence(da) { it.plusDays(1) }.takeWhile { !it.isAfter(fino) }
            .count { RotativoRiposi.codice(rot, 3, it) in setOf("GRS", "R") }
        assertEquals(attesi, gen.assenze.count { it.tipo.riposo })
        assertEquals(fino, gen.finoA)
    }

    @Test
    fun ferieSoloNeiGiorniDiLavoro() {
        val rot = rotativoSintetico()
        val riga = rigaSettimana(3, "PROVA TRE", "1000003")
        val cal = calendarioSintetico(riga)
        val estate = PeriodoFerie("Estivo test", LocalDate.of(2027, 6, 9), LocalDate.of(2027, 6, 15))
        val gen = CalendarioAgente.genera(cal, riga, emptyList(), LocalDate.of(2027, 6, 1), LocalDate.of(2027, 6, 30),
            listOf(estate), 1L, rot)

        val ferie = gen.assenze.filter { it.tipo == TipoAssenza.FERIE }
        assertTrue("attese ferie nel periodo", ferie.isNotEmpty())
        assertTrue("le ferie non cadono su riposi", ferie.all { RotativoRiposi.codice(rot, 3, it.data) == "L" })
        assertTrue("le ferie stanno nel periodo", ferie.all { !it.data.isBefore(estate.dal) && !it.data.isAfter(estate.al.plusDays(10)) })
    }

    @Test
    fun senzaRotativoIlComportamentoNonCambia() {
        val riga = rigaSettimana(3, "PROVA TRE", "1000003")
        val cal = calendarioSintetico(riga)
        val gen = CalendarioAgente.genera(cal, riga, emptyList(), LocalDate.of(2026, 11, 21), LocalDate.of(2026, 12, 5),
            emptyList(), 1L)
        assertEquals(0, gen.assenze.count { it.tipo.riposo && it.data.isAfter(LocalDate.of(2026, 11, 20)) })
    }
}
