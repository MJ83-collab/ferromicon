package it.ferromicon

import java.time.LocalDate

/** Un periodo di ferie programmate, identificato dalla lettera del gruppo. */
data class PeriodoFerie(val lettera: Char, val nome: String, val da: LocalDate, val a: LocalDate)

/**
 * Periodi di ferie programmate invernali, come da avviso aziendale 116/2026 e tabelle 2026-2027.
 * La lettera del gruppo (A–F) corrisponde allo stesso periodo sia in estate sia in inverno.
 * Per gli altri anni non ci sono dati: va inserita la tabella dell'avviso di quell'anno.
 */
object PeriodiFerie {
    private val invernali2026 = listOf(
        PeriodoFerie('A', "Natale", LocalDate.of(2026, 12, 21), LocalDate.of(2026, 12, 27)),
        PeriodoFerie('B', "Pasqua", LocalDate.of(2027, 3, 25), LocalDate.of(2027, 3, 31)),
        PeriodoFerie('C', "Epifania", LocalDate.of(2027, 1, 4), LocalDate.of(2027, 1, 10)),
        PeriodoFerie('D', "25 aprile", LocalDate.of(2027, 4, 25), LocalDate.of(2027, 5, 1)),
        PeriodoFerie('E', "Immacolata", LocalDate.of(2026, 12, 5), LocalDate.of(2026, 12, 11)),
        PeriodoFerie('F', "Capodanno", LocalDate.of(2026, 12, 28), LocalDate.of(2027, 1, 3)),
    )

    /** Tabelle per anno di inizio della stagione invernale (2026 = inverno 2026/2027). */
    private val tabelle: Map<Int, List<PeriodoFerie>> = mapOf(2026 to invernali2026)

    /** Il periodo invernale del gruppo, o null se la tabella di quell'anno non è inserita. */
    fun invernale(lettera: Char, annoInizio: Int): PeriodoFerie? =
        tabelle[annoInizio]?.firstOrNull { it.lettera == lettera.uppercaseChar() }
}
