package it.ferromicon

import kotlin.math.abs

/** Certificazione Unica (modello ministeriale, lavoro dipendente) letta da un PDF. */
data class CertificazioneUnica(
    val file: String,
    /** Anno di imposta (quello a cui si riferiscono i redditi). */
    val anno: Int?,
    val annoCu: Int?,
    /** Importi per chiave: reddito, ritenuteIrpef, addRegionale, addComunale, impostaLorda, totDetrazioni, ... */
    val numeri: Map<String, Double>,
    val giorni: Int?
) {
    val reddito get() = numeri["reddito"]
    val addComunale get() = numeri["addComunale"]
    val valida get() = anno != null && reddito != null
}

object CuParser {

    private val MONEY = Regex("^-?\\d{1,3}(?:\\.\\d{3})*,\\d{2}$")
    private val INTERO = Regex("^\\d{1,3}$")

    private class Campo(val chiave: String, val punto: Int, val sezione: String, val intero: Boolean = false)

    private val CAMPI = listOf(
        Campo("reddito", 1, "fiscale"), Campo("giorni", 6, "fiscale", true),
        Campo("ritenuteIrpef", 21, "fiscale"), Campo("addRegionale", 22, "fiscale"),
        Campo("addComAcconto", 26, "fiscale"), Campo("addComSaldo", 27, "fiscale"), Campo("addComAccontoSucc", 29, "fiscale"),
        Campo("impostaLorda", 361, "fiscale"), Campo("detrazioniFamiglia", 362, "fiscale"),
        Campo("detrazioniLavoro", 367, "fiscale"), Campo("totDetrazioni", 374, "fiscale"),
        Campo("impostaNetta", 375, "fiscale"),
        Campo("premioRisultato", 572, "premi"), Campo("premioImpostaSost", 576, "premi"),
        Campo("imponibileInps", 4, "inps"), Campo("contributiInps", 6, "inps"),
        Campo("arretratiConDetr", 511, "arretrati"), Campo("arretratiSenzaDetr", 512, "arretrati"),
        Campo("ritenuteArretrati", 513, "arretrati")
    )

    /** Vero se il testo della prima pagina è quello di una CU. */
    fun èCu(pagine: List<List<Pezzo>>): Boolean {
        val t = pagine.joinToString(" ") { p -> p.joinToString(" ") { it.str } }
        return Regex("CERTIFICAZIONE\\s+UNICA", RegexOption.IGNORE_CASE).containsMatchIn(t)
    }

    private fun sezioniDi(items: List<Pezzo>): Map<String, List<Pezzo>> {
        val t = items.joinToString(" ") { it.str }
        val out = linkedMapOf<String, List<Pezzo>>()
        items.firstOrNull { Regex("Matricola azienda", RegexOption.IGNORE_CASE).containsMatchIn(it.str) }?.let { mat ->
            out["inps"] = items.filter { it.y > mat.y - 12 && it.y < mat.y + 40 }
        }
        items.firstOrNull { Regex("ANNI PRECEDENTI SOGGETTI A TASSAZIONE SEPARATA", RegexOption.IGNORE_CASE).containsMatchIn(it.str) }?.let { arr ->
            out["arretrati"] = items.filter { it.y > arr.y - 4 && it.y < arr.y + 45 }
        }
        if (Regex("CERTIFICAZIONE LAVORO DIPENDENTE").containsMatchIn(t) &&
            Regex("Ritenute Irpef", RegexOption.IGNORE_CASE).containsMatchIn(t)) out["fiscale"] = items
        if (Regex("PREMI DI\\s*RISULTATO", RegexOption.IGNORE_CASE).containsMatchIn(t) && Regex("571").containsMatchIn(t))
            out["premi"] = items
        return out
    }

    private fun valore(items: List<Pezzo>, punto: Int, intero: Boolean): Double? {
        val labels = items.filter { it.str.trim() == punto.toString() && it.h < 5.6 }
        var best: String? = null
        var bd = 1e9
        for (l in labels) for (it in items) {
            if (it.h < 5.8) continue
            val s = it.str.trim()
            val ok = if (intero) INTERO.matches(s) else MONEY.matches(s)
            if (!ok) continue
            val dx = it.x - l.x
            val dy = it.y - l.y
            if (dx < -2 || dx > 160 || dy < -6 || dy > 22) continue
            // il valore appartiene a un'altra casella se c'è un'etichetta più vicina tra le due
            if (items.any { o -> o !== l && o.h < 5.6 && INTERO.matches(o.str.trim()) &&
                    abs(o.y - l.y) < 4 && o.x > l.x + 1 && o.x < it.x - 1 }) continue
            val d = dx + abs(dy) * 3
            if (d < bd) { bd = d; best = s }
        }
        return best?.let { if (intero) it.toDouble() else CedolinoParser.numero(it) }
    }

    fun parse(pagine: List<List<Pezzo>>, nomeFile: String = ""): CertificazioneUnica {
        val testo = pagine.joinToString(" ") { p -> p.joinToString(" ") { it.str } }
        var anno = Regex("RELATIVA\\s+ALL.?\\s*ANNO\\s+(\\d{4})", RegexOption.IGNORE_CASE).find(testo)?.groupValues?.get(1)?.toInt()
        var annoCu = Regex("UNICA\\s*(\\d{4})", RegexOption.IGNORE_CASE).find(testo)?.groupValues?.get(1)?.toInt()
        if (annoCu == null) annoCu = Regex("CU[_ -]?(\\d{4})", RegexOption.IGNORE_CASE).find(nomeFile)?.groupValues?.get(1)?.toInt()
        if (anno == null && annoCu != null) anno = annoCu - 1
        val sezioni = linkedMapOf<String, List<Pezzo>>()
        pagine.forEach { p -> for ((k, v) in sezioniDi(p)) if (k !in sezioni) sezioni[k] = v }
        val numeri = linkedMapOf<String, Double>()
        for (c in CAMPI) {
            val p = sezioni[c.sezione] ?: continue
            valore(p, c.punto, c.intero)?.let { numeri[c.chiave] = it }
        }
        fun r2(x: Double) = Math.round(x * 100) / 100.0
        val com = r2((numeri["addComAcconto"] ?: 0.0) + (numeri["addComSaldo"] ?: 0.0))
        if (com != 0.0) numeri["addComunale"] = com
        val arr = r2((numeri["arretratiConDetr"] ?: 0.0) + (numeri["arretratiSenzaDetr"] ?: 0.0))
        if (arr != 0.0) numeri["arretrati"] = arr
        return CertificazioneUnica(nomeFile, anno, annoCu, numeri, numeri["giorni"]?.toInt())
    }
}

/**
 * Dati per la stima del netto ricavati dai cedolini e dalle CU importate: reddito imponibile annuo,
 * aliquota INPS e addizionale comunale. Servono a compilare in automatico le impostazioni.
 */
object DatiFiscali {

    data class Proposta(
        val anno: Int,
        val reddito: Double?,
        val fonteReddito: String,
        val inpsPct: Double?,
        val addComunalePct: Double?
    )

    fun daCedolini(cedolini: List<Cedolino>, cu: List<CertificazioneUnica>): Proposta? {
        val ordinari = cedolini.filter { it.ordinario && it.anno != null }
            .sortedBy { it.anno!! * 100 + it.mese!! }
        val ultimo = ordinari.lastOrNull()
        val cuValida = cu.filter { it.valida }.sortedByDescending { it.anno }
        if (ultimo == null && cuValida.isEmpty()) return null

        // INPS a carico del lavoratore: trattenuta sul suo imponibile
        val inpsPct = ultimo?.let { u ->
            val imp = u.n("imponibileMese_inps") ?: u.n("imponibile_inps")
            val inps = u.n("inps")
            if (imp != null && imp > 0 && inps != null) Math.round(inps / imp * 10000) / 100.0 else null
        }
        val cuCom = cuValida.firstOrNull { it.addComunale != null }
        val comunale = cuCom?.let { Math.round(it.addComunale!! / it.reddito!! * 10000) / 100.0 }

        val anno = ultimo?.anno ?: cuValida.first().anno!!
        val cuAnno = cuValida.firstOrNull { it.anno == anno }
        val dicembre = ordinari.lastOrNull { it.anno == anno && it.mese == 12 && it.n("imponibileAnno_irpef") != null }
        if (cuAnno != null) return Proposta(anno, cuAnno.reddito, "CU $anno", inpsPct, comunale)
        if (dicembre != null) return Proposta(anno, dicembre.n("imponibileAnno_irpef"), "cedolino di dicembre", inpsPct, comunale)
        if (ultimo == null) return Proposta(cuValida.first().anno!!, cuValida.first().reddito, "CU ${cuValida.first().anno}", inpsPct, comunale)

        // stima dell'anno in corso dall'ultimo cedolino con i progressivi
        val conAnno = ordinari.filter { it.anno == anno && it.n("imponibileAnno_irpef") != null }
        val u = conAnno.lastOrNull() ?: return Proposta(anno, cuValida.firstOrNull()?.reddito, "ultima CU", inpsPct, comunale)
        val ultimi = conAnno.takeLast(3).mapNotNull { it.n("imponibileMese_irpef") }.filter { it > 0 }
        val media = if (ultimi.isEmpty()) 0.0 else ultimi.average()
        val baseC = u.componenti.values.sum() * (1 - (inpsPct ?: 9.19) / 100)
        val tredicesima = cedolini.firstOrNull { it.anno == anno && it.mese == 13 }
            ?.let { it.n("imponibileMese_tassSep") } ?: baseC
        val mese = u.mese!!
        val stima = u.n("imponibileAnno_irpef")!! + (12 - mese) * media + tredicesima + (if (mese < 7) baseC else 0.0)
        return Proposta(anno, Math.round(stima * 100) / 100.0, "stima da ${mese}/${anno}", inpsPct, comunale)
    }
}
