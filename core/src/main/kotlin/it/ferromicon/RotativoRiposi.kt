package it.ferromicon

import java.time.LocalDate

/**
 * Calendario rotativo dei riposi: per ogni data, il codice delle sette posizioni del ciclo (colonne A..G).
 * Codici: GRS (riposo settimanale), RIP (riposo), 1..5 (giornata di lavoro, dettaglio nel PDF dei turni).
 */
data class Rotativo(val giorni: Map<LocalDate, List<String>>) {
    val primo: LocalDate? get() = giorni.keys.minOrNull()
    val ultimo: LocalDate? get() = giorni.keys.maxOrNull()
}

object RotativoRiposi {

    private val RIGA = Regex("""^(\d\d/\d\d/\d{4})\s+(.+)$""")
    private val VALIDO = Regex("""^(GRS|RIP|[1-9])$""")

    /** Legge le pagine del PDF "Calendario rotativo riposi". Null se non contiene abbastanza giorni. */
    fun leggi(pagine: List<List<Pezzo>>): Rotativo? {
        val mappa = LinkedHashMap<LocalDate, List<String>>()
        for (pagina in pagine) for (riga in righeDi(pagina).map { testoRiga(it) }) {
            val m = RIGA.find(riga) ?: continue
            val codici = m.groupValues[2].trim().split(Regex("""\s+"""))
            if (codici.size < 7) continue
            val sette = codici.take(7)
            if (!sette.all { VALIDO.matches(it) }) continue
            val data = runCatching { LocalDate.parse(m.groupValues[1], FMT_DATA) }.getOrNull() ?: continue
            mappa.putIfAbsent(data, sette)
        }
        return if (mappa.size >= 28) Rotativo(mappa) else null
    }

    /**
     * Posizione (0..6) del rotativo corrispondente all'agente. Si confronta il riposo di ogni giorno
     * della settimana letta nel calendario: una sola colonna deve combaciare, senza contraddizioni.
     */
    fun posizioneDi(riga: RigaAgente, calendario: CalendarioTurnoAgenti, rot: Rotativo): Int? {
        val voti = IntArray(7)
        val valida = BooleanArray(7) { true }
        riga.codici.forEachIndexed { i, codice ->
            if (codice == "D") return@forEachIndexed
            val giorno = calendario.dal.plusDays(i.toLong())
            val colonne = rot.giorni[giorno] ?: return@forEachIndexed
            val riposoAgente = codice == "GRS" || codice == "R"
            for (s in 0..6) {
                val c = colonne[s]
                val riposoPosto = c == "GRS" || c == "RIP"
                if (riposoAgente != riposoPosto) valida[s] = false
                else if (riposoAgente && ((codice == "GRS") == (c == "GRS"))) voti[s]++
                else if (riposoAgente) valida[s] = false
            }
        }
        val candidate = (0..6).filter { valida[it] && voti[it] >= 2 }
        return candidate.singleOrNull()
    }

    /** Codice del giorno per una posizione: GRS, R (riposo) oppure L (lavoro, senza dettaglio). */
    fun codice(rot: Rotativo, posizione: Int, data: LocalDate): String? {
        val colonne = rot.giorni[data] ?: return null
        return when (colonne[posizione]) {
            "GRS" -> "GRS"
            "RIP" -> "R"
            else -> "L"
        }
    }
}
