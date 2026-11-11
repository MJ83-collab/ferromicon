package it.ferromicon

import java.time.LocalDate

/**
 * Generatore di turni ciclici: una sequenza di giornate ripetuta da una data.
 *
 * Ogni elemento della sequenza è il nome di un servizio preferito oppure una sigla:
 *   R = giornata di riposo, RS = riposo settimanale, FE = ferie, D = disponibilità,
 *   "-" = giornata vuota (non inserisce nulla).
 * Gli elementi si separano con virgole (se i nomi contengono spazi) o con spazi.
 * Esempio: "Mattina, Mattina, Pomeriggio, Pomeriggio, Notte, R, RS"
 */
object Generatore {

    data class Risultato(
        val servizi: List<Servizio>,
        val assenze: List<Assenza>,
        val sconosciuti: Set<String>
    )

    private val SIGLE = mapOf(
        "R" to TipoAssenza.RIPOSO,
        "GRS" to TipoAssenza.RIPOSO,
        "RS" to TipoAssenza.RIPOSO_SETTIMANALE,
        "FE" to TipoAssenza.FERIE,
        "D" to TipoAssenza.DISPONIBILITA
    )

    /** Tipo di assenza di una sigla (R, RS, FE, D), o null se non è una sigla. */
    fun sigla(elemento: String): TipoAssenza? = SIGLE[elemento.uppercase()]

    fun elementi(sequenza: String): List<String> {
        val separatore = if (sequenza.contains(',') || sequenza.contains(';')) Regex("""[,;\n]+""") else Regex("""\s+""")
        return sequenza.split(separatore).map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun genera(
        sequenza: String,
        da: LocalDate,
        giorni: Int,
        preferiti: List<Preferito>,
        idIniziale: Long
    ): Risultato {
        val elementi = elementi(sequenza)
        val servizi = mutableListOf<Servizio>()
        val assenze = mutableListOf<Assenza>()
        val sconosciuti = linkedSetOf<String>()
        if (elementi.isEmpty() || giorni <= 0) return Risultato(servizi, assenze, sconosciuti)
        var id = idIniziale

        for (i in 0 until giorni) {
            val giorno = da.plusDays(i.toLong())
            val elemento = elementi[i % elementi.size]
            if (elemento == "-" || elemento == ".") continue
            val tipo = SIGLE[elemento.uppercase()]
            val preferito = preferiti.firstOrNull { it.nome.equals(elemento, ignoreCase = true) }
            when {
                preferito != null -> servizi += preferito.applica(giorno, id++)
                tipo != null -> assenze += Assenza(id++, giorno, tipo)
                else -> sconosciuti += elemento
            }
        }
        return Risultato(servizi, assenze, sconosciuti)
    }
}
