package it.ferromicon

import java.time.LocalDate
import java.time.LocalTime

/**
 * Importazione del turno da testo incollato (dal portale, da un messaggio, da un foglio).
 *
 * Ogni riga con una data e due orari diventa un servizio, per esempio:
 *   "lun 10/11 06:12-14:40 cond 4:10 treni 2611 2618"
 *   "2026-11-11 22:00 04:30 RFR"
 * Una riga con una data e una sigla diventa una giornata senza servizio:
 *   "12/11 RS", "13/11 R", "14/11 ferie", "15/11 malattia", "16/11 D"
 * Le righe non riconosciute vengono restituite per mostrarle all'utente.
 */
object Importa {

    data class Risultato(
        val servizi: List<Servizio>,
        val assenze: List<Assenza>,
        val nonRiconosciute: List<String>
    )

    private val DATA_ISO = Regex("""\b(\d{4})-(\d{1,2})-(\d{1,2})\b""")
    private val DATA_IT = Regex("""\b(\d{1,2})[/-](\d{1,2})(?:[/-](\d{2,4}))?\b""")
    private val ORA = Regex("""\b([01]?\d|2[0-3])[:.]([0-5]\d)\b""")
    private val CONDOTTA = Regex("""\b(?:cond(?:otta)?|scorta)\s*[:=]?\s*(\d{1,2})[:.]([0-5]\d)\b""", RegexOption.IGNORE_CASE)
    private val CONDOTTA_MINUTI = Regex("""\b(?:cond(?:otta)?|scorta)\s*[:=]?\s*(\d{2,3})\b(?![:.]\d)""", RegexOption.IGNORE_CASE)

    private val GIORNO_SETTIMANA =
        Regex("""^\s*(lun|mar|mer|gio|ven|sab|dom)[\p{L}]*\.?(?=\s|$)""", RegexOption.IGNORE_CASE)

    /** Sigle e parole per le giornate senza servizio, in ordine di priorità. */
    private val SIGLE: List<Pair<Regex, TipoAssenza>> = listOf(
        Regex("""\bRS\b|riposo\s+settimanale""", RegexOption.IGNORE_CASE) to TipoAssenza.RIPOSO_SETTIMANALE,
        Regex("""\bfe\b|\bferie\b""", RegexOption.IGNORE_CASE) to TipoAssenza.FERIE,
        Regex("""\bmal\b|malattia""", RegexOption.IGNORE_CASE) to TipoAssenza.MALATTIA,
        Regex("""\bex\s*fest""", RegexOption.IGNORE_CASE) to TipoAssenza.PERMESSO_EX_FESTIVITA,
        Regex("""\bperm(?:esso)?\b|\b104\b""", RegexOption.IGNORE_CASE) to TipoAssenza.PERMESSO,
        Regex("""\bdisp(?:onibilit[aà])?\b|\bD\b""") to TipoAssenza.DISPONIBILITA,
        Regex("""\brecupero\b|\bRF\b""", RegexOption.IGNORE_CASE) to TipoAssenza.RECUPERO_FESTIVO,
        Regex("""\bGRS\b|\bR\b|\briposo\b""", RegexOption.IGNORE_CASE) to TipoAssenza.RIPOSO,
        Regex("""\bCP\b|congedo""", RegexOption.IGNORE_CASE) to TipoAssenza.ALTRO
    )

    fun analizza(testo: String, anno: Int, profilo: Profilo, idIniziale: Long): Risultato {
        val servizi = mutableListOf<Servizio>()
        val assenze = mutableListOf<Assenza>()
        val nonRiconosciute = mutableListOf<String>()
        var id = idIniziale

        testo.lines().map { it.trim() }.filter { it.isNotEmpty() }.forEach { riga ->
            val (data, resto) = trovaData(riga, anno) ?: run { nonRiconosciute += riga; return@forEach }

            // Condotta o scorta esplicita ("cond 4:10" o "cond 250"), tolta prima di cercare gli orari
            var testoOrari = resto
            var condotta = 0
            CONDOTTA.find(testoOrari)?.let {
                condotta = it.groupValues[1].toInt() * 60 + it.groupValues[2].toInt()
                testoOrari = testoOrari.removeRange(it.range)
            } ?: CONDOTTA_MINUTI.find(testoOrari)?.let {
                condotta = it.groupValues[1].toInt()
                testoOrari = testoOrari.removeRange(it.range)
            }

            val orari = ORA.findAll(testoOrari).take(2).toList()
            if (orari.size == 2) {
                val inizio = data.atTime(LocalTime.of(orari[0].groupValues[1].toInt(), orari[0].groupValues[2].toInt()))
                var fine = data.atTime(LocalTime.of(orari[1].groupValues[1].toInt(), orari[1].groupValues[2].toInt()))
                if (!fine.isAfter(inizio)) fine = fine.plusDays(1)
                val note = testoOrari.removeRange(orari[0].range.first, orari[1].range.last + 1)
                    .replace(Regex("""\bRFR\b""", RegexOption.IGNORE_CASE), "")
                    .replace(GIORNO_SETTIMANA, "")
                    .replace(Regex("""\s+"""), " ").trim(' ', '-', '–', ',', ';')
                servizi += Servizio(
                    id = id++,
                    turno = Turno(inizio, fine, profilo, minutiCondotta = condotta),
                    rfrDopo = Regex("""\bRFR\b""", RegexOption.IGNORE_CASE).containsMatchIn(resto),
                    note = note
                )
                return@forEach
            }

            val tipo = SIGLE.firstOrNull { (regex, _) -> regex.containsMatchIn(resto) }?.second
            if (tipo != null) assenze += Assenza(id++, data, tipo)
            else nonRiconosciute += riga
        }
        return Risultato(servizi, assenze, nonRiconosciute)
    }

    /** Data in formato gg/mm[/aa[aa]], gg-mm[-aaaa] o aaaa-mm-gg; restituisce anche il testo restante. */
    private fun trovaData(riga: String, anno: Int): Pair<LocalDate, String>? {
        DATA_ISO.find(riga)?.let { m ->
            val d = runCatching {
                LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
            }.getOrNull() ?: return null
            return d to riga.removeRange(m.range)
        }
        for (m in DATA_IT.findAll(riga)) {
            val anno4 = m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt()?.let { if (it < 100) 2000 + it else it } ?: anno
            val d = runCatching { LocalDate.of(anno4, m.groupValues[2].toInt(), m.groupValues[1].toInt()) }.getOrNull()
            if (d != null) return d to riga.removeRange(m.range)
        }
        return null
    }
}
