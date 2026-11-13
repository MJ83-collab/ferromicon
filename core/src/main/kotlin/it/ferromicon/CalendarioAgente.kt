package it.ferromicon

import java.time.LocalDate

/** Periodo di ferie assegnato (estivo o invernale). */
data class PeriodoFerie(val nome: String, val dal: LocalDate, val al: LocalDate)

object PeriodiFerie {

    /**
     * Periodi estivi indicativi, ripetuti ogni anno.
     * Le date vanno verificate ogni anno: si possono modificare in app.
     */
    fun estivi(anno: Int): List<PeriodoFerie> = listOf(
        Triple("A", "09/06", "15/06"), Triple("B", "29/06", "05/07"), Triple("C", "19/07", "25/07"),
        Triple("D", "07/08", "13/08"), Triple("E", "29/07", "04/08"), Triple("F", "25/08", "31/08")
    ).map { (n, a, b) ->
        fun d(t: String, anno: Int) = LocalDate.of(anno, t.substring(3).toInt(), t.substring(0, 2).toInt())
        val dal = d(a, anno)
        var al = d(b, anno)
        if (al.isBefore(dal)) al = d(b, anno + 1)
        PeriodoFerie("Estivo $n", dal, al)
    }

    /** Anno a cui si riferisce la lettera scelta dall'utente (avviso 37/2026: sequenza per il 2026). */
    const val ANNO_BASE = 2026

    /** Sequenza dei periodi estivi (avviso 37/2026); ogni anno la lettera scorre di un posto. */
    val SEQUENZA_ESTIVA = listOf("D", "A", "C", "E", "F", "B")

    /** Lettera estiva di un anno, partendo da quella di [annoBase]. Ipotesi: la sequenza prosegue ogni anno. */
    fun letteraPerAnno(letteraBase: String, annoBase: Int, anno: Int): String {
        val i = SEQUENZA_ESTIVA.indexOf(letteraBase)
        require(i >= 0) { "Lettera sconosciuta: $letteraBase" }
        val n = ((i + (anno - annoBase)) % 6 + 6) % 6
        return SEQUENZA_ESTIVA[n]
    }

    /** Periodo estivo di un anno per la lettera base scelta dall'utente. */
    fun estivoPerAnno(letteraBase: String, anno: Int): PeriodoFerie? {
        val l = letteraPerAnno(letteraBase, ANNO_BASE, anno)
        return estivi(anno).firstOrNull { it.nome == "Estivo $l" }
    }

    /** Pasqua cattolica (algoritmo di Meeus/Jones/Butcher). */
    fun pasqua(anno: Int): LocalDate {
        val a = anno % 19; val b = anno / 100; val c = anno % 100
        val d = b / 4; val e = b % 4; val f = (b + 8) / 25; val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4; val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val mese = (h + l - 7 * m + 114) / 31
        val giorno = ((h + l - 7 * m + 114) % 31) + 1
        return LocalDate.of(anno, mese, giorno)
    }

    private fun èRiposo(c: String?) = c == "GRS" || c == "R"

    /**
     * Ferie "da riposo a riposo": iniziano dal primo giorno di riposo compreso nel periodo e arrivano
     * alla fine del riposo successivo, se il periodo contiene un solo riposo, altrimenti all'ultimo riposo
     * del periodo. I giorni di riposo restano riposo; gli altri diventano ferie.
     * Restituisce le date dei giorni di ferie (senza i riposi).
     */
    fun giorniDiFerie(codici: Map<LocalDate, String>, periodo: PeriodoFerie): Set<LocalDate> {
        var d = periodo.dal
        var inizio: LocalDate? = null
        var ultimoNelPeriodo: LocalDate? = null
        var blocchi = 0
        var inBlocco = false
        while (!d.isAfter(periodo.al)) {
            if (èRiposo(codici[d])) {
                if (inizio == null) inizio = d
                if (!inBlocco) blocchi++
                inBlocco = true
                ultimoNelPeriodo = d
            } else inBlocco = false
            d = d.plusDays(1)
        }
        val primoRiposo: LocalDate = inizio ?: return emptySet()
        var fine: LocalDate = ultimoNelPeriodo ?: return emptySet()
        if (blocchi == 1) {
            // Un solo riposo nel periodo: si prosegue fino alla fine del riposo successivo.
            var x = fine.plusDays(1)
            var visto = false
            val limite = periodo.al.plusDays(10)
            while (!x.isAfter(limite)) {
                val c = codici[x] ?: break
                if (èRiposo(c)) { visto = true; fine = x } else if (visto) break
                x = x.plusDays(1)
            }
        }
        val giorni = LinkedHashSet<LocalDate>()
        var g: LocalDate = primoRiposo
        while (!g.isAfter(fine)) {
            if (!èRiposo(codici[g]) && codici[g] != null) giorni += g
            g = g.plusDays(1)
        }
        return giorni
    }
}

/** Risultato della generazione del calendario di un agente. */
data class CalendarioGenerato(
    val servizi: List<Servizio>,
    val assenze: List<Assenza>,
    /** Giornate di servizio per cui manca il dettaglio (orari) nel PDF dei turni. */
    val senzaDettaglio: List<String>,
    val giorniFerie: Int,
    val estesoOltreLaSettimana: Boolean,
    /** Ultima data per cui il calendario è noto (dalla settimana letta o dal ciclo). */
    val finoA: LocalDate
)

object CalendarioAgente {

    /** Genera servizi e assenze dell'agente da [da] a [fino], con le giornate di turno e i periodi di ferie. */
    fun genera(
        calendario: CalendarioTurnoAgenti,
        agente: RigaAgente,
        giornate: List<GiornataDefinita>,
        da: LocalDate,
        fino: LocalDate,
        ferie: List<PeriodoFerie>,
        idIniziale: Long,
        rotativo: Rotativo? = null
    ): CalendarioGenerato {
        val codici = LinkedHashMap<LocalDate, String>()
        var d = if (da.isBefore(calendario.dal)) calendario.dal else da
        var ultimo = d.minusDays(1)
        // Oltre la settimana letta: prima il ciclo del turno, poi i riposi del rotativo (se la posizione è nota).
        val posizione = rotativo?.let { RotativoRiposi.posizioneDi(agente, calendario, it) }
        while (!d.isAfter(fino)) {
            val c = calendario.codice(agente, d)
                ?: (if (rotativo != null && posizione != null) RotativoRiposi.codice(rotativo, posizione, d) else null)
                ?: break
            codici[d] = c; ultimo = d
            d = d.plusDays(1)
        }
        val estesa = ultimo.isAfter(calendario.dal.plusDays(agente.codici.size - 1L))

        val giorniFerie = ferie.flatMap { PeriodiFerie.giorniDiFerie(codici, it).toList() }.toSet()
        val profilo = if (calendario.profilo.equals("Condotta", true)) Profilo.MACCHINISTA else Profilo.CAPOTRENO

        val servizi = ArrayList<Servizio>()
        val assenze = ArrayList<Assenza>()
        val senza = ArrayList<String>()
        var id = idIniziale
        for ((data, codice) in codici) {
            when {
                data in giorniFerie -> {
                    val p = ferie.first { data in giorniFerieDi(codici, it) }
                    assenze += Assenza(id++, data, TipoAssenza.FERIE, p.nome)
                }
                codice == "GRS" -> assenze += Assenza(id++, data, TipoAssenza.RIPOSO_SETTIMANALE, "turno")
                codice == "R" -> assenze += Assenza(id++, data, TipoAssenza.RIPOSO, "turno")
                codice == "D" -> assenze += Assenza(id++, data, TipoAssenza.DISPONIBILITA, "turno")
                codice.startsWith("DF") -> assenze += Assenza(id++, data, TipoAssenza.DISPONIBILITA, "turno $codice")
                codice == "F" || codice == "FE" -> assenze += Assenza(id++, data, TipoAssenza.FERIE, "turno")
                codice == "L" -> Unit // giornata di lavoro dal rotativo: l'orario non è noto, nessun servizio inventato
                else -> {
                    val g = TurniPdf.trova(giornate, codice, TipoGiorno.di(data))
                    if (g == null) {
                        if (Regex("""^[A-Z]{3,5}F?\d+$""").matches(codice)) senza += codice
                        else assenze += Assenza(id++, data, TipoAssenza.ALTRO, codice)
                    } else servizi += servizioDa(g, codice, data, profilo, id++)
                }
            }
        }
        return CalendarioGenerato(servizi, assenze, senza.distinct(), giorniFerie.size, estesa, ultimo)
    }

    private fun giorniFerieDi(codici: Map<LocalDate, String>, p: PeriodoFerie) = PeriodiFerie.giorniDiFerie(codici, p)

    /** Servizio della giornata di turno nel giorno indicato. */
    fun servizioDa(g: GiornataDefinita, codice: String, data: LocalDate, profilo: Profilo, id: Long): Servizio {
        val inizio = data.atTime(g.inizio)
        var fine = data.atTime(g.fine)
        if (!fine.isAfter(inizio)) fine = fine.plusDays(1)
        val treni = g.treni.joinToString(", ") { (if (it.vettura) "(" else "") + it.numero + if (it.destinazione.isNotEmpty()) " " + it.destinazione else "" }
        return Servizio(
            id = id,
            turno = Turno(inizio, fine, profilo, g.condottaMin, 0, false),
            note = buildString {
                append("$codice ${g.tipo.sigla}")
                if (g.km > 0) append(" · ${g.km} km")
                if (g.refezione) append(" · refezione")
                if (treni.isNotEmpty()) append("\nTreni: $treni")
            },
            origineTurno = codice
        )
    }
}
