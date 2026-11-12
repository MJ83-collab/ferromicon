package it.ferromicon

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Calendario rotativo dei riposi (sequenze A–G), verificato sul calendario 2026-2027 fino al 07/01/2028.
 * Ogni colonna segue un ciclo di 7 giorni, spostato di un passo ogni blocco di 28 giorni.
 * Oltre il 07/01/2028 la regola non è verificata: va controllata col calendario successivo.
 */
object CalendarioRiposi {
    private val ancora: LocalDate = LocalDate.of(2025, 12, 21)
    private const val SEQUENZE = "ABCDEFG"
    private val ciclo = listOf("5", "GRS", "RIP", "1", "2", "3", "4")

    /** Codice del giorno per la sequenza indicata: un numero di turno, GRS (riposo settimanale) o RIP (riposo). */
    fun codice(sequenza: Char, data: LocalDate): String {
        val indice = SEQUENZE.indexOf(sequenza.uppercaseChar())
        require(indice >= 0) { "Sequenza non valida: $sequenza" }
        val giorni = ChronoUnit.DAYS.between(ancora, data)
        val blocco = Math.floorDiv(giorni, 28L)
        val offset = Math.floorMod(indice - 1 - blocco, 7L)
        return ciclo[Math.floorMod(giorni + offset, 7L).toInt()]
    }

    /** Ultima data verificata sul calendario ORSA. */
    val ultimoVerificato: LocalDate = LocalDate.of(2028, 1, 7)

    /** Numero di giorni di riposo (GRS o RIP) della sequenza nell'intervallo, estremi inclusi. */
    fun riposi(sequenza: Char, da: LocalDate, a: LocalDate): Int =
        generateSequence(da) { it.plusDays(1) }.takeWhile { !it.isAfter(a) }
            .count { codice(sequenza, it) == "GRS" || codice(sequenza, it) == "RIP" }
}
