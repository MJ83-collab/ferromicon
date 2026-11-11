package it.ferromicon

import java.time.Duration
import java.time.YearMonth

/** Una riga del riepilogo annuale. */
data class RigaMese(
    val mese: YearMonth,
    val servizi: Int,
    val minutiLavoro: Long,
    val minutiCondotta: Long,
    val notturni: Int,
    val rfr: Int,
    val ferie: Int,
    val stimaCompetenze: Double,
    val violazioni: Int
)

data class RiepilogoAnno(val anno: Int, val mesi: List<RigaMese>) {
    val servizi get() = mesi.sumOf { it.servizi }
    val minutiLavoro get() = mesi.sumOf { it.minutiLavoro }
    val minutiCondotta get() = mesi.sumOf { it.minutiCondotta }
    val notturni get() = mesi.sumOf { it.notturni }
    val rfr get() = mesi.sumOf { it.rfr }
    val ferie get() = mesi.sumOf { it.ferie }
    val stimaCompetenze get() = mesi.sumOf { it.stimaCompetenze }
    val violazioni get() = mesi.sumOf { it.violazioni }
}

object Annuale {

    fun riepilogo(dati: Dati, anno: Int): RiepilogoAnno {
        val ordinati = dati.servizi.sortedBy { it.turno.inizio }
        val righe = (1..12).map { m ->
            val mese = YearMonth.of(anno, m)
            val delMese = ordinati.withIndex().filter { (_, s) -> YearMonth.from(s.turno.inizio) == mese }
            val violazioni = delMese.count { (i, _) ->
                Analisi.verificaServizio(ordinati, i, dati.assenze).any { it.gravita == Gravita.VIOLAZIONE }
            }
            val servizi = delMese.map { it.value }
            RigaMese(
                mese = mese,
                servizi = servizi.size,
                minutiLavoro = servizi.sumOf { Duration.between(it.turno.inizio, it.turno.fine).toMinutes() },
                minutiCondotta = servizi.sumOf { it.turno.minutiCondotta.toLong() },
                notturni = servizi.count { RegoleAltarail.contaComeNotturno(it.turno) },
                rfr = servizi.count { it.rfrDopo },
                ferie = dati.assenze.count { YearMonth.from(it.data) == mese && it.tipo == TipoAssenza.FERIE },
                stimaCompetenze = Competenze.stimaMese(dati, mese).totale,
                violazioni = violazioni
            )
        }
        return RiepilogoAnno(anno, righe)
    }
}
