package it.ferromicon

import it.ferromicon.Gravita.ATTENZIONE
import it.ferromicon.Gravita.INFO
import it.ferromicon.Gravita.VIOLAZIONE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/** Stessi turni, valutati con le regole dei tre profili: PdC (macchinista), PdA (capotreno), Ufficio. */
class ProfiliTest {

    private fun turno(p: Profilo, dal: String, al: String) =
        Turno(LocalDateTime.parse(dal), LocalDateTime.parse(al), p)

    private fun esiti(t: Turno) = RegoleAltarail.verificaTurno(t)

    private fun gravita(t: Turno, g: Gravita) = esiti(t).any { it.gravita == g }

    // --- PdC (macchinista) ---

    @Test fun pdcGiornoOreOrdinarieNessunaViolazione() {
        assertFalse(gravita(turno(Profilo.MACCHINISTA, "2026-03-10T06:00", "2026-03-10T14:00"), VIOLAZIONE))
    }

    @Test fun pdcGiornoOltreDieci_ore_eViolazione() {
        assertTrue(gravita(turno(Profilo.MACCHINISTA, "2026-03-10T06:00", "2026-03-10T18:00"), VIOLAZIONE))
    }

    @Test fun pdcGiornoOltreOttoEMezza_eAttenzione() {
        assertTrue(gravita(turno(Profilo.MACCHINISTA, "2026-03-10T06:00", "2026-03-10T15:00"), ATTENZIONE))
    }

    @Test fun pdcNotturnoOltreSette_eViolazione() {
        assertTrue(gravita(turno(Profilo.MACCHINISTA, "2026-03-10T23:00", "2026-03-11T07:00"), VIOLAZIONE))
    }

    @Test fun pdcNotturnoSeiOre_nessunaViolazione() {
        assertFalse(gravita(turno(Profilo.MACCHINISTA, "2026-03-10T23:00", "2026-03-11T05:00"), VIOLAZIONE))
    }

    // --- PdA (capotreno) ---

    @Test fun pdaGiornoNove_oreNonSonoViolazione() {
        assertFalse(gravita(turno(Profilo.CAPOTRENO, "2026-03-10T06:00", "2026-03-10T15:00"), VIOLAZIONE))
    }

    @Test fun pdaGiornoOltreNove_eInfoCCNL() {
        assertTrue(gravita(turno(Profilo.CAPOTRENO, "2026-03-10T06:00", "2026-03-10T15:30"), INFO))
    }

    @Test fun pdaGiornoOltreDieci_eViolazione() {
        assertTrue(gravita(turno(Profilo.CAPOTRENO, "2026-03-10T06:00", "2026-03-10T17:00"), VIOLAZIONE))
    }

    @Test fun pdaNotturnoOltreOtto_eViolazione() {
        assertTrue(gravita(turno(Profilo.CAPOTRENO, "2026-03-10T22:00", "2026-03-11T07:00"), VIOLAZIONE))
    }

    // --- Ufficio (staff) ---

    @Test fun ufficioOrarioOrdinario_eOk() {
        assertFalse(gravita(turno(Profilo.UFFICIO, "2026-03-10T08:00", "2026-03-10T15:36"), ATTENZIONE))
        assertFalse(gravita(turno(Profilo.UFFICIO, "2026-03-10T08:00", "2026-03-10T15:36"), VIOLAZIONE))
    }

    @Test fun ufficioOltreOrarioOrdinario_eInfoStraordinario() {
        assertTrue(gravita(turno(Profilo.UFFICIO, "2026-03-10T08:00", "2026-03-10T17:00"), INFO))
    }

    @Test fun ufficioNotturno_eAttenzione() {
        assertTrue(gravita(turno(Profilo.UFFICIO, "2026-03-10T22:00", "2026-03-11T06:00"), ATTENZIONE))
    }

    @Test fun ufficioSenzaRegoleDiCondotta() {
        // Lo staff non ha condotta: nessun esito che parli di condotta.
        assertFalse(esiti(turno(Profilo.UFFICIO, "2026-03-10T08:00", "2026-03-10T15:36"))
            .any { it.messaggio.contains("condotta", ignoreCase = true) })
    }

    @Test fun ogniProfiloHaUnaEtichetta() {
        assertEquals(3, Profilo.values().size)
        Profilo.values().forEach { assertTrue(it.etichetta.isNotBlank()) }
    }
}
