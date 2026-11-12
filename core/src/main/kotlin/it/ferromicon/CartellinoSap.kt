package it.ferromicon

/** Una giornata del cartellino SAP: prima e ultima timbratura e la flessibilità del giorno (in ore). */
data class GiornataSap(
    val giorno: Int,
    val sigla: String,
    val prima: String?,
    val ultima: String?,
    val flex: Double?
)

/** Cartellino di un mese letto dal PDF SAP. [righeGrezze] è il testo letto, per controllare o correggere. */
data class CartellinoSap(
    val anno: Int,
    val mese: Int,
    val giornate: List<GiornataSap>,
    val flexMesePrecedente: Double?,
    val righeGrezze: List<String>
)

/**
 * Legge il testo del cartellino SAP (una riga per riga). Il formato è quello della stampa
 * "Cartellino orologio": ogni giorno inizia con «gg SIGLA codice», poi le righe di timbratura
 * («Timbrature», «Maggior presenza») con orari e valori in ore con la virgola.
 * Le righe che non sono di timbratura (totali di pagina, testo di intestazione) chiudono il giorno.
 */
object LettoreCartellino {

    private val GIORNO = Regex("""^(\d{2})\s+(LU|MA|ME|GI|VE|SA|DO)\b""")
    private val ORA = Regex("""\b\d{2}:\d{2}\b""")
    private val NUMERO = Regex("""(?<![\d,:])-?\d{1,3},\d{2}-?(?![\d,])""")
    private val PERIODO = Regex("""(\d{2})\.(\d{2})\.(\d{4})\s*(?:-|al)\s*(\d{2})\.(\d{2})\.(\d{4})""")
    // Nel PDF SAP la voce è scritta «Flessibilià» (senza la t): il pattern accetta entrambe.
    private val FLEX_PRECEDENTE = Regex("""Flessibili?t?[aà]\s+mese\s+precedente\D*(-?\d{1,3},\d{2}-?)""", RegexOption.IGNORE_CASE)
    private val RIGA_TIMBRATURA = Regex("""^(Timbrature|Maggior presenza)""", RegexOption.IGNORE_CASE)

    /** Converte «0,45-» o «-0,45» in -0.45; «1,02» in 1.02. */
    fun valore(token: String): Double {
        val negativo = token.startsWith("-") || token.endsWith("-")
        val v = token.trim('-').replace(',', '.').toDouble()
        return if (negativo) -v else v
    }

    /** Analizza le righe di un cartellino; null se non si trova il periodo del mese. */
    fun analizza(righe: List<String>): CartellinoSap? {
        val testo = righe.joinToString("\n")
        val periodo = PERIODO.find(testo) ?: return null
        val mese = periodo.groupValues[2].toInt()
        val anno = periodo.groupValues[3].toInt()
        if (mese !in 1..12) return null

        val giornate = mutableListOf<GiornataSap>()
        var blocco: MutableList<String>? = null
        var giorno = 0
        var sigla = ""

        fun chiudi() {
            val b = blocco ?: return
            val orari = b.flatMap { ORA.findAll(it).map { m -> m.value }.toList() }
            val flex = b.flatMap { NUMERO.findAll(it).map { m -> m.value }.toList() }.lastOrNull()
            giornate += GiornataSap(giorno, sigla, orari.firstOrNull(), orari.lastOrNull(),
                flex?.let { valore(it) })
            blocco = null
        }

        for (riga in righe) {
            val t = riga.trim()
            val g = GIORNO.find(t)
            if (g != null) {
                chiudi()
                giorno = g.groupValues[1].toInt()
                sigla = g.groupValues[2]
                blocco = mutableListOf(t)
                continue
            }
            val b = blocco ?: continue
            if (ORA.containsMatchIn(t) || RIGA_TIMBRATURA.containsMatchIn(t)) b += t else chiudi()
        }
        chiudi()

        val flexPrec = FLEX_PRECEDENTE.find(testo)?.let { valore(it.groupValues[1]) }
        return CartellinoSap(anno, mese, giornate, flexPrec, righe)
    }
}
