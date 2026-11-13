package it.ferromicon

import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Limiti di ore del personale mobile (CCNL Mobilità/Area AF 2025, art. 27). */
object LimitiOre {

    const val ORE_SETTIMANALI_MEDIE = 38.0
    const val LIMITE_SETTIMANALE_PROGRAMMAZIONE = 44.0
    const val LIMITE_SETTIMANALE_DISPONIBILITA = 38.0

    /** Ore massime nel mese su media di 38 ore settimanali: giorni × 38 / 7. */
    fun limiteMensile(giorniDelMese: Int): Double = giorniDelMese * ORE_SETTIMANALI_MEDIE / 7.0

    /**
     * Settimane (dal lunedì) in cui le ore superano [limite]. Restituisce lunedì e ore di quella settimana.
     * Ogni intervallo è (inizio, fine) di un servizio.
     */
    fun settimaneOltre(intervalli: List<Pair<LocalDateTime, LocalDateTime>>, limite: Double): List<Pair<LocalDate, Double>> =
        intervalli
            .groupBy { it.first.toLocalDate().with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)) }
            .map { (lunedi, lista) ->
                lunedi to lista.sumOf { Duration.between(it.first, it.second).toMinutes() } / 60.0
            }
            .filter { it.second > limite }
            .sortedBy { it.first }
}
