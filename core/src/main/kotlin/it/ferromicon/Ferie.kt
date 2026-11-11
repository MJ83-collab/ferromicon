package it.ferromicon

import java.time.LocalDate
import java.time.Month

/** Saldo annuo di ferie, permessi e recuperi festivi. */
data class SaldoFerie(
    val anno: Int,
    val ferieSpettanti: Int,
    val ferieAnnoPrecedente: Int,
    val ferieGodute: Int,
    val permessiExFestivitaSpettanti: Int,
    val permessiExFestivitaGoduti: Int,
    val permessiAggiuntivi: Int,
    val altriPermessiGoduti: Int,
    val recuperiFestiviMaturati: Double,
    val recuperiFestiviGoduti: Int,
    val note: List<String>
) {
    val ferieResidue get() = ferieSpettanti + ferieAnnoPrecedente - ferieGodute
    val permessiExFestivitaResidui get() = permessiExFestivitaSpettanti - permessiExFestivitaGoduti
    val permessiAggiuntiviResidui get() = permessiAggiuntivi - altriPermessiGoduti
    val recuperiFestiviResidui get() = recuperiFestiviMaturati - recuperiFestiviGoduti
}

object Ferie {

    /**
     * Mesi di servizio nell'anno (art. 30.1): nell'anno di assunzione si contano
     * i mesi da lavorare, considerando intero il mese con più di 15 giorni.
     */
    fun mesiNellAnno(assunzione: LocalDate?, anno: Int): Int {
        if (assunzione == null || assunzione.year < anno) return 12
        if (assunzione.year > anno) return 0
        val giorniPrimoMese = assunzione.lengthOfMonth() - assunzione.dayOfMonth + 1
        return (12 - assunzione.monthValue) + if (giorniPrimoMese > 15) 1 else 0
    }

    /** Più di 8 anni di servizio entro la fine dell'anno (art. 30.1.b, interpretazione dell'app). */
    fun oltreOttoAnni(assunzione: LocalDate?, anno: Int): Boolean =
        assunzione != null && assunzione.plusYears(8).isBefore(LocalDate.of(anno, Month.DECEMBER, 31))

    /** Giornate di ferie spettanti nell'anno (CCNL 2025, art. 30.1). */
    fun ferieAnnue(imp: Impostazioni, anno: Int): Int {
        val oltre8 = oltreOttoAnni(imp.dataAssunzione, anno)
        val base = when {
            oltre8 && imp.orarioSuSeiGiorni -> 29
            oltre8 -> 25
            imp.orarioSuSeiGiorni -> 24
            else -> 20
        }
        return Math.round(base * mesiNellAnno(imp.dataAssunzione, anno) / 12.0).toInt()
    }

    /** Permessi per festività soppresse: 4 l'anno, in proporzione ai mesi (art. 29.4). */
    fun permessiExFestivita(imp: Impostazioni, anno: Int): Int =
        Math.round(4 * mesiNellAnno(imp.dataAssunzione, anno) / 12.0).toInt()

    /**
     * Recuperi maturati per festività lavorate (CCNL art. 29.2.3 e 29.2.6):
     * una giornata se la prestazione nel festivo supera 3 h, mezza giornata altrimenti.
     */
    fun recuperiFestivi(servizi: List<Servizio>, anno: Int): Double {
        val minutiPerFestivo = mutableMapOf<LocalDate, Long>()
        servizi.forEach { s ->
            Competenze.minutiPerGiorno(s.turno.inizio, s.turno.fine).forEach { (giorno, minuti) ->
                if (giorno.year == anno && Festivi.èFestivoContrattuale(giorno))
                    minutiPerFestivo.merge(giorno, minuti, Long::plus)
            }
        }
        return minutiPerFestivo.values.sumOf { if (it > 3 * 60) 1.0 else 0.5 }
    }

    /** Riposi settimanali caduti in un festivo: un'altra giornata di riposo entro 90 giorni (art. 29.2.4). */
    fun riposiSuFestivi(assenze: List<Assenza>, anno: Int): Int = assenze.count {
        it.data.year == anno && it.tipo == TipoAssenza.RIPOSO_SETTIMANALE && Festivi.èFestivoContrattuale(it.data)
    }

    fun saldo(dati: Dati, anno: Int, finoAl: LocalDate = LocalDate.of(anno, Month.DECEMBER, 31)): SaldoFerie {
        val imp = dati.impostazioni
        val assenze = dati.assenze.filter { it.data.year == anno && !it.data.isAfter(finoAl) }
        fun conta(tipo: TipoAssenza) = assenze.count { it.tipo == tipo }
        val servizi = dati.servizi.filter { !it.turno.inizio.toLocalDate().isAfter(finoAl) }

        val note = mutableListOf<String>()
        if (imp.dataAssunzione == null)
            note += "Data di assunzione non impostata: ferie calcolate come anno intero e anzianità fino a 8 anni."
        if (imp.ferieResidueAnnoPrecedente > 0 && !finoAl.isBefore(LocalDate.of(anno, Month.SEPTEMBER, 30)))
            note += "Le ferie dell'anno precedente vanno godute entro il 30 settembre (art. 30.4)."
        note += "Le ferie si contano a giornata intera; la mezza giornata non è ancora gestita."

        return SaldoFerie(
            anno = anno,
            ferieSpettanti = ferieAnnue(imp, anno),
            ferieAnnoPrecedente = imp.ferieResidueAnnoPrecedente,
            ferieGodute = conta(TipoAssenza.FERIE),
            permessiExFestivitaSpettanti = permessiExFestivita(imp, anno),
            permessiExFestivitaGoduti = conta(TipoAssenza.PERMESSO_EX_FESTIVITA),
            permessiAggiuntivi = imp.permessiAggiuntivi,
            altriPermessiGoduti = conta(TipoAssenza.PERMESSO),
            recuperiFestiviMaturati = recuperiFestivi(servizi, anno) + riposiSuFestivi(assenze, anno),
            recuperiFestiviGoduti = conta(TipoAssenza.RECUPERO_FESTIVO),
            note = note
        )
    }
}
