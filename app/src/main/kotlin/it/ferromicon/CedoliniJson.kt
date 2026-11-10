package it.ferromicon

import org.json.JSONArray
import org.json.JSONObject

/** Conversione in JSON di cedolini e CU, per il salvataggio e il backup. */
object CedoliniJson {

    private fun mappa(m: Map<String, Double>) = JSONObject().apply { m.forEach { (k, v) -> put(k, v) } }

    private fun mappaDa(o: JSONObject?): Map<String, Double> {
        if (o == null) return emptyMap()
        val out = linkedMapOf<String, Double>()
        val chiavi = o.keys()
        while (chiavi.hasNext()) {
            val k = chiavi.next()
            out[k] = o.optDouble(k, 0.0)
        }
        return out
    }

    private fun JSONObject.doppio(chiave: String): Double? =
        if (has(chiave) && !isNull(chiave)) getDouble(chiave) else null

    private fun JSONObject.intero(chiave: String): Int? =
        if (has(chiave) && !isNull(chiave)) getInt(chiave) else null

    fun cedolino(c: Cedolino) = JSONObject().apply {
        put("file", c.file)
        c.anno?.let { put("anno", it) }
        c.mese?.let { put("mese", it) }
        put("periodo", c.periodoTesto)
        put("valuta", c.valuta)
        put("livello", c.livello)
        put("profilo", c.profilo)
        put("presenze", mappa(c.presenze))
        put("componenti", mappa(c.componenti))
        put("numeri", mappa(c.numeri))
        put("tfr", mappa(c.tfr))
        put("fondoMese", mappa(c.fondoMese))
        put("fondoAnno", mappa(c.fondoAnno))
        put("sommaCompetenze", c.sommaCompetenze)
        put("sommaTrattenute", c.sommaTrattenute)
        put("controlloOk", c.controlloOk)
        put("anagrafica", anagrafica(c.anagrafica))
        put("voci", JSONArray().apply {
            c.voci.forEach { v ->
                put(JSONObject().apply {
                    put("codice", v.codice); put("flag", v.flag); put("descrizione", v.descrizione)
                    v.valFig?.let { put("valFig", it) }
                    v.param?.let { put("param", it) }
                    v.compUnit?.let { put("compUnit", it) }
                    v.trattenuta?.let { put("trattenuta", it) }
                    v.competenza?.let { put("competenza", it) }
                })
            }
        })
    }

    fun cedolinoDa(o: JSONObject) = Cedolino(
        file = o.optString("file", ""),
        anno = o.intero("anno"),
        mese = o.intero("mese"),
        periodoTesto = o.optString("periodo", ""),
        valuta = o.optString("valuta", ""),
        livello = o.optString("livello", ""),
        profilo = o.optString("profilo", ""),
        presenze = mappaDa(o.optJSONObject("presenze")),
        componenti = mappaDa(o.optJSONObject("componenti")),
        voci = (o.optJSONArray("voci") ?: JSONArray()).let { a ->
            List(a.length()) {
                val v = a.getJSONObject(it)
                VoceCedolino(
                    codice = v.optString("codice", ""), flag = v.optString("flag", ""),
                    descrizione = v.optString("descrizione", ""),
                    valFig = v.doppio("valFig"), param = v.doppio("param"), compUnit = v.doppio("compUnit"),
                    trattenuta = v.doppio("trattenuta"), competenza = v.doppio("competenza")
                )
            }
        },
        numeri = mappaDa(o.optJSONObject("numeri")),
        tfr = mappaDa(o.optJSONObject("tfr")),
        fondoMese = mappaDa(o.optJSONObject("fondoMese")),
        fondoAnno = mappaDa(o.optJSONObject("fondoAnno")),
        sommaCompetenze = o.optDouble("sommaCompetenze", 0.0),
        sommaTrattenute = o.optDouble("sommaTrattenute", 0.0),
        controlloOk = o.optBoolean("controlloOk", false),
        anagrafica = anagraficaDa(o.optJSONObject("anagrafica"))
    )

    private fun anagrafica(a: AnagraficaCedolino) = JSONObject().apply {
        put("cid", a.cid)
        a.dataAssunzione?.let { put("assunzione", it.toString()) }
        a.livello?.let { put("livello", it.name) }
        put("livelloTesto", a.livelloTesto)
        a.profilo?.let { put("profilo", it.name) }
        put("profiloTesto", a.profiloTesto)
        put("residenza", a.residenza)
        a.apa?.let { put("apa", it) }
        a.partTime?.let { put("partTime", it) }
        a.adPersonam?.let { put("adPersonam", it) }
        a.feriePrecedenti?.let { put("feriePrecedenti", it) }
        a.ferieCorrenti?.let { put("ferieCorrenti", it) }
    }

    private fun anagraficaDa(o: JSONObject?): AnagraficaCedolino {
        if (o == null) return AnagraficaCedolino()
        return AnagraficaCedolino(
            cid = o.optString("cid", ""),
            dataAssunzione = o.optString("assunzione", "").takeIf { it.isNotEmpty() }
                ?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() },
            livello = Livello.values().firstOrNull { it.name == o.optString("livello", "") },
            livelloTesto = o.optString("livelloTesto", ""),
            profilo = Profilo.values().firstOrNull { it.name == o.optString("profilo", "") },
            profiloTesto = o.optString("profiloTesto", ""),
            residenza = o.optString("residenza", ""),
            apa = o.intero("apa"),
            partTime = o.doppio("partTime"),
            adPersonam = o.doppio("adPersonam"),
            feriePrecedenti = o.doppio("feriePrecedenti"),
            ferieCorrenti = o.doppio("ferieCorrenti")
        )
    }

    fun cu(c: CertificazioneUnica) = JSONObject().apply {
        put("file", c.file)
        c.anno?.let { put("anno", it) }
        c.annoCu?.let { put("annoCu", it) }
        c.giorni?.let { put("giorni", it) }
        put("numeri", mappa(c.numeri))
    }

    fun cuDa(o: JSONObject) = CertificazioneUnica(
        file = o.optString("file", ""),
        anno = o.intero("anno"),
        annoCu = o.intero("annoCu"),
        numeri = mappaDa(o.optJSONObject("numeri")),
        giorni = o.intero("giorni")
    )
}
