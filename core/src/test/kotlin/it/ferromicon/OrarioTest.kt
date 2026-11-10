package it.ferromicon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Lettura di un orario GTFS di prova, con le particolarità del formato. */
class OrarioTest {

    private fun gtfsDiProva(): File {
        val file = File.createTempFile("gtfs", ".zip")
        file.deleteOnExit()
        val tabelle = mapOf(
            // BOM iniziale, banchina con stazione madre, nome tra virgolette con virgola
            "stops.txt" to "﻿stop_id,stop_name,parent_station\n" +
                "S1,Milano Cadorna,\nS1B,Milano Cadorna binario 3,S1\nS2,Saronno,\nS3,\"Como, Lago\",\n",
            "routes.txt" to "route_id,route_short_name,route_long_name\nR1,R16,Milano - Como\n",
            "trips.txt" to "route_id,service_id,trip_id,trip_headsign,trip_short_name\n" +
                "R1,FER,T1,Como Lago,2601\nR1,FER,T2,Milano Cadorna,2602\nR1,NOTTE,T3,,2699\n",
            // stop_sequence fuori ordine e orari oltre le 24:00
            "stop_times.txt" to "trip_id,arrival_time,departure_time,stop_id,stop_sequence\n" +
                "T1,06:30:00,06:31:00,S2,2\nT1,06:00:00,06:00:00,S1B,1\nT1,07:05:00,07:05:00,S3,3\n" +
                "T2,08:00:00,08:00:00,S3,1\nT2,09:00:00,09:00:00,S1,2\n" +
                "T3,23:50:00,23:50:00,S1,1\nT3,24:35:00,24:35:00,S3,2\n",
            "calendar.txt" to "service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday,start_date,end_date\n" +
                "FER,1,1,1,1,1,0,0,20260726,20261212\nNOTTE,0,0,0,0,0,1,1,20260726,20261212\n",
            // 11/11 tolto ai feriali, sabato 14/11 aggiunto
            "calendar_dates.txt" to "service_id,date,exception_type\nFER,20261111,2\nFER,20261114,1\n"
        )
        ZipOutputStream(file.outputStream()).use { z ->
            tabelle.forEach { (nome, testo) -> z.putNextEntry(ZipEntry("gtfs/$nome")); z.write(testo.toByteArray()); z.closeEntry() }
        }
        return file
    }

    private val orario by lazy { Gtfs.leggiZip(gtfsDiProva(), "Regione Lombardia - Altarail (prova)") }

    @Test fun leggeCorseFermateEValidita() {
        assertEquals(3, orario.numeroCorse)
        assertEquals(LocalDate.of(2026, 7, 26), orario.validoDal)
        assertEquals(LocalDate.of(2026, 12, 12), orario.validoAl)
        val corse = orario.cerca("2601", LocalDate.of(2026, 11, 10))   // martedì
        assertEquals(1, corse.size)
        val c = corse.single()
        assertEquals(listOf("Milano Cadorna", "Saronno", "Como, Lago"), c.fermate.map { it.stazione })
        assertEquals("R16", c.linea)
        assertEquals(LocalDateTime.parse("2026-11-10T06:31"), c.orario(c.fermate[1].partenza))
    }

    @Test fun rispettaIlCalendario() {
        assertEquals(0, orario.cerca("2601", LocalDate.of(2026, 11, 11)).size)    // tolto
        assertEquals(1, orario.cerca("2601", LocalDate.of(2026, 11, 14)).size)    // sabato aggiunto
        assertEquals(0, orario.cerca("2601", LocalDate.of(2026, 11, 15)).size)    // domenica
        assertEquals(0, orario.cerca("2601", LocalDate.of(2026, 12, 20)).size)    // fuori validità
        assertEquals(1, orario.cerca("02601", LocalDate.of(2026, 11, 10)).size)   // zeri iniziali
        assertTrue(orario.esiste("2602"))
    }

    @Test fun orariOltreMezzanotteERigaDelServizio() {
        val c = orario.cerca("2699", LocalDate.of(2026, 11, 14)).single()
        assertEquals("Como, Lago", c.destinazione)                           // headsign vuoto: capolinea
        val riga = c.inRiga(0, 1, TipoRiga.CONDOTTA)
        assertEquals(LocalDateTime.parse("2026-11-14T23:50"), riga.partenza)
        assertEquals(LocalDateTime.parse("2026-11-15T00:35"), riga.arrivo)
        assertEquals("2699", riga.numero)
        assertEquals("Milano Cadorna", riga.da)
    }

    @Test fun indiceCompattoEquivalente() {
        val riletto = Orario.daIndice(orario.aIndice())
        assertEquals(orario.numeroCorse, riletto.numeroCorse)
        assertEquals(orario.stazioni, riletto.stazioni)
        listOf(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 11), LocalDate.of(2026, 11, 14)).forEach { g ->
            listOf("2601", "2602", "2699").forEach { n -> assertEquals(orario.cerca(n, g), riletto.cerca(n, g)) }
        }
        assertEquals(orario.fonte, riletto.fonte)
    }

    @Test fun fileNonGtfs() {
        val f = File.createTempFile("non", ".zip"); f.deleteOnExit()
        ZipOutputStream(f.outputStream()).use { it.putNextEntry(ZipEntry("x.txt")); it.write("x".toByteArray()); it.closeEntry() }
        val errore = runCatching { Gtfs.leggiZip(f, "x") }.exceptionOrNull()
        assertTrue(errore is Gtfs.FormatoNonValido)
    }
}
