package it.ferromicon

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/** Una differenza trovata confrontando il diario con l'agenda o con il cedolino. */
data class Discrepanza(val data: LocalDate?, val gravita: Gravita, val titolo: String, val dettaglio: String)

/**
 * Controllo incrociato: ciò che risulta fatto (diario di bordo), ciò che era previsto (agenda e turno)
 * e ciò che è stato segnato e pagato (cedolino).
 */
object ControlloDiario {
    private val GIORNO = DateTimeFormatter.ofPattern("dd/MM")

    fun ora(minuti: Int): String = "%02d:%02d".format((minuti / 60) % 24, minuti % 60)
    fun durata(minuti: Int): String = "${minuti / 60} h %02d'".format(minuti % 60)

    private fun minutiDelGiorno(s: Servizio, giorno: LocalDate) =
        java.time.Duration.between(giorno.atStartOfDay(), s.turno.inizio).toMinutes().toInt()

    /** Diario contro agenda, giorno per giorno. [tolleranza] in minuti. */
    fun confrontaConAgenda(diario: Diario, dati: Dati, tolleranza: Int = 10): List<Discrepanza> {
        val out = mutableListOf<Discrepanza>()
        val perData = diario.giorni.groupBy { it.data }
        for (g in diario.giorni) {
            val servizi = dati.servizi.filter { it.turno.inizio.toLocalDate() == g.data }
            val assenza = dati.assenze.firstOrNull { it.data == g.data }
            val giorno = GIORNO.format(g.data)
            if (g.èServizio) {
                val s = servizi.firstOrNull()
                val orari = "${ora(g.inizioPrescritto ?: g.inizioReale ?: 0)}–${ora(g.finePrescritta ?: g.fineReale ?: 0)}"
                if (s == null) {
                    out += Discrepanza(g.data, Gravita.ATTENZIONE, "$giorno: servizio non in agenda",
                        if (assenza != null) "In agenda c'è \"${assenza.tipo.etichetta}\", nel diario ${g.turno} $orari."
                        else "Nel diario c'è ${g.turno} $orari, in agenda niente.")
                    continue
                }
                val ini = minutiDelGiorno(s, g.data)
                val fin = ini + java.time.Duration.between(s.turno.inizio, s.turno.fine).toMinutes().toInt()
                val dIni = g.inizioPrescritto
                val dFin = g.finePrescritta
                if (dIni != null && abs(ini - dIni) > tolleranza)
                    out += Discrepanza(g.data, Gravita.ATTENZIONE, "$giorno: inizio diverso",
                        "Agenda ${ora(ini)}, diario (prescritto) ${ora(dIni)}.")
                if (dFin != null && abs(fin - dFin) > tolleranza)
                    out += Discrepanza(g.data, Gravita.ATTENZIONE, "$giorno: fine diversa",
                        "Agenda ${ora(fin)}, diario (prescritto) ${ora(dFin)}.")
                if (g.condotta > 0 && s.turno.minutiCondotta > 0 && abs(g.condotta - s.turno.minutiCondotta) > tolleranza)
                    out += Discrepanza(g.data, Gravita.ATTENZIONE,
                        "$giorno: ${if (s.turno.profilo == Profilo.CAPOTRENO) "scorta" else "condotta"} diversa",
                        "Agenda ${durata(s.turno.minutiCondotta)}, diario ${durata(g.condotta)}.")
                if (s.origineTurno.isNotEmpty() && g.turno.isNotEmpty() && !s.origineTurno.equals(g.turno, true))
                    out += Discrepanza(g.data, Gravita.INFO, "$giorno: giornata di turno diversa",
                        "In agenda ${s.origineTurno}, nel diario ${g.turno}.")
            } else {
                val tipo = g.tipoAssenza ?: TipoAssenza.ALTRO
                when {
                    servizi.isNotEmpty() -> out += Discrepanza(g.data, Gravita.ATTENZIONE, "$giorno: in agenda c'è un servizio",
                        "Il diario riporta \"${g.etichetta}\".")
                    assenza == null -> out += Discrepanza(g.data, Gravita.INFO, "$giorno: giornata non in agenda",
                        "Nel diario: ${g.etichetta}.")
                    assenza.tipo != tipo && !(assenza.tipo.riposo && tipo.riposo) -> out += Discrepanza(g.data, Gravita.ATTENZIONE,
                        "$giorno: assenza diversa", "Agenda \"${assenza.tipo.etichetta}\", diario \"${g.etichetta}\".")
                }
            }
        }
        // Agenda senza riscontro nel diario (solo nel periodo coperto dal diario)
        val mese = diario.mese
        if (mese != null) {
            val giorni = perData.keys
            dati.servizi.map { it.turno.inizio.toLocalDate() }.filter { java.time.YearMonth.from(it) == mese && it !in giorni }.distinct()
                .forEach { out += Discrepanza(it, Gravita.ATTENZIONE, "${GIORNO.format(it)}: in agenda ma non nel diario", "Servizio senza riscontro.") }
        }
        return out.sortedBy { it.data }
    }

    /** Diario contro cedolino dello stesso mese: giorni segnati e voci pagate. */
    fun confrontaConCedolino(diario: Diario, c: Cedolino): List<Discrepanza> {
        val out = mutableListOf<Discrepanza>()
        val m = diario.mese
        if (m != null && c.anno != null && c.mese != null && (m.year != c.anno || m.monthValue != c.mese)) {
            return listOf(Discrepanza(null, Gravita.INFO, "Mesi diversi",
                "Il cedolino è di ${c.periodoTesto.ifEmpty { "${c.mese}/${c.anno}" }}, il diario di ${m.monthValue}/${m.year}: " +
                    "scegli il cedolino dello stesso mese."))
        }
        val r = diario.riepilogo()
        fun conta(titolo: String, chiave: String, diarioVal: Int) {
            val v = c.presenze[chiave] ?: return
            if (abs(v - diarioVal) >= 0.5)
                out += Discrepanza(null, Gravita.ATTENZIONE, "$titolo diversi",
                    "Cedolino ${num(v)}, diario $diarioVal.")
        }
        conta("Giorni di presenza", "presenze", r.giorniServizio)
        conta("Giorni di riposo", "riposi", r.riposi)
        conta("Giorni di ferie", "ferie", r.ferie)
        conta("Giorni di malattia", "malattie", r.malattia)

        // Ticket pasto: un buono per giornata di presenza
        c.voci.firstOrNull { it.codice == "5560" || it.descrizione.contains("TICKET", true) }?.param?.let { p ->
            if (abs(p - r.giorniServizio) >= 0.5)
                out += Discrepanza(null, Gravita.ATTENZIONE, "Ticket pasto",
                    "Pagati ${num(p)}, giorni di servizio nel diario ${r.giorniServizio}.")
        }
        // Voci a ore che si possono confrontare con i totali del diario
        fun ore(titolo: String, chiave: Regex, minuti: Int) {
            val v = c.voci.firstOrNull { chiave.containsMatchIn(it.descrizione) && (it.param ?: 0.0) > 0 } ?: return
            val pagate = v.param ?: return
            val diarioOre = minuti / 60.0
            if (abs(pagate - diarioOre) > 0.5)
                out += Discrepanza(null, Gravita.INFO, "$titolo: da verificare",
                    "Voce \"${v.descrizione.trim()}\": ${num(pagate)} nel cedolino, ${"%.1f".format(java.util.Locale.ITALIAN, diarioOre)} h nel diario.")
        }
        ore("Straordinario", Regex("STRAORD", RegexOption.IGNORE_CASE), r.straordinario)
        ore("Lavoro notturno", Regex("NOTTURN", RegexOption.IGNORE_CASE), r.lavoroNotturno)
        return out
    }

    private fun num(v: Double) = if (v == Math.floor(v)) v.toInt().toString() else "%.2f".format(java.util.Locale.ITALIAN, v)

    /** Servizi e assenze da aggiungere all'agenda per le giornate del diario che mancano. */
    fun giornateMancanti(diario: Diario, dati: Dati, profilo: Profilo, primoId: Long): Pair<List<Servizio>, List<Assenza>> {
        val servizi = mutableListOf<Servizio>()
        val assenze = mutableListOf<Assenza>()
        var id = primoId
        for (g in diario.giorni) {
            if (g.èServizio) {
                if (dati.servizi.any { it.turno.inizio.toLocalDate() == g.data }) continue
                val ini = g.inizioReale ?: g.inizioPrescritto ?: continue
                val fin = g.fineReale ?: g.finePrescritta ?: continue
                val inizio = g.data.atStartOfDay().plusMinutes(ini.toLong())
                val fine = g.data.atStartOfDay().plusMinutes(fin.toLong())
                servizi += Servizio(id++, Turno(inizio, fine, profilo, g.condotta), rfrDopo = g.rfr,
                    note = "Dal diario di bordo: ${g.turno}", riserva = g.riserva, origineTurno = g.turno)
            } else {
                if (dati.assenze.any { it.data == g.data } || dati.servizi.any { it.turno.inizio.toLocalDate() == g.data }) continue
                assenze += Assenza(id++, g.data, g.tipoAssenza ?: TipoAssenza.ALTRO, g.descrizione)
            }
        }
        return servizi to assenze
    }
}
