package it.ferromicon

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

/**
 * Calendari degli agenti e giornate di turno importati dai PDF. Restano in un file dell'app,
 * separato dall'agenda: contengono nomi di colleghi e si possono cancellare con un tocco.
 */
object AgentiArchivio {

    private fun fileCalendari(ctx: Context) = File(ctx.filesDir, "agenti_calendari.json")
    private fun fileGiornate(ctx: Context) = File(ctx.filesDir, "agenti_giornate.json")

    fun salvaCalendari(ctx: Context, calendari: List<CalendarioTurnoAgenti>) {
        val a = JSONArray()
        calendari.forEach { c ->
            a.put(JSONObject().apply {
                put("turno", c.turno); put("profilo", c.profilo)
                put("dal", c.dal.toString()); put("al", c.al.toString()); put("periodo", c.periodoCiclo)
                put("righe", JSONArray().apply {
                    c.righe.forEach { r ->
                        put(JSONObject().apply {
                            put("p", r.posizione); put("n", r.nome); put("c", r.cid); put("t", r.tabella)
                            put("k", JSONArray().apply { r.codici.forEach { put(it) } })
                        })
                    }
                })
            })
        }
        fileCalendari(ctx).writeText(a.toString())
    }

    fun caricaCalendari(ctx: Context): List<CalendarioTurnoAgenti> = runCatching {
        val a = JSONArray(fileCalendari(ctx).readText())
        List(a.length()) { i ->
            val o = a.getJSONObject(i)
            val righe = o.getJSONArray("righe")
            CalendarioTurnoAgenti(
                o.getString("turno"), o.optString("profilo", "Scorta"),
                LocalDate.parse(o.getString("dal")), LocalDate.parse(o.getString("al")),
                List(righe.length()) { j ->
                    val r = righe.getJSONObject(j)
                    val k = r.getJSONArray("k")
                    RigaAgente(r.getInt("p"), r.getString("n"), r.getString("c"), List(k.length()) { k.getString(it) }, r.optInt("t", 0))
                },
                o.getInt("periodo")
            )
        }
    }.getOrDefault(emptyList())

    /** Aggiunge le giornate di turno lette, sostituendo quelle degli stessi turni. */
    fun salvaGiornate(ctx: Context, nuove: List<GiornataDefinita>) {
        val turni = nuove.map { it.turno }.toSet()
        val tutte = caricaGiornate(ctx).filterNot { it.turno in turni } + nuove
        val a = JSONArray()
        tutte.forEach { g ->
            a.put(JSONObject().apply {
                put("t", g.turno); put("pr", g.profilo); put("n", g.numero); put("g", g.tipo.name)
                put("i", g.inizio.toString()); put("f", g.fine.toString()); put("l", g.lavoroMin); put("s", g.condottaMin)
                put("km", g.km); put("no", g.notturno); put("r", g.riposoMin); put("rf", g.refezione)
                put("tr", JSONArray().apply {
                    g.treni.forEach { t -> put(JSONObject().apply { put("n", t.numero); put("d", t.destinazione); put("v", t.vettura) }) }
                })
            })
        }
        fileGiornate(ctx).writeText(a.toString())
    }

    fun caricaGiornate(ctx: Context): List<GiornataDefinita> = runCatching {
        val a = JSONArray(fileGiornate(ctx).readText())
        List(a.length()) { i ->
            val o = a.getJSONObject(i)
            val tr = o.getJSONArray("tr")
            GiornataDefinita(
                o.getString("t"), o.optString("pr", ""), o.getString("n"), TipoGiorno.valueOf(o.getString("g")),
                LocalTime.parse(o.getString("i")), LocalTime.parse(o.getString("f")), o.getInt("l"), o.getInt("s"),
                o.getInt("km"), o.getBoolean("no"), o.getInt("r"), o.getBoolean("rf"),
                List(tr.length()) { j ->
                    val t = tr.getJSONObject(j)
                    TrenoGiornata(t.getString("n"), t.optString("d", ""), t.optBoolean("v", false))
                }
            )
        }
    }.getOrDefault(emptyList())

    private fun fileRotativo(ctx: Context) = File(ctx.filesDir, "agenti_rotativo.txt")

    /** Rotativo dei riposi: una riga per data, "aaaa-mm-gg;codice;...;codice" (sette colonne). */
    fun salvaRotativo(ctx: Context, rot: Rotativo) {
        fileRotativo(ctx).writeText(rot.giorni.entries.joinToString("\n") { (d, c) ->
            d.toString() + ";" + c.joinToString(";")
        })
    }

    fun caricaRotativo(ctx: Context): Rotativo? = runCatching {
        val mappa = LinkedHashMap<LocalDate, List<String>>()
        fileRotativo(ctx).readLines().forEach { riga ->
            val p = riga.split(";")
            if (p.size == 8) mappa[LocalDate.parse(p[0])] = p.drop(1)
        }
        if (mappa.isEmpty()) null else Rotativo(mappa)
    }.getOrNull()

    fun cancella(ctx: Context) {
        fileCalendari(ctx).delete(); fileGiornate(ctx).delete(); fileRotativo(ctx).delete()
    }
}
