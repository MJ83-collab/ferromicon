package it.ferromicon

import java.time.DateTimeException
import java.time.LocalTime

/** Lettura dell'orario digitato: HHmm (0840, 840) oppure HH:mm (08:40). */
object OraInput {

    fun parse(testo: String): LocalTime? {
        val t = testo.trim()
        val (ore, minuti) = if (t.contains(':')) {
            val parti = t.split(':')
            if (parti.size != 2) return null
            parti[0] to parti[1]
        } else {
            if (t.length !in 3..4 || !t.all { it.isDigit() }) return null
            t.dropLast(2) to t.takeLast(2)
        }
        if (minuti.length != 2 || ore.length !in 1..2) return null
        val h = ore.toIntOrNull() ?: return null
        val m = minuti.toIntOrNull() ?: return null
        return try { LocalTime.of(h, m) } catch (e: DateTimeException) { null }
    }

    /** Forma HHmm, per precompilare il campo. */
    fun formato(ora: LocalTime): String = "%02d%02d".format(ora.hour, ora.minute)
}
