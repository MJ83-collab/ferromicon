package it.ferromicon

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/** Riepilogo esteso del mese: ore, presenze, dormite, notturni, condotta, straordinario, diaria, ferie. */
data class RiepilogoCompleto(
    val mese: YearMonth,
    val profilo: Profilo,
    /** Monte ore del mese: giorni × 38 / 7 (Altarail art. 17.1.1). */
    val monteOreMinuti: Long,
    val lavoroMinuti: Long,
    /** Lavoro più le giornate di assenza valorizzate (7 h 36' ciascuna). */
    val oreRiconosciuteMinuti: Long,
    val presenzeEffettive: Int,
    /** Presenze effettive più le assenze retribuite che danno l'indennità di turno. */
    val presenzeComplessive: Int,
    val giorniDelMese: Int,
    val dormite: Int,
    val serviziNotturni: Int,
    /** Minuti nella fascia 22-6 (indennità di lavoro notturno). */
    val minutiFascia22_6: Long,
    /** Condotta (macchinista) o scorta (capo treno) del mese. */
    val condottaMinuti: Long,
    val condottaFinoA3hMinuti: Long,
    val condottaOltre3hMinuti: Long,
    val straordinarioNormaleMinuti: Long,
    val straordinarioInRcMinuti: Long,
    val straordinarioInRsMinuti: Long,
    /** Ore di assenza dalla residenza (almeno 3 h per giornata), senza e con riposo fuori residenza. */
    val diariaSenzaRfrMinuti: Long,
    val diariaConRfrMinuti: Long,
    val ferieMese: Int,
    val ferieAnno: Int,
    val ferieResidue: Int,
    val festivitaSoppresseGodute: Int,
    val festivitaSoppresseSpettanti: Int,
    val permessiGoduti: Int,
    val permessiSpettanti: Int
) {
    val straordinarioTotaleMinuti get() = straordinarioNormaleMinuti + straordinarioInRcMinuti + straordinarioInRsMinuti
    /** Progresso rispetto al monte ore, da 0 a 1 (oltre 1 se superato). */
    val progresso get() = if (monteOreMinuti == 0L) 0.0 else oreRiconosciuteMinuti.toDouble() / monteOreMinuti
}

/** Riepilogo di una singola giornata. */
data class RiepilogoGiorno(
    val giorno: LocalDate,
    val servizi: List<Servizio>,
    val assenza: Assenza?,
    val durataMinuti: Long,
    val condottaMinuti: Long,
    val condottaContinuativaMinuti: Long,
    val straordinarioMinuti: Long,
    val notturno: Boolean,
    val dormita: Boolean,
    /** Riposo tra la fine dell'ultimo servizio del giorno e il servizio seguente, se c'è. */
    val riposoDopoMinuti: Long?,
    val riposoSettimanale: Boolean,
    /** Ore di lavoro della settimana (da lunedì) che contiene il giorno. */
    val settimanaMinuti: Long
)

object Riepilogo {

    private fun durata(s: Servizio) = Duration.between(s.turno.inizio, s.turno.fine).toMinutes()

    /** Soglia oltre la quale il riposo tra due servizi è considerato settimanale (come in [Analisi]). */
    private const val RIPOSO_SETTIMANALE_MIN = 48L * 60

    /** Un servizio con riposo fuori residenza che comprende una notte (22-6): una dormita. */
    private fun èDormita(ordinati: List<Servizio>, i: Int): Boolean {
        val s = ordinati[i]
        if (!s.rfrDopo) return false
        val prossimo = ordinati.getOrNull(i + 1) ?: return true
        return Competenze.minutiNotturni(s.turno.fine, prossimo.turno.inizio) > 0
    }

    fun mese(dati: Dati, mese: YearMonth): RiepilogoCompleto {
        val ordinati = dati.servizi.sortedBy { it.turno.inizio }
        val delMese = ordinati.withIndex().filter { YearMonth.from(it.value.turno.inizio) == mese }
        val servizi = delMese.map { it.value }
        val assenzeMese = dati.assenze.filter { YearMonth.from(it.data) == mese }
        val profilo = servizi.firstOrNull()?.turno?.profilo ?: ordinati.lastOrNull()?.turno?.profilo ?: Profilo.MACCHINISTA

        val lavoro = servizi.sumOf { durata(it) }
        val valorizzate = assenzeMese.count { !it.tipo.riposo && it.tipo != TipoAssenza.ALTRO }
        val giorniServizio = servizi.map { it.turno.inizio.toLocalDate() }.toSet()
        val presenzaRetribuita = assenzeMese.filter { it.tipo in Competenze.TIPI_CON_INDENNITA_TURNO }
            .map { it.data }.toSet() - giorniServizio

        val condotta = servizi.sumOf { it.turno.minutiCondotta.toLong() }
        val sotto = servizi.sumOf { minOf(it.turno.minutiCondotta, 180).toLong() }

        var normale = 0L; var inRc = 0L; var inRs = 0L
        servizi.forEach { s ->
            val (a, b, c) = Competenze.straordinario(s.turnoEffettivo())
            val tot = a + b + c
            val giorno = s.turno.inizio.toLocalDate()
            when (assenzeMese.firstOrNull { it.data == giorno }?.tipo) {
                TipoAssenza.RIPOSO, TipoAssenza.RECUPERO_FESTIVO -> inRc += tot
                TipoAssenza.RIPOSO_SETTIMANALE -> inRs += tot
                else -> normale += tot
            }
        }

        // Assenza dalla residenza: solo giornate con almeno 3 ore fuori (CCNL art. 77.2)
        val conRfr = delMese.filter { (i, s) -> s.rfrDopo || (i > 0 && ordinati[i - 1].rfrDopo) }.map { it.value }.toSet()
        val fuori = servizi.filter { it.minutiFuoriResidenza >= 180 }
        val diariaRfr = fuori.filter { it in conRfr }.sumOf { it.minutiFuoriResidenza.toLong() }
        val diariaSenza = fuori.filter { it !in conRfr }.sumOf { it.minutiFuoriResidenza.toLong() }

        val saldo = Ferie.saldo(dati, mese.year, mese.atEndOfMonth())
        return RiepilogoCompleto(
            mese = mese, profilo = profilo,
            monteOreMinuti = mese.lengthOfMonth() * 38L * 60 / 7,
            lavoroMinuti = lavoro,
            oreRiconosciuteMinuti = lavoro + valorizzate * Analisi.GIORNATA_CONVENZIONALE,
            presenzeEffettive = giorniServizio.size,
            presenzeComplessive = giorniServizio.size + presenzaRetribuita.size,
            giorniDelMese = mese.lengthOfMonth(),
            dormite = delMese.count { (i, _) -> èDormita(ordinati, i) },
            serviziNotturni = servizi.count { RegoleAltarail.contaComeNotturno(it.turno) },
            minutiFascia22_6 = servizi.sumOf { Competenze.minutiNotturni(it.turno.inizio, it.turno.fine) },
            condottaMinuti = condotta,
            condottaFinoA3hMinuti = sotto,
            condottaOltre3hMinuti = condotta - sotto,
            straordinarioNormaleMinuti = normale,
            straordinarioInRcMinuti = inRc,
            straordinarioInRsMinuti = inRs,
            diariaSenzaRfrMinuti = diariaSenza,
            diariaConRfrMinuti = diariaRfr,
            ferieMese = assenzeMese.count { it.tipo == TipoAssenza.FERIE },
            ferieAnno = saldo.ferieGodute,
            ferieResidue = saldo.ferieResidue,
            festivitaSoppresseGodute = saldo.permessiExFestivitaGoduti,
            festivitaSoppresseSpettanti = saldo.permessiExFestivitaSpettanti,
            permessiGoduti = saldo.altriPermessiGoduti,
            permessiSpettanti = saldo.permessiAggiuntivi
        )
    }

    fun giorno(dati: Dati, giorno: LocalDate): RiepilogoGiorno {
        val ordinati = dati.servizi.sortedBy { it.turno.inizio }
        val deiGiorno = ordinati.withIndex().filter { it.value.turno.inizio.toLocalDate() == giorno }
        val servizi = deiGiorno.map { it.value }
        val ultimo = deiGiorno.lastOrNull()
        val prossimo = ultimo?.let { ordinati.getOrNull(it.index + 1) }
        val riposo = if (ultimo != null && prossimo != null)
            Duration.between(ultimo.value.turno.fine, prossimo.turno.inizio).toMinutes() else null
        val lunedi = giorno.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val settimana = ordinati.filter {
            val d = it.turno.inizio.toLocalDate()
            !d.isBefore(lunedi) && d.isBefore(lunedi.plusDays(7))
        }.sumOf { durata(it) }
        return RiepilogoGiorno(
            giorno = giorno, servizi = servizi,
            assenza = dati.assenze.firstOrNull { it.data == giorno },
            durataMinuti = servizi.sumOf { durata(it) },
            condottaMinuti = servizi.sumOf { it.turno.minutiCondotta.toLong() },
            condottaContinuativaMinuti = servizi.maxOfOrNull { it.turno.maxCondottaConsecutiva.toLong() } ?: 0,
            straordinarioMinuti = servizi.sumOf { s ->
                val (a, b, c) = Competenze.straordinario(s.turnoEffettivo()); a + b + c
            },
            notturno = servizi.any { RegoleAltarail.èNotturno(it.turno) },
            dormita = deiGiorno.any { èDormita(ordinati, it.index) },
            riposoDopoMinuti = riposo,
            riposoSettimanale = riposo != null && riposo >= RIPOSO_SETTIMANALE_MIN,
            settimanaMinuti = settimana
        )
    }

    /** Giorni del mese con almeno una dormita (per il simbolo in calendario). */
    fun giorniConDormita(dati: Dati, mese: YearMonth): Set<LocalDate> {
        val ordinati = dati.servizi.sortedBy { it.turno.inizio }
        return ordinati.withIndex()
            .filter { (i, s) -> YearMonth.from(s.turno.inizio) == mese && èDormita(ordinati, i) }
            .map { it.value.turno.inizio.toLocalDate() }.toSet()
    }

    fun ore(min: Long) = String.format("%d:%02d", min / 60, min % 60)
}
