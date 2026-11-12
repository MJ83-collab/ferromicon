package it.ferromicon

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth

/** Una voce della stima delle competenze variabili. */
data class VoceCompetenza(
    val etichetta: String,
    val importo: Double,
    val dettaglio: String,
    /** Codici della voce nel ruolo paga Altarail, per il confronto con la busta. */
    val codice: String = ""
)

data class StimaCompetenze(val voci: List<VoceCompetenza>, val note: List<String>) {
    val totale get() = voci.sumOf { it.importo }
}

/**
 * Stima indicativa delle competenze variabili del mese.
 *
 * Regole:
 *  - Incentivo condotta e scorta: art. 54 Altarail
 *    (12 €/h di condotta fino a 3 h, 18 €/h oltre; scorta 4 €/h e 4,50 €/h).
 *  - Regolarizzazioni: Contratto Altarail 2012, art. 54.2.2.
 *  - Riserva e formazione: 20 € a prestazione PdM, 10 € PdB.
 *  - Indennità di turno 2,45 € per giornata di presenza (2,25 € prima dell'1/8/2025): CCNL art. 81.1.b.
 *  - Indennità notturna 2,50 €/h (2,40 € prima dell'1/8/2025), fascia 22-6: CCNL 2025, art. 75.
 *  - Indennità domenicale 23,50 € (20 € prima dell'1/8/2025), Pasqua 65 €: CCNL 2025, art. 76.1-3.
 *  - Indennità festiva oraria 35%: CCNL 2025, art. 76.4, su minimo (art. 68.3),
 *    scatti (art. 69), ad personam e salario professionale (art. 72), divisore 160 (art. 68.6).
 *  - Straordinario: oltre 8 h 30' / 7 h notturne (PdM) e 10 h / 8 h notturne (PdB), maggiorazioni
 *    18% feriale diurno, 35% feriale notturno e festivo diurno, 50% festivo notturno (CCNL art. 74).
 *  - Indennità ferie: 40 € PdM, 20 € PdB per giornata di ferie da maggio 2024; 20 € e 10 € da
 *    ottobre 2019.
 *  - Assenza dalla residenza: 1,60 €/h senza RFR, 2,60 €/h con RFR (CCNL art. 77.2, dall'1/8/2025;
 *    prima 1,30 € e 2,20 €), per giornate con almeno 3 ore fuori residenza.
 *  - Pernottamento: 2,80 € per assenza dalla residenza tra le 22 e le 6 (CCNL art. 75.3).
 */
object Competenze {

    // ---------- tabelle ----------

    /** Minimi contrattuali mensili (CCNL 2025, art. 68.3): dal 1/6/2025, 1/11/2025, 1/6/2026. */
    private val MINIMI: Map<Livello, DoubleArray> = mapOf(
        Livello.Q1 to doubleArrayOf(2678.55, 2759.01, 2826.07),
        Livello.Q2 to doubleArrayOf(2353.41, 2424.11, 2483.02),
        Livello.A to doubleArrayOf(2275.99, 2344.37, 2401.34),
        Livello.B1 to doubleArrayOf(2167.61, 2232.73, 2286.99),
        Livello.B2 to doubleArrayOf(2074.71, 2137.04, 2188.97),
        Livello.B3 to doubleArrayOf(2043.75, 2105.14, 2156.31),
        Livello.C1 to doubleArrayOf(1997.30, 2057.30, 2107.30),
        Livello.C2 to doubleArrayOf(1966.33, 2025.40, 2074.62),
        Livello.D1 to doubleArrayOf(1935.36, 1993.51, 2041.95),
        Livello.D2 to doubleArrayOf(1873.44, 1929.72, 1976.62),
        Livello.D3 to doubleArrayOf(1842.47, 1897.82, 1943.94),
        Livello.E1 to doubleArrayOf(1811.50, 1865.92, 1911.26),
        Livello.E2 to doubleArrayOf(1734.10, 1786.18, 1829.60),
        Livello.E3 to doubleArrayOf(1703.12, 1754.29, 1796.91),
        Livello.F1 to doubleArrayOf(1579.25, 1626.70, 1666.23),
        Livello.F2 to doubleArrayOf(1548.29, 1594.81, 1633.56)
    )

    /** Importo di ogni aumento biennale di anzianità (CCNL 2025, art. 69.1). */
    private val SCATTI: Map<Livello, Double> = mapOf(
        Livello.Q1 to 47.19, Livello.Q2 to 38.27, Livello.A to 35.95,
        Livello.B1 to 33.62, Livello.B2 to 31.77, Livello.B3 to 31.30,
        Livello.C1 to 29.91, Livello.C2 to 29.45,
        Livello.D1 to 28.42, Livello.D2 to 25.64, Livello.D3 to 25.22,
        Livello.E1 to 24.34, Livello.E2 to 22.66, Livello.E3 to 22.26,
        Livello.F1 to 18.58, Livello.F2 to 18.22
    )

    /** Salario professionale mensile (CCNL 2025, art. 72; Altarail art. 53). */
    fun salarioProfessionale(p: Profilo) = when (p) {
        Profilo.MACCHINISTA -> 208.96
        Profilo.CAPOTRENO, Profilo.UFFICIO -> 142.54
    }

    fun minimo(livello: Livello, data: LocalDate): Double {
        val valori = MINIMI.getValue(livello)
        return when {
            !data.isBefore(LocalDate.of(2026, 6, 1)) -> valori[2]
            !data.isBefore(LocalDate.of(2025, 11, 1)) -> valori[1]
            else -> valori[0]
        }
    }

    /** Aumenti biennali maturati: decorrono dal mese dopo il compimento del biennio, massimo 7 (art. 69). */
    fun numeroScatti(assunzione: LocalDate?, data: LocalDate): Int {
        if (assunzione == null) return 0
        val meseRiferimento = data.withDayOfMonth(1)
        return (1..7).count { k ->
            !assunzione.plusYears(2L * k).withDayOfMonth(1).plusMonths(1).isAfter(meseRiferimento)
        }
    }

    /** Retribuzione oraria per l'indennità festiva (art. 76.4 e 68.6). */
    fun pagaOraria(imp: Impostazioni, profilo: Profilo, data: LocalDate): Double {
        val mensile = minimo(imp.livello, data) +
            numeroScatti(imp.dataAssunzione, data) * SCATTI.getValue(imp.livello) +
            imp.adPersonam + salarioProfessionale(profilo)
        return mensile / 160.0
    }

    // ---------- incentivi Altarail (art. 54) ----------

    /**
     * Condotta: 12 €/h fino a 3 ore, 18 €/h oltre la terza.
     * Le frazioni d'ora sono calcolate in proporzione al minuto (interpretazione dell'app).
     */
    fun incentivoCondotta(minuti: Int): Double =
        if (minuti <= 180) minuti * 12.0 / 60 else 36.0 + (minuti - 180) * 18.0 / 60

    /** Scorta: 4 €/h fino a 3 ore, 4,50 €/h oltre la terza (frazioni al minuto). */
    fun incentivoScorta(minuti: Int): Double =
        if (minuti <= 180) minuti * 4.0 / 60 else 12.0 + (minuti - 180) * 4.5 / 60

    private val AGOSTO_2025: LocalDate = LocalDate.of(2025, 8, 1)
    fun tariffaNotturna(data: LocalDate) = if (data.isBefore(AGOSTO_2025)) 2.40 else 2.50
    fun indennitaDomenicale(data: LocalDate) = if (data.isBefore(AGOSTO_2025)) 20.0 else 23.50
    fun indennitaTurno(data: LocalDate) = if (data.isBefore(AGOSTO_2025)) 2.25 else 2.45
    fun indennitaRiserva(p: Profilo) = if (p == Profilo.MACCHINISTA) 20.0 else 10.0

    // ---------- utilità sugli orari ----------

    /** Minuti del periodo che cadono in ciascun giorno di calendario. */
    fun minutiPerGiorno(inizio: LocalDateTime, fine: LocalDateTime): Map<LocalDate, Long> {
        val mappa = linkedMapOf<LocalDate, Long>()
        var giorno = inizio.toLocalDate()
        while (giorno.atStartOfDay().isBefore(fine)) {
            val a = if (inizio.isAfter(giorno.atStartOfDay())) inizio else giorno.atStartOfDay()
            val finale = giorno.plusDays(1).atStartOfDay()
            val b = if (fine.isBefore(finale)) fine else finale
            if (b.isAfter(a)) mappa[giorno] = Duration.between(a, b).toMinutes()
            giorno = giorno.plusDays(1)
        }
        return mappa
    }

    /** Minuti nella fascia notturna 22:00-06:00 (art. 75). */
    fun minutiNotturni(inizio: LocalDateTime, fine: LocalDateTime): Long {
        var totale = 0L
        var giorno = inizio.toLocalDate().minusDays(1)
        while (!giorno.isAfter(fine.toLocalDate())) {
            val da = giorno.atTime(LocalTime.of(22, 0))
            val a = giorno.plusDays(1).atTime(LocalTime.of(6, 0))
            val x = if (inizio.isAfter(da)) inizio else da
            val y = if (fine.isBefore(a)) fine else a
            if (y.isAfter(x)) totale += Duration.between(x, y).toMinutes()
            giorno = giorno.plusDays(1)
        }
        return totale
    }

    /** Ore notturne del mese: la frazione oltre 30 minuti vale un'ora intera (art. 75.2). */
    fun oreNotturneArrotondate(minuti: Long): Long = minuti / 60 + if (minuti % 60 > 30) 1 else 0

    // ---------- stima del mese ----------

    private val OTTOBRE_2019: LocalDate = LocalDate.of(2019, 10, 1)
    private val MAGGIO_2024: LocalDate = LocalDate.of(2024, 5, 1)

    /** Indennità per giornata di ferie del personale mobile. */
    fun indennitaFerie(p: Profilo, data: LocalDate): Double {
        val pieno = if (p == Profilo.MACCHINISTA) 40.0 else 20.0
        return when {
            !data.isBefore(MAGGIO_2024) -> pieno
            !data.isBefore(OTTOBRE_2019) -> pieno / 2
            else -> 0.0
        }
    }

    /** Compenso orario per assenza dalla residenza (CCNL art. 77.2). */
    fun tariffaFuoriResidenza(conRfr: Boolean, data: LocalDate): Double =
        if (data.isBefore(AGOSTO_2025)) (if (conRfr) 2.20 else 1.30) else (if (conRfr) 2.60 else 1.60)

    /** Limite oltre il quale la prestazione è straordinario. */
    fun limiteStraordinario(t: Turno): Int {
        val notturno = RegoleAltarail.èNotturno(t)
        return when (t.profilo) {
            Profilo.MACCHINISTA -> if (notturno) 7 * 60 else 8 * 60 + 30
            Profilo.CAPOTRENO, Profilo.UFFICIO -> if (notturno) 8 * 60 else 10 * 60
        }
    }

    /**
     * Minuti di straordinario del servizio, divisi per maggiorazione:
     * le eccedenze sono collocate alla fine del servizio.
     * Restituisce (feriale diurno, feriale notturno o festivo diurno, festivo notturno).
     */
    fun straordinario(t: Turno): Triple<Long, Long, Long> {
        val durata = Duration.between(t.inizio, t.fine).toMinutes()
        val eccedenza = durata - limiteStraordinario(t)
        if (eccedenza <= 0) return Triple(0, 0, 0)
        var a = 0L; var b = 0L; var c = 0L
        var istante = t.fine.minusMinutes(eccedenza)
        while (istante.isBefore(t.fine)) {
            val ora = istante.toLocalTime()
            val notte = ora >= LocalTime.of(22, 0) || ora < LocalTime.of(6, 0)
            val festivo = Festivi.èFestivoContrattuale(istante.toLocalDate())
            when {
                festivo && notte -> c++
                festivo || notte -> b++
                else -> a++
            }
            istante = istante.plusMinutes(1)
        }
        return Triple(a, b, c)
    }

    /** Giornate retribuite che danno l'indennità di turno oltre alla presenza. */
    val TIPI_CON_INDENNITA_TURNO = setOf(
        TipoAssenza.FERIE, TipoAssenza.PERMESSO, TipoAssenza.PERMESSO_EX_FESTIVITA, TipoAssenza.DISPONIBILITA
    )

    fun stimaMese(dati: Dati, mese: YearMonth): StimaCompetenze {
        val ordinati = dati.servizi.sortedBy { it.turno.inizio }
        val servizi = ordinati.filter { YearMonth.from(it.turno.inizio) == mese }
        val assenzeMese = dati.assenze.filter { YearMonth.from(it.data) == mese }
        val voci = mutableListOf<VoceCompetenza>()
        val note = mutableListOf<String>()
        if (servizi.isEmpty() && assenzeMese.isEmpty()) return StimaCompetenze(voci, note)
        val imp = dati.impostazioni
        val profilo = servizi.firstOrNull()?.turno?.profilo ?: dati.servizi.lastOrNull()?.turno?.profilo
            ?: Profilo.MACCHINISTA
        val inizioMese = mese.atDay(1)
        val pdm = profilo == Profilo.MACCHINISTA

        // Incentivi Altarail (per servizio: le fasce si applicano turno per turno)
        val macchina = servizi.filter { it.turno.profilo == Profilo.MACCHINISTA && it.turno.minutiCondotta > 0 }
        val bordo = servizi.filter { it.turno.profilo == Profilo.CAPOTRENO && it.turno.minutiCondotta > 0 }
        if (macchina.isNotEmpty()) {
            val minuti = macchina.sumOf { it.turno.minutiCondotta }
            voci += VoceCompetenza("Incentivo condotta",
                macchina.sumOf { incentivoCondotta(it.turno.minutiCondotta) },
                "${minuti / 60} h ${minuti % 60}' in ${macchina.size} servizi: 12 €/h fino a 3 h, 18 €/h oltre " +
                    "", "1A0A, 1A0B")
        }
        if (bordo.isNotEmpty()) {
            val minuti = bordo.sumOf { it.turno.minutiCondotta }
            voci += VoceCompetenza("Incentivo scorta",
                bordo.sumOf { incentivoScorta(it.turno.minutiCondotta) },
                "${minuti / 60} h ${minuti % 60}' in ${bordo.size} servizi: 4 €/h fino a 3 h, 4,50 €/h oltre " +
                    "", "1A0C, 1A0D")
        }
        val riserve = servizi.filter { it.riserva }
        if (riserve.isNotEmpty()) voci += VoceCompetenza("Riserva e formazione",
            riserve.sumOf { indennitaRiserva(it.turno.profilo) },
            "${riserve.size} prestazioni × ${euro(indennitaRiserva(profilo))}",
            if (pdm) "1A0F, 1A0M" else "1A0Q, 1A0P")
        val regolarizzazioni = servizi.sumOf { it.regolarizzazioni }
        val sovrattasse = servizi.sumOf { it.sovrattasse }
        if (regolarizzazioni > 0 || sovrattasse > 0) voci += VoceCompetenza("Controlleria",
            regolarizzazioni * 2.0 + sovrattasse * 0.25,
            "$regolarizzazioni regolarizzazioni × 2 € + 25% di ${euro(sovrattasse)} di sovrattasse " +
                "(art. 54.2.2)", "1A28, 1A29")

        // Indennità ferie
        val giorniFerie = assenzeMese.filter { it.tipo == TipoAssenza.FERIE }
        val ferie = giorniFerie.sumOf { indennitaFerie(profilo, it.data) }
        if (ferie > 0) voci += VoceCompetenza("Indennità ferie", ferie,
            "${giorniFerie.size} giornate × ${euro(indennitaFerie(profilo, inizioMese))}",
            if (pdm) "1A1A" else "1A1C")

        // Indennità di turno: presenza, assenze retribuite e congedi (CCNL art. 81.1.b)
        val giornate = (servizi.map { it.turno.inizio.toLocalDate() } +
            assenzeMese.filter { it.tipo in TIPI_CON_INDENNITA_TURNO }.map { it.data }).distinct()
        if (giornate.isNotEmpty()) voci += VoceCompetenza("Indennità di turno",
            giornate.sumOf { indennitaTurno(it) },
            "${giornate.size} giornate di presenza o assenza retribuita × ${euro(indennitaTurno(inizioMese))} " +
                "(CCNL art. 81)", "1A43")

        // Notturno 22-6 (art. 75)
        val minutiNotte = servizi.sumOf { minutiNotturni(it.turno.inizio, it.turno.fine) }
        val oreNotte = oreNotturneArrotondate(minutiNotte)
        val tariffa = tariffaNotturna(inizioMese)
        if (oreNotte > 0) voci += VoceCompetenza("Indennità notturna", oreNotte * tariffa,
            "$oreNotte h tra le 22 e le 6 × ${euro(tariffa)} (CCNL art. 75)", "1A40")

        // Minuti lavorati per giorno di calendario
        val perGiorno = mutableMapOf<LocalDate, Long>()
        servizi.forEach { s ->
            minutiPerGiorno(s.turno.inizio, s.turno.fine).forEach { (g, m) -> perGiorno.merge(g, m, Long::plus) }
        }

        // Domenicale (art. 76.1-3)
        var domenicale = 0.0
        val domeniche = mutableListOf<String>()
        perGiorno.filter { (g, _) -> g.dayOfWeek == DayOfWeek.SUNDAY }.forEach { (g, m) ->
            val pieno = if (g == Festivi.pasqua(g.year)) 65.0 else indennitaDomenicale(g)
            domenicale += if (m > 120) pieno else pieno / 2
            domeniche += "${g.dayOfMonth}" + if (m > 120) "" else " (≤2 h)"
        }
        if (domenicale > 0) voci += VoceCompetenza("Indennità domenicale", domenicale,
            "Domeniche lavorate: ${domeniche.joinToString()} (CCNL art. 76)", "1A45, 1A47")

        // Festivo (art. 76.4): 35% della paga oraria per ogni ora nel festivo
        val oraria = pagaOraria(imp, profilo, inizioMese)
        val minutiFestivi = perGiorno.filter { (g, _) -> Festivi.èFestivoContrattuale(g) }.values.sum()
        if (minutiFestivi > 0) voci += VoceCompetenza("Indennità festiva", minutiFestivi / 60.0 * oraria * 0.35,
            "${minutiFestivi / 60} h ${minutiFestivi % 60}' nei festivi × 35% di ${euro(oraria)}/h (CCNL art. 76.4)",
            "1A46")

        // Straordinario (art. 74): retribuzione oraria maggiorata
        var stra = Triple(0L, 0L, 0L)
        servizi.forEach { s ->
            val x = straordinario(s.turnoEffettivo())
            stra = Triple(stra.first + x.first, stra.second + x.second, stra.third + x.third)
        }
        val minutiStra = stra.first + stra.second + stra.third
        if (minutiStra > 0) {
            val importo = (stra.first * 1.18 + stra.second * 1.35 + stra.third * 1.50) / 60.0 * oraria
            voci += VoceCompetenza("Straordinario", importo,
                "${minutiStra / 60} h ${minutiStra % 60}' oltre il limite giornaliero, " +
                    "maggiorazioni 18/35/50% su ${euro(oraria)}/h (CCNL art. 74)", "1A00, 1A01, 1A02, 1A03")
        }

        // Assenza dalla residenza (art. 77.2) e pernottamento (art. 75.3)
        val conRfr = servizi.filter { s ->
            val i = ordinati.indexOf(s)
            s.rfrDopo || (i > 0 && ordinati[i - 1].rfrDopo)
        }.toSet()
        val fuori = servizi.filter { it.minutiFuoriResidenza >= 180 }
        if (fuori.isNotEmpty()) {
            fun ore(lista: List<Servizio>) = oreNotturneArrotondate(lista.sumOf { it.minutiFuoriResidenza.toLong() })
            val oreRfr = ore(fuori.filter { it in conRfr })
            val oreSenza = ore(fuori.filter { it !in conRfr })
            val importo = oreRfr * tariffaFuoriResidenza(true, inizioMese) +
                oreSenza * tariffaFuoriResidenza(false, inizioMese)
            voci += VoceCompetenza("Assenza dalla residenza", importo,
                "$oreSenza h senza RFR × ${euro(tariffaFuoriResidenza(false, inizioMese))}, " +
                    "$oreRfr h con RFR × ${euro(tariffaFuoriResidenza(true, inizioMese))} (CCNL art. 77.2)",
                "1A31, 1A33")
        }
        val pernottamenti = servizi.count { s ->
            val i = ordinati.indexOf(s)
            s.rfrDopo && i + 1 < ordinati.size &&
                minutiNotturni(s.turno.fine, ordinati[i + 1].turno.inizio) > 0
        }
        if (pernottamenti > 0) voci += VoceCompetenza("Pernottamento", pernottamenti * 2.80,
            "$pernottamenti riposi fuori residenza tra le 22 e le 6 × 2,80 € (CCNL art. 75.3)", "1A41")

        if (minutiStra > 0 || minutiFestivi > 0) {
            if (imp.dataAssunzione == null)
                note += "Senza data di assunzione la paga oraria non include gli scatti di anzianità."
            note += "Paga oraria calcolata sul livello ${imp.livello.name}: controllalo nelle impostazioni."
        }
        if (conRfr.isNotEmpty() && fuori.isEmpty())
            note += "Per stimare l'assenza dalla residenza inserisci nei servizi le ore fuori residenza."
        note += "Stima indicativa di importi lordi. Fa fede la busta paga; i codici tra parentesi sono quelli " +
            "del ruolo paga Altarail."
        return StimaCompetenze(voci, note)
    }

    fun euro(v: Double) = String.format(java.util.Locale.ITALIAN, "%.2f €", v)
}
