package it.ferromicon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FigureProfessionaliTest {

    @Test fun nomiUnici() {
        val nomi = FigureProfessionali.tutte.map { it.nome }
        assertEquals(nomi.size, nomi.toSet().size)
    }

    @Test fun importiPositivi() {
        assertTrue(FigureProfessionali.tutte.all { it.importoMensile > 0 })
    }

    @Test fun macchinistaERiferimentoCCNL() {
        assertEquals(208.96, FigureProfessionali.importo("Macchinista")!!, 0.001)
        assertEquals(212.52, FigureProfessionali.importo("Impiegato Direttivo")!!, 0.001)
        assertEquals(104.02, FigureProfessionali.importo("Specialista Tecnico Amministrativo")!!, 0.001)
    }

    @Test fun nomeVuotoOIgnoto_eNull() {
        assertNull(FigureProfessionali.importo(""))
        assertNull(FigureProfessionali.importo("Figura inventata"))
    }

    @Test fun salarioProfessionaleUsaLaFiguraScelta() {
        assertEquals(104.02, Competenze.salarioProfessionale(Profilo.UFFICIO, "Specialista Tecnico Amministrativo"), 0.001)
    }

    @Test fun salarioProfessionaleSenzaFiguraSegueIlProfilo() {
        assertEquals(208.96, Competenze.salarioProfessionale(Profilo.MACCHINISTA), 0.001)
        assertEquals(142.54, Competenze.salarioProfessionale(Profilo.CAPOTRENO), 0.001)
    }

    @Test fun etichettaConImportoInEuro() {
        assertTrue(FigureProfessionali.tutte.first { it.nome == "Macchinista" }.etichetta.endsWith("208,96 €"))
    }
}
