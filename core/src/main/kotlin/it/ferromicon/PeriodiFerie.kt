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

    /** Ordine ciclico dei gruppi nelle stagioni programmate (accordo 4 aprile 2016, sequenza estate 2026). */
    private const val SEQUENZA = "DACEFB"

    /**
     * Lettera del gruppo per un anno, data la lettera dell'anno di partenza (2026).
     * Il gruppo D del 2026 diventa A nel 2027, C nel 2028, E nel 2029, F nel 2030, B nel 2031, D nel 2032.
     */
    fun letteraPerAnno(letteraDi2026: Char, anno: Int): Char {
        val inizio = SEQUENZA.indexOf(letteraDi2026.uppercaseChar())
        require(inizio >= 0) { "Lettera di gruppo non valida: $letteraDi2026" }
        val passi = Math.floorMod(anno - 2026, SEQUENZA.length)
        return SEQUENZA[Math.floorMod(inizio + passi, SEQUENZA.length)]
    }

    /**
     * Piano di un anno: lettera del gruppo e, se la tabella c'è, periodo invernale.
     * I riposi della sequenza sono contati nel periodo e valgono solo fino al 07/01/2028 verificato:
     * oltre, il calcolo presuppone che il ciclo continui.
     */
    fun piano(anno: Int, letteraDi2026: Char, sequenza: Char): PianoAnno {
        val lettera = letteraPerAnno(letteraDi2026, anno)
        val periodo = invernale(lettera, anno)
        val riposi = periodo?.let { CalendarioRiposi.riposi(sequenza, it.da, it.a) }
        val verificato = periodo?.let { !it.a.isAfter(CalendarioRiposi.ultimoVerificato) } ?: false
        return PianoAnno(anno, lettera, periodo, riposi, verificato)
    }
}

/** Piano di un anno: lettera, periodo (se noto), riposi nel periodo e se è nel calendario verificato. */
data class PianoAnno(
    val anno: Int,
    val lettera: Char,
    val periodo: PeriodoFerie?,
    val riposiNelPeriodo: Int?,
    val riposiVerificati: Boolean,
)
