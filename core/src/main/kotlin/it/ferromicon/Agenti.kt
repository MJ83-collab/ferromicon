package it.ferromicon

import java.text.Normalizer
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/** Raggruppa i pezzi di una pagina in righe (stessa altezza, entro la tolleranza), ciascuna ordinata da sinistra. */
internal fun righeDi(pagina: List<Pezzo>, tolleranza: Double = 3.0): List<List<Pezzo>> {
    val ordinati = pagina.sortedBy { it.y }
    val righe = ArrayList<MutableList<Pezzo>>()
    var yRiga = Double.NEGATIVE_INFINITY
    for (p in ordinati) {
        if (righe.isEmpty() || abs(p.y - yRiga) > tolleranza) {
            righe += mutableListOf(p); yRiga = p.y
        } else righe.last() += p
    }
    return righe.map { r -> r.sortedBy { it.x } }
}

internal fun testoRiga(r: List<Pezzo>) = r.joinToString(" ") { it.str.trim() }.replace(Regex("""\s+"""), " ").trim()

internal val FMT_DATA: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

/** Una riga del calendario degli agenti: un agente e i suoi codici giorno per giorno. */
data class RigaAgente(
    /** Numero della riga nella tabella (posizione nel ciclo del turno). */
    val posizione: Int,
    val nome: String,
    val cid: String,
    val codici: List<String>,
    /** 0 = ciclo principale; le altre sono tabelle a parte (agenti fuori ciclo). */
    val tabella: Int
)

/** Calendario degli agenti di un turno (un impianto) per il periodo [dal, al]. */
data class CalendarioTurnoAgenti(
    val turno: String,
    val profilo: String,
    val dal: LocalDate,
    val al: LocalDate,
    val righe: List<RigaAgente>,
    /** Numero di righe del ciclo principale, comprese quelle vuote (posti non assegnati). */
    val periodoCiclo: Int
) {
    /** Ciclo dei codici ricostruito dalle righe (ogni riga è lo stesso ciclo spostato di un giorno). */
    val ciclo: List<String?>? by lazy { costruisciCiclo() }

    /** Quanta parte dei codici letti coincide col ciclo ricostruito (0..1). */
    val concordanza: Double by lazy { calcolaConcordanza() }

    /** L'estensione oltre la settimana letta è affidabile. */
    val estendibile: Boolean get() = ciclo != null && concordanza >= 0.9

    private fun principali() = righe.filter { it.tabella == 0 }

    private fun costruisciCiclo(): List<String?>? {
        val p = periodoCiclo
        if (p < 7) return null
        val voti = Array(p) { HashMap<String, Int>() }
        for (r in principali()) r.codici.forEachIndexed { d, c ->
            val k = (r.posizione + d) % p
            voti[k][c] = (voti[k][c] ?: 0) + 1
        }
        // Le posizioni rimaste vuote (nessun agente assegnato nelle righe vicine) restano sconosciute.
        if (voti.count { it.isEmpty() } * 4 > p) return null
        // Una disponibilità (D) può sostituire una giornata per un singolo agente: vince il codice diverso da D.
        return voti.map { v ->
            if (v.isEmpty()) return@map null
            val senzaD = v.filterKeys { it != "D" }
            (if (senzaD.isNotEmpty()) senzaD else v).maxByOrNull { it.value }!!.key
        }
    }

    private fun calcolaConcordanza(): Double {
        val c = ciclo ?: return 0.0
        var tot = 0; var ok = 0
        for (r in principali()) r.codici.forEachIndexed { d, x ->
            tot++; if (c[(r.posizione + d) % periodoCiclo] == x) ok++
        }
        return if (tot == 0) 0.0 else ok.toDouble() / tot
    }

    /**
     * Codice dell'agente in una data: quello letto nella settimana del calendario, poi quello
     * previsto dal ciclo (solo per gli agenti del ciclo principale quando l'estensione è affidabile).
     */
    fun codice(r: RigaAgente, data: LocalDate): String? {
        val k = java.time.temporal.ChronoUnit.DAYS.between(dal, data).toInt()
        if (k >= 0 && k < r.codici.size) return r.codici[k]
        if (r.tabella != 0 || !estendibile) return null
        val c = ciclo ?: return null
        return c[Math.floorMod(r.posizione + k, periodoCiclo)]
    }
}

object CalendarioAgenti {

    private val INTESTAZIONE = Regex("""TURNO:\s*\[(.+?)\]\s*PROFILO:\s*(\S+).*?DAL:\s*(\d\d/\d\d/\d{4})\s*AL:\s*(\d\d/\d\d/\d{4})""")
    private val RIGA = Regex("""^(\d+)\s+(.+?)\s+(\d{7})\s+(\S.*)$""")
    private val VUOTA = Regex("""^(\d+)\s+N\.\s?A\.$""")
    private val SCOPERTO = Regex("""^(\d+)\s+SCOPERTO\b.*$""")

    /** Legge le pagine del PDF "Calendario Agenti" (una lista di pezzi per pagina). */
    fun leggi(pagine: List<List<Pezzo>>): List<CalendarioTurnoAgenti> {
        class Costruzione(val turno: String, val profilo: String, val dal: LocalDate, val al: LocalDate) {
            val righe = ArrayList<RigaAgente>()
            var tabella = 0
            var ultimo = 0
            var periodo = 0
        }
        val per = LinkedHashMap<String, Costruzione>()
        for (pagina in pagine) {
            val righe = righeDi(pagina).map { testoRiga(it) }
            val testata = righe.take(8).joinToString(" ")
            val m = INTESTAZIONE.find(testata) ?: continue
            val c = per.getOrPut(m.groupValues[1]) {
                Costruzione(m.groupValues[1], m.groupValues[2],
                    LocalDate.parse(m.groupValues[3], FMT_DATA), LocalDate.parse(m.groupValues[4], FMT_DATA))
            }
            for (t in righe) {
                val n: Int
                var agente: RigaAgente? = null
                val r = RIGA.find(t)
                val v = VUOTA.find(t)
                val s = SCOPERTO.find(t)
                when {
                    r != null -> {
                        n = r.groupValues[1].toInt()
                        val giorni = r.groupValues[4].trim().split(Regex("""\s+"""))
                        if (giorni.size < 7) continue
                        if (n <= c.ultimo) c.tabella++
                        agente = RigaAgente(n, r.groupValues[2].trim(), r.groupValues[3], giorni.take(7), c.tabella)
                    }
                    v != null -> { n = v.groupValues[1].toInt(); if (n <= c.ultimo) c.tabella++ }
                    s != null -> { n = s.groupValues[1].toInt(); if (n <= c.ultimo) c.tabella++ }
                    else -> continue
                }
                c.ultimo = n
                if (c.tabella == 0) c.periodo = maxOf(c.periodo, n)
                if (agente != null) c.righe += agente
            }
        }
        return per.values.map { CalendarioTurnoAgenti(it.turno, it.profilo, it.dal, it.al, it.righe, it.periodo) }
    }

    data class Trovato(val calendario: CalendarioTurnoAgenti, val riga: RigaAgente)

    private fun normalizza(t: String): String =
        Normalizer.normalize(t, Normalizer.Form.NFD).replace(Regex("""\p{M}+"""), "")
            .uppercase().replace(Regex("""[^A-Z0-9 ]"""), " ").replace(Regex("""\s+"""), " ").trim()

    /** Cerca per CID (anche parziale, almeno 4 cifre) o per nome e cognome (tutte le parole indicate). */
    fun cerca(calendari: List<CalendarioTurnoAgenti>, ricerca: String): List<Trovato> {
        val q = ricerca.trim()
        if (q.isEmpty()) return emptyList()
        val cifre = q.filter { it.isDigit() }
        val trovati = if (cifre.length >= 4 && cifre.length == q.replace(" ", "").length) {
            calendari.flatMap { c -> c.righe.filter { it.cid.contains(cifre) }.map { Trovato(c, it) } }
        } else {
            val parole = normalizza(q).split(" ").filter { it.isNotEmpty() }
            calendari.flatMap { c ->
                c.righe.filter { r ->
                    val n = normalizza(r.nome).split(" ")
                    parole.all { p -> n.any { it.startsWith(p) } }
                }.map { Trovato(c, it) }
            }
        }
        return trovati.sortedBy { it.riga.nome }
    }
}
