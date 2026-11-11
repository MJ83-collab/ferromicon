package it.ferromicon

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Stato di recupero di una festività dell'anno. */
data class StatoFestivo(
    val data: LocalDate,
    val nome: String,
    /** Ultimo giorno utile per il recupero. */
    val scadenza: LocalDate,
    /** Giorni alla scadenza (negativo se scaduto). */
    val giorniAllaScadenza: Long,
    /** Il recupero è ancora possibile (oppure forzato a mano). */
    val recuperabile: Boolean,
    /** Scelta manuale dell'utente, se presente. */
    val forzato: Boolean?,
    /** Frazione dei 90 giorni già trascorsa, da 0 a 1, per la barra di avanzamento. */
    val avanzamento: Double
) {
    val scaduto get() = giorniAllaScadenza < 0
}

/**
 * Scadenze di recupero delle festività.
 *
 * Gli artt. 29.2.4 e 29.2.5 CCNL) indica 90 giorni per le giornate di riposo
 * che coincidono con un festivo; per le festività lavorate indica 30 o 60 giorni "se richiesto
 * entro i termini". Come per l'indennità, l'app usa un solo termine, 90 giorni, che si può
 * correggere a mano con l'interruttore di ogni festività.
 */
object RecuperoFestivi {

    const val GIORNI_RECUPERO = 90L

    /** Festività dell'anno che danno diritto al recupero (esclusa la domenica di Pasqua). */
    fun stati(anno: Int, oggi: LocalDate, forzati: Map<LocalDate, Boolean> = emptyMap()): List<StatoFestivo> =
        Festivi.dellAnno(anno).filterKeys { Festivi.èFestivoContrattuale(it) }.map { (data, nome) ->
            val scadenza = data.plusDays(GIORNI_RECUPERO)
            val giorni = ChronoUnit.DAYS.between(oggi, scadenza)
            val trascorsi = ChronoUnit.DAYS.between(data, oggi).coerceIn(0, GIORNI_RECUPERO)
            val forzato = forzati[data]
            StatoFestivo(
                data = data, nome = nome, scadenza = scadenza, giorniAllaScadenza = giorni,
                recuperabile = forzato ?: (giorni >= 0), forzato = forzato,
                avanzamento = if (oggi.isBefore(data)) 0.0 else trascorsi.toDouble() / GIORNI_RECUPERO
            )
        }.sortedBy { it.data }

    /** Testo di stato come "Scaduto da 71 gg", "Recuperabile · scadenza tra 53 gg", "scade il 07/03/2027". */
    fun descrizione(s: StatoFestivo, oggi: LocalDate): String {
        val f = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")
        return when {
            s.forzato == true && s.scaduto -> "Recuperabile (forzato) · scaduto da ${-s.giorniAllaScadenza} gg"
            s.forzato == false -> "Non recuperabile (forzato)"
            s.scaduto -> "Scaduto da ${-s.giorniAllaScadenza} gg"
            oggi.isBefore(s.data) -> "Recuperabile · scade il ${f.format(s.scadenza)}"
            else -> "Recuperabile · scadenza tra ${s.giorniAllaScadenza} gg"
        }
    }

    /** Recuperi ancora esigibili nell'anno: festività lavorate, recuperabili, non ancora godute. */
    fun daRecuperare(anno: Int, oggi: LocalDate, forzati: Map<LocalDate, Boolean>, servizi: List<Servizio>): List<StatoFestivo> {
        val lavorate = servizi.flatMap { s ->
            Competenze.minutiPerGiorno(s.turno.inizio, s.turno.fine).keys
        }.toSet()
        return stati(anno, oggi, forzati).filter { it.data in lavorate && it.recuperabile }
    }
}
