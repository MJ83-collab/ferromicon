package it.ferromicon

import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

/*
 * Motore delle regole Altarail per il personale mobile.
 *
 * Due livelli di limite: quello aziendale Altarail e quello del CCNL Mobilità/AF
 * (servizi passeggeri regionali e locali), che vale anche dove l'azienda non dispone.
 * Quando i due danno valori diversi, il limite Altarail produce
 * VIOLAZIONE/ATTENZIONE e il valore CCNL viene mostrato come INFO.
 */

enum class Profilo(val etichetta: String) {
    MACCHINISTA("Macchinista (PdC)"),
    CAPOTRENO("Capotreno (PdA)"),
    /** Personale di staff (ufficio): orario fisso 08:15-16:21 e flessibilità mensile ±5 h. Regole in [OrarioStaff]. */
    UFFICIO("Personale di ufficio")
}

data class Turno(
    val inizio: LocalDateTime,
    val fine: LocalDateTime,
    val profilo: Profilo,
    /** Minuti di condotta totali nel servizio (solo PdM). */
    val minutiCondotta: Int = 0,
    /** Tratto di condotta consecutiva più lungo, in minuti (solo PdM). */
    val maxCondottaConsecutiva: Int = 0,
    /** Turno "a zeta": riposo dopo notturno riducibile a 14 ore. */
    val turnoAZeta: Boolean = false
)

enum class Gravita { OK, INFO, ATTENZIONE, VIOLAZIONE }

/**
 * Limiti di durata della prestazione scelti dall'utente al posto di quelli del contratto
 * (per esempio per un accordo di impianto). Valori in minuti.
 */
data class LimitiOrario(val diurnoMinuti: Int, val notturnoMinuti: Int) {
    companion object {
        /** Limiti del contratto Altarail (art. 17.2.2 e 17.3.2); il macchinista ha 10 h una volta tra due riposi. */
        fun contratto(p: Profilo) = when (p) {
            Profilo.MACCHINISTA -> LimitiOrario(8 * 60 + 30, 7 * 60)
            Profilo.CAPOTRENO -> LimitiOrario(10 * 60, 8 * 60)
            Profilo.UFFICIO -> LimitiOrario(OrarioStaff.PRESTAZIONE_MINUTI, OrarioStaff.PRESTAZIONE_MINUTI)
        }
    }
}

data class Esito(val gravita: Gravita, val messaggio: String)

object RegoleAltarail {

    private val MEZZANOTTE: LocalTime = LocalTime.MIDNIGHT
    private val ORE_5: LocalTime = LocalTime.of(5, 0)
    private val ORE_0_01: LocalTime = LocalTime.of(0, 1)
    private val ORE_1: LocalTime = LocalTime.of(1, 0)

    // ---------- utilità sugli orari ----------

    /** Minuti del periodo [inizio, fine) che cadono nella fascia 00:00-05:00. */
    fun minutiInFasciaNotturna(inizio: LocalDateTime, fine: LocalDateTime): Long {
        var totale = 0L
        var giorno = inizio.toLocalDate()
        while (!giorno.isAfter(fine.toLocalDate())) {
            // LocalDateTime non soddisfa il vincolo di maxOf/minOf: confronto esplicito.
            val inizioFascia = giorno.atTime(MEZZANOTTE)
            val fineFascia = giorno.atTime(ORE_5)
            val a = if (inizio.isAfter(inizioFascia)) inizio else inizioFascia
            val b = if (fine.isBefore(fineFascia)) fine else fineFascia
            if (b.isAfter(a)) totale += Duration.between(a, b).toMinutes()
            giorno = giorno.plusDays(1)
        }
        return totale
    }

    /** Fine servizio tra 00:01 e 01:00 dopo un inizio nel giorno precedente. */
    private fun fineTraMezzanotteEUna(t: Turno): Boolean {
        val ora = t.fine.toLocalTime()
        return t.fine.toLocalDate().isAfter(t.inizio.toLocalDate()) &&
            !ora.isBefore(ORE_0_01) && !ora.isAfter(ORE_1)
    }

    /**
     * Servizio notturno: interessa la fascia 00:00-05:00 (Altarail art. 16.3).
     * Per il PdM la prestazione che termina tra 00:01 e 01:00 è equiparata
     * a una diurna (Altarail art. 17.3.1).
     */
    fun èNotturno(t: Turno): Boolean = when (t.profilo) {
        // PdM: ai fini dell'orario massimo conta la fascia 01:00-05:00 (art. 17.3.1;
        //). Una fine tra 00:01 e 01:00 resta quindi diurna.
        Profilo.MACCHINISTA -> minutiInFascia(t.inizio, t.fine, ORE_1, ORE_5) > 0
        Profilo.CAPOTRENO -> minutiInFasciaNotturna(t.inizio, t.fine) > 0
        Profilo.UFFICIO -> minutiInFasciaNotturna(t.inizio, t.fine) > 0
    }

    /**
     * Servizio da contare tra i notturni (2 tra due riposi, 11 al mese, 79 l'anno):
     * fascia 00:00-05:00 per tutti (CCNL SP2 B.5;).
     */
    fun contaComeNotturno(t: Turno): Boolean = minutiInFasciaNotturna(t.inizio, t.fine) > 0

    /** Minuti del periodo che cadono nella fascia giornaliera [da, a), con da < a. */
    fun minutiInFascia(inizio: LocalDateTime, fine: LocalDateTime, da: LocalTime, a: LocalTime): Long {
        var totale = 0L
        var giorno = inizio.toLocalDate()
        while (!giorno.isAfter(fine.toLocalDate())) {
            val x = giorno.atTime(da).let { if (inizio.isAfter(it)) inizio else it }
            val y = giorno.atTime(a).let { if (fine.isBefore(it)) fine else it }
            if (y.isAfter(x)) totale += Duration.between(x, y).toMinutes()
            giorno = giorno.plusDays(1)
        }
        return totale
    }

    private fun ore(min: Long): String =
        String.format("%d h %02d'", min / 60, min % 60)

    // ---------- singolo servizio ----------

    fun verificaTurno(t: Turno, limiti: LimitiOrario? = null): List<Esito> {
        val esiti = mutableListOf<Esito>()
        val durata = Duration.between(t.inizio, t.fine).toMinutes()
        if (durata <= 0) {
            esiti += Esito(Gravita.VIOLAZIONE, "Orari non validi: la fine deve essere dopo l'inizio.")
            return esiti
        }
        if (t.minutiCondotta > durata) esiti += Esito(Gravita.ATTENZIONE,
            "I minuti di ${if (t.profilo == Profilo.MACCHINISTA) "condotta" else "scorta"} " +
                "(${t.minutiCondotta}) superano la durata del servizio: controlla il dato.")
        val notturno = èNotturno(t)
        esiti += Esito(Gravita.INFO,
            "Durata ${ore(durata)}, servizio ${if (notturno) "notturno" else "diurno"}" +
                if (t.profilo == Profilo.MACCHINISTA && fineTraMezzanotteEUna(t))
                    " (fine tra 00:01 e 01:00: equiparato a diurno, Altarail art. 17.3.1)." else ".")

        esiti += if (limiti != null) verificaDurataPersonalizzata(durata, notturno, limiti)
            else verificaDurata(t, durata, notturno)
        if (t.profilo == Profilo.MACCHINISTA) esiti += verificaCondotta(t, notturno)
        esiti += verificaPausa(t, durata, notturno)
        return esiti
    }

    private fun verificaDurataPersonalizzata(durata: Long, notturno: Boolean, limiti: LimitiOrario): List<Esito> {
        val limite = if (notturno) limiti.notturnoMinuti else limiti.diurnoMinuti
        val tipo = if (notturno) "notturna" else "diurna"
        return listOf(
            if (durata > limite) Esito(Gravita.VIOLAZIONE,
                "Prestazione $tipo oltre ${ore(limite.toLong())}: limite del profilo personalizzato.")
            else Esito(Gravita.OK, "Prestazione $tipo entro ${ore(limite.toLong())} (profilo personalizzato)."))
    }

    /** Altarail artt. 17.2.2 e 17.3.2; confronto con CCNL 2025 SP2 punto B.1. */
    private fun verificaDurata(t: Turno, durata: Long, notturno: Boolean): List<Esito> {
        val esiti = mutableListOf<Esito>()
        when (t.profilo) {
            Profilo.MACCHINISTA -> if (notturno) {
                if (durata > 7 * 60) esiti += Esito(Gravita.VIOLAZIONE,
                    "Prestazione notturna oltre 7 h (Altarail art. 17.3.2; CCNL SP2 B.1: 7 h con MEC1/MEC4).")
                else esiti += Esito(Gravita.OK, "Prestazione notturna entro 7 h.")
            } else {
                when {
                    durata > 10 * 60 -> esiti += Esito(Gravita.VIOLAZIONE,
                        "Prestazione oltre 10 h: supera anche il limite elevato (Altarail art. 17.2.2).")
                    durata > 8 * 60 + 30 -> esiti += Esito(Gravita.ATTENZIONE,
                        "Prestazione oltre 8 h 30'. È ammessa fino a 10 h una sola volta tra due " +
                            "riposi settimanali (Altarail art. 17.2.2): verifica che sia l'unica.")
                    else -> esiti += Esito(Gravita.OK, "Prestazione entro 8 h 30'.")
                }
            }
            Profilo.UFFICIO -> if (notturno) {
                esiti += Esito(Gravita.ATTENZIONE,
                    "Lavoro notturno non previsto per il personale di staff: verifica il dato.")
            } else {
                if (durata > OrarioStaff.PRESTAZIONE_MINUTI) esiti += Esito(Gravita.INFO,
                    "Oltre 7 h 36' di orario ordinario: l'eccedenza è straordinario.")
                else esiti += Esito(Gravita.OK, "Prestazione entro 7 h 36' (orario ordinario staff).")
            }
            Profilo.CAPOTRENO -> if (notturno) {
                if (durata > 8 * 60) esiti += Esito(Gravita.VIOLAZIONE,
                    "Prestazione notturna oltre 8 h (Altarail art. 17.3.2; CCNL SP2 B.1).")
                else esiti += Esito(Gravita.OK, "Prestazione notturna entro 8 h.")
            } else {
                if (durata > 10 * 60) esiti += Esito(Gravita.VIOLAZIONE,
                    "Prestazione oltre 10 h (Altarail art. 17.2.2).")
                else esiti += Esito(Gravita.OK, "Prestazione entro 10 h (Altarail art. 17.2.2).")
                if (durata > 9 * 60) esiti += Esito(Gravita.INFO,
                    "Il CCNL 2025 (SP2, B.1) prevede 9 h per i moduli MEB1/MEB2; il contratto " +
                        "Altarail ne ammette 10.")
            }
        }
        return esiti
    }

    /** Altarail art. 17.7; CCNL 2025 SP2 punto B.4 (stessi valori per MEC1/MEC4). */
    private fun verificaCondotta(t: Turno, notturno: Boolean): List<Esito> {
        val esiti = mutableListOf<Esito>()
        val limite = if (notturno) 270 else 330
        if (t.minutiCondotta > limite) esiti += Esito(Gravita.VIOLAZIONE,
            "Condotta giornaliera ${ore(t.minutiCondotta.toLong())}: oltre ${ore(limite.toLong())} " +
                "(Altarail art. 17.7.${if (notturno) "4" else "3"}).")
        else esiti += Esito(Gravita.OK,
            "Condotta giornaliera ${ore(t.minutiCondotta.toLong())} su ${ore(limite.toLong())}.")

        if (t.maxCondottaConsecutiva > 300) esiti += Esito(Gravita.VIOLAZIONE,
            "Condotta consecutiva ${ore(t.maxCondottaConsecutiva.toLong())}: oltre 5 h " +
                "(Altarail art. 17.7.2). Le soste sotto 15' contano come condotta (art. 17.7.1).")
        else if (t.maxCondottaConsecutiva > 0) esiti += Esito(Gravita.OK,
            "Condotta consecutiva entro 5 h.")
        return esiti
    }

    /** Altarail art. 17.9; CCNL 2025 art. 27 punti 1.12 e 2.6. */
    /**
     * Refezione: 30' in turno se il servizio interessa per più di 2 h la fascia
     * 11-15 o 18-22 (art. 17.9). Altrimenti pausa di 30' oltre
     * 6 h diurne (CCNL art. 27) o di 15' dopo 6 h notturne. Le pause sono assorbite
     * dalla refezione.
     */
    private fun verificaPausa(t: Turno, durata: Long, notturno: Boolean): List<Esito> {
        val pranzo = minutiInFascia(t.inizio, t.fine, LocalTime.of(11, 0), LocalTime.of(15, 0))
        val cena = minutiInFascia(t.inizio, t.fine, LocalTime.of(18, 0), LocalTime.of(22, 0))
        val fasce = listOfNotNull(
            "11-15".takeIf { pranzo > 120 },
            "18-22".takeIf { cena > 120 }
        )
        return when {
            fasce.isNotEmpty() -> listOf(Esito(Gravita.INFO,
                "Spetta la refezione di 30' in turno (fascia ${fasce.joinToString(" e ")}; " +
                    "art. 17.9)."))
            !notturno && durata > 6 * 60 -> listOf(Esito(Gravita.INFO,
                "Oltre 6 h diurne: spetta una pausa di 30' (CCNL art. 27)."))
            notturno && durata > 6 * 60 -> listOf(Esito(Gravita.INFO,
                "Oltre 6 h notturne: spetta una pausa di 15'."))
            else -> emptyList()
        }
    }

    // ---------- riposo tra due servizi ----------

    /** Riposo giornaliero in residenza: Altarail art. 17.4; confronto CCNL SP2 B.2. */
    /** Ore minime di riposo in residenza dopo il servizio e motivo (Altarail art. 17.4). */
    fun minimoRiposoDopo(t: Turno): Pair<Int, String> {
        val notturno = èNotturno(t)
        return when {
            notturno && t.turnoAZeta -> 14 to "dopo notturno in turno a zeta"
            // Art. 17.4 elenca il caso 00:01-01:00 a parte, per tutto il personale mobile:
            // anche per il PdB, per cui quel servizio è notturno, bastano 14 h.
            // Confermato dai turni ufficiali PdB: dopo queste prestazioni il riposo minimo è 14 h.
            fineTraMezzanotteEUna(t) -> 14 to "dopo una prestazione terminata tra 00:01 e 01:00"
            notturno -> 16 to "dopo una prestazione notturna"
            else -> 11 to "dopo una prestazione diurna"
        }
    }

    /**
     * Primo istante in cui può iniziare il servizio successivo: fine + riposo minimo
     * in residenza, oppure fine + 7 h se segue un riposo fuori residenza (CCNL SP2 B.3).
     */
    fun ripresaMinima(t: Turno, fuoriResidenza: Boolean = false): LocalDateTime =
        if (fuoriResidenza) t.fine.plusHours(7) else t.fine.plusHours(minimoRiposoDopo(t).first.toLong())

    fun verificaRiposo(precedente: Turno, successivo: Turno): List<Esito> {
        val esiti = mutableListOf<Esito>()
        val riposo = Duration.between(precedente.fine, successivo.inizio).toMinutes()
        if (riposo < 0) {
            esiti += Esito(Gravita.VIOLAZIONE, "Il servizio successivo inizia prima della fine del precedente.")
            return esiti
        }
        val (minimo, motivo) = minimoRiposoDopo(precedente)
        if (riposo < minimo * 60L) esiti += Esito(Gravita.VIOLAZIONE,
            "Riposo ${ore(riposo)}: servono almeno $minimo h $motivo (Altarail art. 17.4).")
        else esiti += Esito(Gravita.OK, "Riposo ${ore(riposo)}: minimo $minimo h $motivo rispettato.")

        // CCNL 2025 SP2 B.2: 14 h dopo diurno, 18 h dopo servizio nella fascia 0-5.
        val minimoCcnl = if (minutiInFasciaNotturna(precedente.inizio, precedente.fine) > 0) 18 else 14
        if (riposo < minimoCcnl * 60L) esiti += Esito(Gravita.INFO,
            "Il CCNL 2025 (SP2, B.2) prevede $minimoCcnl h; il contratto Altarail ne richiede $minimo. " +
                "Vale la deroga aziendale (CCNL art. 4.7), salvo accordi diversi.")
        return esiti
    }

    /** Riposo fuori residenza: CCNL 2025 SP2 punto B.3, richiamato da Altarail art. 17.5. */
    fun verificaRfr(andata: Turno, ritorno: Turno): List<Esito> {
        val esiti = mutableListOf<Esito>()
        val rfr = Duration.between(andata.fine, ritorno.inizio).toMinutes()
        val inFascia = minutiInFasciaNotturna(andata.fine, ritorno.inizio)
        val ok = (rfr >= 7 * 60 && inFascia > 0) || (rfr >= 6 * 60 && inFascia >= 4 * 60)
        if (ok) esiti += Esito(Gravita.OK, "RFR di ${ore(rfr)} con ${ore(inFascia)} nella fascia 0-5: valido.")
        else esiti += Esito(Gravita.VIOLAZIONE,
            "RFR di ${ore(rfr)} con ${ore(inFascia)} nella fascia 0-5: servono 7 h che interessino " +
                "la fascia 0-5, oppure 6 h con almeno 4 h nella fascia (CCNL SP2 B.3.a).")

        val assenza = Duration.between(andata.inizio, ritorno.fine).toMinutes()
        if (assenza > 24 * 60) esiti += Esito(Gravita.VIOLAZIONE,
            "Assenza dalla residenza ${ore(assenza)}: oltre 24 h (CCNL SP2 B.3.c).")

        val lavoro = Duration.between(andata.inizio, andata.fine).toMinutes() +
            Duration.between(ritorno.inizio, ritorno.fine).toMinutes()
        if (lavoro > 16 * 60) esiti += Esito(Gravita.VIOLAZIONE,
            "I due periodi intorno all'RFR sommano ${ore(lavoro)}: oltre 16 h (CCNL SP2 B.1.a).")

        val maxMese = if (andata.profilo == Profilo.MACCHINISTA) 5 else 4
        esiti += Esito(Gravita.INFO,
            "Limiti RFR da controllare sul turno: max 2 tra due riposi settimanali e $maxMese al mese (CCNL SP2 B.3.b).")
        return esiti
    }

    /** Promemoria dei limiti che richiedono il turno completo. */
    val promemoria: List<String> = listOf(
        "Orario: 38 h medie settimanali, calcolate nel mese, tra 30 e 44 h (Altarail art. 17.1.1).",
        "Notturni: max 2 tra due riposi settimanali, 11 al mese, 79 l'anno (CCNL SP2 B.5); max 320 ore notturne annue (Altarail art. 17.3.3).",
        "Riposo settimanale: almeno 62 h consecutive con 2 giornate solari complete (Altarail art. 17.6.2); 52 riposi + 52 giornate libere l'anno.",
        "Termine del servizio: il servizio può prolungarsi di 1 h oltre il limite massimo della prestazione giornaliera o di 2 h oltre il termine programmato (CCNL SP2 B.6;).",
        "Festività: il 7 dicembre (S. Ambrogio) sostituisce il 29 giugno (Altarail art. 19.1)."
    )
}
