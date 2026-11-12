package it.ferromicon

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/*
 * Modello dei dati, senza dipendenze Android (compilabile e testabile sulla JVM).
 */

/** Un servizio salvato in agenda. */
data class Servizio(
    val id: Long,
    /** Per il capo treno, [Turno.minutiCondotta] contiene i minuti di scorta. */
    val turno: Turno,
    /** Il riposo che segue questo servizio è fuori residenza (RFR). */
    val rfrDopo: Boolean = false,
    val note: String = "",
    /** Regolarizzazioni effettuate (Altarail art. 54.2.2.a). */
    val regolarizzazioni: Int = 0,
    /** Somme riscosse come sovrattassa, in euro (Altarail art. 54.2.2.b). */
    val sovrattasse: Double = 0.0,
    /** Prestazione di riserva o formazione (indennità a prestazione). */
    val riserva: Boolean = false,
    /** Minuti di assenza dalla residenza (dalla partenza del treno al rientro), per l'art. 77.2. */
    val minutiFuoriResidenza: Int = 0,
    /** Treni e attività del servizio, in ordine. Se presenti, la condotta si calcola da qui. */
    val righe: List<RigaTreno> = emptyList(),
    /** Materiale rotabile (es. TSR, Coradia, Caravaggio, Vivalto). */
    val materiale: String = "",
    /** Collega di equipaggio (capo treno o macchinista). */
    val secondoAgente: String = "",
    /** Giornata del turno dell'impianto da cui deriva questo servizio (vuoto = inserito a mano). */
    val origineTurno: String = "",
    /** Straordinario: lavoro in più rispetto al turno previsto (codice S nel calendario). */
    val straordinario: Boolean = false,
) {
    /** Ritardo all'arrivo dell'ultima riga con orario reale, in minuti (negativo = anticipo). */
    val ritardoFinale: Long
        get() = righe.lastOrNull { it.arrivoReale != null }?.let {
            java.time.Duration.between(it.arrivo, it.arrivoReale).toMinutes()
        } ?: 0

    /**
     * Turno con gli orari reali: la fine si sposta del ritardo dell'ultimo treno
     * (se positivo) e condotta o scorta sono quelle effettive delle righe.
     */
    fun turnoEffettivo(): Turno {
        val base = conRigheApplicate().turno
        return if (ritardoFinale > 0) base.copy(fine = base.fine.plusMinutes(ritardoFinale)) else base
    }

    /** Servizio con condotta (o scorta) e condotta continuativa ricalcolate dalle righe programmate. */
    fun conRigheApplicate(): Servizio {
        if (righe.isEmpty()) return this
        val tipo = if (turno.profilo == Profilo.MACCHINISTA) TipoRiga.CONDOTTA else TipoRiga.SCORTA
        return copy(turno = turno.copy(
            minutiCondotta = Righe.minuti(righe, tipo, reali = true),
            maxCondottaConsecutiva = if (tipo == TipoRiga.CONDOTTA) Righe.continuativaMassima(righe, reali = true) else 0
        ))
    }
}

/** Tipo di attività di una riga del servizio. */
enum class TipoRiga(val etichetta: String) {
    CONDOTTA("Condotta"),
    SCORTA("Scorta"),
    VETTURA("In vettura"),
    ALTRO("Altra attività")
}

/** Una riga del servizio: un treno o un'attività, con orari programmati ed eventualmente reali. */
data class RigaTreno(
    val numero: String = "",
    val da: String = "",
    val a: String = "",
    val partenza: LocalDateTime,
    val arrivo: LocalDateTime,
    val tipo: TipoRiga = TipoRiga.CONDOTTA,
    val partenzaReale: LocalDateTime? = null,
    val arrivoReale: LocalDateTime? = null
) {
    val inizioEffettivo: LocalDateTime get() = partenzaReale ?: partenza
    val fineEffettiva: LocalDateTime get() = arrivoReale ?: arrivo
}

/** Calcoli sulle righe di un servizio. */
object Righe {

    /** Soste inferiori a 15 minuti tra due treni contano come condotta continuativa (Altarail art. 17.7.1). */
    const val SOSTA_MINIMA = 15L

    private fun inizio(r: RigaTreno, reali: Boolean) = if (reali) r.inizioEffettivo else r.partenza
    private fun fine(r: RigaTreno, reali: Boolean) = if (reali) r.fineEffettiva else r.arrivo

    /** Minuti totali delle righe del tipo indicato. */
    fun minuti(righe: List<RigaTreno>, tipo: TipoRiga, reali: Boolean = false): Int =
        righe.filter { it.tipo == tipo }
            .sumOf { maxOf(0L, java.time.Duration.between(inizio(it, reali), fine(it, reali)).toMinutes()) }
            .toInt()

    /**
     * Tratto di condotta continuativa più lungo: righe di condotta consecutive separate da soste
     * sotto i 15 minuti formano un unico tratto, soste comprese.
     */
    fun continuativaMassima(righe: List<RigaTreno>, reali: Boolean = false): Int {
        val condotta = righe.filter { it.tipo == TipoRiga.CONDOTTA }.sortedBy { inizio(it, reali) }
        var massimo = 0L
        var inizioTratto: LocalDateTime? = null
        var fineTratto: LocalDateTime? = null
        for (r in condotta) {
            val a = inizio(r, reali)
            val b = fine(r, reali)
            if (fineTratto != null && java.time.Duration.between(fineTratto, a).toMinutes() < SOSTA_MINIMA) {
                if (b.isAfter(fineTratto)) fineTratto = b
            } else {
                inizioTratto = a; fineTratto = b
            }
            massimo = maxOf(massimo, java.time.Duration.between(inizioTratto, fineTratto).toMinutes())
        }
        return massimo.toInt()
    }

    /**
     * Data e ora di un orario scritto nel modulo: si ancora al giorno del servizio e passa al
     * giorno dopo se cadrebbe più di 2 ore prima dell'inizio del servizio (servizi a cavallo della mezzanotte).
     */
    fun ancora(inizioServizio: LocalDateTime, ora: LocalTime): LocalDateTime {
        val candidato = inizioServizio.toLocalDate().atTime(ora)
        return if (candidato.isBefore(inizioServizio.minusHours(2))) candidato.plusDays(1) else candidato
    }

    /** Righe che si sovrappongono nel tempo (dato probabilmente sbagliato). */
    fun sovrapposte(righe: List<RigaTreno>): List<Pair<RigaTreno, RigaTreno>> {
        val ordinate = righe.sortedBy { it.partenza }
        return ordinate.zipWithNext().filter { (x, y) -> y.partenza.isBefore(x.arrivo) }
    }
}

/**
 * Tipi di giornata senza servizio.
 * [riposo] = giornata di riposo (non è un'assenza che interrompe i controlli sui riposi).
 */
enum class TipoAssenza(val etichetta: String, val simbolo: String, val riposo: Boolean) {
    RIPOSO_SETTIMANALE("Riposo settimanale", "🛌", true),
    RIPOSO("Giornata di riposo", "😴", true),
    RECUPERO_FESTIVO("Recupero festività lavorata", "🔁", true),
    FERIE("Ferie", "🏖", false),
    PERMESSO_EX_FESTIVITA("Permesso ex festività", "🎫", false),
    PERMESSO("Altro permesso retribuito", "📝", false),
    /** Giornata di disponibilità non utilizzata: vale 1/5 dell'orario settimanale. */
    DISPONIBILITA("Disponibilità", "📟", false),
    MALATTIA("Malattia", "🤒", false),
    INFORTUNIO("Infortunio", "🩹", false),
    PARENTALE("Giorno parentale", "👶", false),
    /** Chiusura decisa dall'azienda: non consuma ferie e non è un riposo da spostare. */
    CHIUSURA_AZIENDALE("Chiusura aziendale", "🏢", false),
    ALTRO("Altra assenza", "📌", false);

    /** Codice breve per il calendario e la legenda. */
    val codice: String get() = when (this) {
        RIPOSO_SETTIMANALE -> "RS"; RIPOSO -> "R"; RECUPERO_FESTIVO -> "RF"; FERIE -> "F"
        PERMESSO_EX_FESTIVITA -> "PF"; PERMESSO -> "P"; DISPONIBILITA -> "D"; MALATTIA -> "M"
        INFORTUNIO -> "I"; PARENTALE -> "PG"; CHIUSURA_AZIENDALE -> "CA"; ALTRO -> "A"
    }
}

/** Una giornata senza servizio. */
data class Assenza(val id: Long, val data: LocalDate, val tipo: TipoAssenza, val note: String = "")

/** Livelli e posizioni retributive del CCNL 2025 (art. 68.3 e 69). */
enum class Livello { Q1, Q2, A, B1, B2, B3, C1, C2, D1, D2, D3, E1, E2, E3, F1, F2 }

/** Impostazioni personali usate per ferie e competenze. */
data class Impostazioni(
    val dataAssunzione: LocalDate? = null,
    val livello: Livello = Livello.B3,
    /** Assegno ad personam pensionabile mensile, in euro (art. 68.4). */
    val adPersonam: Double = 0.0,
    /** Orario settimanale su 6 giorni invece che su 5 (art. 30.1). */
    val orarioSuSeiGiorni: Boolean = false,
    /** Ferie dell'anno precedente ancora da godere (entro il 30/9, art. 30.4). */
    val ferieResidueAnnoPrecedente: Int = 0,
    /** Permessi retribuiti aggiuntivi riconosciuti (Altarail artt. 20.1 e 20.2). */
    val permessiAggiuntivi: Int = 0,
    /** Minuti di preavviso del promemoria prima del servizio; 0 = disattivato. */
    val promemoriaMinuti: Int = 60,
    /** Limiti di prestazione personalizzati; null = limiti del contratto Altarail. */
    val limitiOrario: LimitiOrario? = null,
    /** Scelte manuali sul recupero delle festività: vero = recuperabile, falso = non recuperabile. */
    val festiviForzati: Map<LocalDate, Boolean> = emptyMap(),
    /** Reddito imponibile annuo (dal CU o dal cedolino di dicembre), per stimare il netto; 0 = non impostato. */
    val redditoAnnuo: Double = 0.0,
    /** Contributi INPS a carico del lavoratore, in %. */
    val inpsPct: Double = 9.19,
    /** Addizionale comunale, in %. */
    val addComunalePct: Double = 0.8,
    /** Profilo scelto all'avvio (macchinista o capotreno); null = non ancora scelto. */
    val profilo: Profilo? = null,
    /** CID (matricola) dell'utente: serve a trovare il suo calendario nei PDF dell'impianto. */
    val cid: String = "",
    /** Nome dell'utente: dà il nome al suo profilo (agenda, calendari, esportazioni). */
    val nome: String = "",
    /** Giorni di congedo parentale spettanti nell'anno (0 = non impostato). */
    val parentaliSpettanti: Int = 0,
    /** Giorni di permesso (ex festività e altri permessi) spettanti nell'anno; 0 = non impostato. */
    val permessiSpettanti: Int = 0,
) {
    /** Profilo da usare per i nuovi servizi. */
    val profiloAttivo: Profilo get() = profilo ?: Profilo.MACCHINISTA

    fun parametriFisco() = Fisco.Parametri(inps = inpsPct / 100, addComunalePct = addComunalePct)
}

/**
 * Servizio preferito: un modello con orari e dati ricorrenti, da inserire con un tocco
 * in qualsiasi giorno.
 */
data class Preferito(
    val id: Long,
    val nome: String,
    val oraInizio: LocalTime,
    val oraFine: LocalTime,
    val profilo: Profilo,
    val minutiCondotta: Int = 0,
    val maxCondottaConsecutiva: Int = 0,
    val turnoAZeta: Boolean = false,
    val rfrDopo: Boolean = false,
    val riserva: Boolean = false,
    val minutiFuoriResidenza: Int = 0,
    val note: String = "",
    val materiale: String = "",
    val secondoAgente: String = "",
    /** Righe treno del preferito, con le date del giorno di riferimento. */
    val righe: List<RigaTreno> = emptyList(),
    val giornoRiferimento: LocalDate? = null
) {
    /** Crea il servizio nel giorno indicato; se la fine è prima dell'inizio, termina il giorno dopo. */
    fun applica(giorno: LocalDate, id: Long, daTurno: Boolean = false): Servizio {
        val inizio = giorno.atTime(oraInizio)
        var fine = giorno.atTime(oraFine)
        if (!fine.isAfter(inizio)) fine = fine.plusDays(1)
        val spostamento = giornoRiferimento?.let { java.time.temporal.ChronoUnit.DAYS.between(it, giorno) } ?: 0L
        return Servizio(
            id = id,
            turno = Turno(inizio, fine, profilo, minutiCondotta, maxCondottaConsecutiva, turnoAZeta),
            rfrDopo = rfrDopo, note = note, riserva = riserva, minutiFuoriResidenza = minutiFuoriResidenza,
            materiale = materiale, secondoAgente = secondoAgente,
            origineTurno = if (daTurno) nome else "",
            // Le righe si copiano senza orari reali: quelli valgono solo per il giorno in cui sono stati registrati.
            righe = righe.map {
                it.copy(partenza = it.partenza.plusDays(spostamento), arrivo = it.arrivo.plusDays(spostamento),
                    partenzaReale = null, arrivoReale = null)
            }
        )
    }

    companion object {
        fun da(s: Servizio, nome: String, id: Long) = Preferito(
            id = id, nome = nome,
            oraInizio = s.turno.inizio.toLocalTime(), oraFine = s.turno.fine.toLocalTime(),
            profilo = s.turno.profilo, minutiCondotta = s.turno.minutiCondotta,
            maxCondottaConsecutiva = s.turno.maxCondottaConsecutiva, turnoAZeta = s.turno.turnoAZeta,
            rfrDopo = s.rfrDopo, riserva = s.riserva, minutiFuoriResidenza = s.minutiFuoriResidenza, note = s.note,
            materiale = s.materiale, secondoAgente = s.secondoAgente, righe = s.righe,
            giornoRiferimento = s.turno.inizio.toLocalDate()
        )
    }
}

/** Copia del servizio spostata di [giorni], con un nuovo identificativo. */
fun Servizio.spostato(giorni: Long, nuovoId: Long) = copy(
    id = nuovoId,
    turno = turno.copy(inizio = turno.inizio.plusDays(giorni), fine = turno.fine.plusDays(giorni)),
    righe = righe.map {
        it.copy(partenza = it.partenza.plusDays(giorni), arrivo = it.arrivo.plusDays(giorni),
            partenzaReale = null, arrivoReale = null)
    }
)

/** Voce della rubrica stazioni (telefoni del DM, del deposito, note). */
data class Stazione(
    val id: Long,
    val nome: String,
    val sigla: String = "",
    val telefono: String = "",
    val note: String = ""
)

/** Elemento eliminato, recuperabile dal cestino per 30 giorni. */
data class VoceCestino(
    val eliminatoIl: LocalDateTime,
    val servizio: Servizio? = null,
    val assenza: Assenza? = null
) {
    val descrizione: String
        get() = servizio?.let {
            val f = java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm")
            "Servizio ${f.format(it.turno.inizio)}–${java.time.format.DateTimeFormatter.ofPattern("HH:mm").format(it.turno.fine)}"
        } ?: assenza?.let { "${it.tipo.etichetta} ${java.time.format.DateTimeFormatter.ofPattern("dd/MM").format(it.data)}" }
        ?: "Elemento"
}

data class Dati(
    val servizi: List<Servizio>,
    val assenze: List<Assenza>,
    val impostazioni: Impostazioni = Impostazioni(),
    val preferiti: List<Preferito> = emptyList(),
    val stazioni: List<Stazione> = emptyList(),
    val cestino: List<VoceCestino> = emptyList(),
    val perimetri: List<Perimetro> = emptyList(),
    /** Cedolini e CU importati dai PDF (restano solo sul telefono). */
    val cedolini: List<Cedolino> = emptyList(),
    val certificazioniUniche: List<CertificazioneUnica> = emptyList(),
    /** Turno dell'impianto caricato dal personale viaggiante (null = nessuno). */
    val turno: TurnoImpianto? = null
) {
    /** Sposta un servizio nel cestino. */
    fun eliminaServizio(id: Long, adesso: LocalDateTime): Dati {
        val s = servizi.firstOrNull { it.id == id } ?: return this
        return copy(servizi = servizi - s, cestino = cestino + VoceCestino(adesso, servizio = s))
    }

    /** Sposta un'assenza nel cestino. */
    fun eliminaAssenza(id: Long, adesso: LocalDateTime): Dati {
        val a = assenze.firstOrNull { it.id == id } ?: return this
        return copy(assenze = assenze - a, cestino = cestino + VoceCestino(adesso, assenza = a))
    }

    /** Rimette in agenda un elemento del cestino (un'assenza sostituisce quella dello stesso giorno). */
    fun ripristina(voce: VoceCestino): Dati = copy(
        servizi = voce.servizio?.let { servizi + it } ?: servizi,
        assenze = voce.assenza?.let { a -> assenze.filterNot { it.data == a.data } + a } ?: assenze,
        cestino = cestino - voce
    )

    /** Toglie dal cestino gli elementi eliminati da più di [giorni] giorni. */
    fun pulisciCestino(adesso: LocalDateTime, giorni: Long = 30): Dati =
        copy(cestino = cestino.filter { it.eliminatoIl.isAfter(adesso.minusDays(giorni)) })
}

/**
 * Servizi nuovi senza i doppioni: scarta quelli con lo stesso inizio e la stessa fine
 * di un servizio già presente o di uno precedente nella lista.
 */
fun deduplica(esistenti: List<Servizio>, nuovi: List<Servizio>): List<Servizio> {
    val chiavi = esistenti.map { it.turno.inizio to it.turno.fine }.toMutableSet()
    return nuovi.filter { chiavi.add(it.turno.inizio to it.turno.fine) }
}

/** Esportazione CSV (separatore ";" per Excel in italiano), servizi e assenze. */
object Csv {
    fun genera(dati: Dati): String {
        val righe = mutableListOf(
            "data;tipo;inizio;fine;profilo;durata_min;condotta_o_scorta_min;notturno;rfr_dopo;festivo;regolarizzazioni;sovrattasse;riserva;note"
        )
        val elementi: List<Pair<LocalDate, Any>> =
            dati.servizi.map { it.turno.inizio.toLocalDate() to it } + dati.assenze.map { it.data to it }
        elementi.sortedBy { it.first }.forEach { (data, e) ->
            val festivo = if (Festivi.èFestivo(data)) "si" else "no"
            when (e) {
                is Servizio -> {
                    val t = e.turno
                    righe += listOf(
                        data, "Servizio", t.inizio.toLocalTime(), t.fine.toLocalTime(), t.profilo.name,
                        Duration.between(t.inizio, t.fine).toMinutes(), t.minutiCondotta,
                        if (RegoleAltarail.èNotturno(t)) "si" else "no",
                        if (e.rfrDopo) "si" else "no", festivo, e.regolarizzazioni,
                        String.format(java.util.Locale.ITALIAN, "%.2f", e.sovrattasse),
                        if (e.riserva) "si" else "no", pulisci(e.note)
                    ).joinToString(";")
                }
                is Assenza -> righe += listOf(
                    data, e.tipo.etichetta, "", "", "", "", "", "", "", festivo, "", "", "", pulisci(e.note)
                ).joinToString(";")
            }
        }
        return righe.joinToString("\n")
    }

    private fun pulisci(s: String) = s.replace(";", ",").replace("\n", " ")
}
