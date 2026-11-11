package it.ferromicon

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Cosa includere in un'esportazione o in una sincronizzazione, e per quale periodo. */
data class OpzioniCalendario(
    val servizi: Boolean = true,
    val riposi: Boolean = true,
    /** Ferie e permessi. */
    val ferie: Boolean = true,
    /** Disponibilità, malattia e altre assenze. */
    val altre: Boolean = true,
    val da: LocalDate? = null,
    val a: LocalDate? = null
) {
    fun accetta(t: TipoAssenza) = when (t) {
        TipoAssenza.FERIE, TipoAssenza.PERMESSO, TipoAssenza.PERMESSO_EX_FESTIVITA -> ferie
        else -> if (t.riposo) riposi else altre
    }

    fun nelPeriodo(d: LocalDate) = (da == null || !d.isBefore(da)) && (a == null || !d.isAfter(a))

    /** Solo ciò che le opzioni ammettono. */
    fun applica(dati: Dati): Dati = dati.copy(
        servizi = if (servizi) dati.servizi.filter { nelPeriodo(it.turno.inizio.toLocalDate()) } else emptyList(),
        assenze = dati.assenze.filter { accetta(it.tipo) && nelPeriodo(it.data) }
    )
}

/**
 * Esportazione in formato iCalendar (.ics), importabile in Google Calendar,
 * Calendario di Samsung, Outlook e simili. Gli orari sono nel fuso Europe/Rome.
 */
object CalendarioIcs {

    private val FMT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val FMT_DATA = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val FMT_ORA = DateTimeFormatter.ofPattern("HH:mm")

    /** Fuso orario Europe/Rome con le regole dell'ora legale europea. */
    private val VTIMEZONE = listOf(
        "BEGIN:VTIMEZONE", "TZID:Europe/Rome",
        "BEGIN:DAYLIGHT", "TZOFFSETFROM:+0100", "TZOFFSETTO:+0200", "TZNAME:CEST",
        "DTSTART:19700329T020000", "RRULE:FREQ=YEARLY;BYMONTH=3;BYDAY=-1SU", "END:DAYLIGHT",
        "BEGIN:STANDARD", "TZOFFSETFROM:+0200", "TZOFFSETTO:+0100", "TZNAME:CET",
        "DTSTART:19701025T030000", "RRULE:FREQ=YEARLY;BYMONTH=10;BYDAY=-1SU", "END:STANDARD",
        "END:VTIMEZONE"
    )

    fun genera(
        completi: Dati,
        adesso: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC),
        opzioni: OpzioniCalendario = OpzioniCalendario()
    ): String {
        val dati = opzioni.applica(completi)
        val stamp = FMT.format(adesso) + "Z"
        val righe = mutableListOf("BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//Ferromicon//IT", "CALSCALE:GREGORIAN")
        righe += VTIMEZONE

        dati.servizi.sortedBy { it.turno.inizio }.forEach { s ->
            val t = s.turno
            val durata = Duration.between(t.inizio, t.fine).toMinutes()
            val dettagli = buildList {
                add("Durata ${durata / 60} h ${durata % 60}'")
                if (t.minutiCondotta > 0) add(
                    (if (t.profilo == Profilo.MACCHINISTA) "Condotta" else "Scorta") +
                        " ${t.minutiCondotta / 60} h ${t.minutiCondotta % 60}'")
                if (RegoleAltarail.èNotturno(t)) add("Notturno")
                if (s.rfrDopo) add("Riposo fuori residenza dopo il servizio")
                if (s.riserva) add("Riserva o formazione")
                if (s.note.isNotBlank()) add(s.note)
            }
            righe += listOf(
                "BEGIN:VEVENT",
                "UID:servizio-${s.id}@ferromicon",
                "DTSTAMP:$stamp",
                "DTSTART;TZID=Europe/Rome:${FMT.format(t.inizio)}",
                "DTEND;TZID=Europe/Rome:${FMT.format(t.fine)}",
                "SUMMARY:" + testo("Servizio ${FMT_ORA.format(t.inizio)}–${FMT_ORA.format(t.fine)}" +
                    if (s.note.isNotBlank()) " ${s.note}" else ""),
                "DESCRIPTION:" + testo(dettagli.joinToString("\n")),
                "X-FM-PROFILO:${t.profilo.name}",
                "X-FM-CONDOTTA:${t.minutiCondotta}",
                "X-FM-RFR:${if (s.rfrDopo) 1 else 0}",
                "X-FM-RISERVA:${if (s.riserva) 1 else 0}",
                "X-FM-NOTE:" + testo(s.note),
                "END:VEVENT"
            )
        }
        dati.assenze.sortedBy { it.data }.forEach { a ->
            righe += listOf(
                "BEGIN:VEVENT",
                "UID:assenza-${a.id}@ferromicon",
                "DTSTAMP:$stamp",
                "DTSTART;VALUE=DATE:${FMT_DATA.format(a.data)}",
                "DTEND;VALUE=DATE:${FMT_DATA.format(a.data.plusDays(1))}",
                "SUMMARY:" + testo(a.tipo.etichetta),
                "X-FM-TIPO:${a.tipo.name}",
                "X-FM-NOTE:" + testo(a.note),
                "TRANSP:TRANSPARENT",
                "END:VEVENT"
            )
        }
        righe += "END:VCALENDAR"
        return righe.flatMap { piega(it) }.joinToString("\r\n", postfix = "\r\n")
    }

    /**
     * Rilegge un file prodotto da Ferromicon (servizi e assenze). Gli eventi estranei sono ignorati.
     * Gli identificativi sono rinumerati a partire da [primoId] per non scontrarsi con quelli esistenti.
     */
    fun leggi(contenuto: String, primoId: Long = 1): Dati {
        val righe = mutableListOf<String>()
        contenuto.split("\r\n", "\n").forEach { r ->
            if ((r.startsWith(" ") || r.startsWith("\t")) && righe.isNotEmpty()) righe[righe.size - 1] += r.substring(1)
            else righe += r
        }
        val servizi = mutableListOf<Servizio>()
        val assenze = mutableListOf<Assenza>()
        var evento: MutableMap<String, String>? = null
        var id = primoId
        for (r in righe) {
            if (r == "BEGIN:VEVENT") { evento = mutableMapOf(); continue }
            if (r == "END:VEVENT") {
                val e = evento; evento = null
                if (e != null) runCatching {
                    val uid = e["UID"] ?: return@runCatching
                    if (uid.startsWith("servizio-")) {
                        val inizio = LocalDateTime.parse(e["DTSTART"]!!, FMT)
                        val fine = LocalDateTime.parse(e["DTEND"]!!, FMT)
                        val profilo = e["X-FM-PROFILO"]?.let { n -> Profilo.values().firstOrNull { it.name == n } }
                            ?: Profilo.MACCHINISTA
                        servizi += Servizio(id++, Turno(inizio, fine, profilo, e["X-FM-CONDOTTA"]?.toIntOrNull() ?: 0),
                            rfrDopo = e["X-FM-RFR"] == "1", note = disfa(e["X-FM-NOTE"] ?: ""),
                            riserva = e["X-FM-RISERVA"] == "1")
                    } else if (uid.startsWith("assenza-")) {
                        val tipo = TipoAssenza.values().firstOrNull { it.name == e["X-FM-TIPO"] }
                            ?: TipoAssenza.values().firstOrNull { it.etichetta == disfa(e["SUMMARY"] ?: "") }
                            ?: TipoAssenza.ALTRO
                        val inizio = LocalDate.parse(e["DTSTART"]!!, FMT_DATA)
                        val fine = e["DTEND"]?.let { LocalDate.parse(it, FMT_DATA) } ?: inizio.plusDays(1)
                        var g = inizio
                        while (g.isBefore(fine)) { assenze += Assenza(id++, g, tipo, disfa(e["X-FM-NOTE"] ?: "")); g = g.plusDays(1) }
                    }
                }
                continue
            }
            val e = evento ?: continue
            val due = r.indexOf(':')
            if (due < 0) continue
            val nome = r.substring(0, due).substringBefore(';')
            if (nome !in e) e[nome] = r.substring(due + 1)
        }
        return Dati(servizi, assenze)
    }

    /** Inverso di [testo]. */
    private fun disfa(s: String): String {
        val b = StringBuilder()
        var i = 0
        while (i < s.length) {
            if (s[i] == '\\' && i + 1 < s.length) {
                b.append(if (s[i + 1] == 'n' || s[i + 1] == 'N') '\n' else s[i + 1]); i += 2
            } else { b.append(s[i]); i++ }
        }
        return b.toString()
    }

    /** Testo con i caratteri speciali protetti come richiede iCalendar. */
    private fun testo(s: String) = s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\n", "\\n")

    /** Spezza le righe oltre 75 caratteri (RFC 5545). */
    private fun piega(riga: String): List<String> {
        if (riga.length <= 75) return listOf(riga)
        val parti = mutableListOf(riga.substring(0, 75))
        var i = 75
        while (i < riga.length) {
            parti += " " + riga.substring(i, minOf(i + 74, riga.length))
            i += 74
        }
        return parti
    }
}
