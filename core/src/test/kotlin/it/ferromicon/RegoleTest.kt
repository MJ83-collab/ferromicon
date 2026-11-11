package it.ferromicon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

/** Test delle regole con casi di turno concreti. */
class RegoleTest {

    private fun dt(s: String) = LocalDateTime.parse(s)
    private fun pdm(da: String, a: String, condotta: Int = 0, zeta: Boolean = false) =
        Turno(dt(da), dt(a), Profilo.MACCHINISTA, minutiCondotta = condotta, turnoAZeta = zeta)
    private fun pdb(da: String, a: String, scorta: Int = 0) =
        Turno(dt(da), dt(a), Profilo.CAPOTRENO, minutiCondotta = scorta)
    private var prossimoId = 1L
    private fun srv(t: Turno, rfr: Boolean = false) = Servizio(prossimoId++, t, rfrDopo = rfr)
    /** Esito peggiore, ignorando le righe informative (come fa l'app). */
    private fun peggiore(e: List<Esito>) = e.maxOf { if (it.gravita == Gravita.INFO) Gravita.OK else it.gravita }

    // ---------- servizio singolo ----------

    @Test fun pdmCheFinisceTraMezzanotteEUnaEDiurno() {
        assertFalse(RegoleAltarail.èNotturno(pdm("2026-11-09T16:00", "2026-11-10T00:30")))
        assertFalse(RegoleAltarail.èNotturno(pdm("2026-11-09T16:00", "2026-11-10T01:00")))
        assertTrue(RegoleAltarail.èNotturno(pdm("2026-11-09T16:00", "2026-11-10T01:01")))
        assertTrue(RegoleAltarail.èNotturno(pdb("2026-11-09T16:00", "2026-11-10T00:30")))
        assertTrue(RegoleAltarail.èNotturno(pdm("2026-11-09T22:00", "2026-11-10T04:00")))
        // per il conteggio dei notturni vale 00:00-05:00 anche per il PdM
        assertTrue(RegoleAltarail.contaComeNotturno(pdm("2026-11-09T16:00", "2026-11-10T00:30")))
    }

    @Test fun refezione() {
        val pranzo = RegoleAltarail.verificaTurno(pdm("2026-11-09T10:00", "2026-11-09T17:00"))
        assertTrue(pranzo.any { it.messaggio.contains("refezione") && it.messaggio.contains("11-15") })
        val mattina = RegoleAltarail.verificaTurno(pdm("2026-11-09T05:00", "2026-11-09T12:00"))
        assertTrue(mattina.any { it.messaggio.contains("pausa di 30'") })
    }

    @Test fun durataMacchinista() {
        assertEquals(Gravita.OK, peggiore(RegoleAltarail.verificaTurno(pdm("2026-11-09T06:00", "2026-11-09T14:30"))))
        assertEquals(Gravita.ATTENZIONE, peggiore(RegoleAltarail.verificaTurno(pdm("2026-11-09T06:00", "2026-11-09T15:30"))))
        assertEquals(Gravita.VIOLAZIONE, peggiore(RegoleAltarail.verificaTurno(pdm("2026-11-09T06:00", "2026-11-09T16:30"))))
        // notturno oltre 7 h
        assertEquals(Gravita.VIOLAZIONE, peggiore(RegoleAltarail.verificaTurno(pdm("2026-11-09T21:00", "2026-11-10T04:30"))))
    }

    @Test fun durataCapotreno() {
        assertTrue(peggiore(RegoleAltarail.verificaTurno(pdb("2026-11-09T06:00", "2026-11-09T16:00"))) < Gravita.ATTENZIONE)
        assertEquals(Gravita.VIOLAZIONE, peggiore(RegoleAltarail.verificaTurno(pdb("2026-11-09T06:00", "2026-11-09T16:30"))))
    }

    @Test fun condotta() {
        assertEquals(Gravita.OK, peggiore(RegoleAltarail.verificaTurno(pdm("2026-11-09T06:00", "2026-11-09T14:00", condotta = 330))))
        assertEquals(Gravita.VIOLAZIONE, peggiore(RegoleAltarail.verificaTurno(pdm("2026-11-09T06:00", "2026-11-09T14:00", condotta = 331))))
        val consecutiva = Turno(dt("2026-11-09T06:00"), dt("2026-11-09T14:00"), Profilo.MACCHINISTA, 300, 301)
        assertEquals(Gravita.VIOLAZIONE, peggiore(RegoleAltarail.verificaTurno(consecutiva)))
    }

    // ---------- riposi ----------

    @Test fun riposoDopoDiurno11Ore() {
        val a = pdm("2026-11-09T06:00", "2026-11-09T14:00")
        assertEquals(Gravita.OK, peggiore(RegoleAltarail.verificaRiposo(a, pdm("2026-11-10T01:00", "2026-11-10T06:00"))))
        assertEquals(Gravita.VIOLAZIONE, peggiore(RegoleAltarail.verificaRiposo(a, pdm("2026-11-10T00:59", "2026-11-10T06:00"))))
    }

    @Test fun riposoDopoFineTraMezzanotteEUna14Ore() {
        val a = pdm("2026-11-09T16:00", "2026-11-10T00:30")
        assertEquals(Gravita.VIOLAZIONE, peggiore(RegoleAltarail.verificaRiposo(a, pdm("2026-11-10T14:00", "2026-11-10T20:00"))))
        assertTrue(peggiore(RegoleAltarail.verificaRiposo(a, pdm("2026-11-10T14:30", "2026-11-10T20:00"))) < Gravita.ATTENZIONE)
    }

    @Test fun riposoDopoNotturno16OppureZeta14() {
        val notte = pdm("2026-11-09T22:00", "2026-11-10T04:00")
        assertEquals(Gravita.VIOLAZIONE, peggiore(RegoleAltarail.verificaRiposo(notte, pdm("2026-11-10T19:00", "2026-11-11T01:00"))))
        assertTrue(peggiore(RegoleAltarail.verificaRiposo(notte, pdm("2026-11-10T20:00", "2026-11-11T01:00"))) < Gravita.ATTENZIONE)
        val zeta = pdm("2026-11-09T22:00", "2026-11-10T04:00", zeta = true)
        assertTrue(peggiore(RegoleAltarail.verificaRiposo(zeta, pdm("2026-11-10T18:00", "2026-11-10T23:00"))) < Gravita.ATTENZIONE)
    }

    @Test fun riposoFuoriResidenza() {
        val andata = pdm("2026-11-09T17:00", "2026-11-09T23:00")
        assertTrue(peggiore(RegoleAltarail.verificaRfr(andata, pdm("2026-11-10T06:00", "2026-11-10T09:00"))) < Gravita.ATTENZIONE)
        // 6 h ma solo 3 nella fascia 0-5
        val tardi = pdm("2026-11-09T17:00", "2026-11-10T02:00")
        assertEquals(Gravita.VIOLAZIONE, peggiore(RegoleAltarail.verificaRfr(tardi, pdm("2026-11-10T08:00", "2026-11-10T10:00"))))
    }

    // ---------- più servizi ----------

    @Test fun riposoSettimanale62OreConDueGiornate() {
        val ok = listOf(srv(pdm("2026-11-06T06:00", "2026-11-06T14:00")), srv(pdm("2026-11-09T06:00", "2026-11-09T14:00")))
        assertTrue(Analisi.verificaServizio(ok, 1).none { it.gravita == Gravita.VIOLAZIONE })
        val corto = listOf(srv(pdm("2026-11-06T14:00", "2026-11-06T22:00")), srv(pdm("2026-11-09T05:00", "2026-11-09T12:00")))
        assertTrue(Analisi.verificaServizio(corto, 1).any { it.gravita == Gravita.VIOLAZIONE && it.messaggio.contains("62") })
    }

    @Test fun ferieTraDueServiziNonGeneranoErroriDiRiposo() {
        val s = listOf(srv(pdm("2026-11-06T14:00", "2026-11-06T22:00")), srv(pdm("2026-11-09T05:00", "2026-11-09T12:00")))
        val ferie = listOf(Assenza(1, LocalDate.parse("2026-11-07"), TipoAssenza.FERIE))
        val esiti = Analisi.verificaServizio(s, 1, ferie)
        assertTrue(esiti.none { it.gravita == Gravita.VIOLAZIONE })
    }

    @Test fun dueProlungamentiTraDueRiposiSonoViolazione() {
        val periodo = listOf(
            srv(pdm("2026-11-09T06:00", "2026-11-09T15:30")),
            srv(pdm("2026-11-10T06:00", "2026-11-10T15:30"))
        )
        assertTrue(Analisi.verificaPeriodo(periodo).any { it.messaggio.contains("10 h") })
    }

    @Test fun limiteMensileNotturni() {
        val notti = (1..12).map { g ->
            val d = LocalDate.of(2026, 11, 1).plusDays((g * 2).toLong())
            srv(pdm("${d}T22:00", "${d.plusDays(1)}T04:00"))
        }
        val r = Analisi.riepilogoMese(notti, YearMonth.of(2026, 11))
        assertTrue(r.esiti.any { it.messaggio.contains("massimo 11") })
    }

    // ---------- festivi ----------

    @Test fun festivi() {
        assertEquals(LocalDate.of(2026, 4, 5), Festivi.pasqua(2026))
        assertEquals(LocalDate.of(2027, 3, 28), Festivi.pasqua(2027))
        assertTrue(Festivi.èFestivo(LocalDate.of(2026, 12, 7)))
        assertFalse(Festivi.èFestivo(LocalDate.of(2026, 6, 29)))
        assertTrue(Festivi.èFestivo(LocalDate.of(2026, 10, 4)))
        assertFalse(Festivi.èFestivo(LocalDate.of(2025, 10, 4)))
        assertFalse(Festivi.èFestivoContrattuale(LocalDate.of(2026, 4, 5)))
        assertTrue(Festivi.èFestivoContrattuale(LocalDate.of(2026, 4, 6)))
    }

    // ---------- ferie ----------

    @Test fun ferieSpettanti() {
        assertEquals(25, Ferie.ferieAnnue(Impostazioni(dataAssunzione = LocalDate.of(2015, 3, 1)), 2026))
        assertEquals(29, Ferie.ferieAnnue(Impostazioni(dataAssunzione = LocalDate.of(2015, 3, 1), orarioSuSeiGiorni = true), 2026))
        assertEquals(20, Ferie.ferieAnnue(Impostazioni(dataAssunzione = LocalDate.of(2020, 3, 10)), 2026))
        // assunto il 20 marzo: marzo ha 12 giorni (non conta), aprile-dicembre = 9 mesi -> 15
        assertEquals(9, Ferie.mesiNellAnno(LocalDate.of(2026, 3, 20), 2026))
        assertEquals(15, Ferie.ferieAnnue(Impostazioni(dataAssunzione = LocalDate.of(2026, 3, 20)), 2026))
        assertEquals(3, Ferie.permessiExFestivita(Impostazioni(dataAssunzione = LocalDate.of(2026, 3, 20)), 2026))
    }

    @Test fun saldoFerieERecuperiFestivi() {
        val servizi = listOf(
            srv(pdm("2026-12-08T06:00", "2026-12-08T14:00")),  // Immacolata, > 3 h
            srv(pdm("2026-12-25T06:00", "2026-12-25T08:00"))   // Natale, <= 3 h
        )
        val assenze = listOf(
            Assenza(1, LocalDate.parse("2026-08-10"), TipoAssenza.FERIE),
            Assenza(2, LocalDate.parse("2026-08-11"), TipoAssenza.FERIE),
            Assenza(3, LocalDate.parse("2026-12-20"), TipoAssenza.RECUPERO_FESTIVO)
        )
        val dati = Dati(servizi, assenze, Impostazioni(dataAssunzione = LocalDate.of(2010, 1, 1), ferieResidueAnnoPrecedente = 3))
        val saldo = Ferie.saldo(dati, 2026)
        assertEquals(26, saldo.ferieResidue)            // 25 + 3 - 2
        assertEquals(1.5, saldo.recuperiFestiviMaturati, 0.001)
        assertEquals(0.5, saldo.recuperiFestiviResidui, 0.001)
    }

    // ---------- competenze ----------

    @Test fun incentivoCondotta() {
        // 12 €/h fino a 3 h, 18 €/h oltre
        assertEquals(24.0, Competenze.incentivoCondotta(120), 0.001)
        assertEquals(36.0, Competenze.incentivoCondotta(180), 0.001)
        assertEquals(81.0, Competenze.incentivoCondotta(330), 0.001)  // 36 + 2,5 h × 18
        assertEquals(1.0, Competenze.incentivoCondotta(5), 0.001)
    }

    @Test fun incentivoScorta() {
        assertEquals(8.0, Competenze.incentivoScorta(120), 0.001)
        assertEquals(12.0, Competenze.incentivoScorta(180), 0.001)
        assertEquals(16.5, Competenze.incentivoScorta(240), 0.001)
        assertEquals(21.0, Competenze.incentivoScorta(300), 0.001)
    }

    @Test fun indennitaPerData() {
        assertEquals(2.40, Competenze.tariffaNotturna(LocalDate.of(2025, 7, 31)), 0.001)
        assertEquals(2.50, Competenze.tariffaNotturna(LocalDate.of(2025, 8, 1)), 0.001)
        assertEquals(2.45, Competenze.indennitaTurno(LocalDate.of(2026, 11, 1)), 0.001)
        assertEquals(20.0, Competenze.indennitaRiserva(Profilo.MACCHINISTA), 0.001)
    }

    @Test fun oreNotturne() {
        assertEquals(8 * 60L, Competenze.minutiNotturni(dt("2026-11-09T22:00"), dt("2026-11-10T06:00")))
        assertEquals(120L, Competenze.minutiNotturni(dt("2026-11-09T04:00"), dt("2026-11-09T12:00")))
        assertEquals(30L, Competenze.minutiNotturni(dt("2026-11-09T14:00"), dt("2026-11-09T22:30")))
        assertEquals(2L, Competenze.oreNotturneArrotondate(150))   // 2 h 30' -> 2
        assertEquals(3L, Competenze.oreNotturneArrotondate(151))   // 2 h 31' -> 3
    }

    @Test fun scattiAnzianita() {
        // assunto il 10/5/2015: bienni compiuti a maggio 2017...2025 = 5, decorrono da giugno
        assertEquals(5, Competenze.numeroScatti(LocalDate.of(2015, 5, 10), LocalDate.of(2026, 11, 1)))
        assertEquals(4, Competenze.numeroScatti(LocalDate.of(2015, 5, 10), LocalDate.of(2025, 5, 31)))
        assertEquals(5, Competenze.numeroScatti(LocalDate.of(2015, 5, 10), LocalDate.of(2025, 6, 1)))
        assertEquals(7, Competenze.numeroScatti(LocalDate.of(1990, 1, 1), LocalDate.of(2026, 1, 1)))
    }

    @Test fun stimaMeseDomenicaliEFestivi() {
        val dati = Dati(
            listOf(
                srv(pdm("2026-11-01T06:00", "2026-11-01T14:00", condotta = 180)),  // domenica e Ognissanti
                srv(pdm("2026-11-08T06:00", "2026-11-08T07:30", condotta = 60))    // domenica, <= 2 h
            ),
            emptyList(),
            Impostazioni(dataAssunzione = LocalDate.of(2015, 5, 10), livello = Livello.B3)
        )
        val stima = Competenze.stimaMese(dati, YearMonth.of(2026, 11))
        val voce = { nome: String -> stima.voci.first { it.etichetta == nome }.importo }
        assertEquals(23.50 + 11.75, voce("Indennità domenicale"), 0.001)
        assertEquals(36.0 + 12.0, voce("Incentivo condotta"), 0.001)
        assertEquals(2 * 2.45, voce("Indennità di turno"), 0.001)
        // paga oraria novembre 2026, B3: (2156,31 + 5×31,30 + 208,96) / 160
        val oraria = (2156.31 + 5 * 31.30 + 208.96) / 160
        assertEquals(8 * oraria * 0.35, voce("Indennità festiva"), 0.01)
    }

    @Test fun pasquaHaIndennitaPropria() {
        val dati = Dati(listOf(srv(pdm("2026-04-05T06:00", "2026-04-05T14:00"))), emptyList())
        val stima = Competenze.stimaMese(dati, YearMonth.of(2026, 4))
        assertEquals(65.0, stima.voci.first { it.etichetta == "Indennità domenicale" }.importo, 0.001)
        assertTrue(stima.voci.none { it.etichetta == "Indennità festiva" })
    }

    // ---------- voci aggiuntive ----------

    @Test fun straordinarioMacchinista() {
        // 9 h diurne feriali: 30' oltre 8 h 30'
        assertEquals(Triple(30L, 0L, 0L), Competenze.straordinario(pdm("2026-11-10T06:00", "2026-11-10T15:00")))
        // servizio che finisce alle 23:00: 30' oltre, tutti dopo le 22 -> notturno
        assertEquals(Triple(0L, 30L, 0L), Competenze.straordinario(pdm("2026-11-10T14:00", "2026-11-10T23:00")))
        // festivo (8 dicembre), diurno
        assertEquals(Triple(0L, 30L, 0L), Competenze.straordinario(pdm("2026-12-08T06:00", "2026-12-08T15:00")))
        // capo treno: 10 h non sono straordinario
        assertEquals(Triple(0L, 0L, 0L), Competenze.straordinario(pdb("2026-11-10T06:00", "2026-11-10T16:00")))
    }

    @Test fun indennitaFerieEGiornateDiTurno() {
        assertEquals(40.0, Competenze.indennitaFerie(Profilo.MACCHINISTA, LocalDate.of(2026, 7, 1)), 0.001)
        assertEquals(20.0, Competenze.indennitaFerie(Profilo.MACCHINISTA, LocalDate.of(2023, 7, 1)), 0.001)
        assertEquals(20.0, Competenze.indennitaFerie(Profilo.CAPOTRENO, LocalDate.of(2026, 7, 1)), 0.001)
        val dati = Dati(
            listOf(srv(pdm("2026-07-01T06:00", "2026-07-01T14:00"))),
            listOf(Assenza(1, LocalDate.of(2026, 7, 2), TipoAssenza.FERIE),
                   Assenza(2, LocalDate.of(2026, 7, 3), TipoAssenza.FERIE),
                   Assenza(3, LocalDate.of(2026, 7, 4), TipoAssenza.RIPOSO))
        )
        val stima = Competenze.stimaMese(dati, YearMonth.of(2026, 7))
        val voce = { nome: String -> stima.voci.first { it.etichetta == nome }.importo }
        assertEquals(80.0, voce("Indennità ferie"), 0.001)
        assertEquals(3 * 2.45, voce("Indennità di turno"), 0.001)   // presenza + 2 ferie, non il riposo
    }

    @Test fun assenzaDallaResidenzaEPernottamento() {
        val andata = Servizio(100, pdm("2026-11-10T16:00", "2026-11-10T23:00"), rfrDopo = true, minutiFuoriResidenza = 8 * 60)
        val ritorno = Servizio(101, pdm("2026-11-11T07:00", "2026-11-11T10:00"), minutiFuoriResidenza = 4 * 60)
        val stima = Competenze.stimaMese(Dati(listOf(andata, ritorno), emptyList()), YearMonth.of(2026, 11))
        val voce = { nome: String -> stima.voci.first { it.etichetta == nome }.importo }
        assertEquals(12 * 2.60, voce("Assenza dalla residenza"), 0.001)
        assertEquals(2.80, voce("Pernottamento"), 0.001)
    }

    @Test fun monteOreERiposoSuFestivo() {
        val r = Analisi.riepilogoMese(listOf(srv(pdm("2026-11-10T06:00", "2026-11-10T14:00"))), YearMonth.of(2026, 11),
            listOf(Assenza(1, LocalDate.of(2026, 11, 2), TipoAssenza.FERIE)))
        // 30 giorni × 38 / 7 = 162 h 51'; ore riconosciute 8 h + 7 h 36'
        assertTrue(r.esiti.any { it.messaggio.contains("15 h 36'") && it.messaggio.contains("162 h 51'") })
        val dati = Dati(emptyList(), listOf(Assenza(1, LocalDate.of(2026, 12, 8), TipoAssenza.RIPOSO_SETTIMANALE)))
        assertEquals(1.0, Ferie.saldo(dati, 2026).recuperiFestiviMaturati, 0.001)
    }

    // ---------- preferiti, duplicazione, riepilogo annuale ----------

    @Test fun preferitoNotturnoFinisceIlGiornoDopo() {
        val p = Preferito(1, "Notte", java.time.LocalTime.of(22, 0), java.time.LocalTime.of(4, 0),
            Profilo.MACCHINISTA, minutiCondotta = 200)
        val s = p.applica(LocalDate.of(2026, 11, 20), 9)
        assertEquals(dt("2026-11-21T04:00"), s.turno.fine)
        assertEquals(200, s.turno.minutiCondotta)
        val tornato = Preferito.da(s, "Notte", 2)
        assertEquals(p.oraInizio, tornato.oraInizio)
    }

    @Test fun duplicaSpostaInizioEFine() {
        val s = srv(pdm("2026-11-20T22:00", "2026-11-21T04:00"))
        val copia = s.spostato(7, 999)
        assertEquals(dt("2026-11-27T22:00"), copia.turno.inizio)
        assertEquals(dt("2026-11-28T04:00"), copia.turno.fine)
        assertEquals(999L, copia.id)
    }

    @Test fun condottaOltreLaDurataEUnDatoSbagliato() {
        val e = RegoleAltarail.verificaTurno(pdm("2026-11-10T06:00", "2026-11-10T08:00", condotta = 200))
        assertTrue(e.any { it.gravita == Gravita.ATTENZIONE && it.messaggio.contains("superano la durata") })
    }

    @Test fun riepilogoAnnuale() {
        val dati = Dati(
            listOf(
                srv(pdm("2026-01-10T06:00", "2026-01-10T14:00", condotta = 180)),
                srv(pdm("2026-03-10T22:00", "2026-03-11T04:00")),
                srv(pdm("2026-03-11T06:00", "2026-03-11T12:00"))   // riposo di 2 h: violazione
            ),
            listOf(Assenza(1, LocalDate.of(2026, 8, 10), TipoAssenza.FERIE))
        )
        val r = Annuale.riepilogo(dati, 2026)
        assertEquals(12, r.mesi.size)
        assertEquals(3, r.servizi)
        assertEquals(1, r.notturni)
        assertEquals(1, r.ferie)
        assertEquals(1, r.violazioni)
        assertTrue(r.stimaCompetenze > 0)
    }

    // ---------- import da testo, generatore, calendario, ripresa minima ----------

    @Test fun importaTurnoDaTesto() {
        val testo = """
            lun 10/11 06:12-14:40 cond 4:10 treni 2611 2618
            mar 11/11 22:00 04:30 RFR
            2026-11-12 07:15 10:05 condotta 150
            gio 13/11 RS
            14/11 R
            15/11/2026 ferie
            una riga senza senso
        """.trimIndent()
        val r = Importa.analizza(testo, 2026, Profilo.MACCHINISTA, 1)
        assertEquals(3, r.servizi.size)
        val primo = r.servizi[0]
        assertEquals(dt("2026-11-10T06:12"), primo.turno.inizio)
        assertEquals(dt("2026-11-10T14:40"), primo.turno.fine)
        assertEquals(250, primo.turno.minutiCondotta)
        assertEquals("treni 2611 2618", primo.note)
        assertEquals(dt("2026-11-12T04:30"), r.servizi[1].turno.fine)
        assertTrue(r.servizi[1].rfrDopo)
        assertEquals(150, r.servizi[2].turno.minutiCondotta)
        assertEquals(listOf(TipoAssenza.RIPOSO_SETTIMANALE, TipoAssenza.RIPOSO, TipoAssenza.FERIE), r.assenze.map { it.tipo })
        assertEquals(listOf("una riga senza senso"), r.nonRiconosciute)
    }

    @Test fun generatoreCiclico() {
        val preferiti = listOf(
            Preferito(1, "Mattina", java.time.LocalTime.of(6, 0), java.time.LocalTime.of(14, 0), Profilo.MACCHINISTA),
            Preferito(2, "Notte", java.time.LocalTime.of(22, 0), java.time.LocalTime.of(4, 0), Profilo.MACCHINISTA)
        )
        val r = Generatore.genera("Mattina, mattina, Notte, R, RS, -, Boh", LocalDate.of(2026, 11, 2), 14, preferiti, 100)
        assertEquals(6, r.servizi.size)        // 3 per ciclo, 2 cicli
        assertEquals(4, r.assenze.size)
        assertEquals(setOf("Boh"), r.sconosciuti)
        assertEquals(dt("2026-11-05T04:00"), r.servizi[2].turno.fine)
        assertEquals(LocalDate.of(2026, 11, 9), r.servizi[3].turno.inizio.toLocalDate())
        assertEquals(listOf("A", "B"), Generatore.elementi("A B"))
    }

    @Test fun calendarioIcs() {
        val dati = Dati(
            listOf(Servizio(7, pdm("2026-11-10T22:00", "2026-11-11T04:00", condotta = 200), note = "treni 1, 2; prova")),
            listOf(Assenza(8, LocalDate.of(2026, 11, 12), TipoAssenza.FERIE))
        )
        val ics = CalendarioIcs.genera(dati, dt("2026-11-09T10:00"))
        assertTrue(ics.startsWith("BEGIN:VCALENDAR\r\n"))
        assertTrue(ics.contains("DTSTART;TZID=Europe/Rome:20261110T220000"))
        assertTrue(ics.contains("DTEND;TZID=Europe/Rome:20261111T040000"))
        assertTrue(ics.contains("DTSTART;VALUE=DATE:20261112"))
        assertTrue(ics.contains("treni 1\\, 2\\; prova"))
        assertTrue(ics.lines().all { it.length <= 76 })
        assertTrue(ics.trimEnd().endsWith("END:VCALENDAR"))
    }

    @Test fun icsAndataERitorno() {
        val dati = Dati(
            listOf(Servizio(7, pdm("2026-11-10T22:00", "2026-11-11T04:00", condotta = 200), rfrDopo = true, note = "treni 1, 2; prova")),
            listOf(Assenza(8, LocalDate.of(2026, 11, 12), TipoAssenza.FERIE),
                Assenza(9, LocalDate.of(2026, 11, 13), TipoAssenza.RIPOSO_SETTIMANALE, "casa"),
                Assenza(10, LocalDate.of(2026, 11, 14), TipoAssenza.DISPONIBILITA))
        )
        val ics = CalendarioIcs.genera(dati, dt("2026-11-09T10:00"))
        val letto = CalendarioIcs.leggi(ics, 100)
        assertEquals(1, letto.servizi.size)
        assertEquals(dati.servizi[0].turno, letto.servizi[0].turno)
        assertEquals("treni 1, 2; prova", letto.servizi[0].note)
        assertTrue(letto.servizi[0].rfrDopo)
        assertEquals(listOf(TipoAssenza.FERIE, TipoAssenza.RIPOSO_SETTIMANALE, TipoAssenza.DISPONIBILITA), letto.assenze.map { it.tipo })
        assertEquals("casa", letto.assenze[1].note)
        assertEquals(100L, letto.servizi[0].id)
    }

    @Test fun icsConOpzioni() {
        val dati = Dati(
            listOf(Servizio(1, pdm("2026-11-10T06:00", "2026-11-10T14:00")), Servizio(2, pdm("2026-11-20T06:00", "2026-11-20T14:00"))),
            listOf(Assenza(8, LocalDate.of(2026, 11, 12), TipoAssenza.FERIE),
                Assenza(9, LocalDate.of(2026, 11, 13), TipoAssenza.RIPOSO),
                Assenza(10, LocalDate.of(2026, 11, 14), TipoAssenza.MALATTIA))
        )
        val soloFerie = CalendarioIcs.leggi(CalendarioIcs.genera(dati, opzioni = OpzioniCalendario(servizi = false, riposi = false, altre = false)))
        assertEquals(0, soloFerie.servizi.size)
        assertEquals(listOf(TipoAssenza.FERIE), soloFerie.assenze.map { it.tipo })
        val periodo = CalendarioIcs.leggi(CalendarioIcs.genera(dati, opzioni = OpzioniCalendario(da = LocalDate.of(2026, 11, 15))))
        assertEquals(1, periodo.servizi.size)
        assertEquals(0, periodo.assenze.size)
    }

    @Test fun ripresaMinima() {
        assertEquals(dt("2026-11-11T01:00"), RegoleAltarail.ripresaMinima(pdm("2026-11-10T06:00", "2026-11-10T14:00")))
        assertEquals(dt("2026-11-11T20:00"), RegoleAltarail.ripresaMinima(pdm("2026-11-10T22:00", "2026-11-11T04:00")))
        assertEquals(dt("2026-11-11T11:00"), RegoleAltarail.ripresaMinima(pdm("2026-11-10T22:00", "2026-11-11T04:00"), true))
    }

    // ---------- righe treno, orari reali, cestino, rubrica, qualità riposi, doppioni ----------

    private fun riga(n: String, da: String, a: String, tipo: TipoRiga = TipoRiga.CONDOTTA,
                     realeA: String? = null) =
        RigaTreno(n, "X", "Y", dt(da), dt(a), tipo, arrivoReale = realeA?.let { dt(it) })

    @Test fun condottaCalcolataDalleRighe() {
        val righe = listOf(
            riga("2601", "2026-11-10T06:00", "2026-11-10T07:30"),
            riga("2602", "2026-11-10T07:40", "2026-11-10T09:00"),   // sosta 10': stesso tratto
            riga("V", "2026-11-10T09:10", "2026-11-10T09:40", TipoRiga.VETTURA),
            riga("2610", "2026-11-10T10:00", "2026-11-10T11:00")    // sosta 60': nuovo tratto
        )
        assertEquals(90 + 80 + 60, Righe.minuti(righe, TipoRiga.CONDOTTA))
        assertEquals(180, Righe.continuativaMassima(righe))     // 06:00-09:00 con la sosta
        val s = Servizio(1, pdm("2026-11-10T05:30", "2026-11-10T11:30"), righe = righe).conRigheApplicate()
        assertEquals(230, s.turno.minutiCondotta)
        assertEquals(180, s.turno.maxCondottaConsecutiva)
    }

    @Test fun orariRealiSpostanoLaFineEControllano() {
        val righe = listOf(riga("2601", "2026-11-10T06:00", "2026-11-10T14:00", realeA = "2026-11-10T14:45"))
        val s = Servizio(1, pdm("2026-11-10T05:45", "2026-11-10T14:15"), righe = righe)
        assertEquals(45L, s.ritardoFinale)
        assertEquals(dt("2026-11-10T15:00"), s.turnoEffettivo().fine)
        val esiti = Analisi.verificaServizio(listOf(s), 0)
        assertTrue(esiti.any { it.messaggio.startsWith("Con gli orari reali") })
        // lo straordinario usa la fine effettiva: 9 h 15' - 8 h 30' = 45'
        assertEquals(Triple(45L, 0L, 0L), Competenze.straordinario(s.turnoEffettivo()))
    }

    @Test fun ancoraOrariOltreMezzanotte() {
        val inizio = dt("2026-11-10T22:00")
        assertEquals(dt("2026-11-11T01:30"), Righe.ancora(inizio, java.time.LocalTime.of(1, 30)))
        assertEquals(dt("2026-11-10T21:00"), Righe.ancora(inizio, java.time.LocalTime.of(21, 0)))
    }

    @Test fun righeSovrapposte() {
        val righe = listOf(riga("1", "2026-11-10T06:00", "2026-11-10T07:00"), riga("2", "2026-11-10T06:50", "2026-11-10T08:00"))
        val s = Servizio(1, pdm("2026-11-10T05:30", "2026-11-10T08:30"), righe = righe)
        assertTrue(Analisi.verificaServizio(listOf(s), 0).any { it.messaggio.contains("si sovrappongono") })
    }

    @Test fun preferitoConRigheSiSpostaDiGiorno() {
        val s = Servizio(1, pdm("2026-11-10T06:00", "2026-11-10T14:00"), materiale = "Caravaggio",
            righe = listOf(riga("2601", "2026-11-10T06:30", "2026-11-10T08:00", realeA = "2026-11-10T08:10")))
        val p = Preferito.da(s, "Turno 105", 9)
        val nuovo = p.applica(LocalDate.of(2026, 11, 17), 10)
        assertEquals(dt("2026-11-17T06:30"), nuovo.righe[0].partenza)
        assertEquals(null, nuovo.righe[0].arrivoReale)
        assertEquals("Caravaggio", nuovo.materiale)
        val copia = s.spostato(1, 11)
        assertEquals(dt("2026-11-11T08:00"), copia.righe[0].arrivo)
    }

    @Test fun cestinoEliminaRipristinaEPulisce() {
        val s = srv(pdm("2026-11-10T06:00", "2026-11-10T14:00"))
        val a = Assenza(5, LocalDate.of(2026, 11, 12), TipoAssenza.FERIE)
        val adesso = dt("2026-11-10T20:00")
        var d = Dati(listOf(s), listOf(a)).eliminaServizio(s.id, adesso).eliminaAssenza(5, adesso)
        assertEquals(0, d.servizi.size); assertEquals(0, d.assenze.size); assertEquals(2, d.cestino.size)
        d = d.ripristina(d.cestino.first { it.servizio != null })
        assertEquals(1, d.servizi.size); assertEquals(1, d.cestino.size)
        assertEquals(0, d.pulisciCestino(adesso.plusDays(31)).cestino.size)
        assertEquals(1, d.pulisciCestino(adesso.plusDays(29)).cestino.size)
    }

    @Test fun deduplicaScartaIDoppioni() {
        val a = srv(pdm("2026-11-10T06:00", "2026-11-10T14:00"))
        val nuovi = listOf(srv(pdm("2026-11-10T06:00", "2026-11-10T14:00")), srv(pdm("2026-11-11T06:00", "2026-11-11T14:00")),
            srv(pdm("2026-11-11T06:00", "2026-11-11T14:00")))
        assertEquals(1, deduplica(listOf(a), nuovi).size)
    }

    @Test fun qualitaDeiRiposi() {
        val ordinati = listOf(
            srv(pdm("2026-11-10T06:00", "2026-11-10T12:00")),
            srv(pdm("2026-11-10T23:30", "2026-11-11T00:30")),   // 11 h 30' su 11: al limite
            srv(pdm("2026-11-11T18:00", "2026-11-11T22:00")),   // 17 h 30' su 14 (fine 00:30): ampio
            srv(pdm("2026-11-12T08:00", "2026-11-12T14:00"))    // 10 h su 11: sotto il minimo
        )
        val q = Analisi.qualitaRiposi(ordinati, emptyList(), YearMonth.of(2026, 11))
        assertEquals(1, q[Analisi.QualitaRiposo.AL_LIMITE])
        assertEquals(1, q[Analisi.QualitaRiposo.AMPIO])
        assertEquals(1, q[Analisi.QualitaRiposo.INSUFFICIENTE])
    }

    @Test fun riposoDopoFineTraMezzanotteEUnaVale14OrePerEntrambiIProfili() {
        val g = LocalDate.of(2026, 10, 14)
        for (p in Profilo.values()) {
            val t = Turno(g.atTime(18, 0), g.plusDays(1).atTime(0, 30), p)
            assertEquals(14, RegoleAltarail.minimoRiposoDopo(t).first)
        }
        // Fine dopo l'01:00: per il PdB resta notturno con 16 h
        val n = Turno(g.atTime(18, 0), g.plusDays(1).atTime(1, 30), Profilo.CAPOTRENO)
        assertEquals(16, RegoleAltarail.minimoRiposoDopo(n).first)
    }
}
