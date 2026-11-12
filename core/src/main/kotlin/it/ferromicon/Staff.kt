package it.ferromicon

/**
 * Regole del personale di staff (ufficio), dal Vademecum Trenord Employee Self Service.
 * Gli orari sono in minuti dalla mezzanotte.
 */
object OrarioStaff {
    /** Prestazione ordinaria giornaliera: 7 h 36' (sul cartellino "7,36"). */
    const val PRESTAZIONE_MINUTI = 7 * 60 + 36

    /** Ingresso tra 08:15 e 09:00. */
    const val INGRESSO_DA = 8 * 60 + 15
    const val INGRESSO_A = 9 * 60

    /** Fascia rigida: mattina 09:00-12:30, pomeriggio 14:00-16:21. */
    const val RIGIDA_MATTINA_DA = 9 * 60
    const val RIGIDA_MATTINA_A = 12 * 60 + 30
    const val RIGIDA_POMERIGGIO_DA = 14 * 60
    const val RIGIDA_POMERIGGIO_A = 16 * 60 + 21

    /** Uscita tra 16:21 e 17:06. */
    const val USCITA_DA = 16 * 60 + 21
    const val USCITA_A = 17 * 60 + 6

    /** Pausa pranzo di 30' da fruire tra 12:30 e 14:00. */
    const val PAUSA_MINUTI = 30

    /** Flessibilità mensile: saldo entro ±5 h. */
    const val FLESSIBILITA_MAX = 5 * 60

    /**
     * Riporto al mese successivo di un saldo di flessibilità, in minuti (anche negativo).
     * Restituisce (riportato, eccedenza). Il saldo positivo oltre +5 h si riporta come +5 h
     * e l'eccedenza va persa. Il saldo negativo oltre -5 h si riporta come -5 h e
     * l'eccedenza (negativa) viene trattenuta in busta paga.
     */
    fun riportoFlessibilita(saldo: Int): Pair<Int, Int> {
        val riportato = saldo.coerceIn(-FLESSIBILITA_MAX, FLESSIBILITA_MAX)
        return riportato to (saldo - riportato)
    }
}
