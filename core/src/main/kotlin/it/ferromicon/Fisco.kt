package it.ferromicon

/**
 * Stima del netto: quanto resta in tasca di un importo lordo aggiuntivo (per esempio lo straordinario)
 * dato il reddito imponibile annuo. Calcolo indicativo: IRPEF a scaglioni, detrazioni per lavoro
 * dipendente, addizionali, contributi INPS. Le regole per anno sono quelle verificate sulle CU 2015-2025
 * del calcolo fiscale; per gli anni successivi valgono quelle più recenti note.
 */
object Fisco {

    /** Parametri personali: aliquota INPS a carico del lavoratore e addizionali, in percentuale. */
    data class Parametri(
        val inps: Double = 0.0919,
        val addComunalePct: Double = 0.8,
        /** Addizionale regionale fissa in %; null = scaglioni della Lombardia. */
        val addRegionalePct: Double? = null
    )

    data class Regole(
        val scaglioni: List<Pair<Double, Double>>,
        val vecchie: Boolean = false,
        val ulterioreDetrazione: Double = 0.0,
        val detrazioneFinoA15000: Double = 1880.0,
        val bonus65: Boolean = false,
        val cuneo: Boolean = false
    )

    data class Irpef(val lorda: Double, val detrazioni: Double, val ulteriore: Double, val esente: Double) {
        val netta get() = maxOf(0.0, lorda - detrazioni - ulteriore)
    }

    data class NettoExtra(val lordo: Double, val inps: Double, val imponibile: Double, val tasse: Double) {
        val netto get() = imponibile - tasse
    }

    private const val INF = 1e12

    fun regole(anno: Int): Regole = when {
        anno <= 2021 -> Regole(
            listOf(15000.0 to .23, 28000.0 to .27, 55000.0 to .38, 75000.0 to .41, INF to .43),
            vecchie = true, ulterioreDetrazione = when (anno) { 2020 -> 0.5; 2021 -> 1.0; else -> 0.0 }
        )
        anno <= 2023 -> Regole(listOf(15000.0 to .23, 28000.0 to .25, 50000.0 to .35, INF to .43),
            detrazioneFinoA15000 = 1880.0, bonus65 = true)
        anno == 2024 -> Regole(listOf(28000.0 to .23, 50000.0 to .35, INF to .43),
            detrazioneFinoA15000 = 1955.0, bonus65 = true)
        anno == 2025 -> Regole(listOf(28000.0 to .23, 50000.0 to .35, INF to .43),
            detrazioneFinoA15000 = 1955.0, bonus65 = true, cuneo = true)
        else -> Regole(listOf(28000.0 to .23, 50000.0 to .33, INF to .43),
            detrazioneFinoA15000 = 1955.0, bonus65 = true, cuneo = true)
    }

    fun irpef(reddito: Double, anno: Int): Irpef {
        val g = regole(anno)
        var da = 0.0
        var lorda = 0.0
        for ((fino, aliquota) in g.scaglioni) {
            lorda += maxOf(0.0, minOf(reddito, fino) - da) * aliquota
            da = fino
        }
        var detr = 0.0
        var ulteriore = 0.0
        var esente = 0.0
        if (g.vecchie) {
            detr = when {
                reddito <= 8000 -> 1880.0
                reddito <= 28000 -> 978 + 902 * (28000 - reddito) / 20000
                reddito <= 55000 -> 978 * (55000 - reddito) / 27000
                else -> 0.0
            }
            if (g.ulterioreDetrazione > 0) {
                ulteriore = when {
                    reddito in 28000.0..35000.0 && reddito > 28000 -> 960 + 240 * (35000 - reddito) / 7000
                    reddito > 35000 && reddito <= 40000 -> 960 * (40000 - reddito) / 5000
                    else -> 0.0
                } * g.ulterioreDetrazione
            }
        } else {
            detr = when {
                reddito <= 15000 -> g.detrazioneFinoA15000
                reddito <= 28000 -> 1910 + 1190 * (28000 - reddito) / 13000
                reddito <= 50000 -> 1910 * (50000 - reddito) / 22000
                else -> 0.0
            }
            if (g.bonus65 && reddito > 25000 && reddito <= 35000) detr += 65
            if (g.cuneo) {
                ulteriore = when {
                    reddito > 20000 && reddito <= 32000 -> 1000.0
                    reddito > 32000 && reddito <= 40000 -> 1000 * (40000 - reddito) / 8000
                    else -> 0.0
                }
                esente = when {
                    reddito <= 8500 -> reddito * .071
                    reddito <= 15000 -> reddito * .053
                    reddito <= 20000 -> reddito * .048
                    else -> 0.0
                }
            }
        }
        return Irpef(lorda, detr, ulteriore, esente)
    }

    /** Addizionale regionale: aliquota fissa scelta dall'utente oppure scaglioni della Lombardia. */
    fun regionale(reddito: Double, fissaPct: Double?): Double {
        if (fissaPct != null) return reddito * fissaPct / 100
        var da = 0.0
        var tot = 0.0
        for ((fino, a) in listOf(15000.0 to .0123, 28000.0 to .0158, 50000.0 to .0172, INF to .0173)) {
            tot += maxOf(0.0, minOf(reddito, fino) - da) * a
            da = fino
        }
        return tot
    }

    fun tasse(reddito: Double, anno: Int, p: Parametri): Double {
        val i = irpef(reddito, anno)
        return i.netta + regionale(reddito, p.addRegionalePct) + reddito * p.addComunalePct / 100 - i.esente
    }

    /** Netto in tasca di un importo lordo aggiuntivo, partendo dal reddito imponibile annuo [reddito]. */
    fun nettoExtra(reddito: Double, lordo: Double, anno: Int, p: Parametri = Parametri()): NettoExtra {
        val imponibile = lordo * (1 - p.inps)
        val t = tasse(reddito + imponibile, anno, p) - tasse(reddito, anno, p)
        return NettoExtra(lordo, lordo * p.inps, imponibile, t)
    }

    /** Aliquota marginale effettiva (IRPEF, detrazioni perse, addizionali) su 1 € di imponibile. */
    fun marginale(reddito: Double, anno: Int, p: Parametri = Parametri()): Double {
        val d = 10.0
        return (tasse(reddito + d, anno, p) - tasse(reddito, anno, p)) / d
    }
}
