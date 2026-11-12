package it.ferromicon

import org.json.JSONArray
import org.json.JSONObject

/** Salvataggio dei cartellini SAP nell'archivio JSON (e nelle copie di sicurezza). */
object CartellinoSapJson {

    fun aJson(c: CartellinoSap): JSONObject = JSONObject().apply {
        put("anno", c.anno)
        put("mese", c.mese)
        put("flexMesePrecedente", c.flexMesePrecedente ?: JSONObject.NULL)
        val giornate = JSONArray()
        for (g in c.giornate) {
            giornate.put(JSONObject().apply {
                put("giorno", g.giorno)
                put("sigla", g.sigla)
                put("prima", g.prima ?: JSONObject.NULL)
                put("ultima", g.ultima ?: JSONObject.NULL)
                put("flex", g.flex ?: JSONObject.NULL)
            })
        }
        put("giornate", giornate)
        val righe = JSONArray()
        for (r in c.righeGrezze) righe.put(r)
        put("righe", righe)
    }

    fun daJson(o: JSONObject): CartellinoSap {
        val giornate = mutableListOf<GiornataSap>()
        val a = o.optJSONArray("giornate")
        if (a != null) for (i in 0 until a.length()) {
            val g = a.getJSONObject(i)
            giornate += GiornataSap(
                giorno = g.optInt("giorno"),
                sigla = g.optString("sigla"),
                prima = g.optString("prima", "").ifBlank { null },
                ultima = g.optString("ultima", "").ifBlank { null },
                flex = if (g.isNull("flex")) null else g.getDouble("flex")
            )
        }
        val righe = mutableListOf<String>()
        val r = o.optJSONArray("righe")
        if (r != null) for (i in 0 until r.length()) righe += r.getString(i)
        return CartellinoSap(
            anno = o.optInt("anno"),
            mese = o.optInt("mese"),
            giornate = giornate,
            flexMesePrecedente = if (o.isNull("flexMesePrecedente")) null else o.getDouble("flexMesePrecedente"),
            righeGrezze = righe
        )
    }
}
