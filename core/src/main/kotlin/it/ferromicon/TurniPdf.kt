package it.ferromicon

import java.time.LocalTime

/** Tipo di giorno delle giornate di turno: feriale (lun-ven), sabato, domenica e festivi. */
enum class TipoGiorno(val sigla: String) {
    FERIALE("LMXGV"), SABATO("S"), FESTIVO("D");

    companion object {
        fun di(data: java.time.LocalDate): TipoGiorno = when {
            data.dayOfWeek == java.time.DayOfWeek.SUNDAY || Festivi.èFestivoContrattuale(data) -> FESTIVO
            data.dayOfWeek == java.time.DayOfWeek.SATURDAY -> SABATO
            else -> FERIALE
        }
    }
}

data class TrenoGiornata(val numero: String, val destinazione: String, val vettura: Boolean)

/** Una giornata di turno come stampata nel PDF dei turni del personale mobile. */
data class GiornataDefinita(
    /** Codice del turno, es. VAOR_S. */
    val turno: String,
    /** "Condotta" o "Scorta". */
    val profilo: String,
    val numero: String,
    val tipo: TipoGiorno,
    val inizio: LocalTime,
    val fine: LocalTime,
    val lavoroMin: Int,
    /** Minuti di condotta o di scorta. */
    val condottaMin: Int,
    val km: Int,
    val notturno: Boolean,
    /** Riposo dopo la giornata, in minuti. */
    val riposoMin: Int,
    val refezione: Boolean,
    val treni: List<TrenoGiornata>
) {
    val codice: String get() = turno.substringBefore('_') + numero
}

object TurniPdf {

    private val ORA = Regex("""\[(\d\d):(\d\d)]""")
    private val INTESTAZIONE = Regex("""TURNO:\s*\[(\w+)]""")
    private val PROFILO = Regex("""PROFILO:\s*(\w+)""")
    private val NUMERO = Regex("""^F?\d+$""")
    private val TRENO = Regex("""^(\()?(\d{3,5})\)?$""")
    private val STAZIONE = Regex("""^[A-Za-z]{2,6}$""")

    private fun minuti(t: String): Int? {
        val m = Regex("""^(\d{1,2}):(\d\d)$""").find(t) ?: return null
        return m.groupValues[1].toInt() * 60 + m.groupValues[2].toInt()
    }

    /** Nome del turno della pagina (es. VAOR_S), o null se non è una pagina di turno. */
    fun turnoDellaPagina(pagina: List<Pezzo>): String? {
        val testata = righeDi(pagina).take(6).joinToString(" ") { testoRiga(it) }
        return INTESTAZIONE.find(testata)?.groupValues?.get(1)
    }

    fun leggi(pagine: List<List<Pezzo>>): List<GiornataDefinita> = pagine.flatMap { leggiPagina(it) }

    fun leggiPagina(pagina: List<Pezzo>): List<GiornataDefinita> {
        val testata = righeDi(pagina).take(6).joinToString(" ") { testoRiga(it) }
        val turno = INTESTAZIONE.find(testata)?.groupValues?.get(1) ?: return emptyList()
        val profilo = PROFILO.find(testata)?.groupValues?.get(1) ?: ""

        // Ancore: "[hh:mm] [hh:mm]" a sinistra, una per giornata.
        val ancore = righeDi(pagina.filter { it.x < 110 && ORA.containsMatchIn(it.str) }, 2.5)
        val risultati = ArrayList<GiornataDefinita>()
        for (riga in ancore) {
            val ore = riga.flatMap { p -> ORA.findAll(p.str).map { LocalTime.of(it.groupValues[1].toInt(), it.groupValues[2].toInt()) } }
            if (ore.size < 2) continue
            val y = riga.first().y
            val xOre = riga.first().x

            // Numero della giornata: la cifra grande a sinistra, o il testo prima delle ore.
            val primo = riga.first().str.trim()
            val numero = (if (primo.startsWith("[")) null else primo.substringBefore('[').trim().takeIf { it.isNotEmpty() })
                ?: pagina.filter { it.x < xOre && abs(it.y - y) <= 8 && NUMERO.matches(it.str.trim()) }
                    .minByOrNull { it.x }?.str?.trim()
                ?: continue

            val tipoPezzo = pagina.filter { it.x < 90 && it.y < y - 8 && it.y > y - 80 && it.str.trim() in listOf("LMXGV", "S", "D") }
                .maxByOrNull { it.y } ?: continue
            val tipo = TipoGiorno.values().first { it.sigla == tipoPezzo.str.trim() }

            // Riepilogo a destra: Lav, Sct, Km, Not, Rip.
            val valori = pagina.filter { it.x > 715 && it.y > y - 2 && it.y < y + 22 }
                .sortedBy { it.x }.flatMap { it.str.trim().split(Regex("""\s+""")) }
            if (valori.size < 5) continue
            val lav = minuti(valori[0]) ?: continue
            val sct = minuti(valori[1]) ?: continue
            val km = valori[2].toIntOrNull() ?: 0
            val notturno = valori[3].lowercase().startsWith("s")
            val rip = minuti(valori[4]) ?: continue

            // Treni: numeri scritti in verticale nella fascia sopra l'ancora, da sinistra a destra.
            val fascia = pagina.filter { it.x in 60.0..715.0 && it.y > y - 80 && it.y < y + 2 }
            val treniPezzi = fascia.filter { TRENO.matches(it.str.trim()) }.sortedBy { it.x }
            val treni = ArrayList<TrenoGiornata>()
            for (t in treniPezzi) {
                val m = TRENO.find(t.str.trim())!!
                val dest = fascia.filter { STAZIONE.matches(it.str.trim()) && abs(it.x - t.x) < 2.0 &&
                    it.str.trim().uppercase() !in listOf("REFEZ", "SPOST") }
                    .minByOrNull { abs(it.x - t.x) }?.str?.trim() ?: ""
                val voce = TrenoGiornata(m.groupValues[2], dest, m.groupValues[1] == "(")
                if (treni.lastOrNull()?.let { it.numero == voce.numero && it.vettura == voce.vettura } != true) treni += voce
            }
            val refez = fascia.any { it.str.trim().equals("REFEZ", ignoreCase = true) }
            risultati += GiornataDefinita(turno, profilo, numero, tipo, ore[0], ore[1], lav, sct, km, notturno, rip, refez, treni)
        }
        return risultati
    }

    private fun abs(d: Double) = kotlin.math.abs(d)

    /** Trova la giornata per codice (es. VAOR6) e tipo di giorno; ripiega su altri tipi se manca. */
    fun trova(defs: List<GiornataDefinita>, codice: String, tipo: TipoGiorno): GiornataDefinita? {
        val candidate = defs.filter { it.codice.equals(codice, ignoreCase = true) }
        return candidate.firstOrNull { it.tipo == tipo }
            ?: candidate.firstOrNull { it.tipo == TipoGiorno.FERIALE }
            ?: candidate.firstOrNull()
    }
}
