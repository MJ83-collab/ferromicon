package it.ferromicon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

/** Test di perimetri di residenza, recupero festivi, limiti personalizzati e riepilogo esteso. */
class NovitaTest {

    private fun dt(s: String) = LocalDateTime.parse(s)
    private fun riga(da: String, a: String, p: String, ar: String, reale: String? = null) =
        RigaTreno(da = da, a = a, partenza = dt(p), arrivo = dt(ar), arrivoReale = reale?.let { dt(it) })
    private var id = 1L
    private fun srv(profilo: Profilo, da: String, a: String, condotta: Int = 0, rfr: Boolean = false) =
        Servizio(id++, Turno(dt(da), dt(a), profilo, minutiCondotta = condotta), rfrDopo = rfr)

    // ---------- perimetri di residenza ----------

    @Test fun nomiStazioneConfrontabili() {
        assertEquals(Residenza.normalizza("Milano  CADORNA"), Residenza.normalizza(" milano cadorna "))
        assertEquals("citta", Residenza.normalizza("Città"))
        val p = listOf(Perimetro(1, "Varese", listOf("Varese", "Gazzada Schianno")))
        assertTrue(Residenza.dentro("VARESE", p))
        assertFalse(Residenza.dentro("Milano Cadorna", p))
        assertFalse(Residenza.dentro("Varese", listOf(Perimetro(1, "Varese", listOf("Varese"), attivo = false))))
    }

    @Test fun oreFuoriResidenzaDalleRighe() {
        val p = listOf(Perimetro(1, "Varese", listOf("Varese")))
        val andataRitorno = listOf(
            riga("Varese", "Milano Cadorna", "2026-11-10T06:00", "2026-11-10T07:05"),
            riga("Milano Cadorna", "Varese", "2026-11-10T17:00", "2026-11-10T18:05")
        )
        assertEquals(12 * 60 + 5, Residenza.minutiFuori(andataRitorno, p))
        // orario reale: l'ultimo treno arriva 10' dopo
        val conRitardo = listOf(andataRitorno[0],
            riga("Milano Cadorna", "Varese", "2026-11-10T17:00", "2026-11-10T18:05", reale = "2026-11-10T18:15"))
        assertEquals(12 * 60 + 15, Residenza.minutiFuori(conRitardo, p))
        // senza rientro (riposo fuori residenza): fino all'ultimo arrivo
        val senzaRientro = listOf(andataRitorno[0],
            riga("Milano Cadorna", "Laveno", "2026-11-10T17:00", "2026-11-10T18:30"))
        assertEquals(12 * 60 + 30, Residenza.minutiFuori(senzaRientro, p))
        // servizio tutto dentro il perimetro, o nessun perimetro: zero
        assertEquals(0, Residenza.minutiFuori(listOf(riga("Varese", "Varese", "2026-11-10T06:00", "2026-11-10T07:00")), p))
        assertEquals(0, Residenza.minutiFuori(andataRitorno, emptyList()))
        assertEquals(0, Residenza.minutiFuori(emptyList(), p))
    }

    @Test fun serviziConFuoriResidenzaRicalcolato() {
        val p = listOf(Perimetro(1, "Varese", listOf("Varese")))
        val s = srv(Profilo.MACCHINISTA, "2026-11-10T06:00", "2026-11-10T18:05")
            .copy(righe = listOf(
                riga("Varese", "Milano Cadorna", "2026-11-10T06:00", "2026-11-10T07:05"),
                riga("Milano Cadorna", "Varese", "2026-11-10T17:00", "2026-11-10T18:05")))
        assertEquals(725, Residenza.conFuoriResidenza(s, p).minutiFuoriResidenza)
        // senza risultato resta il valore inserito a mano
        assertEquals(200, Residenza.conFuoriResidenza(s.copy(minutiFuoriResidenza = 200, righe = emptyList()), p)
            .minutiFuoriResidenza)
    }

    // ---------- recupero festivi ----------

    @Test fun scadenzeDeiFestivi() {
        val oggi = LocalDate.of(2026, 11, 10)
        val stati = RecuperoFestivi.stati(2026, oggi).associateBy { it.data }
        // la Pasqua non è nell'elenco (ha un'indennità propria)
        assertNull(stati[Festivi.pasqua(2026)])
        val ferragosto = stati.getValue(LocalDate.of(2026, 8, 15))
        assertEquals(3, ferragosto.giorniAllaScadenza)
        assertTrue(ferragosto.recuperabile)
        assertEquals(53, stati.getValue(LocalDate.of(2026, 10, 4)).giorniAllaScadenza)
        assertEquals(81, stati.getValue(LocalDate.of(2026, 11, 1)).giorniAllaScadenza)
        val capodanno = stati.getValue(LocalDate.of(2026, 1, 1))
        assertTrue(capodanno.scaduto)
        assertFalse(capodanno.recuperabile)
        assertEquals("Scaduto da 223 gg", RecuperoFestivi.descrizione(capodanno, oggi))
        val natale = stati.getValue(LocalDate.of(2026, 12, 25))
        assertEquals("Recuperabile · scade il 25/03/2027", RecuperoFestivi.descrizione(natale, oggi))
        assertEquals("Recuperabile · scadenza tra 53 gg",
            RecuperoFestivi.descrizione(stati.getValue(LocalDate.of(2026, 10, 4)), oggi))
    }

    @Test fun festiviForzatiAMano() {
        val oggi = LocalDate.of(2026, 11, 10)
        val capodanno = LocalDate.of(2026, 1, 1)
        val ferragosto = LocalDate.of(2026, 8, 15)
        val stati = RecuperoFestivi.stati(2026, oggi, mapOf(capodanno to true, ferragosto to false)).associateBy { it.data }
        assertTrue(stati.getValue(capodanno).recuperabile)
        assertFalse(stati.getValue(ferragosto).recuperabile)
        assertEquals("Non recuperabile (forzato)", RecuperoFestivi.descrizione(stati.getValue(ferragosto), oggi))
    }

    @Test fun festiviLavoratiDaRecuperare() {
        val oggi = LocalDate.of(2026, 11, 10)
        val servizi = listOf(
            srv(Profilo.MACCHINISTA, "2026-08-15T08:00", "2026-08-15T14:00"),
            srv(Profilo.MACCHINISTA, "2026-01-01T08:00", "2026-01-01T14:00")   // scaduto
        )
        val d = RecuperoFestivi.daRecuperare(2026, oggi, emptyMap(), servizi)
        assertEquals(listOf(LocalDate.of(2026, 8, 15)), d.map { it.data })
    }

    // ---------- limiti personalizzati ----------

    @Test fun limitiPersonalizzatiSostituisconoQuelliDelContratto() {
        val t = Turno(dt("2026-11-10T06:00"), dt("2026-11-10T15:00"), Profilo.CAPOTRENO)
        assertTrue(RegoleAltarail.verificaTurno(t).none { it.gravita == Gravita.VIOLAZIONE })
        val stretti = LimitiOrario(8 * 60 + 30, 7 * 60)
        val esiti = RegoleAltarail.verificaTurno(t, stretti)
        assertTrue(esiti.any { it.gravita == Gravita.VIOLAZIONE && it.messaggio.contains("personalizzato") })
        assertEquals(LimitiOrario(10 * 60, 8 * 60), LimitiOrario.contratto(Profilo.CAPOTRENO))
        assertEquals(LimitiOrario(8 * 60 + 30, 7 * 60), LimitiOrario.contratto(Profilo.MACCHINISTA))
        // anche tramite l'analisi del servizio
        val ordinati = listOf(Servizio(1, t))
        assertTrue(Analisi.verificaServizio(ordinati, 0, emptyList(), stretti).any { it.gravita == Gravita.VIOLAZIONE })
        assertTrue(Analisi.verificaServizio(ordinati, 0, emptyList()).none { it.gravita == Gravita.VIOLAZIONE })
    }

    // ---------- riepilogo del mese e del giorno ----------

    private fun datiDiProva(): Dati {
        id = 1
        val servizi = listOf(
            srv(Profilo.MACCHINISTA, "2026-11-03T06:00", "2026-11-03T15:00", condotta = 200),   // 30' di straordinario
            srv(Profilo.MACCHINISTA, "2026-11-08T06:00", "2026-11-08T15:30"),                    // domenica in RS: 60'
            srv(Profilo.MACCHINISTA, "2026-11-10T14:00", "2026-11-10T22:00", rfr = true),
            srv(Profilo.MACCHINISTA, "2026-11-11T06:00", "2026-11-11T12:00", condotta = 100)
        )
        val assenze = listOf(
            Assenza(1, LocalDate.of(2026, 11, 4), TipoAssenza.FERIE),
            Assenza(2, LocalDate.of(2026, 11, 8), TipoAssenza.RIPOSO_SETTIMANALE)
        )
        return Dati(servizi, assenze)
    }

    @Test fun riepilogoMensileCompleto() {
        val r = Riepilogo.mese(datiDiProva(), YearMonth.of(2026, 11))
        assertEquals(30 * 38L * 60 / 7, r.monteOreMinuti)
        assertEquals("162:51", Riepilogo.ore(r.monteOreMinuti))
        assertEquals(4, r.presenzeEffettive)
        assertEquals(5, r.presenzeComplessive)       // + la giornata di ferie
        assertEquals(30, r.giorniDelMese)
        assertEquals(1, r.dormite)
        assertEquals(0, r.serviziNotturni)
        assertEquals(300, r.condottaMinuti)
        assertEquals(280, r.condottaFinoA3hMinuti)   // 180 + 100
        assertEquals(20, r.condottaOltre3hMinuti)
        assertEquals(30, r.straordinarioNormaleMinuti)
        assertEquals(60, r.straordinarioInRsMinuti)
        assertEquals(0, r.straordinarioInRcMinuti)
        assertEquals(90, r.straordinarioTotaleMinuti)
        assertEquals(1, r.ferieMese)
        assertEquals(1, r.ferieAnno)
        // lavoro: 9 h + 9 h 30' + 8 h + 6 h = 32 h 30'; più una giornata di ferie da 7 h 36'
        assertEquals(32 * 60 + 30L, r.lavoroMinuti)
        assertEquals(32 * 60 + 30L + 456, r.oreRiconosciuteMinuti)
    }

    @Test fun diariaSeparaSenzaERfr() {
        val base = datiDiProva()
        val servizi = base.servizi.map {
            when (it.id) {
                1L -> it.copy(minutiFuoriResidenza = 600)    // senza RFR
                3L -> it.copy(minutiFuoriResidenza = 800)    // con RFR dopo
                4L -> it.copy(minutiFuoriResidenza = 120)    // sotto le 3 h: non conta
                else -> it
            }
        }
        val r = Riepilogo.mese(base.copy(servizi = servizi), YearMonth.of(2026, 11))
        assertEquals(600, r.diariaSenzaRfrMinuti)
        assertEquals(800, r.diariaConRfrMinuti)
    }

    @Test fun riepilogoDelGiornoConDormita() {
        val d = datiDiProva()
        val g = Riepilogo.giorno(d, LocalDate.of(2026, 11, 10))
        assertEquals(480, g.durataMinuti)
        assertTrue(g.dormita)
        assertEquals(480L, g.riposoDopoMinuti)
        assertFalse(g.riposoSettimanale)
        assertEquals(480L + 360, g.settimanaMinuti)      // martedì + mercoledì della settimana da lunedì 9
        assertEquals(setOf(LocalDate.of(2026, 11, 10)), Riepilogo.giorniConDormita(d, YearMonth.of(2026, 11)))
        val vuoto = Riepilogo.giorno(d, LocalDate.of(2026, 11, 20))
        assertTrue(vuoto.servizi.isEmpty())
        assertNull(vuoto.riposoDopoMinuti)
        assertFalse(vuoto.dormita)
        // la domenica in riposo settimanale ha l'assenza collegata
        assertEquals(TipoAssenza.RIPOSO_SETTIMANALE, Riepilogo.giorno(d, LocalDate.of(2026, 11, 8)).assenza?.tipo)
    }
}
