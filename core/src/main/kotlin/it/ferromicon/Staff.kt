package it.ferromicon

/**
 * Regole del personale di staff (ufficio), dal Vademecum Trenord Employee Self Service.
 * Gli orari sono in minuti dalla mezzanotte.
 */
object OrarioStaff {
    /** Prestazione ordinaria giornaliera: 7 h 36' (sul cartellino "7,36"). */
    const val PRESTAZIONE_MINUTI = 7 * 60 + 36

    /** Ingresso tra 08:15 e 09:00. */
    const val INGRESSO_DA = 8 * 60 + 15
    const val INGRESSO_A = 9 * 60

    /** Fascia rigida: mattina 09:00-12:30, pomeriggio 14:00-16:21. */
    const val RIGIDA_MATTINA_DA = 9 * 60
    const val RIGIDA_MATTINA_A = 12 * 60 + 30
    const val RIGIDA_POMERIGGIO_DA = 14 * 60
    const val RIGIDA_POMERIGGIO_A = 16 * 60 + 21

    /** Uscita tra 16:21 e 17:06. */
    const val USCITA_DA = 16 * 60 + 21
    const val USCITA_A = 17 * 60 + 6

    /** Pausa pranzo di 30' da fruire tra 12:30 e 14:00. */
    const val PAUSA_MINUTI = 30

    /** Flessibilità mensile: saldo entro ±5 h. */
    const val FLESSIBILITA_MAX = 5 * 60

    /**
     * Riporto al mese successivo di un saldo di flessibilità, in minuti (anche negativo).
     * Restituisce (riportato, eccedenza). Il saldo positivo oltre +5 h si riporta come +5 h
     * e l'eccedenza va persa. Il saldo negativo oltre -5 h si riporta come -5 h e
     * l'eccedenza (negativa) viene trattenuta in busta paga.
     */
    fun riportoFlessibilita(saldo: Int): Pair<Int, Int> {
        val riportato = saldo.coerceIn(-FLESSIBILITA_MAX, FLESSIBILITA_MAX)
        return riportato to (saldo - riportato)
    }

    /** Minuti da «HH:MM», oppure null se il formato non è valido. */
    fun minutiDa(orario: String?): Int? {
        if (orario == null) return null
        val p = orario.split(":")
        if (p.size != 2) return null
        val h = p[0].toIntOrNull() ?: return null
        val m = p[1].toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }

    /**
     * Flessibilità del cartellino in minuti con segno. Il cartellino scrive ore e minuti:
     * «1,46» sono 1 h 46', «0,05-» sono -5 minuti.
     */
    fun minutiFlex(valore: Double): Int {
        val centesimi = Math.round(Math.abs(valore) * 100).toInt()
        val minuti = (centesimi / 100) * 60 + centesimi % 100
        return if (valore < 0) -minuti else minuti
    }

    /**
     * Saldo mensile di flessibilità in minuti: riporto del mese precedente più la somma
     * della flessibilità di ogni giornata.
     */
    fun saldoMese(giornate: List<GiornataSap>, riportoPrecedente: Int = 0): Int =
        riportoPrecedente + giornate.sumOf { g -> g.flex?.let { minutiFlex(it) } ?: 0 }

    /**
     * Giornate lavorate con meno di due timbrature (entrata e uscita). La pausa pranzo non si timbra:
     * è inclusa e a discrezione del dipendente. Le giornate senza timbrature (ferie, riposi) non contano.
     */
    fun giornateConTimbratureInsufficienti(giornate: List<GiornataSap>, minimo: Int = 2): List<GiornataSap> =
        giornate.filter { it.numeroTimbrature in 1 until minimo }

    /**
     * Giorni in cui l'uscita avviene prima delle 16:21 senza un permesso ad ore dello stesso giorno.
     * [giorniConPermesso] sono i numeri di giorno con almeno un permesso registrato.
     */
    fun usciteAnticipateSenzaPermesso(giornate: List<GiornataSap>, giorniConPermesso: Set<Int>): List<Int> =
        giornate.filter { g ->
            val uscita = minutiDa(g.ultima)
            g.numeroTimbrature > 0 && uscita != null && uscita < USCITA_DA && g.giorno !in giorniConPermesso
        }.map { it.giorno }

    /** Minuti lavorati nei periodi di orario di un servizio fuori sede o di una trasferta (coppie «da», «a»). */
    fun minutiPeriodi(periodi: List<Pair<String, String>>): Int =
        periodi.sumOf { (da, a) ->
            val inizio = minutiDa(da)
            val fine = minutiDa(a)
            if (inizio != null && fine != null && fine > inizio) fine - inizio else 0
        }

    /**
     * Minuti addebitati per un permesso ad ore: sotto l'ora si scala un'ora, sopra l'ora si scalano
     * i minuti esatti, la giornata intera scala 7 h 36'.
     */
    fun costoPermessoOre(minuti: Int): Int = when {
        minuti <= 0 -> 0
        minuti >= PRESTAZIONE_MINUTI -> PRESTAZIONE_MINUTI
        minuti < 60 -> 60
        else -> minuti
    }

    /** Avviso per la schermata dello staff quando il saldo supera ±5 ore; null se il saldo è entro il limite. */
    fun avvisoFlessibilita(saldoMinuti: Int): String? = when {
        saldoMinuti > FLESSIBILITA_MAX ->
            "Saldo di flessibilità oltre +5 h: si riportano +5 h e il resto va perso."
        saldoMinuti < -FLESSIBILITA_MAX ->
            "Saldo di flessibilità oltre -5 h: si riportano -5 h e il resto va trattenuto in busta paga."
        else -> null
    }

    /** Giorni di ex festività ancora fruibili, su 4 giorni individuali non frazionabili nell'anno. */
    fun exFestivitaResidue(giorniUsati: Int): Int = (EX_FESTIVITA_GIORNI - giorniUsati).coerceAtLeast(0)

    const val EX_FESTIVITA_GIORNI = 4

    /**
     * Accessori del macchinista: minuti tra l'arrivo prescritto dell'ultimo treno del servizio
     * e il termine finale della giornata. Se il termine è prima dell'arrivo, restituisce 0.
     */
    fun accessoriPdm(arrivoUltimoTreno: java.time.LocalDateTime, terminePrescritto: java.time.LocalDateTime): Int =
        java.time.Duration.between(arrivoUltimoTreno, terminePrescritto).toMinutes().toInt().coerceAtLeast(0)

    /** Fine reale della giornata: arrivo reale dell'ultimo treno più gli accessori. */
    fun fineRealePdm(arrivoRealeUltimoTreno: java.time.LocalDateTime, accessori: Int): java.time.LocalDateTime =
        arrivoRealeUltimoTreno.plusMinutes(accessori.toLong())

    /** Durata minima della pausa refezione (art. 17.9), in minuti. */
    const val REFEZIONE_MINUTI = 30

    /**
     * Accessori di giornata (PdB e PdM): tempo di servizio non in condotta,
     * meno la refezione e i treni fuori servizio (vuoti verso deposito, né condotta né accessori).
     */
    fun accessoriGiornata(lavoro: Int, condotta: Int, refezione: Int, fuoriServizio: Int): Int =
        (lavoro - condotta - refezione - fuoriServizio).coerceAtLeast(0)
}
