package it.ferromicon

import java.time.Duration
import java.time.LocalDateTime
import java.text.Normalizer

/**
 * Perimetro di residenza: le stazioni che contano come "in residenza" per il calcolo
 * delle ore di assenza (CCNL art. 77.2: dal primo comune fuori residenza al rientro).
 */
data class Perimetro(
    val id: Long,
    val nome: String,
    val stazioni: List<String> = emptyList(),
    val attivo: Boolean = true
)

object Residenza {

    /** Nome confrontabile: senza maiuscole, accenti, punteggiatura e spazi doppi. */
    fun normalizza(nome: String): String =
        Normalizer.normalize(nome.trim().lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    /** Stazioni dei perimetri attivi, normalizzate. */
    fun stazioniAttive(perimetri: List<Perimetro>): Set<String> =
        perimetri.filter { it.attivo }.flatMap { it.stazioni }.map { normalizza(it) }.filter { it.isNotEmpty() }.toSet()

    fun dentro(stazione: String, perimetri: List<Perimetro>): Boolean {
        val n = normalizza(stazione)
        return n.isNotEmpty() && n in stazioniAttive(perimetri)
    }

    /**
     * Minuti di assenza dalla residenza di un servizio, calcolati dalle righe treno:
     * dalla partenza della prima riga che esce dal perimetro all'arrivo dell'ultima riga che
     * rientra. Se il servizio non rientra (riposo fuori residenza) conta fino all'ultimo arrivo.
     * Restituisce 0 se mancano perimetri, stazioni sulle righe o uscite dal perimetro.
     * Usa gli orari reali quando ci sono.
     */
    fun minutiFuori(righe: List<RigaTreno>, perimetri: List<Perimetro>): Int {
        if (righe.isEmpty() || stazioniAttive(perimetri).isEmpty()) return 0
        val ordinate = righe.filter { it.da.isNotBlank() && it.a.isNotBlank() }
            .sortedBy { it.inizioEffettivo }
        val uscita = ordinate.firstOrNull { dentro(it.da, perimetri) && !dentro(it.a, perimetri) }
            ?: ordinate.firstOrNull { !dentro(it.da, perimetri) }
            ?: return 0
        val inizio: LocalDateTime = uscita.inizioEffettivo
        val dopo = ordinate.filter { !it.inizioEffettivo.isBefore(inizio) }
        val rientro = dopo.lastOrNull { dentro(it.a, perimetri) }
        val fine = (rientro ?: dopo.last()).fineEffettiva
        return maxOf(0L, Duration.between(inizio, fine).toMinutes()).toInt()
    }

    /** Il servizio con le ore fuori residenza ricalcolate dalle righe, se il calcolo dà un risultato. */
    fun conFuoriResidenza(s: Servizio, perimetri: List<Perimetro>): Servizio {
        val m = minutiFuori(s.righe, perimetri)
        return if (m > 0) s.copy(minutiFuoriResidenza = m) else s
    }
}
