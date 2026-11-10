package it.ferromicon

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

/** Diari di bordo importati: un file per mese nella memoria privata dell'app, mai fuori dal telefono. */
object DiarioArchivio {

    private fun cartella(ctx: Context) = File(ctx.filesDir, "diari").apply { mkdirs() }

    private fun file(ctx: Context, mese: YearMonth) = File(cartella(ctx), "diario-$mese.json")

    fun salva(ctx: Context, d: Diario): Boolean {
        val mese = d.mese ?: return false
        file(ctx, mese).writeText(aJson(d).toString())
        return true
    }

    /** Dal mese più recente. */
    fun elenco(ctx: Context): List<Diario> =
        (cartella(ctx).listFiles() ?: emptyArray())
            .filter { it.name.startsWith("diario-") && it.name.endsWith(".json") }
            .mapNotNull { f -> runCatching { daJson(JSONObject(f.readText())) }.getOrNull() }
            .sortedByDescending { it.mese?.toString() ?: "" }

    fun cancellaTutti(ctx: Context) {
        cartella(ctx).listFiles()?.forEach { it.delete() }
    }

    private fun JSONObject.intero(k: String): Int? = if (has(k) && !isNull(k)) getInt(k) else null

    fun aJson(d: Diario) = JSONObject().apply {
        put("matricola", d.matricola)
        put("agente", d.agente)
        put("settore", d.settore)
        put("residenza", d.residenza)
        d.mese?.let { put("mese", it.toString()) }
        d.totaleStampato?.let { t ->
            put("totale", JSONObject().apply {
                put("condotta", t.condotta); put("lavoro", t.lavoro); put("notturno", t.lavoroNotturno)
                put("straordinario", t.straordinario); put("fuori", t.fuoriResidenza)
            })
        }
        put("giorni", JSONArray().apply {
            d.giorni.forEach { g ->
                put(JSONObject().apply {
                    put("data", g.data.toString())
                    put("turno", g.turno)
                    put("descrizione", g.descrizione)
                    put("condotta", g.condotta); put("lavoro", g.lavoro); put("notturno", g.lavoroNotturno)
                    put("straordinario", g.straordinario); put("fuori", g.fuoriResidenza)
                    put("rfr", g.rfr); put("lavoroInRiposo", g.lavoroInRiposo)
                    put("attivita", JSONArray().apply {
                        g.attivita.forEach { a ->
                            put(JSONObject().apply {
                                put("tipo", a.tipo); put("treno", a.treno)
                                a.prescrittoPartenza?.let { put("pp", it) }
                                a.prescrittoArrivo?.let { put("pa", it) }
                                a.realePartenza?.let { put("rp", it) }
                                a.realeArrivo?.let { put("ra", it) }
                                put("ap", a.accessoriePartenza); put("aa", a.accessorieArrivo)
                                put("da", a.da); put("a", a.a)
                            })
                        }
                    })
                })
            }
        })
    }

    fun daJson(o: JSONObject): Diario {
        val giorni = o.getJSONArray("giorni")
        return Diario(
            matricola = o.optString("matricola", ""),
            agente = o.optString("agente", ""),
            settore = o.optString("settore", ""),
            residenza = o.optString("residenza", ""),
            mese = o.optString("mese", "").takeIf { it.isNotEmpty() }?.let { YearMonth.parse(it) },
            giorni = List(giorni.length()) { i ->
                val g = giorni.getJSONObject(i)
                val att = g.optJSONArray("attivita") ?: JSONArray()
                GiornoDiario(
                    data = LocalDate.parse(g.getString("data")),
                    turno = g.optString("turno", ""),
                    descrizione = g.optString("descrizione", ""),
                    attivita = List(att.length()) { j ->
                        val a = att.getJSONObject(j)
                        AttivitaDiario(a.optString("tipo", ""), a.optString("treno", ""),
                            a.intero("pp"), a.intero("pa"), a.intero("rp"), a.intero("ra"),
                            a.optInt("ap", 0), a.optInt("aa", 0), a.optString("da", ""), a.optString("a", ""))
                    },
                    condotta = g.optInt("condotta", 0), lavoro = g.optInt("lavoro", 0),
                    lavoroNotturno = g.optInt("notturno", 0), straordinario = g.optInt("straordinario", 0),
                    fuoriResidenza = g.optInt("fuori", 0), rfr = g.optBoolean("rfr", false),
                    lavoroInRiposo = g.optInt("lavoroInRiposo", 0)
                )
            },
            totaleStampato = o.optJSONObject("totale")?.let { t ->
                TotaliDiario(t.optInt("condotta", 0), t.optInt("lavoro", 0), t.optInt("notturno", 0),
                    t.optInt("straordinario", 0), t.optInt("fuori", 0))
            }
        )
    }
}
