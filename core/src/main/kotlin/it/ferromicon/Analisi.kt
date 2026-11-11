package it.ferromicon

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/**
 * Controlli che richiedono più servizi: riposo settimanale, limiti tra due
 * riposi settimanali, limiti mensili e annui.
 *
 * Un intervallo tra due servizi è trattato come riposo settimanale se contiene
 * una giornata segnata "Riposo settimanale" oppure dura almeno 48 h (soglia
 * pratica dell'app, non una norma). Se contiene ferie, permessi, malattia o
 * altre assenze, il riposo non viene verificato.
 */
object Analisi {

    private const val SOGLIA_RIPOSO_SETTIMANALE_MIN = 48L * 60

    /** Valore di una giornata di assenza: 1/5 di 38 ore = 7 h 36'. */
    const val GIORNATA_CONVENZIONALE = 38L * 60 / 5

    private fun intervallo(a: Servizio, b: Servizio): Long =
        Duration.between(a.turno.fine, b.turno.inizio).toMinutes()

    private fun durata(s: Servizio): Long =
        Duration.between(s.turno.inizio, s.turno.fine).toMinutes()

    private fun ore(min: Long) = String.format("%d h %02d'", min / 60, min % 60)

    /** Giornate solari intere comprese tra due istanti. */
    fun giornateIntere(da: LocalDateTime, a: LocalDateTime): Int {
        var giorno = da.toLocalDate()
        if (da.toLocalTime() != LocalTime.MIDNIGHT) giorno = giorno.plusDays(1)
        var n = 0
        while (!giorno.plusDays(1).atStartOfDay().isAfter(a)) {
            n++
            giorno = giorno.plusDays(1)
        }
        return n
    }

    private enum class Intervallo { GIORNALIERO, SETTIMANALE, ASSENZA }

    /** Assenze le cui giornate cadono interamente tra due servizi. */
    private fun assenzeTra(a: Servizio, b: Servizio, assenze: List<Assenza>) = assenze.filter {
        !it.data.atStartOfDay().isBefore(a.turno.fine) && !it.data.plusDays(1).atStartOfDay().isAfter(b.turno.inizio)
    }

    private fun tipoIntervallo(a: Servizio, b: Servizio, assenze: List<Assenza>): Intervallo {
        val tra = assenzeTra(a, b, assenze)
        return when {
            tra.any { !it.tipo.riposo } -> Intervallo.ASSENZA
            tra.any { it.tipo == TipoAssenza.RIPOSO_SETTIMANALE } -> Intervallo.SETTIMANALE
            intervallo(a, b) >= SOGLIA_RIPOSO_SETTIMANALE_MIN -> Intervallo.SETTIMANALE
            else -> Intervallo.GIORNALIERO
        }
    }

    /** Esiti di un servizio nel contesto del servizio precedente e delle assenze. */
    fun verificaServizio(
        ordinati: List<Servizio>,
        indice: Int,
        assenze: List<Assenza> = emptyList(),
        limiti: LimitiOrario? = null
    ): List<Esito> {
        val corrente = ordinati[indice]
        val esiti = RegoleAltarail.verificaTurno(corrente.turno, limiti).toMutableList()

        // Righe treno: sovrapposizioni e controlli con gli orari reali
        Righe.sovrapposte(corrente.righe).forEach { (x, y) ->
            esiti += Esito(Gravita.ATTENZIONE,
                "Le righe ${x.numero.ifBlank { x.tipo.etichetta }} e ${y.numero.ifBlank { y.tipo.etichetta }} si sovrappongono.")
        }
        val ritardo = corrente.ritardoFinale
        if (ritardo != 0L) {
            esiti += Esito(Gravita.INFO,
                if (ritardo > 0) "Ultimo treno arrivato con $ritardo' di ritardo: fine effettiva " +
                    "${corrente.turnoEffettivo().fine.toLocalTime()}."
                else "Ultimo treno arrivato con ${-ritardo}' di anticipo.")
            if (ritardo > 0) RegoleAltarail.verificaTurno(corrente.turnoEffettivo())
                .filter { it.gravita == Gravita.VIOLAZIONE || it.gravita == Gravita.ATTENZIONE }
                .forEach { esiti += it.copy(messaggio = "Con gli orari reali: ${it.messaggio}") }
        }

        // Giorni toccati dal servizio (una fine alle 00:00 non tocca il giorno dopo).
        val t = corrente.turno
        val giorni = mutableSetOf(t.inizio.toLocalDate())
        if (t.fine.toLocalTime() != LocalTime.MIDNIGHT) giorni += t.fine.toLocalDate()
        assenze.filter { it.data in giorni }
            .forEach { esiti += Esito(Gravita.ATTENZIONE,
                "Il servizio cade su una giornata segnata come ${it.tipo.etichetta.lowercase()} (${it.data}).") }

        if (indice == 0) return esiti
        val precedente = ordinati[indice - 1]
        esiti += when (tipoIntervallo(precedente, corrente, assenze)) {
            Intervallo.ASSENZA -> listOf(Esito(Gravita.INFO,
                "Dopo ferie o assenze: il riposo prima del servizio non viene verificato."))
            Intervallo.SETTIMANALE ->
                verificaRiposoSettimanale(precedente.turno.fine, corrente.turno.inizio)
            Intervallo.GIORNALIERO ->
                if (precedente.rfrDopo) RegoleAltarail.verificaRfr(precedente.turno, corrente.turno)
                else RegoleAltarail.verificaRiposo(precedente.turno, corrente.turno)
        }
        return esiti
    }

    /** Altarail art. 17.6.2: almeno 62 h consecutive con 2 giornate solari intere. */
    fun verificaRiposoSettimanale(da: LocalDateTime, a: LocalDateTime): List<Esito> {
        val minuti = Duration.between(da, a).toMinutes()
        val giornate = giornateIntere(da, a)
        return if (minuti >= 62 * 60 && giornate >= 2)
            listOf(Esito(Gravita.OK, "Riposo settimanale di ${ore(minuti)} con $giornate giornate intere."))
        else
            listOf(Esito(Gravita.VIOLAZIONE,
                "Riposo settimanale di ${ore(minuti)} con $giornate giornate intere: servono " +
                    "almeno 62 h e 2 giornate solari intere (Altarail art. 17.6.2)."))
    }

    /** Divide i servizi in periodi separati da riposi settimanali. */
    fun periodiTraRiposi(ordinati: List<Servizio>, assenze: List<Assenza> = emptyList()): List<List<Servizio>> {
        val periodi = mutableListOf<MutableList<Servizio>>()
        ordinati.forEachIndexed { i, s ->
            if (i == 0 || tipoIntervallo(ordinati[i - 1], s, assenze) != Intervallo.GIORNALIERO)
                periodi += mutableListOf<Servizio>()
            periodi.last() += s
        }
        return periodi
    }

    /** Limiti tra due riposi settimanali. */
    fun verificaPeriodo(periodo: List<Servizio>): List<Esito> {
        val esiti = mutableListOf<Esito>()
        if (periodo.isEmpty()) return esiti
        val etichetta = "Periodo dal ${periodo.first().turno.inizio.toLocalDate()} " +
            "al ${periodo.last().turno.fine.toLocalDate()}"

        val lunghe = periodo.count {
            it.turno.profilo == Profilo.MACCHINISTA && !RegoleAltarail.èNotturno(it.turno) &&
                durata(it) > 8 * 60 + 30
        }
        if (lunghe > 1) esiti += Esito(Gravita.VIOLAZIONE,
            "$etichetta: $lunghe prestazioni oltre 8 h 30'. Le 10 h sono ammesse una sola volta " +
                "tra due riposi settimanali (Altarail art. 17.2.2).")

        val notturni = periodo.count { RegoleAltarail.contaComeNotturno(it.turno) }
        if (notturni > 2) esiti += Esito(Gravita.VIOLAZIONE,
            "$etichetta: $notturni servizi notturni, massimo 2 tra due riposi settimanali (CCNL SP2 B.5).")

        val rfr = periodo.dropLast(1).count { it.rfrDopo }
        if (rfr > 2) esiti += Esito(Gravita.VIOLAZIONE,
            "$etichetta: $rfr riposi fuori residenza, massimo 2 tra due riposi settimanali (CCNL SP2 B.3.b).")
        return esiti
    }

    data class RiepilogoMese(
        val servizi: Int,
        val minutiLavoro: Long,
        val minutiCondotta: Long,
        val notturni: Int,
        val rfr: Int,
        val festiviLavorati: List<Pair<LocalDate, String>>,
        /** Giornate senza servizio nel mese, per tipo. */
        val assenzeMese: Map<TipoAssenza, Int>,
        /** Giornate senza servizio da inizio anno a fine mese, per tipo. */
        val assenzeAnno: Map<TipoAssenza, Int>,
        val esiti: List<Esito>
    )

    fun riepilogoMese(
        tutti: List<Servizio>,
        mese: YearMonth,
        assenze: List<Assenza> = emptyList()
    ): RiepilogoMese {
        val ordinati = tutti.sortedBy { it.turno.inizio }
        val delMese = ordinati.filter { YearMonth.from(it.turno.inizio) == mese }
        val esiti = mutableListOf<Esito>()
        val profilo = delMese.firstOrNull()?.turno?.profilo ?: Profilo.MACCHINISTA

        // Limiti mensili
        val notturni = delMese.count { RegoleAltarail.contaComeNotturno(it.turno) }
        if (notturni > 11) esiti += Esito(Gravita.VIOLAZIONE,
            "$notturni servizi notturni nel mese: massimo 11 (CCNL SP2 B.5).")
        val rfr = delMese.count { it.rfrDopo }
        val maxRfr = if (profilo == Profilo.MACCHINISTA) 5 else 4
        if (rfr > maxRfr) esiti += Esito(Gravita.VIOLAZIONE,
            "$rfr riposi fuori residenza nel mese: massimo $maxRfr (CCNL SP2 B.3.b).")

        // Ore settimanali: massimo 44 h nella settimana (Altarail art. 17.1.1)
        val inizioSettimana = mese.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        var lunedi = inizioSettimana
        while (!lunedi.isAfter(mese.atEndOfMonth())) {
            val fineSett = lunedi.plusDays(7)
            val minuti = ordinati
                .filter { !it.turno.inizio.toLocalDate().isBefore(lunedi) && it.turno.inizio.toLocalDate().isBefore(fineSett) }
                .sumOf { durata(it) }
            if (minuti > 44 * 60) esiti += Esito(Gravita.VIOLAZIONE,
                "Settimana dal $lunedi: ${ore(minuti)} di lavoro, oltre 44 h (Altarail art. 17.1.1).")
            lunedi = fineSett
        }

        val minutiLavoro = delMese.sumOf { durata(it) }
        // Monte ore del mese: giorni × 38 / 7 (Altarail art. 17.1.1).
        // Ferie, permessi, malattia e disponibilità valgono 1/5 dell'orario settimanale (7 h 36').
        val assenzeDelMese = assenze.filter { YearMonth.from(it.data) == mese }
        val giorniValorizzati = assenzeDelMese.count { !it.tipo.riposo && it.tipo != TipoAssenza.ALTRO }
        val monteOre = mese.lengthOfMonth() * 38 * 60 / 7
        val oreRiconosciute = minutiLavoro + giorniValorizzati * GIORNATA_CONVENZIONALE
        if (delMese.isNotEmpty()) esiti += Esito(Gravita.INFO,
            "Ore del mese: ${ore(oreRiconosciute)} (lavoro ${ore(minutiLavoro)}" +
                (if (giorniValorizzati > 0) " + $giorniValorizzati giornate di assenza da 7 h 36'" else "") +
                ") su un monte ore di ${ore(monteOre.toLong())} (${mese.lengthOfMonth()} giorni × 38 / 7).")

        // Limiti annui
        val anno = ordinati.filter { it.turno.inizio.year == mese.year && !YearMonth.from(it.turno.inizio).isAfter(mese) }
        val notturniAnno = anno.count { RegoleAltarail.contaComeNotturno(it.turno) }
        if (notturniAnno > 79) esiti += Esito(Gravita.VIOLAZIONE,
            "$notturniAnno servizi notturni da inizio anno: massimo 79 (CCNL SP2 B.5).")
        val minutiNotteAnno = anno.sumOf { RegoleAltarail.minutiInFasciaNotturna(it.turno.inizio, it.turno.fine) }
        if (minutiNotteAnno > 320 * 60) esiti += Esito(Gravita.VIOLAZIONE,
            "${ore(minutiNotteAnno)} nella fascia 0-5 da inizio anno: massimo 320 h (Altarail art. 17.3.3, stima).")

        // Limiti tra riposi settimanali, per i periodi che toccano il mese
        periodiTraRiposi(ordinati, assenze)
            .filter { p -> p.any { YearMonth.from(it.turno.inizio) == mese } }
            .forEach { esiti += verificaPeriodo(it) }

        val festivi = delMese.mapNotNull { s ->
            val d = s.turno.inizio.toLocalDate()
            Festivi.nome(d)?.let { d to it }
        }.distinct()

        return RiepilogoMese(
            servizi = delMese.size,
            minutiLavoro = minutiLavoro,
            minutiCondotta = delMese.sumOf { it.turno.minutiCondotta.toLong() },
            notturni = notturni,
            rfr = rfr,
            festiviLavorati = festivi,
            assenzeMese = assenzeDelMese.groupingBy { it.tipo }.eachCount(),
            assenzeAnno = assenze
                .filter { it.data.year == mese.year && !YearMonth.from(it.data).isAfter(mese) }
                .groupingBy { it.tipo }.eachCount(),
            esiti = esiti
        )
    }

    /** Fasce di qualità del riposo giornaliero in residenza, in base al margine sul minimo. */
    enum class QualitaRiposo(val etichetta: String) {
        INSUFFICIENTE("sotto il minimo"),
        AL_LIMITE("al limite (meno di 1 h in più)"),
        BUONO("buono (1-3 h in più)"),
        AMPIO("ampio (oltre 3 h in più)")
    }

    /**
     * Qualità dei riposi giornalieri che precedono i servizi del mese (esclusi RFR, riposi
     * settimanali e periodi con assenze).
     */
    fun qualitaRiposi(ordinati: List<Servizio>, assenze: List<Assenza>, mese: YearMonth): Map<QualitaRiposo, Int> {
        val conteggio = sortedMapOf<QualitaRiposo, Int>()
        for (i in 1 until ordinati.size) {
            val prec = ordinati[i - 1]
            val corr = ordinati[i]
            if (YearMonth.from(corr.turno.inizio) != mese || prec.rfrDopo) continue
            if (tipoIntervallo(prec, corr, assenze) != Intervallo.GIORNALIERO) continue
            val margine = Duration.between(prec.turno.fine, corr.turno.inizio).toMinutes() -
                RegoleAltarail.minimoRiposoDopo(prec.turno).first * 60L
            val q = when {
                margine < 0 -> QualitaRiposo.INSUFFICIENTE
                margine < 60 -> QualitaRiposo.AL_LIMITE
                margine < 180 -> QualitaRiposo.BUONO
                else -> QualitaRiposo.AMPIO
            }
            conteggio.merge(q, 1, Int::plus)
        }
        return conteggio
    }
}
