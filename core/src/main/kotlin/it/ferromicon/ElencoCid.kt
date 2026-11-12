package it.ferromicon

/** Una persona dell'elenco CID: CID, nominativo e impianto (vuoto se non noto). */
data class VoceCid(val cid: Long, val nominativo: String, val impianto: String)

/** Elenco CID in CSV con colonne CID;Cognome e nome;Impianto (prima riga di intestazione). */
object ElencoCid {

    fun leggi(testo: String): List<VoceCid> = testo.lines().drop(1).mapNotNull { riga ->
        val c = riga.split(';')
        if (c.size < 3) return@mapNotNull null
        val cid = c[0].trim().toLongOrNull() ?: return@mapNotNull null
        val nominativo = c[1].trim()
        if (nominativo.isEmpty()) return@mapNotNull null
        VoceCid(cid, nominativo, c[2].trim())
    }

    /** Cerca per CID (inizio) o per parte del nominativo, senza distinguere maiuscole. */
    fun cerca(elenco: List<VoceCid>, query: String, massimo: Int = 50): List<VoceCid> {
        val q = query.trim().uppercase()
        if (q.isEmpty()) return emptyList()
        return elenco.filter { it.cid.toString().startsWith(q) || it.nominativo.uppercase().contains(q) }.take(massimo)
    }
}
