package it.ferromicon

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * Date del cambio orario: la seconda domenica di giugno e di dicembre.
 * Il turno dell'impianto vale di norma fino al giorno prima.
 */
object CambioOrario {
    private fun secondaDomenica(anno: Int, mese: Int) =
        LocalDate.of(anno, mese, 1).with(TemporalAdjusters.dayOfWeekInMonth(2, DayOfWeek.SUNDAY))

    /** Primo cambio orario successivo alla data. */
    fun prossimo(da: LocalDate): LocalDate =
        listOf(secondaDomenica(da.year, 6), secondaDomenica(da.year, 12), secondaDomenica(da.year + 1, 6))
            .first { it.isAfter(da) }

    /** Ultimo giorno di validità del turno in corso. */
    fun ultimoGiornoDelTurno(da: LocalDate): LocalDate = prossimo(da).minusDays(1)
}

/**
 * Turno dell'impianto: la sequenza di giornate (nomi di servizi preferiti o sigle R, RS, FE, D, "-")
 * che si ripete fino al cambio turno. [posizione] è l'indice (da 0) della giornata che cade
 * in [dataRiferimento].
 */
data class TurnoImpianto(
    val nome: String = "",
    val impianto: String = "",
    val giornate: List<String> = emptyList(),
    val dataRiferimento: LocalDate,
    val posizione: Int = 0,
    val validoFino: LocalDate
) {
    private fun indice(data: LocalDate): Int =
        Math.floorMod(ChronoUnit.DAYS.between(dataRiferimento, data) + posizione, giornate.size.toLong()).toInt()

    /** Giornata del turno prevista per la data; null se il turno è finito o è vuoto. */
    fun elementoPer(data: LocalDate): String? =
        if (giornate.isEmpty() || data.isAfter(validoFino)) null else giornate[indice(data)]

    /** Giorni che mancano al cambio turno (negativo = turno già scaduto). */
    fun giorniAllaFine(oggi: LocalDate): Long = ChronoUnit.DAYS.between(oggi, validoFino)

    /** Stesso ciclo, prolungato fino al cambio orario successivo alla fine attuale. */
    fun prolungato(): TurnoImpianto = copy(validoFino = CambioOrario.ultimoGiornoDelTurno(validoFino.plusDays(1)))

    /** Giornate che non sono né sigle né preferiti salvati (il calendario le lascerebbe vuote). */
    fun sconosciute(preferiti: List<Preferito>): List<String> = giornate.filter { g ->
        g != "-" && g != "." && Generatore.sigla(g) == null && preferiti.none { it.nome.equals(g, ignoreCase = true) }
    }.distinct()

    /** Testo del ciclo da condividere con un collega o incollare di nuovo. */
    fun comeTesto(): String = giornate.joinToString(", ")

    /** Numero della giornata nel ciclo (da 1). */
    fun numeroGiornata(data: LocalDate): Int? =
        if (giornate.isEmpty() || data.isAfter(validoFino)) null else indice(data) + 1
}

/** Cosa cambia in agenda compilando il turno. */
data class Compilazione(
    val servizi: List<Servizio>,
    val assenze: List<Assenza>,
    val rimuoviServizi: List<Servizio>,
    val rimuoviAssenze: List<Assenza>,
    /** Giorni lasciati intatti perché contengono servizi o assenze inseriti o modificati a mano. */
    val saltati: Int,
    val sconosciuti: Set<String>,
    /** Gli elementi sostituiti finiscono nel cestino (sostituzione forzata di un solo giorno). */
    val nelCestino: Boolean = false
)

/** Una giornata del turno in una data. */
data class GiornoTurno(val data: LocalDate, val numero: Int, val nome: String)

/** Numeri del ciclo e problemi trovati percorrendolo due volte (compreso il rientro dall'ultima alla prima giornata). */
data class RiepilogoCiclo(
    val giornate: Int,
    val lavorative: Int,
    val riposi: Int,
    val notturne: Int,
    /** Minuti di presenza di un ciclo intero. */
    val minutiPresenza: Int,
    val problemi: List<String>
)

object PianoTurno {

    const val NOTA_TURNO = "turno"

    private fun giornoDi(s: Servizio) = s.turno.inizio.toLocalDate()

    /** Il servizio è ancora quello di default della sua giornata di turno (non modificato a mano)? */
    private fun nonModificato(s: Servizio, preferiti: List<Preferito>): Boolean {
        if (s.origineTurno.isEmpty()) return false
        val p = preferiti.firstOrNull { it.nome.equals(s.origineTurno, ignoreCase = true) } ?: return false
        return p.applica(giornoDi(s), 0, daTurno = true) == s.copy(id = 0)
    }

    /**
     * Compila l'agenda con le giornate del turno da [da] fino alla fine del turno (o a [fino]).
     * I giorni con servizi o assenze inseriti o modificati a mano restano intatti, salvo [forza].
     * Le giornate già compilate dal turno e non modificate vengono ricalcolate.
     */
    fun compila(
        turno: TurnoImpianto, da: LocalDate, preferiti: List<Preferito>, dati: Dati,
        idIniziale: Long, forza: Boolean = false, fino: LocalDate = turno.validoFino,
        /** Giornata da usare al posto di quella prevista nel primo giorno (cambio di giornata). */
        sostituto: String? = null
    ): Compilazione {
        val nuoviS = mutableListOf<Servizio>()
        val nuoveA = mutableListOf<Assenza>()
        val remS = mutableListOf<Servizio>()
        val remA = mutableListOf<Assenza>()
        val sconosciuti = linkedSetOf<String>()
        var saltati = 0
        var id = idIniziale
        var giorno = da
        val ultimo = if (fino.isBefore(turno.validoFino)) fino else turno.validoFino
        while (!giorno.isAfter(ultimo)) {
            val g = giorno
            giorno = giorno.plusDays(1)
            val elemento = (if (g == da) sostituto else null) ?: turno.elementoPer(g) ?: break
            val sS = dati.servizi.filter { giornoDi(it) == g }
            val sA = dati.assenze.filter { it.data == g }
            val sostituibile = forza ||
                (sS.all { nonModificato(it, preferiti) } && sA.all { it.note == NOTA_TURNO })
            if (!sostituibile) { saltati++; continue }

            val vuoto = elemento == "-" || elemento == "."
            val tipo = Generatore.sigla(elemento)
            val pref = preferiti.firstOrNull { it.nome.equals(elemento, ignoreCase = true) }
            val nuovoS = if (!vuoto && pref != null) pref.applica(g, id, daTurno = true) else null
            val nuovaA = if (!vuoto && pref == null && tipo != null) Assenza(id, g, tipo, NOTA_TURNO) else null
            if (!vuoto && nuovoS == null && nuovaA == null) { sconosciuti += elemento; continue }

            // Già identico a quanto previsto: nessuna modifica (gli id restano stabili).
            val identico = (nuovoS != null && sA.isEmpty() && sS.size == 1 && sS[0].copy(id = 0) == nuovoS.copy(id = 0)) ||
                (nuovaA != null && sS.isEmpty() && sA.size == 1 && sA[0].tipo == nuovaA.tipo && sA[0].note == NOTA_TURNO) ||
                (vuoto && sS.isEmpty() && sA.isEmpty())
            if (identico) continue
            id++
            remS += sS; remA += sA
            nuovoS?.let { nuoviS += it }
            nuovaA?.let { nuoveA += it }
        }
        return Compilazione(nuoviS, nuoveA, remS, remA, saltati, sconosciuti, nelCestino = forza)
    }

    /** Carica la giornata di turno di default per un solo giorno, sostituendo ciò che c'è. */
    fun giornata(
        turno: TurnoImpianto, giorno: LocalDate, preferiti: List<Preferito>, dati: Dati, id: Long,
        sostituto: String? = null
    ) = compila(turno, giorno, preferiti, dati, id, forza = true, fino = giorno, sostituto = sostituto)

    /** Le giornate dei prossimi [quanti] giorni a partire da [da] (si ferma alla fine del turno). */
    fun prossimiGiorni(turno: TurnoImpianto, da: LocalDate, quanti: Int): List<GiornoTurno> =
        (0 until quanti).mapNotNull { i ->
            val d = da.plusDays(i.toLong())
            val e = turno.elementoPer(d) ?: return@mapNotNull null
            GiornoTurno(d, turno.numeroGiornata(d)!!, e)
        }

    /** Toglie dall'agenda, da [da] in poi, ciò che il turno ha generato e che non è stato modificato. */
    fun rimuoviGenerati(turno: TurnoImpianto?, da: LocalDate, preferiti: List<Preferito>, dati: Dati): Compilazione =
        Compilazione(
            emptyList(), emptyList(),
            dati.servizi.filter { !giornoDi(it).isBefore(da) && nonModificato(it, preferiti) },
            dati.assenze.filter { !it.data.isBefore(da) && it.note == NOTA_TURNO },
            0, emptySet()
        )

    /**
     * Numeri del ciclo e controllo dei riposi: il ciclo viene steso due volte di seguito e si
     * verificano i servizi del secondo giro, così si controlla anche il rientro dall'ultima giornata alla prima.
     */
    fun riepilogoCiclo(turno: TurnoImpianto, preferiti: List<Preferito>, limiti: LimitiOrario? = null): RiepilogoCiclo {
        val n = turno.giornate.size
        if (n == 0) return RiepilogoCiclo(0, 0, 0, 0, 0, emptyList())
        val inizio = LocalDate.of(2026, 1, 5)
        val servizi = mutableListOf<Servizio>()
        val assenze = mutableListOf<Assenza>()
        val origine = mutableMapOf<Long, Int>()
        var id = 1L
        for (k in 0 until 2 * n) {
            val e = turno.giornate[k % n]
            val g = inizio.plusDays(k.toLong())
            val p = preferiti.firstOrNull { it.nome.equals(e, ignoreCase = true) }
            val tipo = Generatore.sigla(e)
            if (p != null) { servizi += p.applica(g, id); origine[id] = k % n; id++ }
            else if (tipo != null) assenze += Assenza(id++, g, tipo)
        }
        val ordinati = servizi.sortedBy { it.turno.inizio }
        val problemi = linkedSetOf<String>()
        ordinati.forEachIndexed { i, sv ->
            if (sv.turno.inizio.toLocalDate() < inizio.plusDays(n.toLong())) return@forEachIndexed
            Analisi.verificaServizio(ordinati, i, assenze, limiti)
                .filter { it.gravita == Gravita.VIOLAZIONE }
                .forEach { problemi += "Giornata ${(origine[sv.id] ?: 0) + 1} (${turno.giornate[origine[sv.id] ?: 0]}): ${it.messaggio}" }
        }
        val primoGiro = ordinati.filter { it.turno.inizio.toLocalDate() < inizio.plusDays(n.toLong()) }
        return RiepilogoCiclo(
            giornate = n,
            lavorative = primoGiro.size,
            riposi = assenze.count { it.tipo.riposo && it.data < inizio.plusDays(n.toLong()) },
            notturne = primoGiro.count {
                it.turno.fine.toLocalDate() != it.turno.inizio.toLocalDate() || it.turno.inizio.hour >= 22 || it.turno.inizio.hour < 5
            },
            minutiPresenza = primoGiro.sumOf { java.time.Duration.between(it.turno.inizio, it.turno.fine).toMinutes().toInt() },
            problemi = problemi.toList()
        )
    }

    /** Dati con la compilazione applicata. */
    fun applica(dati: Dati, c: Compilazione, adesso: java.time.LocalDateTime): Dati {
        val cestino = if (c.nelCestino)
            dati.cestino + c.rimuoviServizi.map { VoceCestino(adesso, servizio = it) } +
                c.rimuoviAssenze.map { VoceCestino(adesso, assenza = it) }
        else dati.cestino
        return dati.copy(
            servizi = (dati.servizi - c.rimuoviServizi.toSet() + c.servizi).sortedBy { it.turno.inizio },
            assenze = (dati.assenze - c.rimuoviAssenze.toSet() + c.assenze).sortedBy { it.data },
            cestino = cestino
        )
    }
}

/** Periodi di ferie che, uniti ai riposi e ai festivi, danno più giorni consecutivi a casa. */
data class ProspettoFerie(val da: LocalDate, val a: LocalDate, val giorniFerie: Int) {
    val giorniTotali: Int get() = (ChronoUnit.DAYS.between(da, a) + 1).toInt()
}

object PianoFerie {

    /**
     * I [quanti] periodi migliori nell'intervallo, con esattamente [giorniFerie] giorni di ferie.
     * [libero] dice se un giorno è già di riposo o festivo.
     */
    fun migliori(
        da: LocalDate, a: LocalDate, giorniFerie: Int, quanti: Int = 5, libero: (LocalDate) -> Boolean
    ): List<ProspettoFerie> {
        if (giorniFerie <= 0) return emptyList()
        val trovati = mutableListOf<ProspettoFerie>()
        var s = da
        while (!s.isAfter(a)) {
            // Si parte dal primo giorno di un blocco libero (o dal primo di lavoro).
            if (!libero(s) || s == da || !libero(s.minusDays(1))) {
                var costo = 0
                var d = s
                var fine: LocalDate? = null
                while (!d.isAfter(a)) {
                    if (!libero(d)) {
                        if (costo == giorniFerie) break
                        costo++
                    }
                    fine = d
                    d = d.plusDays(1)
                }
                if (fine != null && costo == giorniFerie) {
                    trovati += ProspettoFerie(s, fine, costo)
                }
            }
            s = s.plusDays(1)
        }
        val scelti = mutableListOf<ProspettoFerie>()
        for (p in trovati.sortedWith(compareByDescending<ProspettoFerie> { it.giorniTotali }.thenBy { it.da })) {
            if (scelti.none { !(p.a.isBefore(it.da) || p.da.isAfter(it.a)) }) scelti += p
            if (scelti.size == quanti) break
        }
        return scelti.sortedBy { it.da }
    }
}
