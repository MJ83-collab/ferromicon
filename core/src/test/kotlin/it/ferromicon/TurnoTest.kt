package it.ferromicon

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class TurnoTest {
    private fun pref(nome: String, i: Int, f: Int) = Preferito(
        1, nome, LocalTime.of(i, 0), LocalTime.of(f, 0), Profilo.MACCHINISTA, minutiCondotta = 240)

    private val m = pref("Mattina", 5, 12)
    private val n = pref("Notte", 22, 5)
    private val lun = LocalDate.of(2026, 11, 9)

    private fun turno(fino: LocalDate = lun.plusDays(13)) = TurnoImpianto(
        "T1", "Milano", listOf("Mattina", "Notte", "R", "RS"), lun, 0, fino)

    @Test fun cambioOrarioSecondaDomenica() {
        assertEquals(LocalDate.of(2026, 12, 13), CambioOrario.prossimo(LocalDate.of(2026, 11, 10)))
        assertEquals(LocalDate.of(2027, 6, 13), CambioOrario.prossimo(LocalDate.of(2026, 12, 13)))
        assertEquals(LocalDate.of(2026, 12, 12), CambioOrario.ultimoGiornoDelTurno(LocalDate.of(2026, 11, 10)))
    }

    @Test fun posizioneECiclo() {
        val t = turno().copy(posizione = 2)
        assertEquals("R", t.elementoPer(lun))
        assertEquals("RS", t.elementoPer(lun.plusDays(1)))
        assertEquals("Mattina", t.elementoPer(lun.plusDays(2)))
        assertEquals(3, t.numeroGiornata(lun))
        assertNull(t.elementoPer(lun.plusDays(14)))
        assertEquals("RS", t.elementoPer(lun.minusDays(3)))
    }

    @Test fun compilaFinoAlCambioTurno() {
        val c = PianoTurno.compila(turno(), lun, listOf(m, n), Dati(emptyList(), emptyList()), 100)
        assertEquals(8, c.servizi.size)
        assertEquals(6, c.assenze.size)
        assertTrue(c.servizi.all { it.origineTurno.isNotEmpty() })
        assertEquals(0, c.saltati)
        assertTrue(c.servizi.maxOf { it.turno.inizio.toLocalDate() } <= lun.plusDays(13))
    }

    @Test fun rispettaIGiorniModificatiAMano() {
        val base = Dati(emptyList(), emptyList())
        val c1 = PianoTurno.compila(turno(), lun, listOf(m, n), base, 100)
        val d1 = PianoTurno.applica(base, c1, LocalDateTime.now())
        // Ricompilare non cambia nulla.
        val c2 = PianoTurno.compila(turno(), lun, listOf(m, n), d1, 500)
        assertTrue(c2.servizi.isEmpty() && c2.rimuoviServizi.isEmpty())
        // Modifica a mano: si aggiunge un treno alla mattina del primo giorno.
        val s0 = d1.servizi.first { it.turno.inizio.toLocalDate() == lun }
        val riga = RigaTreno("10", "A", "B", lun.atTime(6, 0), lun.atTime(7, 0))
        val d2 = d1.copy(servizi = d1.servizi - s0 + s0.copy(righe = listOf(riga)))
        val c3 = PianoTurno.compila(turno(), lun, listOf(m, n), d2, 900)
        assertEquals(1, c3.saltati)
        assertTrue(c3.rimuoviServizi.none { it.id == s0.id })
        // Ripristino della giornata di default: sostituisce e manda l'originale nel cestino.
        val c4 = PianoTurno.giornata(turno(), lun, listOf(m, n), d2, 900)
        val d3 = PianoTurno.applica(d2, c4, LocalDateTime.now())
        assertEquals(1, d3.servizi.count { it.turno.inizio.toLocalDate() == lun })
        assertTrue(d3.servizi.first { it.turno.inizio.toLocalDate() == lun }.righe.isEmpty())
        assertEquals(1, d3.cestino.size)
    }

    @Test fun giornataInseritaASmanoNonVieneCalpestata() {
        val manuale = Servizio(1, Turno(lun.atTime(14, 0), lun.atTime(20, 0), Profilo.MACCHINISTA))
        val d = Dati(listOf(manuale), emptyList())
        val c = PianoTurno.compila(turno(), lun, listOf(m, n), d, 100)
        assertEquals(1, c.saltati)
        assertTrue(c.rimuoviServizi.isEmpty())
    }

    @Test fun elementoSconosciuto() {
        val t = turno().copy(giornate = listOf("Boh"))
        val c = PianoTurno.compila(t, lun, emptyList(), Dati(emptyList(), emptyList()), 1)
        assertEquals(setOf("Boh"), c.sconosciuti)
    }

    @Test fun pianificazioneFerie() {
        // Libero: sabato e domenica. Con 5 giorni di ferie dal lunedì al venerdì: sab-dom + 5 + sab-dom = 9.
        val libero = { d: LocalDate -> d.dayOfWeek.value >= 6 }
        val r = PianoFerie.migliori(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), 5, 1, libero)
        assertEquals(1, r.size)
        assertEquals(9, r[0].giorniTotali)
        assertEquals(5, r[0].giorniFerie)
    }

    @Test fun scadenzaEProlungamento() {
        val t = turno(fino = LocalDate.of(2026, 12, 12))
        assertEquals(33, t.giorniAllaFine(lun))
        assertEquals(-1, t.giorniAllaFine(LocalDate.of(2026, 12, 13)))
        val p = t.prolungato()
        assertEquals(LocalDate.of(2027, 6, 12), p.validoFino)
        // il ciclo continua senza salti
        assertEquals(t.elementoPer(LocalDate.of(2026, 12, 12)), p.elementoPer(LocalDate.of(2026, 12, 12)))
        assertNotNull(p.elementoPer(LocalDate.of(2026, 12, 13)))
    }

    @Test fun nomiSconosciutiETesto() {
        val t = turno().copy(giornate = listOf("Mattina", "Pomeriggio", "R", "-"))
        assertEquals(listOf("Pomeriggio"), t.sconosciute(listOf(m)))
        assertEquals("Mattina, Pomeriggio, R, -", t.comeTesto())
        assertEquals(t.giornate, Generatore.elementi(t.comeTesto()))
    }

    @Test fun prossimiGiorniESostituto() {
        val t = turno()
        val p = PianoTurno.prossimiGiorni(t, lun, 3)
        assertEquals(listOf("Mattina", "Notte", "R"), p.map { it.nome })
        assertEquals(listOf(1, 2, 3), p.map { it.numero })
        assertEquals(2, PianoTurno.prossimiGiorni(t, lun.plusDays(12), 5).size)
        val c = PianoTurno.giornata(t, lun, listOf(m, n), Dati(emptyList(), emptyList()), 1, sostituto = "Notte")
        assertEquals("Notte", c.servizi.single().origineTurno)
    }

    @Test fun rimuoveSoloIGenerati() {
        val base = Dati(emptyList(), emptyList())
        val d1 = PianoTurno.applica(base, PianoTurno.compila(turno(), lun, listOf(m, n), base, 100), LocalDateTime.now())
        val manuale = Servizio(7, Turno(lun.plusDays(20).atTime(8, 0), lun.plusDays(20).atTime(14, 0), Profilo.MACCHINISTA))
        val d2 = d1.copy(servizi = d1.servizi + manuale)
        val r = PianoTurno.rimuoviGenerati(turno(), lun, listOf(m, n), d2)
        val d3 = PianoTurno.applica(d2, r, LocalDateTime.now())
        assertEquals(listOf(manuale), d3.servizi)
        assertTrue(d3.assenze.isEmpty())
    }

    @Test fun cicloControllaIlRientro() {
        // Notte 22-05 seguita da Mattina 05-12 alla fine del giro: riposo insufficiente nel rientro.
        val t = turno().copy(giornate = listOf("Mattina", "Notte"))
        val r = PianoTurno.riepilogoCiclo(t, listOf(m, n))
        assertEquals(2, r.lavorative)
        assertEquals(1, r.notturne)
        assertEquals(7 * 60 + 7 * 60, r.minutiPresenza)
        assertTrue(r.problemi.isNotEmpty())
        val sano = PianoTurno.riepilogoCiclo(turno().copy(giornate = listOf("Mattina", "R", "R")), listOf(m, n))
        assertTrue(sano.problemi.isEmpty())
        assertEquals(2, sano.riposi)
    }
}
