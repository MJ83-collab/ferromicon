package it.ferromicon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfrontoStraordinarioTest {

    private fun dati(
        mensile: Double = 1600.0,
        oreEffettive: Double = 0.0,
        divisore: ConfrontoStraordinario.Divisore = ConfrontoStraordinario.Divisore.FISSE_160,
        oreStra: Double = 10.0,
        magg: Double = 0.18,
        notturne: Double = 0.0
    ) = ConfrontoStraordinario.Dati(
        mensile = mensile, oreEffettiveMese = oreEffettive, divisore = divisore,
        reddito = 30000.0, anno = 2026, parametri = Fisco.Parametri(),
        oreStra = oreStra, maggiorazione = magg, oreNotturneStra = notturne, tariffaNotturna = 2.50
    )

    @Test fun oraBaseCon160OreFisse() {
        assertEquals(10.0, ConfrontoStraordinario.calcola(dati()).oraBase, 1e-9)
    }

    @Test fun oraBaseConOreEffettive() {
        val r = ConfrontoStraordinario.calcola(dati(oreEffettive = 200.0,
            divisore = ConfrontoStraordinario.Divisore.ORE_EFFETTIVE))
        assertEquals(8.0, r.oraBase, 1e-9)
    }

    @Test fun oreEffettiveZeroRicadonoSu160() {
        val r = ConfrontoStraordinario.calcola(dati(oreEffettive = 0.0,
            divisore = ConfrontoStraordinario.Divisore.ORE_EFFETTIVE))
        assertEquals(10.0, r.oraBase, 1e-9)
    }

    @Test fun lordoStraordinarioEMaggiorazione() {
        // 10 ore a 10 €/h con +18% = 118 €
        val r = ConfrontoStraordinario.calcola(dati())
        assertEquals(118.0, r.lordoStra, 1e-9)
        assertEquals(11.8, r.lordoOraStra, 1e-9)
    }

    @Test fun trattenuteSonoPositive() {
        val r = ConfrontoStraordinario.calcola(dati())
        assertTrue(r.trattenuteStra > 0)
        assertTrue(r.nettoOraStra < r.lordoOraStra)
        assertTrue(r.nettoOraNormale < r.lordoOraNormale)
    }

    @Test fun nettoConStraordinarioSommaTurnoEStraordinario() {
        val r = ConfrontoStraordinario.calcola(dati())
        assertEquals(r.nettoTurno + r.nettoStra, r.nettoConStra, 1e-9)
        assertTrue(r.nettoConStra > r.nettoTurno)
    }

    @Test fun maggiorazioneMediaPonderata() {
        // 60 minuti feriali (+18%) e 60 minuti festivi notturni (+50%) → media 34%
        assertEquals(0.34, ConfrontoStraordinario.maggiorazioneMedia(60, 0, 60), 1e-9)
        assertEquals(0.0, ConfrontoStraordinario.maggiorazioneMedia(0, 0, 0), 1e-9)
    }

    @Test fun nessunoStraordinarioNessunaTrattenuta() {
        val r = ConfrontoStraordinario.calcola(dati(oreStra = 0.0))
        assertEquals(0.0, r.lordoStra, 1e-9)
        assertEquals(0.0, r.nettoStra, 1e-9)
    }

    @Test fun notturneSenzaOreNonContano() {
        assertEquals(0.0, ConfrontoStraordinario.calcola(dati(notturne = 0.0)).nettoNotturno, 1e-9)
        assertTrue(ConfrontoStraordinario.calcola(dati(notturne = 5.0)).nettoNotturno > 0)
    }
}
