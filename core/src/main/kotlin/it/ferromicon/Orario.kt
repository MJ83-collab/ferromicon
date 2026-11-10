package it.ferromicon

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.io.StringReader
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/*
 * Orario ufficiale Altarail in formato GTFS, pubblicato da Regione Lombardia sul portale
 * Open Data (dati.lombardia.it). Il file non è incluso nell'app: lo importa l'utente.
 *
 * Dallo zip GTFS si costruisce un indice compatto in testo, salvato nell'app, con:
 *   - le fermate (nome),
 *   - i calendari (giorni della settimana, validità, eccezioni),
 *   - per ogni corsa: numero treno, linea, destinazione, calendario e fermate con orari.
 */

/** Una fermata di una corsa: orari in minuti dalla mezzanotte del giorno di servizio (possono superare 1440). */
data class FermataCorsa(val stazione: String, val arrivo: Int, val partenza: Int)

/** Una corsa (treno) in un giorno preciso, con le fermate in ordine. */
data class Corsa(
    val numero: String,
    val linea: String,
    val destinazione: String,
    val giorno: LocalDate,
    val fermate: List<FermataCorsa>
) {
    fun orario(minuti: Int): LocalDateTime = giorno.atStartOfDay().plusMinutes(minuti.toLong())
    val origine get() = fermate.first().stazione
    val capolinea get() = fermate.last().stazione
}

/** Calendario GTFS: giorni della settimana attivi tra due date, più le eccezioni. */
data class CalendarioServizio(
    val giorni: Set<DayOfWeek> = emptySet(),
    val dal: LocalDate? = null,
    val al: LocalDate? = null,
    val aggiunte: Set<LocalDate> = emptySet(),
    val tolte: Set<LocalDate> = emptySet()
) {
    fun attivo(d: LocalDate): Boolean {
        if (d in tolte) return false
        if (d in aggiunte) return true
        return dal != null && al != null && !d.isBefore(dal) && !d.isAfter(al) && d.dayOfWeek in giorni
    }
}

/** Orario pronto per le ricerche, letto dall'indice compatto. */
class Orario(
    val fonte: String,
    val validoDal: LocalDate?,
    val validoAl: LocalDate?,
    val stazioni: List<String>,
    private val calendari: Map<String, CalendarioServizio>,
    private val corse: List<CorsaIndice>
) {
    /** Corsa nell'indice: le fermate puntano all'elenco delle stazioni. */
    class CorsaIndice(
        val numero: String, val linea: String, val destinazione: String, val servizio: String,
        val fermate: List<Triple<Int, Int, Int>>
    )

    val numeroCorse get() = corse.size

    /** Corse con quel numero di treno attive nel giorno indicato. */
    fun cerca(numero: String, giorno: LocalDate): List<Corsa> {
        val n = numero.trim().trimStart('0')
        if (n.isEmpty()) return emptyList()
        return corse.filter { it.numero.trimStart('0') == n }
            .filter { calendari[it.servizio]?.attivo(giorno) ?: true }
            .map { c ->
                Corsa(c.numero, c.linea, c.destinazione, giorno,
                    c.fermate.map { (s, a, p) -> FermataCorsa(stazioni[s], a, p) })
            }
    }

    /** Numeri di treno presenti, per sapere se l'orario contiene un treno in qualche giorno. */
    fun esiste(numero: String) = corse.any { it.numero.trimStart('0') == numero.trim().trimStart('0') }

    // ---------- indice compatto ----------

    /**
     * Formato testo, una voce per riga:
     *   F|fonte|validoDal|validoAl
     *   S|nome stazione                          (l'ordine dà l'indice)
     *   C|servizio|giorni(1234567)|dal|al|aggiunte(aaaammgg,...)|tolte
     *   T|numero|linea|destinazione|servizio|stazione,arrivo,partenza;...
     */
    fun aIndice(): String = buildString {
        appendLine(listOf("F", pulisci(fonte), validoDal?.toString() ?: "", validoAl?.toString() ?: "").joinToString("|"))
        stazioni.forEach { appendLine("S|" + pulisci(it)) }
        calendari.forEach { (id, c) ->
            appendLine(listOf("C", pulisci(id), c.giorni.map { it.value }.sorted().joinToString(""),
                c.dal?.toString() ?: "", c.al?.toString() ?: "",
                c.aggiunte.sorted().joinToString(",") { it.format(BASE) },
                c.tolte.sorted().joinToString(",") { it.format(BASE) }).joinToString("|"))
        }
        corse.forEach { c ->
            appendLine(listOf("T", pulisci(c.numero), pulisci(c.linea), pulisci(c.destinazione), pulisci(c.servizio),
                c.fermate.joinToString(";") { "${it.first},${it.second},${it.third}" }).joinToString("|"))
        }
    }

    companion object {
        private val BASE: DateTimeFormatter = DateTimeFormatter.BASIC_ISO_DATE
        private fun pulisci(s: String) = s.replace('|', '/').replace('\n', ' ')

        fun daIndice(reader: Reader): Orario {
            var fonte = ""; var dal: LocalDate? = null; var al: LocalDate? = null
            val stazioni = mutableListOf<String>()
            val calendari = mutableMapOf<String, CalendarioServizio>()
            val corse = mutableListOf<CorsaIndice>()
            BufferedReader(reader).forEachLine { riga ->
                val p = riga.split('|')
                when (p[0]) {
                    "F" -> { fonte = p[1]; dal = p[2].takeIf { it.isNotEmpty() }?.let(LocalDate::parse)
                             al = p[3].takeIf { it.isNotEmpty() }?.let(LocalDate::parse) }
                    "S" -> stazioni += p[1]
                    "C" -> calendari[p[1]] = CalendarioServizio(
                        giorni = p[2].map { DayOfWeek.of(it.digitToInt()) }.toSet(),
                        dal = p[3].takeIf { it.isNotEmpty() }?.let(LocalDate::parse),
                        al = p[4].takeIf { it.isNotEmpty() }?.let(LocalDate::parse),
                        aggiunte = p[5].split(',').filter { it.isNotEmpty() }.map { LocalDate.parse(it, BASE) }.toSet(),
                        tolte = p[6].split(',').filter { it.isNotEmpty() }.map { LocalDate.parse(it, BASE) }.toSet()
                    )
                    "T" -> corse += CorsaIndice(p[1], p[2], p[3], p[4],
                        p[5].split(';').filter { it.isNotEmpty() }.map {
                            val (s, a, d) = it.split(',').map(String::toInt); Triple(s, a, d)
                        })
                }
            }
            return Orario(fonte, dal, al, stazioni, calendari, corse)
        }

        fun daIndice(testo: String) = daIndice(StringReader(testo))
    }
}

/** Lettura di uno zip GTFS e costruzione dell'orario. */
object Gtfs {

    class FormatoNonValido(messaggio: String) : Exception(messaggio)

    /**
     * Legge lo zip GTFS da file (accesso diretto alle voci, così stop_times.txt, il più grande,
     * si legge in streaming dopo le altre tabelle). Servono stops.txt, trips.txt e stop_times.txt.
     */
    fun leggiZip(file: java.io.File, fonte: String): Orario = java.util.zip.ZipFile(file).use { zip ->
        val voci = zip.entries().toList().associateBy { it.name.substringAfterLast('/') }
        listOf("stops.txt", "trips.txt", "stop_times.txt").forEach {
            if (it !in voci) throw FormatoNonValido("Nel file manca $it: non sembra un orario GTFS.")
        }
        fun tabella(nome: String) = voci[nome]?.let { Csv(zip.getInputStream(it)) }
        costruisci(
            tabella("stops.txt")!!, tabella("trips.txt")!!, { tabella("stop_times.txt")!! },
            tabella("routes.txt"), tabella("calendar.txt"), tabella("calendar_dates.txt"), tabella("feed_info.txt"), fonte
        )
    }

    fun costruisci(
        stops: Csv, trips: Csv, stopTimes: () -> Csv,
        routes: Csv?, calendar: Csv?, calendarDates: Csv?, feedInfo: Csv?, fonte: String
    ): Orario {
        // Fermate: le banchine con una stazione "madre" prendono il nome della stazione
        val nomeFermata = mutableMapOf<String, String>()
        val madre = mutableMapOf<String, String>()
        stops.righe { r ->
            nomeFermata[r["stop_id"]] = r["stop_name"].trim()
            r["parent_station"].takeIf { it.isNotBlank() }?.let { madre[r["stop_id"]] = it }
        }
        fun nome(id: String) = madre[id]?.let { nomeFermata[it] } ?: nomeFermata[id] ?: id

        val linee = mutableMapOf<String, String>()
        routes?.righe { r -> linee[r["route_id"]] = r["route_short_name"].ifBlank { r["route_long_name"] } }

        class Viaggio(val numero: String, val linea: String, val destinazione: String, val servizio: String)
        val viaggi = mutableMapOf<String, Viaggio>()
        trips.righe { r ->
            val id = r["trip_id"]
            // Il numero del treno è in trip_short_name; in mancanza si prova l'ultimo numero nel trip_id
            val numero = r["trip_short_name"].ifBlank { Regex("""\d{3,6}""").findAll(id).lastOrNull()?.value ?: id }
            viaggi[id] = Viaggio(numero.trim(), linee[r["route_id"]] ?: "", r["trip_headsign"].trim(), r["service_id"])
        }

        // Fermate di ogni viaggio in forma compatta: sequenza | stazione | arrivo | partenza in un Long
        val stazioni = mutableListOf<String>()
        val indiceStazione = mutableMapOf<String, Int>()
        fun idx(n: String) = indiceStazione.getOrPut(n) { stazioni += n; stazioni.size - 1 }
        val fermate = mutableMapOf<String, MutableList<Long>>()
        stopTimes().righe { r ->
            val id = r["trip_id"]
            if (id !in viaggi) return@righe
            val arrivo = minuti(r["arrival_time"]) ?: minuti(r["departure_time"]) ?: return@righe
            val partenza = minuti(r["departure_time"]) ?: arrivo
            val seq = (r["stop_sequence"].toIntOrNull() ?: 0).coerceIn(0, 0x7FFF).toLong()
            val stazione = idx(nome(r["stop_id"])).toLong()
            fermate.getOrPut(id) { mutableListOf() } +=
                (seq shl 48) or (stazione shl 28) or (arrivo.toLong().coerceIn(0, 0x3FFF) shl 14) or
                    partenza.toLong().coerceIn(0, 0x3FFF)
        }

        val corse = fermate.mapNotNull { (id, lista) ->
            if (lista.size < 2) return@mapNotNull null
            val v = viaggi.getValue(id)
            val ordinate = lista.sorted().map { x ->
                Triple(((x shr 28) and 0xFFFFF).toInt(), ((x shr 14) and 0x3FFF).toInt(), (x and 0x3FFF).toInt())
            }
            Orario.CorsaIndice(v.numero, v.linea, v.destinazione.ifBlank { stazioni[ordinate.last().first] }, v.servizio,
                ordinate)
        }.sortedBy { it.numero }

        // Calendari
        val calendari = mutableMapOf<String, CalendarioServizio>()
        val fmt = DateTimeFormatter.BASIC_ISO_DATE
        calendar?.righe { r ->
            val giorni = listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")
                .mapIndexedNotNull { i, g -> if (r[g] == "1") DayOfWeek.of(i + 1) else null }.toSet()
            calendari[r["service_id"]] = CalendarioServizio(giorni,
                runCatching { LocalDate.parse(r["start_date"], fmt) }.getOrNull(),
                runCatching { LocalDate.parse(r["end_date"], fmt) }.getOrNull())
        }
        calendarDates?.righe { r ->
            val d = runCatching { LocalDate.parse(r["date"], fmt) }.getOrNull() ?: return@righe
            val c = calendari[r["service_id"]] ?: CalendarioServizio()
            calendari[r["service_id"]] = if (r["exception_type"] == "2") c.copy(tolte = c.tolte + d)
                else c.copy(aggiunte = c.aggiunte + d)
        }

        // Validità del feed: da feed_info, altrimenti dai calendari
        var dal: LocalDate? = null
        var al: LocalDate? = null
        feedInfo?.righe { r ->
            dal = runCatching { LocalDate.parse(r["feed_start_date"], fmt) }.getOrNull()
            al = runCatching { LocalDate.parse(r["feed_end_date"], fmt) }.getOrNull()
        }
        if (dal == null) dal = (calendari.values.mapNotNull { it.dal } + calendari.values.flatMap { it.aggiunte }).minOrNull()
        if (al == null) al = (calendari.values.mapNotNull { it.al } + calendari.values.flatMap { it.aggiunte }).maxOrNull()

        return Orario(fonte, dal, al, stazioni, calendari, corse)
    }

    /** "HH:MM:SS" (anche oltre 24:00) in minuti dalla mezzanotte del giorno di servizio. */
    fun minuti(t: String): Int? {
        val p = t.trim().split(':')
        if (p.size < 2) return null
        val h = p[0].toIntOrNull() ?: return null
        val m = p[1].toIntOrNull() ?: return null
        return h * 60 + m
    }

    /** Lettore CSV semplice per GTFS: intestazione, virgolette, BOM iniziale. */
    class Csv(input: InputStream) {
        private val reader = BufferedReader(InputStreamReader(input, Charsets.UTF_8))

        class Riga(private val colonne: Map<String, Int>, private val valori: List<String>) {
            operator fun get(nome: String): String = colonne[nome]?.let { valori.getOrNull(it) } ?: ""
        }

        fun righe(azione: (Riga) -> Unit) {
            val intestazione = reader.readLine()?.removePrefix("﻿") ?: return
            val colonne = campi(intestazione).mapIndexed { i, n -> n.trim() to i }.toMap()
            while (true) {
                val riga = reader.readLine() ?: break
                if (riga.isBlank()) continue
                azione(Riga(colonne, campi(riga)))
            }
        }

        private fun campi(riga: String): List<String> {
            val out = mutableListOf<String>()
            val sb = StringBuilder()
            var virgolette = false
            var i = 0
            while (i < riga.length) {
                val c = riga[i]
                when {
                    virgolette && c == '"' && i + 1 < riga.length && riga[i + 1] == '"' -> { sb.append('"'); i++ }
                    c == '"' -> virgolette = !virgolette
                    c == ',' && !virgolette -> { out += sb.toString(); sb.clear() }
                    else -> sb.append(c)
                }
                i++
            }
            out += sb.toString()
            return out
        }
    }
}

/** Righe del servizio ricavate da una corsa dell'orario, tra la stazione di salita e quella di discesa. */
fun Corsa.inRiga(daIndice: Int, aIndice: Int, tipo: TipoRiga): RigaTreno {
    val salita = fermate[daIndice]
    val discesa = fermate[aIndice]
    return RigaTreno(
        numero = numero,
        da = salita.stazione,
        a = discesa.stazione,
        partenza = orario(salita.partenza),
        arrivo = orario(discesa.arrivo),
        tipo = tipo
    )
}
