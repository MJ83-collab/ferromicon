package it.ferromicon

import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Esportazione e importazione di tutti i dati in CSV, uno per tipo, dentro un unico zip.
 * Ogni riga ha colonne leggibili (id, date, nomi) e, all'ultimo posto, il JSON completo della voce:
 * così si reimporta esattamente quello che si è esportato, anche i campi che il foglio non mostra.
 * Separatore «;», testo tra virgolette, UTF-8 con BOM (Excel lo apre correttamente).
 */
object ScambioCsv {

    const val NOME_ZIP = "ferromicon-dati.zip"

    private fun campo(t: String) = "\"" + t.replace("\r", " ").replace("\n", " ").replace("\"", "\"\"") + "\""

    private fun file(intestazione: String, righe: List<List<String>>): ByteArray {
        val sb = StringBuilder("﻿")
        sb.append(intestazione).append("\r\n")
        righe.forEach { r -> sb.append(r.joinToString(";") { campo(it) }).append("\r\n") }
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    /** Un file CSV per ogni tipo di dato. */
    fun tabelle(d: Dati): Map<String, ByteArray> {
        val m = LinkedHashMap<String, ByteArray>()
        m["servizi.csv"] = file("id;inizio;fine;profilo;json", d.servizi.sortedBy { it.turno.inizio }.map {
            listOf(it.id.toString(), it.turno.inizio.toString(), it.turno.fine.toString(), it.turno.profilo.name,
                Archivio.servizioAJson(it).toString())
        })
        m["assenze.csv"] = file("id;data;tipo;json", d.assenze.sortedBy { it.data }.map {
            listOf(it.id.toString(), it.data.toString(), it.tipo.name, Archivio.assenzaAJson(it).toString())
        })
        m["impostazioni.csv"] = file("json", listOf(listOf(Archivio.impostazioniAJson(d.impostazioni).toString())))
        m["preferiti.csv"] = file("id;nome;json", d.preferiti.map {
            listOf(it.id.toString(), it.nome, Archivio.preferitoAJson(it).toString())
        })
        m["stazioni.csv"] = file("id;nome;json", d.stazioni.map { s ->
            listOf(s.id.toString(), s.nome, JSONObject().apply {
                put("id", s.id); put("nome", s.nome); put("sigla", s.sigla); put("telefono", s.telefono); put("note", s.note)
            }.toString())
        })
        m["perimetri.csv"] = file("id;nome;json", d.perimetri.map { p ->
            listOf(p.id.toString(), p.nome, JSONObject().apply {
                put("id", p.id); put("nome", p.nome); put("attivo", p.attivo); put("stazioni", JSONArray(p.stazioni))
            }.toString())
        })
        m["cedolini.csv"] = file("anno;mese;file;json", d.cedolini.map { c ->
            listOf(c.anno?.toString() ?: "", c.mese?.toString() ?: "", c.file, CedoliniJson.cedolino(c).toString())
        })
        m["cu.csv"] = file("anno;file;json", d.certificazioniUniche.map { c ->
            listOf(c.anno?.toString() ?: "", c.file, CedoliniJson.cu(c).toString())
        })
        m["cartellini.csv"] = file("anno;mese;json", d.cartellini.map { c ->
            listOf(c.anno.toString(), c.mese.toString(), CartellinoSapJson.aJson(c).toString())
        })
        d.turno?.let { t -> m["turno.csv"] = file("json", listOf(listOf(Archivio.turnoAJson(t).toString()))) }
        return m
    }

    fun zip(tabelle: Map<String, ByteArray>, out: OutputStream) {
        ZipOutputStream(out).use { z ->
            tabelle.forEach { (nome, bytes) ->
                z.putNextEntry(ZipEntry(nome))
                z.write(bytes)
                z.closeEntry()
            }
        }
    }

    /** Legge lo zip e restituisce il testo di ogni file CSV. */
    fun leggiZip(input: InputStream): Map<String, String> {
        val m = HashMap<String, String>()
        ZipInputStream(input).use { z ->
            var e = z.nextEntry
            while (e != null) {
                if (!e.isDirectory && e.name.endsWith(".csv")) m[e.name] = z.readBytes().toString(Charsets.UTF_8)
                e = z.nextEntry
            }
        }
        return m
    }

    /** Divide una riga CSV rispettando le virgolette e i doppi apici. */
    private fun campi(riga: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var dentro = false
        var i = 0
        while (i < riga.length) {
            val c = riga[i]
            when {
                dentro && c == '"' && i + 1 < riga.length && riga[i + 1] == '"' -> { sb.append('"'); i++ }
                c == '"' -> dentro = !dentro
                c == ';' && !dentro -> { out += sb.toString(); sb.clear() }
                else -> sb.append(c)
            }
            i++
        }
        out += sb.toString()
        return out
    }

    /** Righe di dati di un CSV (senza intestazione), come JSON dall'ultima colonna. */
    private fun json(file: Map<String, String>, nome: String): List<JSONObject> {
        val testo = file[nome] ?: return emptyList()
        return testo.removePrefix("﻿").split("\n").map { it.trimEnd('\r') }
            .drop(1).filter { it.isNotBlank() }
            .map { JSONObject(campi(it).last()) }
    }

    /** Aggiunge o sostituisce i dati letti dai CSV; le voci con lo stesso identificativo vengono sostituite. */
    fun importa(base: Dati, file: Map<String, String>): Dati {
        val servizi = json(file, "servizi.csv").map { Archivio.servizioDa(it) }
        val assenze = json(file, "assenze.csv").map { Archivio.assenzaDa(it) }
        val preferiti = json(file, "preferiti.csv").map { Archivio.preferitoDa(it) }
        val stazioni = json(file, "stazioni.csv").map { o ->
            Stazione(id = o.getLong("id"), nome = o.optString("nome"), sigla = o.optString("sigla"),
                telefono = o.optString("telefono"), note = o.optString("note"))
        }
        val perimetri = json(file, "perimetri.csv").map { o ->
            Perimetro(id = o.getLong("id"), nome = o.optString("nome"),
                stazioni = o.optJSONArray("stazioni")?.let { a -> List(a.length()) { a.getString(it) } } ?: emptyList(),
                attivo = o.optBoolean("attivo", true))
        }
        val cedolini = json(file, "cedolini.csv").map { CedoliniJson.cedolinoDa(it) }
        val cu = json(file, "cu.csv").map { CedoliniJson.cuDa(it) }
        val cartellini = json(file, "cartellini.csv").map { CartellinoSapJson.daJson(it) }
        val impostazioni = json(file, "impostazioni.csv").firstOrNull()?.let { Archivio.impostazioniDa(it) }
        val turno = json(file, "turno.csv").firstOrNull()?.let { Archivio.turnoDa(it) }

        fun <T, K> unisci(vecchi: List<T>, nuovi: List<T>, chiave: (T) -> K): List<T> {
            if (nuovi.isEmpty()) return vecchi
            val chiavi = nuovi.map(chiave).toSet()
            return vecchi.filterNot { chiave(it) in chiavi } + nuovi
        }

        return base.copy(
            servizi = unisci(base.servizi, servizi) { it.id },
            assenze = unisci(base.assenze, assenze) { it.id },
            preferiti = unisci(base.preferiti, preferiti) { it.id },
            stazioni = unisci(base.stazioni, stazioni) { it.id },
            perimetri = unisci(base.perimetri, perimetri) { it.id },
            cedolini = unisci(base.cedolini, cedolini) { it.anno to it.mese },
            certificazioniUniche = unisci(base.certificazioniUniche, cu) { it.anno },
            cartellini = unisci(base.cartellini, cartellini) { it.anno to it.mese },
            impostazioni = impostazioni ?: base.impostazioni,
            turno = turno ?: base.turno
        )
    }
}
