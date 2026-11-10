package it.ferromicon

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class DiarioTest {

    // Costruisce le righe del diario alle stesse posizioni del modulo reale.
    private fun cella(s: String, destra: Double, y: Double) = Pezzo(s, destra - s.length * 3.3, y, s.length * 3.3, 6.0)

    private fun riga(y: Double, data: String, turno: String, tipo: String, treno: String = "",
                     pp: String = "", pa: String = "", rp: String = "", ra: String = "",
                     da: String = "", a: String = "", tot: List<String> = emptyList()): List<Pezzo> {
        val l = mutableListOf(Pezzo(data, 13.0, y, 30.0), Pezzo(turno, 50.0, y, 18.0), Pezzo(tipo, 72.0, y, 40.0))
        if (treno.isNotEmpty()) l += cella(treno, 141.8, y)
        if (pp.isNotEmpty()) l += cella(pp, 167.5, y)
        if (pa.isNotEmpty()) l += cella(pa, 195.5, y)
        if (rp.isNotEmpty()) l += cella(rp, 223.5, y)
        if (ra.isNotEmpty()) l += cella(ra, 251.5, y)
        if (da.isNotEmpty()) l += Pezzo(da, 315.0, y, 30.0)
        if (a.isNotEmpty()) l += Pezzo(a, 377.0, y, 30.0)
        val destre = listOf(453.5, 474.5, 495.5, 516.5, 537.7)
        tot.forEachIndexed { i, v -> if (v.isNotEmpty()) l += cella(v, destre[i], y) }
        return l
    }

    private fun pagina(): List<List<Pezzo>> {
        val p = mutableListOf<Pezzo>()
        p += Pezzo("Matricola:", 10.0, 36.9, 30.0); p += Pezzo("1234567", 45.0, 36.9, 27.0)
        p += Pezzo("Agente:", 78.0, 36.9, 26.0); p += Pezzo("Rossi", 106.0, 36.9, 20.0); p += Pezzo("Mario", 135.0, 36.9, 20.0)
        p += Pezzo("Lavoro", 489.8, 60.5, 5.5); p += Pezzo("notturno", 489.8, 36.9, 5.5)
        p += Pezzo("Settore:", 10.0, 46.9, 24.0); p += Pezzo("Scorta", 36.5, 46.9, 20.0)
        p += Pezzo("Residenza:", 62.5, 46.9, 35.0); p += Pezzo("VARESE_UN", 99.5, 46.9, 42.0)
        p += Pezzo("Periodo:", 10.0, 56.9, 26.0); p += Pezzo("gennaio", 38.0, 56.9, 25.0); p += Pezzo("2026", 64.9, 56.9, 15.0)
        p += Pezzo("Orario Prescritto", 152.0, 74.0, 40.0); p += Pezzo("Orario Reale", 213.0, 74.0, 30.0)
        p += Pezzo("Presenza/Attivita'", 72.9, 87.2, 46.0)
        var y = 100.0
        fun r(vararg l: List<Pezzo>) { l.forEach { p += it }; y += 9.5 }
        r(riga(y, "05/01/2026", "RS", "Riposo Settimana"))
        // servizio a cavallo della mezzanotte: la coda ha la data del giorno dopo
        r(riga(y, "07/01/2026", "VA031", "Inizio", pp = "22:00", pa = "22:05", rp = "22:00", ra = "22:05",
            da = "VARESE", a = "VARESE", tot = listOf("120", "400", "100", "", "380")))
        r(riga(y, "07/01/2026", "VA031", "REFEZ", pp = "22:05", pa = "22:35", rp = "22:05", ra = "22:35"))
        r(riga(y, "07/01/2026", "VA031", "Inlinea", treno = "24581", pp = "23:40", pa = "00:18", rp = "23:41", ra = "00:20"))
        r(riga(y, "08/01/2026", "VA031", "Fine", pp = "00:18", pa = "00:40", rp = "00:20", ra = "00:42"))
        r(riga(y, "09/01/2026", "A997", "Malattia"))
        r(riga(y, "10/01/2026", "VA005", "Inizio", pp = "08:00", pa = "08:05", rp = "08:00", ra = "08:05",
            tot = listOf("60", "200", "", "", "180")))
        r(riga(y, "10/01/2026", "VA005", "Fine", pp = "11:15", pa = "11:20", rp = "11:15", ra = "11:30"))
        p += Pezzo("Totale diario", 399.0, y, 40.0)
        p += cella("180", 453.5, y); p += cella("600", 474.5, y); p += cella("100", 495.5, y); p += cella("560", 537.7, y)
        return listOf(p)
    }

    @Test fun leggeIlDiario() {
        assertTrue(DiarioBordo.èDiario(pagina()))
        val d = DiarioBordo.leggi(pagina())!!
        assertEquals("1234567", d.matricola)
        assertEquals("Rossi Mario", d.agente)
        assertEquals("VARESE_UN", d.residenza)
        assertEquals(java.time.YearMonth.of(2026, 1), d.mese)
        assertEquals(Profilo.CAPOTRENO, d.profilo)
        assertEquals(4, d.giorni.size)       // la coda dell'8 gennaio è parte del servizio del 7
        val notte = d.giorni.first { it.turno == "VA031" }
        assertEquals(LocalDate.of(2026, 1, 7), notte.data)
        assertEquals(22 * 60, notte.inizioPrescritto)
        assertEquals(24 * 60 + 40, notte.finePrescritta)
        assertEquals(24 * 60 + 42, notte.fineReale)
        assertEquals(120, notte.condotta)
        assertEquals(1, notte.refezioni)
        assertEquals(2, notte.ritardoFinale)
        assertEquals(TipoAssenza.RIPOSO_SETTIMANALE, d.giorni[0].tipoAssenza)
        assertEquals(TipoAssenza.MALATTIA, d.giorni.first { it.turno == "A997" }.tipoAssenza)
        val r = d.riepilogo()
        assertEquals(2, r.giorniServizio); assertEquals(1, r.riposi); assertEquals(1, r.malattia)
        assertEquals(180, r.condotta); assertEquals(600, r.lavoro)
        assertTrue(d.totaliTornano)
    }

    @Test fun confrontoConAgenda() {
        val d = DiarioBordo.leggi(pagina())!!
        fun dt(g: Int, h: Int, m: Int) = LocalDateTime.of(2026, 1, g, h, m)
        val giusto = Servizio(1, Turno(dt(7, 22, 0), dt(8, 0, 40).plusMinutes(60 * 0), Profilo.CAPOTRENO, 120), origineTurno = "VA031")
        val sbagliato = Servizio(2, Turno(dt(10, 8, 40), dt(10, 11, 20), Profilo.CAPOTRENO, 180))
        val dati = Dati(listOf(giusto, sbagliato), listOf(Assenza(9, LocalDate.of(2026, 1, 5), TipoAssenza.RIPOSO_SETTIMANALE),
            Assenza(10, LocalDate.of(2026, 1, 9), TipoAssenza.FERIE)))
        val dif = ControlloDiario.confrontaConAgenda(d, dati)
        assertTrue(dif.any { it.titolo.startsWith("10/01") && it.titolo.contains("inizio") })
        assertTrue(dif.any { it.titolo.startsWith("09/01") && it.titolo.contains("assenza") })
        assertTrue(dif.any { it.titolo.startsWith("10/01") && it.titolo.contains("condotta") || it.titolo.contains("scorta") })
        assertFalse(dif.any { it.titolo.startsWith("05/01") })
        assertFalse(dif.any { it.titolo.startsWith("07/01") })
    }

    @Test fun giornateMancantiEdAgendaVuota() {
        val d = DiarioBordo.leggi(pagina())!!
        val (servizi, assenze) = ControlloDiario.giornateMancanti(d, Dati(emptyList(), emptyList()), Profilo.CAPOTRENO, 500)
        assertEquals(2, servizi.size)
        assertEquals(2, assenze.size)
        assertEquals(LocalDateTime.of(2026, 1, 7, 22, 0), servizi[0].turno.inizio)
        assertEquals(LocalDateTime.of(2026, 1, 8, 0, 42), servizi[0].turno.fine)
        assertEquals(120, servizi[0].turno.minutiCondotta)
    }

    private fun cedolino(anno: Int, mese: Int, presenze: Map<String, Double>, voci: List<VoceCedolino> = emptyList()) =
        Cedolino("", anno, mese, "", "", "", "", presenze, emptyMap(), voci, emptyMap(), emptyMap(), emptyMap(), emptyMap(),
            0.0, 0.0, true)

    @Test fun confrontoConCedolino() {
        val d = DiarioBordo.leggi(pagina())!!
        val ticket = VoceCedolino("5560", "", "TICKET NON SOGGETTO", 100.0, 3.0, null, null, null)
        val dif = ControlloDiario.confrontaConCedolino(d,
            cedolino(2026, 1, mapOf("presenze" to 3.0, "riposi" to 1.0, "malattie" to 1.0), listOf(ticket)))
        assertTrue(dif.any { it.titolo.startsWith("Giorni di presenza") })
        assertTrue(dif.any { it.titolo == "Ticket pasto" })
        assertFalse(dif.any { it.titolo.startsWith("Giorni di riposo") })
        assertFalse(dif.any { it.titolo.startsWith("Giorni di malattia") })
        val giusto = ControlloDiario.confrontaConCedolino(d, cedolino(2026, 1, mapOf("presenze" to 2.0, "riposi" to 1.0)))
        assertTrue(giusto.isEmpty())
        val altroMese = ControlloDiario.confrontaConCedolino(d, cedolino(2026, 9, mapOf("presenze" to 17.0)))
        assertEquals("Mesi diversi", altroMese.single().titolo)
    }

    @Test fun profiloDalCedolino() {
        val testi = listOf(
            "Data Nascita : 01.01.1980 Pos.INPS : 0000000000",
            "Data Assunz. : 17.11.2003 Pos.INAIL : 0000000000 CID: 01234567 C.F.XXXXXXXXXXXXXXXX",
            "Livello : B1 Tecnici Specializzati",
            "Profilo : CAPO TRENO/CAPO SERVIZI TRENO AREA: L890 ST. di VARESE",
            "Numero A.P.A : 7 Data pross A.P.A: CDC : 9TL64003COSTI DIRETTI SCORTA"
        )
        val a = AnagraficaCedolino.leggi(testi, mapOf("res.ferie ap" to 7.0, "res.ferie ac" to 15.0),
            mapOf("ASS. AD PERSONAM PENS." to 91.74))
        assertEquals("1234567", a.cid)
        assertEquals(LocalDate.of(2003, 11, 17), a.dataAssunzione)
        assertEquals(Livello.B1, a.livello)
        assertEquals(Profilo.CAPOTRENO, a.profilo)
        assertEquals("VARESE", a.residenza)
        assertEquals(7, a.apa)
        val imp = a.applica(Impostazioni())
        assertEquals(Profilo.CAPOTRENO, imp.profilo)
        assertEquals(Livello.B1, imp.livello)
        assertEquals(91.74, imp.adPersonam, 0.001)
        assertEquals(7, imp.ferieResidueAnnoPrecedente)
        assertEquals(Profilo.MACCHINISTA, AnagraficaCedolino.leggi(listOf("Profilo : MACCHINISTA AREA: L890 ST. di VARESE"),
            emptyMap(), emptyMap()).profilo)
    }
}
