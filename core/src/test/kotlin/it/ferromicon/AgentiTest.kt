package it.ferromicon

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class AgentiTest {

    private fun riga(y: Double, testo: String) = listOf(Pezzo(testo, 10.0, y, 100.0))

    /** Pagina sintetica come il calendario degli agenti: un turno di 7 posizioni (ciclo di 7 giorni). */
    private fun paginaCalendario(): List<Pezzo> {
        val ciclo = listOf("GRS", "R", "TEOR1", "TEOR2", "TEOR3", "TEOR4", "TEOR5")
        val p = ArrayList<Pezzo>()
        p += riga(10.0, "TURNO: [TESTA] PROFILO: Scorta IN VIGORE DAL: 14/11/2026 AL: 20/11/2026")
        p += riga(20.0, "14 15 16 17 18 19 20")
        for (n in 1..7) {
            val codici = (0 until 7).map { ciclo[(n + it) % 7] }.toMutableList()
            val nome = if (n == 4) "N. A." else "ROSSI MARIO $n. 31000$n" + "0".repeat(0)
            val t = if (n == 4) "$n N. A." else "$n ROSSI M. 310000$n ${codici.joinToString(" ")}"
            p += riga(30.0 + n * 10, t)
        }
        return p
    }

    @Test fun leggeCalendarioECiclo() {
        val c = CalendarioAgenti.leggi(listOf(paginaCalendario())).single()
        assertEquals("TESTA", c.turno)
        assertEquals(LocalDate.of(2026, 11, 14), c.dal)
        assertEquals(6, c.righe.size)
        assertEquals(7, c.periodoCiclo)
        assertTrue(c.estendibile)
        val r = c.righe.first { it.posizione == 1 }
        // settimana letta e poi estensione col ciclo, senza salti
        assertEquals(r.codici[6], c.codice(r, LocalDate.of(2026, 11, 20)))
        assertEquals(r.codici[0], c.codice(r, LocalDate.of(2026, 11, 21)))
        assertEquals(r.codici[1], c.codice(r, LocalDate.of(2026, 11, 22)))
    }

    @Test fun cercaPerCidENome() {
        val c = CalendarioAgenti.leggi(listOf(paginaCalendario()))
        assertEquals(1, CalendarioAgenti.cerca(c, "3100002").size)
        assertEquals(6, CalendarioAgenti.cerca(c, "rossi").size)
        assertEquals(6, CalendarioAgenti.cerca(c, "Rossi M").size)
        assertTrue(CalendarioAgenti.cerca(c, "bianchi").isEmpty())
    }

    @Test fun ferieDaRiposoARiposo() {
        // ciclo: GRS R + 5 giorni di lavoro, da lunedì 3/8/2026: sab-dom riposo
        val codici = LinkedHashMap<LocalDate, String>()
        var d = LocalDate.of(2026, 8, 1)
        val c = listOf("GRS", "R", "X1", "X2", "X3", "X4", "X5")
        for (i in 0 until 28) { codici[d] = c[i % 7]; d = d.plusDays(1) }
        val periodo = PeriodoFerie("Estivo D", LocalDate.of(2026, 8, 7), LocalDate.of(2026, 8, 13))
        val ferie = PeriodiFerie.giorniDiFerie(codici, periodo)
        // primo riposo nel periodo: 8/8 (GRS), riposo successivo: 15-16/8; ferie = 5 giorni di lavoro in mezzo
        assertEquals(setOf(10, 11, 12, 13, 14).map { LocalDate.of(2026, 8, it) }.toSet(), ferie)
    }

    @Test fun periodiEstiviPredefiniti() {
        val p = PeriodiFerie.estivi(2026)
        assertEquals(6, p.size)
        assertEquals(LocalDate.of(2026, 8, 7), p.first { it.nome.endsWith("D") }.dal)
    }

    private fun pezzoTurni(testo: String, x: Double, y: Double) = Pezzo(testo, x, y, 20.0)

    @Test fun leggeGiornataDiTurno() {
        val p = listOf(
            pezzoTurni("IMPIANTO: VARESE_UN", 120.0, 12.0), pezzoTurni("TURNO: [VAOR_S] [68550]", 310.0, 12.0),
            pezzoTurni("PROFILO: Scorta", 500.0, 12.0),
            pezzoTurni("LMXGV", 15.0, 38.0),
            pezzoTurni("1", 10.0, 98.0), pezzoTurni("[12:37]", 27.0, 100.7), pezzoTurni("[21:03]", 58.0, 100.7),
            pezzoTurni("24557", 446.0, 68.0), pezzoTurni("TREV", 446.5, 50.0),
            pezzoTurni("REFEZ", 484.0, 65.0), pezzoTurni("(25566", 450.0, 70.0),
            pezzoTurni("24566", 522.0, 68.0), pezzoTurni("VA", 523.0, 57.0),
            pezzoTurni("Lav", 727.0, 90.0), pezzoTurni("08:26", 727.0, 104.0), pezzoTurni("05:49", 749.0, 104.0),
            pezzoTurni("229", 771.0, 104.0), pezzoTurni("no", 793.0, 104.0), pezzoTurni("11:42", 815.0, 104.0)
        )
        val g = TurniPdf.leggi(listOf(p)).single()
        assertEquals("VAOR6".length, ("VAOR" + "6").length)
        assertEquals("VAOR1", g.codice)
        assertEquals(TipoGiorno.FERIALE, g.tipo)
        assertEquals(LocalTime.of(12, 37), g.inizio)
        assertEquals(LocalTime.of(21, 3), g.fine)
        assertEquals(349, g.condottaMin)
        assertEquals(229, g.km)
        assertEquals(11 * 60 + 42, g.riposoMin)
        assertTrue(g.refezione)
        assertEquals(listOf("24557", "25566", "24566"), g.treni.map { it.numero })
        assertTrue(g.treni[1].vettura)
        assertEquals("TREV", g.treni[0].destinazione)
        assertEquals("VAOR_S", TurniPdf.turnoDellaPagina(p))
    }

    @Test fun generaCalendarioAgente() {
        val cal = CalendarioAgenti.leggi(listOf(paginaCalendario())).single()
        val agente = cal.righe.first { it.posizione == 1 }
        val def = GiornataDefinita("TEOR_S", "Scorta", "1", TipoGiorno.FERIALE, LocalTime.of(22, 0), LocalTime.of(5, 0),
            420, 240, 200, true, 800, false, emptyList())
        val defs = listOf("1", "2", "3", "4", "5").map { def.copy(numero = it) } +
            listOf("1", "2", "3", "4", "5").map { def.copy(numero = it, tipo = TipoGiorno.SABATO) } +
            listOf("1", "2", "3", "4", "5").map { def.copy(numero = it, tipo = TipoGiorno.FESTIVO) }
        val g = CalendarioAgente.genera(cal, agente, defs, LocalDate.of(2026, 11, 14), LocalDate.of(2026, 12, 11),
            emptyList(), 1000)
        assertEquals(28, g.servizi.size + g.assenze.size)
        assertTrue(g.estesoOltreLaSettimana)
        assertTrue(g.senzaDettaglio.isEmpty())
        assertEquals(8, g.assenze.count { it.tipo.riposo })
        val s = g.servizi.first()
        assertTrue(s.turno.fine.isAfter(s.turno.inizio) && s.turno.fine.toLocalDate() != s.turno.inizio.toLocalDate())
        assertEquals(240, s.turno.minutiCondotta)
        assertEquals(Profilo.CAPOTRENO, s.turno.profilo)
        // con un periodo di ferie le giornate di lavoro del periodo diventano ferie
        val conFerie = CalendarioAgente.genera(cal, agente, defs, LocalDate.of(2026, 11, 14), LocalDate.of(2026, 12, 11),
            listOf(PeriodoFerie("Invernale", LocalDate.of(2026, 11, 28), LocalDate.of(2026, 12, 4))), 1000)
        assertTrue(conFerie.giorniFerie > 0)
        assertEquals(g.servizi.size - conFerie.giorniFerie, conFerie.servizi.size)
    }

    @Test fun senzaDettaglioSegnalaLeGiornateMancanti() {
        val cal = CalendarioAgenti.leggi(listOf(paginaCalendario())).single()
        val g = CalendarioAgente.genera(cal, cal.righe.first(), emptyList(), LocalDate.of(2026, 11, 14),
            LocalDate.of(2026, 11, 20), emptyList(), 1)
        assertTrue(g.senzaDettaglio.isNotEmpty())
        assertTrue(g.servizi.isEmpty())
    }
}
