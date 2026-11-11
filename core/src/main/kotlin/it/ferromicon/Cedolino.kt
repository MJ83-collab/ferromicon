package it.ferromicon

import kotlin.math.abs

/** Un pezzo di testo di una pagina PDF: stringa, posizione (x, y dall'alto), larghezza e altezza del carattere. */
data class Pezzo(val str: String, val x: Double, val y: Double, val w: Double, val h: Double = 0.0)

/** Una riga della tabella delle voci del cedolino. */
data class VoceCedolino(
    val codice: String,
    val flag: String,
    val descrizione: String,
    val valFig: Double?,
    val param: Double?,
    val compUnit: Double?,
    val trattenuta: Double?,
    val competenza: Double?
)

/**
 * Cedolino Altarail (modulo SAP ZHRPY_CEDOLINO_3NORD) letto da un PDF.
 * I valori sparsi (INPS, IRPEF, imponibili, netto, totali) stanno in [numeri] con le stesse chiavi
 * del cedolino: "inps", "irpef", "tassSep", "fondi", "imponibile_inps", "imponibileMese_irpef",
 * "imponibileAnno_irpef", "totCompetenze", "totTrattenute", "totaleRetribuzione", "netto",
 * "arrPrec", "arrCorr", "tfrDisponibile".
 */
data class Cedolino(
    val file: String,
    val anno: Int?,
    val mese: Int?,
    val periodoTesto: String,
    val valuta: String,
    val livello: String,
    val profilo: String,
    val presenze: Map<String, Double>,
    val componenti: Map<String, Double>,
    val voci: List<VoceCedolino>,
    val numeri: Map<String, Double>,
    val tfr: Map<String, Double>,
    val fondoMese: Map<String, Double>,
    val fondoAnno: Map<String, Double>,
    val sommaCompetenze: Double,
    val sommaTrattenute: Double,
    /** Le somme delle voci tornano con i totali stampati e con il netto. */
    val controlloOk: Boolean,
    /** Profilo, livello, CID e altri dati personali letti dall'intestazione. */
    val anagrafica: AnagraficaCedolino = AnagraficaCedolino()
) {
    fun n(chiave: String): Double? = numeri[chiave]
    val netto get() = numeri["netto"]
    val lordo get() = numeri["totCompetenze"] ?: sommaCompetenze
    val trattenute get() = numeri["totTrattenute"] ?: sommaTrattenute
    /** Mese 13 e oltre: mensilità aggiuntive (tredicesima, quattordicesima). */
    val ordinario get() = (mese ?: 0) in 1..12
}

/**
 * Lettura del cedolino dalle posizioni del testo nel PDF. Il calcolo è quello del cedolino
 * dell'autore: le righe si ricostruiscono dalle coordinate e i valori si assegnano alle colonne per
 * vicinanza del bordo destro.
 */
object CedolinoParser {

    private val NUM = Regex("^-?\\d{1,3}(?:\\.\\d{3})*,\\d+-?$")
    private val MESI = listOf("gennaio", "febbraio", "marzo", "aprile", "maggio", "giugno", "luglio",
        "agosto", "settembre", "ottobre", "novembre", "dicembre")

    fun èNumero(s: String) = NUM.matches(s.trim())

    fun numero(testo: String?): Double? {
        if (testo == null) return null
        var s = testo.trim()
        var neg = false
        if (s.endsWith("-")) { neg = true; s = s.dropLast(1) }
        if (s.startsWith("-")) { neg = !neg; s = s.drop(1) }
        val v = s.replace(".", "").replace(",", ".").toDoubleOrNull() ?: return null
        return if (neg) -v else v
    }

    private class Seg(var str: String, val x: Double, val y: Double, var r: Double)
    private class Riga(val y: Double, val items: MutableList<Seg> = mutableListOf()) {
        var text = ""
    }

    private fun righe(pezzi: List<Pezzo>): List<Riga> {
        val its = pezzi.filter { it.str.isNotBlank() }
            .map { Seg(it.str.replace(Regex("\\s+"), " ").trim(), it.x, it.y, it.x + it.w) }
            .sortedWith(compareBy({ it.y }, { it.x }))
        val rows = mutableListOf<Riga>()
        for (it in its) {
            var row = rows.firstOrNull { r -> abs(r.y - it.y) < 2 }
            if (row == null) { row = Riga(it.y); rows += row }
            row.items += it
        }
        for (r in rows) {
            r.items.sortBy { it.x }
            val seg = mutableListOf<Seg>()
            for (it in r.items) {
                val last = seg.lastOrNull()
                if (last != null && !èNumero(it.str) && !èNumero(last.str) && it.x - last.r < 6) {
                    last.str += " " + it.str
                    last.r = it.r
                } else seg += Seg(it.str, it.x, it.y, it.r)
            }
            r.items.clear(); r.items.addAll(seg)
            r.text = seg.joinToString(" ") { it.str }
        }
        return rows.sortedBy { it.y }
    }

    private fun trovaRiga(rows: List<Riga>, re: Regex, da: Double = 0.0) =
        rows.firstOrNull { it.y >= da && re.containsMatchIn(it.text) }

    private fun trovaItem(rows: List<Riga>, re: Regex): Pair<Seg, Double>? {
        for (r in rows) for (it in r.items) if (re.containsMatchIn(it.str)) return it to r.y
        return null
    }

    private class Col(val seg: Seg, val k: String)

    private fun colonna(cols: List<Col>, it: Seg): Col? {
        var best: Col? = null
        var d = 1e9
        for (c in cols) {
            val dd = abs(c.seg.r - it.r)
            if (dd < d) { d = dd; best = c }
        }
        return if (d < 30) best else null
    }

    private class Acc {
        var anno: Int? = null
        var mese: Int? = null
        var periodoTesto = ""
        var valuta = ""
        var livello = ""
        var profilo = ""
        val presenze = linkedMapOf<String, Double>()
        val componenti = linkedMapOf<String, Double>()
        val voci = mutableListOf<VoceCedolino>()
        val numeri = linkedMapOf<String, Double>()
        val tfr = linkedMapOf<String, Double>()
        var fondoMese: Map<String, Double> = emptyMap()
        var fondoAnno: Map<String, Double> = emptyMap()
        val testi = mutableListOf<String>()
    }

    private val RE_STIPENDIO = Regex("Stipendio di\\s+(\\S+)\\s+(\\d{4})", RegexOption.IGNORE_CASE)
    private val RE_VALUTA = Regex("Valuta\\s*:\\s*(\\d{2}\\.\\d{2}\\.\\d{4})")
    private val RE_CODICE = Regex("^([0-9A-Z/]{4})(?=\\s|$)\\s*(.*)$")

    private fun parsePagina(pezzi: List<Pezzo>, out: Acc) {
        val rows = righe(pezzi)
        rows.forEach { out.testi += it.text }

        // periodo
        for (r in rows) {
            RE_STIPENDIO.find(r.text)?.let { m ->
                val k = m.groupValues[1].lowercase()
                var mese = MESI.indexOf(k) + 1
                if (mese == 0) Regex("^(\\d+)").find(k)?.let { mese = it.groupValues[1].toInt() }
                out.anno = m.groupValues[2].toInt()
                out.mese = mese.takeIf { it > 0 }
                out.periodoTesto = "${m.groupValues[1]} ${m.groupValues[2]}"
            }
            RE_VALUTA.find(r.text)?.let { out.valuta = it.groupValues[1] }
        }

        // anagrafica utile
        trovaRiga(rows, Regex("^Livello"))?.let { out.livello = it.text.replace(Regex("^Livello\\s*:?\\s*"), "").trim() }
        trovaRiga(rows, Regex("^Profilo"))?.let {
            out.profilo = it.text.replace(Regex("^Profilo\\s*:?\\s*"), "").replace(Regex("\\s*AREA:.*$"), "").trim()
        }

        // presenze
        val hp = trovaRiga(rows, Regex("PRESENZE"))
        if (hp != null) {
            val vals = rows.firstOrNull { r -> r.y > hp.y && r.y < hp.y + 18 && r.items.all { èNumero(it.str) } }
            if (vals != null) {
                val cols = hp.items.map { Col(it, it.str) }
                for (it in vals.items) {
                    val c = colonna(cols, it) ?: continue
                    numero(it.str)?.let { v -> out.presenze[c.k.lowercase()] = v }
                }
            }
        }

        // componenti della retribuzione
        val hc = trovaRiga(rows, Regex("COMPONENTI DELLA RETRIBUZIONE"))
        val ht = trovaRiga(rows, Regex("TOTALE RETRIBUZIONE"))
        if (hc != null && ht != null) {
            for (r in rows) if (r.y > hc.y + 2 && r.y < ht.y - 1) {
                for (i in 0 until r.items.size - 1) {
                    if (!èNumero(r.items[i].str) && èNumero(r.items[i + 1].str))
                        numero(r.items[i + 1].str)?.let { out.componenti[r.items[i].str] = it }
                }
            }
        }
        if (ht != null) ht.items.firstOrNull { èNumero(it.str) }?.let { numero(it.str)?.let { v -> out.numeri["totaleRetribuzione"] = v } }

        // voci
        val hv = rows.firstOrNull { r -> r.items.any { it.str == "VAL.FIGURAT" } }
        val prev = trovaRiga(rows, Regex("^PREVIDEN"))
        if (hv != null) {
            val nomi = mapOf("VAL.FIGURAT" to "valFig", "PARAMETRI" to "param", "COMP.UNITARI" to "compUnit",
                "TRATTENUTE" to "trattenuta", "COMPETENZE" to "competenza")
            val cols = hv.items.filter { it.str in nomi }.map { Col(it, nomi.getValue(it.str)) }
            val xMinNum = (cols.minOfOrNull { it.seg.x } ?: 0.0) - 25
            val fine = prev?.y ?: 1e9
            for (r in rows) {
                if (r.y <= hv.y + 2 || r.y >= fine) continue
                val first = r.items.firstOrNull() ?: continue
                if (first.x >= 40) continue
                val m = RE_CODICE.find(first.str) ?: continue
                val valori = hashMapOf<String, Double?>()
                val desc = mutableListOf<String>()
                for (it in r.items.drop(1)) {
                    if (èNumero(it.str) && it.x >= xMinNum) {
                        val c = colonna(cols, it)
                        if (c != null) valori[c.k] = numero(it.str)
                    } else desc += it.str
                }
                out.voci += VoceCedolino(
                    codice = m.groupValues[1], flag = m.groupValues[2].trim(),
                    descrizione = desc.joinToString(" ").replace(Regex("\\s+"), " ").trim(),
                    valFig = valori["valFig"], param = valori["param"], compUnit = valori["compUnit"],
                    trattenuta = valori["trattenuta"], competenza = valori["competenza"]
                )
            }
        }

        // blocchi in basso: valore nella colonna trattenute
        val hTot = trovaRiga(rows, Regex("TOT\\.TRATTENUTE"))
        if (hTot != null) {
            val cT = hTot.items.firstOrNull { Regex("TOT\\.TRATTENUTE").containsMatchIn(it.str) }
            val cC = hTot.items.firstOrNull { Regex("TOT\\.COMPETENZE").containsMatchIn(it.str) }
            val blocchi = listOf("PREVIDEN" to "inps", "FONDI" to "fondi", "IRPEF" to "irpef", "TASS.SEP" to "tassSep")
            val ys = blocchi.map { (lab, _) -> trovaRiga(rows, Regex("^" + lab.replace(".", "\\.")))?.y }
            blocchi.forEachIndexed { i, (_, key) ->
                val y0 = ys[i] ?: return@forEachIndexed
                val y1 = ys.drop(i + 1).firstOrNull { it != null } ?: hTot.y
                var first: Seg? = null
                var mese: Seg? = null
                var anno: Seg? = null
                fun inBlocco(r: Riga) = r.y >= y0 - 3 && r.y < y1 - 3
                val rAnno = rows.firstOrNull { r ->
                    inBlocco(r) && r.items.firstOrNull()?.let { it.x < 35 } == true &&
                        Regex("^anno", RegexOption.IGNORE_CASE).containsMatchIn(r.text.replace(Regex("\\s"), ""))
                }
                for (r in rows) {
                    if (!inBlocco(r)) continue
                    for (it in r.items) {
                        if (!èNumero(it.str)) continue
                        if (cT != null && abs(it.r - cT.r) < 12) {
                            out.numeri[key] = (out.numeri[key] ?: 0.0) + (numero(it.str) ?: 0.0)
                        } else if (it.x < 120) {
                            if (first == null) first = it
                            if (rAnno != null && r.y > rAnno.y + 1) { if (anno == null) anno = it }
                            else if (mese == null) mese = it
                        }
                    }
                }
                first?.let { f -> numero(f.str)?.let { out.numeri["imponibile_$key"] = it } }
                if (key == "fondi") {
                    val reImp = Regex("Impon\\.?\\s*Fondi", RegexOption.IGNORE_CASE)
                    val hRow = rows.firstOrNull { r -> inBlocco(r) && r.items.any { reImp.containsMatchIn(it.str) } }
                    if (hRow != null) {
                        val nomiF = listOf(
                            Regex("Impon\\.?\\s*Fondi", RegexOption.IGNORE_CASE) to "impFondi",
                            Regex("Impon\\.?\\s*Conv", RegexOption.IGNORE_CASE) to "impConv",
                            Regex("Quota\\s*TFR", RegexOption.IGNORE_CASE) to "tfr",
                            Regex("Azienda", RegexOption.IGNORE_CASE) to "azienda",
                            Regex("Quota\\s*Base", RegexOption.IGNORE_CASE) to "base",
                            Regex("Volont", RegexOption.IGNORE_CASE) to "volontaria"
                        )
                        val colsF = hRow.items.mapNotNull { s ->
                            nomiF.firstOrNull { (re, _) -> re.containsMatchIn(s.str) }?.let { Col(s, it.second) }
                        }
                        val fm = linkedMapOf<String, Double>()
                        val fa = linkedMapOf<String, Double>()
                        for (r in rows) {
                            if (!inBlocco(r) || r === hRow) continue
                            for (it in r.items) {
                                if (!èNumero(it.str) || (cT != null && abs(it.r - cT.r) < 12) || it.x > 440) continue
                                val c = colonna(colsF, it) ?: continue
                                val v = abs(numero(it.str) ?: continue)
                                if (rAnno != null && r.y > rAnno.y + 1) fa[c.k] = v else fm[c.k] = v
                            }
                        }
                        if (fm.isNotEmpty() || fa.isNotEmpty()) { out.fondoMese = fm; out.fondoAnno = fa }
                    }
                }
                if (rAnno != null) {
                    out.numeri["imponibileMese_$key"] = mese?.let { numero(it.str) } ?: 0.0
                    anno?.let { a -> numero(a.str)?.let { out.numeri["imponibileAnno_$key"] = it } }
                }
            }
            // riga valori TFR e totali
            val vt = rows.firstOrNull { r -> r.y > hTot.y + 2 && r.y < hTot.y + 22 && r.items.any { èNumero(it.str) } }
            if (vt != null) {
                val colsT = hTot.items.map { Col(it, it.str) }
                for (it in vt.items) {
                    if (!èNumero(it.str)) continue
                    val c = colonna(colsT, it) ?: continue
                    val v = numero(it.str) ?: continue
                    if (!Regex("TOT\\.").containsMatchIn(c.seg.str)) out.tfr[c.seg.str] = v
                    if (c.seg === cT) out.numeri["totTrattenute"] = v
                    else if (c.seg === cC) out.numeri["totCompetenze"] = v
                    else if (Regex("TFR Disponib").containsMatchIn(c.seg.str)) out.numeri["tfrDisponibile"] = v
                }
            }
            // arrotondamenti
            val hArr = trovaRiga(rows, Regex("ARR\\.MESE\\.PREC"))
            if (hArr != null) {
                val cp = hArr.items.firstOrNull { Regex("ARR\\.MESE\\.PREC").containsMatchIn(it.str) }
                val cc = hArr.items.firstOrNull { Regex("ARR\\.MESE\\.CORR").containsMatchIn(it.str) }
                for (r in rows) if (r.y > hArr.y + 2 && r.y < hArr.y + 20) {
                    for (it in r.items) if (èNumero(it.str)) {
                        if (cp != null && abs(it.r - cp.r) < 12) numero(it.str)?.let { v -> out.numeri["arrPrec"] = v }
                        if (cc != null && abs(it.r - cc.r) < 12 && it.x > 500) numero(it.str)?.let { v -> out.numeri["arrCorr"] = v }
                    }
                }
            }
        }

        // netto
        val nIt = trovaItem(rows, Regex("^NETTO( A PAGARE)?$"))
        if (nIt != null) {
            for (r in rows) if (r.y > nIt.second + 2 && r.y < nIt.second + 30) {
                val it = r.items.firstOrNull { èNumero(it.str) && it.x > 500 }
                if (it != null) { numero(it.str)?.let { v -> out.numeri["netto"] = v }; break }
            }
        }
    }

    private fun r2(x: Double) = Math.round(x * 100) / 100.0

    /** Legge un cedolino da tutte le pagine del PDF. [nomeFile] serve da riserva per anno e mese ("2025_11"). */
    fun parse(pagine: List<List<Pezzo>>, nomeFile: String = ""): Cedolino {
        val out = Acc()
        pagine.forEach { parsePagina(it, out) }
        if (out.anno == null || out.mese == null) {
            Regex("(\\d{4})_(\\d{2})").find(nomeFile)?.let { m ->
                if (out.anno == null) out.anno = m.groupValues[1].toInt()
                if (out.mese == null) out.mese = m.groupValues[2].toInt()
            }
        }
        val n = out.numeri
        val sC = r2(out.voci.sumOf { it.competenza ?: 0.0 })
        val sT = r2(out.voci.sumOf { it.trattenuta ?: 0.0 } + (n["inps"] ?: 0.0) + (n["irpef"] ?: 0.0) +
            (n["tassSep"] ?: 0.0) + (n["fondi"] ?: 0.0))
        val netto = n["netto"]
        val totC = n["totCompetenze"]
        val totT = n["totTrattenute"]
        val ok = netto != null &&
            (totC == null || abs(sC - totC) < 0.02) &&
            (totT == null || abs(sT - totT) < 0.02) &&
            (totC == null || totT == null ||
                abs(totC - totT + (n["arrPrec"] ?: 0.0) - (n["arrCorr"] ?: 0.0) - netto) < 1.01)
        return Cedolino(
            file = nomeFile, anno = out.anno, mese = out.mese, periodoTesto = out.periodoTesto, valuta = out.valuta,
            livello = out.livello, profilo = out.profilo, presenze = out.presenze, componenti = out.componenti,
            voci = out.voci, numeri = n, tfr = out.tfr, fondoMese = out.fondoMese, fondoAnno = out.fondoAnno,
            sommaCompetenze = sC, sommaTrattenute = sT, controlloOk = ok,
            anagrafica = AnagraficaCedolino.leggi(out.testi, out.presenze, out.componenti)
        )
    }
}
