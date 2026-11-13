package it.ferromicon

import java.time.Duration
import java.time.YearMonth
import java.time.LocalTime

/**
 * Confronto tra turno senza straordinario e turno con straordinario: lordo, netto (ricalcolo annuo
 * con detrazioni), valore dell'ora normale e straordinaria, impegno e ore notturne.
 * Stima indicativa: non è il conteggio del cedolino.
 */
object ConfrontoStraordinario {

    /** Divisore per il valore orario della paga mensile. */
    enum class Divisore { FISSE_160, ORE_EFFETTIVE }

    /** Quali voci mostrare: l'utente attiva o disattiva ciascuna. */
    data class Scelte(
        val turnoSolo: Boolean = true,
        val turnoConStraordinario: Boolean = true,
        val valoreOra: Boolean = true,
        val impegno: Boolean = true,
        val notturne: Boolean = true,
        val tasse: Boolean = true
    )

    data class Dati(
        /** Paga lorda mensile base, senza straordinario. */
        val mensile: Double,
        /** Ore effettivamente lavorate nel mese (usate con Divisore.ORE_EFFETTIVE). */
        val oreEffettiveMese: Double,
        val divisore: Divisore,
        /** Reddito imponibile annuo di riferimento (Impostazioni), senza il mese confrontato. */
        val reddito: Double,
        val anno: Int,
        val parametri: Fisco.Parametri,
        /** Ore di straordinario nel mese. */
        val oreStra: Double,
        /** Maggiorazione media dello straordinario, es. 0.18, 0.35, 0.50. */
        val maggiorazione: Double,
        /** Ore notturne dentro lo straordinario. */
        val oreNotturneStra: Double,
        /** Indennità lorda per ora notturna (Competenze.tariffaNotturna). */
        val tariffaNotturna: Double
    )

    data class Risultato(
        val oraBase: Double,
        val lordoTurno: Double,
        val nettoTurno: Double,
        val lordoStra: Double,
        val nettoStra: Double,
        val nettoConStra: Double,
        val lordoOraNormale: Double,
        val nettoOraNormale: Double,
        val lordoOraStra: Double,
        val nettoOraStra: Double,
        /** Trattenute (INPS e imposte) sulle ore di straordinario. */
        val trattenuteStra: Double,
        val oreStra: Double,
        val oreNotturne: Double,
        val nettoNotturno: Double
    )

    fun calcola(d: Dati): Risultato {
        val oreMese = if (d.divisore == Divisore.FISSE_160) 160.0
            else if (d.oreEffettiveMese > 0) d.oreEffettiveMese else 160.0
        val oraBase = d.mensile / oreMese
        val p = d.parametri
        // Base del mese: l'imponibile del turno si somma al reddito di riferimento
        val baseConTurno = d.reddito + d.mensile * (1 - p.inps)

        val nettoTurno = Fisco.nettoExtra(d.reddito, d.mensile, d.anno, p).netto

        val lordoOraStra = oraBase * (1 + d.maggiorazione)
        val lordoStra = lordoOraStra * d.oreStra
        val extraStra = Fisco.nettoExtra(baseConTurno, lordoStra, d.anno, p)
        val nettoStra = extraStra.netto

        val nettoNotturno = if (d.oreNotturneStra > 0)
            Fisco.nettoExtra(baseConTurno, d.tariffaNotturna * d.oreNotturneStra, d.anno, p).netto else 0.0

        return Risultato(
            oraBase = oraBase,
            lordoTurno = d.mensile,
            nettoTurno = nettoTurno,
            lordoStra = lordoStra,
            nettoStra = nettoStra,
            nettoConStra = nettoTurno + nettoStra,
            lordoOraNormale = oraBase,
            nettoOraNormale = Fisco.nettoExtra(d.reddito, oraBase, d.anno, p).netto,
            lordoOraStra = lordoOraStra,
            nettoOraStra = Fisco.nettoExtra(baseConTurno, lordoOraStra, d.anno, p).netto,
            trattenuteStra = lordoStra - nettoStra,
            oreStra = d.oreStra,
            oreNotturne = d.oreNotturneStra,
            nettoNotturno = nettoNotturno
        )
    }

    /** Ore notturne (22–6) dentro l'eccedenza di straordinario di un servizio. */
    fun minutiNotturniStraordinario(t: Turno): Long {
        val durata = Duration.between(t.inizio, t.fine).toMinutes()
        val eccedenza = durata - Competenze.limiteStraordinario(t)
        if (eccedenza <= 0) return 0
        var n = 0L
        var istante = t.fine.minusMinutes(eccedenza)
        while (istante.isBefore(t.fine)) {
            val ora = istante.toLocalTime()
            if (ora >= LocalTime.of(22, 0) || ora < LocalTime.of(6, 0)) n++
            istante = istante.plusMinutes(1)
        }
        return n
    }

    /** Maggiorazione media ponderata sui minuti (a: +18%, b: +35%, c: +50%). 0 se non ci sono minuti. */
    fun maggiorazioneMedia(a: Long, b: Long, c: Long): Double {
        val tot = a + b + c
        return if (tot == 0L) 0.0 else (a * 0.18 + b * 0.35 + c * 0.50) / tot
    }

    /** Ore lavorate di un elenco di turni (durata inizio → fine), in ore decimali. */
    fun oreTurni(turni: List<Turno>, mese: YearMonth): Double =
        turni.filter { YearMonth.from(it.inizio) == mese }
            .sumOf { Duration.between(it.inizio, it.fine).toMinutes() } / 60.0
}
