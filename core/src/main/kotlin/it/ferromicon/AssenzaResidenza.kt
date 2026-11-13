package it.ferromicon

import java.time.LocalDate

/**
 * Compenso orario per l'assenza dalla residenza del personale mobile (CCNL Mobilità/Area AF 2025, art. 77.2),
 * in vigore dal 1° agosto 2025. Per il periodo precedente non ci sono valori verificati: restituisce null.
 */
object AssenzaResidenza {

    private val DAL: LocalDate = LocalDate.of(2025, 8, 1)

    /**
     * Euro per ora di assenza dalla residenza.
     * - Italia, senza riposo fuori residenza: 1,60 €; con riposo fuori residenza: 2,60 €.
     * - Estero, senza riposo: 2,00 €; con riposo: 3,20 €.
     * - Accompagnamento notte: 1,00 € in Italia, 1,60 € all'estero.
     */
    fun tariffaOraria(conRiposo: Boolean, estero: Boolean, accompagnamentoNotte: Boolean, data: LocalDate): Double? {
        if (data.isBefore(DAL)) return null
        return when {
            accompagnamentoNotte -> if (estero) 1.60 else 1.00
            estero -> if (conRiposo) 3.20 else 2.00
            else -> if (conRiposo) 2.60 else 1.60
        }
    }
}
