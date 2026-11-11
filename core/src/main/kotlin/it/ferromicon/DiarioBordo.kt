package it.ferromicon

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/** Una riga del diario di bordo: un'attività della giornata (treno, trasferimento, refezione, ecc.). Orari in minuti dalla mezzanotte, possono superare 1440. */
data class AttivitaDiario(
    val tipo: String,
    val treno: String = "",
    val prescrittoPartenza: Int? = null,
    val prescrittoArrivo: Int? = null,
    val realePartenza: Int? = null,
    val realeArrivo: Int? = null,
    val accessoriePartenza: Int = 0,
    val accessorieArrivo: Int = 0,
    val da: String = "",
    val a: String = ""
) {
    /** Minuti di ritardo all'arrivo rispetto al prescritto (negativo = anticipo). */
    val ritardoArrivo: Int? get() = if (prescrittoArrivo != null && realeArrivo != null) realeArrivo - prescrittoArrivo else null
}

/** Una giornata del diario, con i totali che il sistema aziendale ha calcolato. */
data class GiornoDiario(
    val data: LocalDate,
    /** Codice del turno o dell'assenza (per esempio VA041, RS, A997). */
    val turno: String,
    /** Testo della colonna presenza/attività quando la riga non ha orari (riposo, malattia…). */
    val descrizione: String = "",
    val attivita: List<AttivitaDiario> = emptyList(),
    /** Minuti di condotta (macchinista) o di scorta (capotreno). */
    val condotta: Int = 0,
    val lavoro: Int = 0,
    val lavoroNotturno: Int = 0,
    val straordinario: Int = 0,
    val fuoriResidenza: Int = 0,
    val rfr: Boolean = false,
    val lavoroInRiposo: Int = 0
) {
    val èServizio get() = attivita.isNotEmpty()

    val inizioPrescritto: Int? get() = attivita.firstNotNullOfOrNull { it.prescrittoPartenza }
    val finePrescritta: Int? get() = attivita.lastOrNull { it.prescrittoArrivo != null }?.prescrittoArrivo
    val inizioReale: Int? get() = attivita.firstNotNullOfOrNull { it.realePartenza }
    val fineReale: Int? get() = attivita.lastOrNull { it.realeArrivo != null }?.realeArrivo

    val refezioni get() = attivita.count { it.tipo.equals("REFEZ", true) }
    val ritardoFinale: Int? get() = if (fineReale != null && finePrescritta != null) fineReale!! - finePrescritta!! else null

    /** Minuti di lavoro: quelli stampati, altrimenti da primo a ultimo orario reale. */
    val lavoroEffettivo: Int get() = if (lavoro > 0) lavoro else
        if (inizioReale != null && fineReale != null) fineReale!! - inizioReale!! else 0

    /** Tipo di assenza quando la giornata non ha servizio. */
    val tipoAssenza: TipoAssenza? get() = if (èServizio) null else DiarioBordo.tipoDaTesto(descrizione.ifEmpty { turno })

    val riserva get() = attivita.any { it.tipo.equals("RIS", true) }

    val etichetta: String get() = if (èServizio) "Servizio $turno" else descrizione.ifEmpty { turno }
}

data class TotaliDiario(val condotta: Int, val lavoro: Int, val lavoroNotturno: Int, val straordinario: Int, val fuoriResidenza: Int)

data class RiepilogoDiario(
    val giorniServizio: Int,
    val riposi: Int,
    val ferie: Int,
    val malattia: Int,
    val altreAssenze: Int,
    val condotta: Int,
    val lavoro: Int,
    val lavoroNotturno: Int,
    val straordinario: Int,
    val fuoriResidenza: Int,
    val refezioni: Int,
    /** Giornate finite almeno 15' dopo il prescritto. */
    val giorniInRitardo: Int
)

data class Diario(
    val matricola: String,
    val agente: String,
    val settore: String,
    val residenza: String,
    val mese: YearMonth?,
    val giorni: List<GiornoDiario>,
    /** Totale stampato in fondo al diario, se presente. */
    val totaleStampato: TotaliDiario? = null
) {
    val profilo: Profilo? get() = when {
        settore.contains("scorta", true) -> Profilo.CAPOTRENO
        settore.contains("condotta", true) || settore.contains("macchin", true) -> Profilo.MACCHINISTA
        else -> null
    }

    fun riepilogo(): RiepilogoDiario {
        val assenze = giorni.filter { !it.èServizio }.map { it.tipoAssenza }
        return RiepilogoDiario(
            giorniServizio = giorni.count { it.èServizio },
            riposi = assenze.count { it?.riposo == true },
            ferie = assenze.count { it == TipoAssenza.FERIE },
            malattia = assenze.count { it == TipoAssenza.MALATTIA },
            altreAssenze = assenze.count { it != null && !it.riposo && it != TipoAssenza.FERIE && it != TipoAssenza.MALATTIA },
            condotta = giorni.sumOf { it.condotta },
            lavoro = giorni.sumOf { it.lavoro },
            lavoroNotturno = giorni.sumOf { it.lavoroNotturno },
            straordinario = giorni.sumOf { it.straordinario },
            fuoriResidenza = giorni.sumOf { it.fuoriResidenza },
            refezioni = giorni.sumOf { it.refezioni },
            giorniInRitardo = giorni.count { (it.ritardoFinale ?: 0) >= 15 }
        )
    }

    /** I totali delle giornate coincidono con quelli stampati in fondo. */
    val totaliTornano: Boolean get() {
        val t = totaleStampato ?: return true
        val r = riepilogo()
        return r.condotta == t.condotta && r.lavoro == t.lavoro && r.lavoroNotturno == t.lavoroNotturno &&
            r.fuoriResidenza == t.fuoriResidenza && r.straordinario == t.straordinario
    }
}

/**
 * Lettura del diario di bordo mensile (PDF): per ogni giornata le attività con orario prescritto e reale,
 * le attività accessorie, le stazioni e i totali di condotta/scorta, lavoro, notturno, straordinario e
 * fuori residenza. Le colonne si riconoscono dalla posizione sulla pagina.
 */
object DiarioBordo {
    private val DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val RE_DATA = Regex("^\\d{2}/\\d{2}/\\d{4}$")
    private val RE_ORA = Regex("^\\d{1,2}:\\d{2}$")
    private val MESI = listOf("gennaio", "febbraio", "marzo", "aprile", "maggio", "giugno", "luglio", "agosto",
        "settembre", "ottobre", "novembre", "dicembre")

    /** Posizione del bordo destro di ogni colonna numerica, rilevata dal modulo del diario. */
    private enum class Col { DATA, TURNO, ATTIVITA, TRENO, PRES_PART, PRES_ARR, REALE_PART, REALE_ARR, ACC_PART, ACC_ARR,
        ST_PART, ST_ARR, COND, LAVORO, NOTTURNO, STRAORD, FUORI, RFR, LAV_RIPOSO }

    private fun colonna(p: Pezzo): Col? {
        val r = p.x + p.w
        return when {
            p.x < 47 -> Col.DATA
            p.x < 70 -> Col.TURNO
            p.x < 123 -> Col.ATTIVITA
            p.x < 146 -> Col.TRENO
            r in 155.0..185.0 && p.x > 146 -> Col.PRES_PART
            r in 185.0..213.0 -> Col.PRES_ARR
            r in 213.0..241.0 -> Col.REALE_PART
            r in 241.0..265.0 -> Col.REALE_ARR
            r in 265.0..290.0 && p.x < 290 -> Col.ACC_PART
            r in 290.0..312.0 && p.x > 285 -> Col.ACC_ARR
            p.x in 312.0..372.0 -> Col.ST_PART
            p.x in 372.0..437.0 -> Col.ST_ARR
            r in 437.0..463.0 -> Col.COND
            r in 463.0..484.0 -> Col.LAVORO
            r in 484.0..505.0 -> Col.NOTTURNO
            r in 505.0..526.0 -> Col.STRAORD
            r in 526.0..547.0 -> Col.FUORI
            r in 547.0..568.0 -> Col.RFR
            r in 568.0..592.0 -> Col.LAV_RIPOSO
            else -> null
        }
    }

    fun minuti(s: String?): Int? {
        if (s == null || !RE_ORA.matches(s)) return null
        val (h, m) = s.split(":")
        return h.toInt() * 60 + m.toInt()
    }

    /** Tipo di giornata senza servizio dal testo del diario (anche abbreviato). */
    fun tipoDaTesto(testo: String): TipoAssenza {
        val t = testo.lowercase()
        return when {
            "settiman" in t -> TipoAssenza.RIPOSO_SETTIMANALE
            "recuper" in t -> TipoAssenza.RECUPERO_FESTIVO
            "riposo" in t -> TipoAssenza.RIPOSO
            "ferie" in t -> TipoAssenza.FERIE
            "malatt" in t -> TipoAssenza.MALATTIA
            "ex fest" in t || "exfest" in t -> TipoAssenza.PERMESSO_EX_FESTIVITA
            "permess" in t -> TipoAssenza.PERMESSO
            "disponib" in t -> TipoAssenza.DISPONIBILITA
            else -> TipoAssenza.ALTRO
        }
    }

    private class Riga(val y: Double, val pezzi: MutableList<Pezzo> = mutableListOf())

    private fun righe(pezzi: List<Pezzo>): List<Riga> {
        val rows = mutableListOf<Riga>()
        for (p in pezzi.filter { it.str.isNotBlank() }.sortedWith(compareBy({ it.y }, { it.x }))) {
            val r = rows.firstOrNull { abs(it.y - p.y) < 3.0 } ?: Riga(p.y).also { rows += it }
            r.pezzi += p
        }
        rows.forEach { r -> r.pezzi.sortBy { it.x } }
        return rows.sortedBy { it.y }
    }

    private class RigaDati(
        val data: LocalDate, val turno: String, val tipo: String, val treno: String,
        val pp: Int?, val pa: Int?, val rp: Int?, val ra: Int?, val ap: Int, val aa: Int, val da: String, val a: String,
        val cond: Int?, val lavoro: Int?, val notte: Int?, val straord: Int?, val fuori: Int?, val rfr: Boolean, val lavRip: Int?
    )

    fun leggi(pagine: List<List<Pezzo>>): Diario? {
        var matricola = ""; var agente = ""; var settore = ""; var residenza = ""; var mese: YearMonth? = null
        var totale: TotaliDiario? = null
        val dati = mutableListOf<RigaDati>()
        for (pagina in pagine) {
            for (r in righe(pagina)) {
                val testo = r.pezzi.joinToString(" ") { it.str.trim() }
                // L'intestazione sta a sinistra: a destra ci sono i titoli delle colonne
                val sinistra = r.pezzi.filter { it.x < 300 }.joinToString(" ") { it.str.trim() }
                if (r.pezzi.isNotEmpty() && RE_DATA.matches(r.pezzi.first().str.trim())) {
                    val data = runCatching { LocalDate.parse(r.pezzi.first().str.trim(), DATA) }.getOrNull() ?: continue
                    val celle = r.pezzi.drop(1).groupBy { colonna(it) }
                    fun t(c: Col) = celle[c]?.sortedBy { it.x }?.joinToString(" ") { it.str.trim() }.orEmpty()
                    fun n(c: Col) = t(c).toIntOrNull()
                    dati += RigaDati(data, t(Col.TURNO), t(Col.ATTIVITA), t(Col.TRENO),
                        minuti(t(Col.PRES_PART)), minuti(t(Col.PRES_ARR)), minuti(t(Col.REALE_PART)), minuti(t(Col.REALE_ARR)),
                        n(Col.ACC_PART) ?: 0, n(Col.ACC_ARR) ?: 0, t(Col.ST_PART), t(Col.ST_ARR),
                        n(Col.COND), n(Col.LAVORO), n(Col.NOTTURNO), n(Col.STRAORD), n(Col.FUORI),
                        t(Col.RFR).isNotEmpty(), n(Col.LAV_RIPOSO))
                    continue
                }
                Regex("Matricola:\\s*(\\d+)\\s+Agente:\\s*(.+)$").find(sinistra)?.let {
                    matricola = it.groupValues[1]; agente = it.groupValues[2].trim()
                }
                Regex("Settore:\\s*(\\S+)\\s+Residenza:\\s*(\\S+)").find(sinistra)?.let {
                    if (settore.isEmpty()) { settore = it.groupValues[1]; residenza = it.groupValues[2] }
                }
                Regex("Periodo:\\s*(\\p{L}+)\\s+(\\d{4})").find(sinistra)?.let {
                    val m = MESI.indexOf(it.groupValues[1].lowercase()) + 1
                    if (m > 0 && mese == null) mese = YearMonth.of(it.groupValues[2].toInt(), m)
                }
                if (testo.contains("Totale diario")) {
                    val nums = r.pezzi.filter { it.str.trim().toIntOrNull() != null }
                    fun v(c: Col) = nums.firstOrNull { colonna(it) == c }?.str?.trim()?.toIntOrNull() ?: 0
                    totale = TotaliDiario(v(Col.COND), v(Col.LAVORO), v(Col.NOTTURNO), v(Col.STRAORD), v(Col.FUORI))
                }
            }
        }
        if (dati.isEmpty()) return null
        val giorni = mutableListOf<GiornoDiario>()
        var corrente = mutableListOf<RigaDati>()
        fun chiudi() {
            if (corrente.isEmpty()) return
            val prima = corrente.first()
            val conOrari = corrente.filter { it.pp != null || it.rp != null }
            val tot = corrente.firstOrNull { it.cond != null || it.lavoro != null || it.notte != null || it.fuori != null }
            // Gli orari dopo la mezzanotte si stampano come 00:xx: si riportano oltre 24 ore in ordine crescente.
            var prec = 0
            fun norm(t: Int?): Int? {
                if (t == null) return null
                var v = t
                while (v < prec - 600) v += 1440
                if (v > prec) prec = v
                return v
            }
            val attivita = conOrari.map {
                val pp = norm(it.pp); val pa = norm(it.pa); val rp = norm(it.rp); val ra = norm(it.ra)
                AttivitaDiario(it.tipo, it.treno, pp, pa, rp, ra, it.ap, it.aa, it.da, it.a)
            }
            giorni += GiornoDiario(
                data = prima.data, turno = prima.turno,
                descrizione = if (conOrari.isEmpty()) prima.tipo else "",
                attivita = attivita,
                condotta = tot?.cond ?: 0, lavoro = tot?.lavoro ?: 0, lavoroNotturno = tot?.notte ?: 0,
                straordinario = tot?.straord ?: 0, fuoriResidenza = tot?.fuori ?: 0,
                rfr = corrente.any { it.rfr }, lavoroInRiposo = tot?.lavRip ?: 0
            )
            corrente = mutableListOf()
        }
        for (d in dati) {
            if (corrente.isNotEmpty() && (corrente.first().data != d.data || corrente.first().turno != d.turno)) {
                // Le righe del giorno dopo con lo stesso turno e senza totali sono la coda di un servizio a cavallo della mezzanotte.
                val coda = d.turno == corrente.first().turno &&
                    (d.data == corrente.last().data.plusDays(1) || d.data == corrente.last().data) &&
                    d.pp != null && d.cond == null && d.lavoro == null && d.fuori == null
                if (!coda) chiudi()
            }
            corrente += d
        }
        chiudi()
        val m = mese ?: giorni.map { YearMonth.from(it.data) }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        return Diario(matricola, agente, settore, residenza, m, giorni, totale)
    }

    /** Riconosce il PDF di un diario di bordo. */
    fun èDiario(pagine: List<List<Pezzo>>): Boolean {
        val t = pagine.take(3).joinToString(" ") { p -> p.joinToString(" ") { it.str } }
        return t.contains("Orario Prescritto") && t.contains("Orario Reale") ||
            (t.contains("Periodo:") && t.contains("Matricola:") && t.contains("Presenza/Attivit"))
    }
}
