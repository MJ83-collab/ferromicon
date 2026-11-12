package it.ferromicon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CartellinoSapTest {

    private val righe = listOf(
        "Cartellino orologio con selezione data 01.11.2026-30.11.2026",
        "01 DO RS Ognissanti",
        "02 LU G118 Timbrature 08:19 16:28 7,36 0,00 0,03 0,00 0,03",
        "03 MA G118 Timbrature 08:22 16:31 7,36 0,00 0,03 0,00 0,03",
        "Maggior presenza 16:28 7,36 0,00",
        "09 LU G118 Timbrature 08:12 15:36 6,51 0,00 0,00 0,00 0,45-",
        "6,51 0,00 0,00 0,00 0,45-",
        "Flessibilità mese precedente 5,00"
    )

    @Test fun leggePeriodoEGiornate() {
        val c = LettoreCartellino.analizza(righe)!!
        assertEquals(2026, c.anno)
        assertEquals(11, c.mese)
        assertEquals(4, c.giornate.size)
        assertEquals(1, c.giornate[0].giorno)
        assertEquals("DO", c.giornate[0].sigla)
        assertNull(c.giornate[0].prima)
    }

    @Test fun orariEFlexGiornaliero() {
        val c = LettoreCartellino.analizza(righe)!!
        val due = c.giornate.first { it.giorno == 2 }
        assertEquals("08:19", due.prima)
        assertEquals("16:28", due.ultima)
        assertEquals(0.03, due.flex!!, 0.0001)
    }

    @Test fun flexNegativoConSegnoFinale() {
        val c = LettoreCartellino.analizza(righe)!!
        assertEquals(-0.45, c.giornate.first { it.giorno == 9 }.flex!!, 0.0001)
    }

    @Test fun flexMesePrecedenteNonEFlexDiGiornata() {
        val c = LettoreCartellino.analizza(righe)!!
        assertEquals(5.0, c.flexMesePrecedente!!, 0.0001)
        // La riga «Flessibilità mese precedente» non deve diventare la flex del giorno 9.
        assertEquals(-0.45, c.giornate.first { it.giorno == 9 }.flex!!, 0.0001)
    }

    @Test fun senzaPeriodoNonAnalizza() {
        assertNull(LettoreCartellino.analizza(listOf("01 DO RS Ognissanti")))
    }

    @Test fun valoreConSegnoDaTrattinoFinaleOIniziale() {
        assertEquals(-1.5, LettoreCartellino.valore("1,50-"), 0.0001)
        assertEquals(-1.5, LettoreCartellino.valore("-1,50"), 0.0001)
        assertEquals(1.02, LettoreCartellino.valore("1,02"), 0.0001)
    }

    /** Cartellino SAP reale di ottobre 2026 (testo estratto dal PDF, pagine 1-3). */
    private val ottobre = listOf(
        "Cartellino orologio",
        "Dal 01.10.2026 al 31.10.2026",
        "                                                                                         12.11.2026 Pagina 1 / 3",
        "Per richieste di chiarimenti rivolgersi a   Dati personali / organizzativi",
        ", n. tel.:                                             Nome                          Cognome                     Posizione",
        "                                                    MARZIA                        FERRARIS                     POSIZIONE",
        "                                                 Unità organizzativa             Area del personale                C.I.D",
        "ST. di VARESE, PIAZZALE TRIESTE, 1,              DISTRETTO 4                          L890                      03102962",
        "21100,VARESE",
        "Sig.a FERRARIS MARZIA",
        "VIA MAGNANI 2",
        "Mese: Ottobre - 2026",
        "Singoli risultati per giorno",
        "Giorno                                      Da         Fino a          Lavoro Giustific Extra                Ore      Flex",
        "                                                                       Ordinari Assenza orario               Autorizz",
        "                                                                       o                                     ate",
        "01   GI   G118     Timbrature               08:14       16:22             7,36         0,00           0,01      0,00         0,01",
        "                   Maggior presenza         16:21       16:22",
        "02   VE   G118     Timbrature               08:13       16:23             7,36         0,00           0,02      0,00         0,02",
        "                   Maggior presenza         16:21       16:23",
        "03   SA   RC",
        "04   DO   RS       San Francesco d'Assisi",
        "05   LU   G118     Timbrature               08:18       16:25             7,36         0,00           0,01      0,00         0,01",
        "                   Maggior presenza         16:24       16:25",
        "06   MA   G118     Timbrature               08:09       16:21             7,36         0,00           0,00      0,00         0,00",
        "07   ME   G118     Timbrature               08:05       16:23             7,36         0,00           0,02      0,00         0,02",
        "                   Maggior presenza         16:21       16:23",
        "08   GI   G118     Timbrature               08:10       16:28             7,36         0,00           0,07      0,00         0,07",
        "                   Maggior presenza         16:21       16:28",
        "09   VE   G118     Timbrature               08:11       16:51             7,36         0,00           0,30      0,00         0,30",
        "                   Maggior presenza         16:21       16:51",
        "10   SA   RC",
        "11   DO   RS",
        "12   LU   G118     Timbrature               08:10       16:28             7,36         0,00           0,07      0,00         0,07",
        "                   Maggior presenza         16:21       16:28",
        "13   MA   G118     Timbrature               08:16       16:29             7,36         0,00           0,07      0,00         0,07",
        "                   Maggior presenza         16:22       16:29",
        "14   ME   G118     Timbrature               08:16       16:31             7,36         0,00           0,09      0,00         0,09",
        "                   Maggior presenza         16:22       16:31",
        "15   GI   G118     Timbrature               08:41       16:50             7,36         0,00           0,03      0,00         0,03",
        "                   Maggior presenza         16:47       16:50",
        "16   VE   G118     Timbrature               08:24       17:10             7,36         0,00           0,40      0,00         0,40",
        "                   Maggior presenza         16:30       17:10",
        "17   SA   RC",
        "                                                                  12.11.2026 Pagina 2/3",
        "Giorno                              Da      Fino a   Lavoro Giustific Extra      Ore      Flex",
        "                                                     Ordinari Assenza orario     Autorizz",
        "                                                     o                           ate",
        "18   DO   RS",
        "19   LU   G118   Timbrature         08:21   16:31       7,36     0,00     0,04      0,00         0,04",
        "                 Maggior presenza   16:27   16:31",
        "20   MA   G118   Timbrature         08:16   16:22       7,36     0,00     0,00      0,00         0,00",
        "21   ME   G118   Timbrature         08:16   16:23       7,36     0,00     0,01      0,00         0,01",
        "                 Maggior presenza   16:22   16:23",
        "22   GI   G118   Timbrature         08:17   16:30       7,36     0,00     0,07      0,00         0,07",
        "                 Maggior presenza   16:23   16:30",
        "23   VE   G118   Timbrature         08:12   18:07       7,36     0,00     1,46      0,00         1,46",
        "                 Maggior presenza   16:21   18:07",
        "24   SA   RC",
        "25   DO   RS",
        "26   LU   G118   Timbrature         08:06   16:21       7,36     0,00     0,00      0,00         0,00",
        "27   MA   G118   Timbrature         08:09   16:31       7,36     0,00     0,10      0,00         0,10",
        "                 Maggior presenza   16:21   16:31",
        "28   ME   G118   Timbrature         08:03   16:26       7,36     0,00     0,05      0,00         0,05",
        "                 Maggior presenza   16:21   16:26",
        "29   GI   G118   Timbrature         08:19   16:32       7,36     0,00     0,07      0,00         0,07",
        "                 Maggior presenza   16:25   16:32",
        "30   VE   G118   Timbrature         08:09   16:30       7,36     0,00     0,09      0,00         0,09",
        "                 Maggior presenza   16:21   16:30",
        "31   SA   RC",
        "                                                                                   12.11.2026 Pagina 3/3",
        "Riepilogo per il periodo dal 01.10.2026 al 31.10.2026",
        "alla scadenza 31.10.2026",
        "Assenze contrattuali",
        "Monte ore                      Riducibile da   Riducibile fino a Diritto           Consumo           Residuo",
        "Ferie AC                       01.01.2026      31.12.2026                  15,00             4,00               11,00",
        "Ferie AP                       01.01.2026      31.12.2026                  7,00              7,00               0,00",
        "Ferie 10 gg                    01.01.2026      31.12.2026                  10,00             10,00              0,00",
        "Ex festività                   01.01.2026      31.12.2026                  4,00              0,00               4,00",
        "Permessi ad ore                01.01.2026      31.12.2026                  22,80             11,36              11,44",
        "Festività in riposo            04.10.2026      02.01.2027                  1,00              0,00               1,00",
        "Flessibilià Mese Precedente:                                                                             5,00",
        "Per richieste di chiarimento rivolgersi al proprio resp. gestione tempi."
    )

    @Test fun cartellinoRealeDiOttobre() {
        val c = LettoreCartellino.analizza(ottobre)!!
        assertEquals(2026, c.anno)
        assertEquals(10, c.mese)
        assertEquals(31, c.giornate.size)
        assertEquals(5.0, c.flexMesePrecedente!!, 0.0001)
    }

    @Test fun cartellinoRealeOrariEFlex() {
        val c = LettoreCartellino.analizza(ottobre)!!
        val nove = c.giornate.first { it.giorno == 9 }
        assertEquals("VE", nove.sigla)
        assertEquals("08:11", nove.prima)
        assertEquals("16:51", nove.ultima)
        assertEquals(0.30, nove.flex!!, 0.0001)
        assertEquals(1.46, c.giornate.first { it.giorno == 23 }.flex!!, 0.0001)
        assertNull(c.giornate.first { it.giorno == 3 }.flex)
    }
}
