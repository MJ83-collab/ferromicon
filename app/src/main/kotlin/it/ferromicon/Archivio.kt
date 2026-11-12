package it.ferromicon

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Salvataggio locale in un file JSON nella memoria privata dell'app.
 * Lo stesso formato è usato per il backup su file e il ripristino.
 */
object Archivio {

    private const val NOME_FILE = "servizi.json"
    private const val VERSIONE_FORMATO = 6
    private const val STATO = "stato_archivio"
    private const val KEY_DANNEGGIATO = "danneggiato"

    // ---------- lettura e scrittura ----------

    fun caricaTutto(ctx: Context): Dati {
        val file = File(ctx.filesDir, NOME_FILE)
        if (!file.exists()) return Dati(emptyList(), emptyList())
        return try {
            daTesto(file.readText())
        } catch (e: Exception) {
            // File danneggiato: lo mettiamo da parte e lo segnaliamo, invece di perderlo in silenzio.
            file.renameTo(File(ctx.filesDir, "$NOME_FILE.danneggiato"))
            ctx.getSharedPreferences(STATO, Context.MODE_PRIVATE).edit().putBoolean(KEY_DANNEGGIATO, true).apply()
            Dati(emptyList(), emptyList())
        }
    }

    /** True se l'archivio è stato messo da parte perché illeggibile e l'utente non è ancora stato avvisato. */
    fun danneggiatoDaSegnalare(ctx: Context): Boolean =
        ctx.getSharedPreferences(STATO, Context.MODE_PRIVATE).getBoolean(KEY_DANNEGGIATO, false)

    fun segnalazioneDanneggiatoVista(ctx: Context) {
        ctx.getSharedPreferences(STATO, Context.MODE_PRIVATE).edit().putBoolean(KEY_DANNEGGIATO, false).apply()
    }

    fun carica(ctx: Context): List<Servizio> = caricaTutto(ctx).servizi

    fun salvaTutto(ctx: Context, dati: Dati) {
        val pulito = dati.pulisciCestino(LocalDateTime.now())
        // Scrittura su file temporaneo e rinomina, per non corrompere l'archivio.
        val tmp = File(ctx.filesDir, "$NOME_FILE.tmp")
        tmp.writeText(aTesto(pulito))
        tmp.renameTo(File(ctx.filesDir, NOME_FILE))
        backupAutomatico(ctx)
        Promemoria.programma(ctx)
        Widget.aggiornaTutti(ctx)
    }

    private inline fun modifica(ctx: Context, cambia: (Dati) -> Dati) = salvaTutto(ctx, cambia(caricaTutto(ctx)))

    // ---------- backup automatico: una copia al giorno, ultime 14 ----------

    private const val COPIE_CONSERVATE = 14

    private fun cartellaBackup(ctx: Context) = File(ctx.filesDir, "backup").apply { mkdirs() }

    /** Copia dell'archivio con la data di oggi; elimina le copie più vecchie. */
    private fun backupAutomatico(ctx: Context) {
        runCatching {
            val origine = File(ctx.filesDir, NOME_FILE)
            if (!origine.exists()) return
            origine.copyTo(File(cartellaBackup(ctx), "servizi-${LocalDate.now()}.json"), overwrite = true)
            backupDisponibili(ctx).drop(COPIE_CONSERVATE).forEach { it.delete() }
        }
    }

    /** Copia di sicurezza immediata (prima di operazioni che cambiano molti giorni). */
    fun copiaDiSicurezza(ctx: Context) = backupAutomatico(ctx)

    /** Copie automatiche, dalla più recente. */
    fun backupDisponibili(ctx: Context): List<File> =
        cartellaBackup(ctx).listFiles()?.filter { it.name.startsWith("servizi-") }?.sortedByDescending { it.name }
            ?: emptyList()

    fun leggiBackup(file: File): Dati = daTesto(file.readText())

    // ---------- modifiche ----------

    /**
     * Aggiunge servizi e assenze (importazione e generatore): i servizi già presenti con
     * gli stessi orari non vengono duplicati; le assenze sostituiscono quelle degli stessi giorni.
     * Restituisce il numero di servizi effettivamente aggiunti.
     */
    fun aggiungi(ctx: Context, servizi: List<Servizio>, assenze: List<Assenza>): Int {
        val d = caricaTutto(ctx)
        val nuovi = deduplica(d.servizi, servizi)
        val giorni = assenze.map { it.data }.toSet()
        salvaTutto(ctx, d.copy(
            servizi = d.servizi + nuovi,
            assenze = d.assenze.filterNot { it.data in giorni } + assenze
        ))
        return nuovi.size
    }

    fun aggiornaOInserisci(ctx: Context, servizio: Servizio) = modifica(ctx) { d ->
        d.copy(servizi = d.servizi.filterNot { it.id == servizio.id } + servizio)
    }

    /** Sposta il servizio nel cestino. */
    fun elimina(ctx: Context, id: Long) = modifica(ctx) { it.eliminaServizio(id, LocalDateTime.now()) }

    /** Inserisce un'assenza per ogni giorno da [da] ad [a], sostituendo quelle già presenti. */
    fun inserisciAssenze(ctx: Context, da: LocalDate, a: LocalDate, tipo: TipoAssenza, note: String) = modifica(ctx) { d ->
        val giorni = generateSequence(da) { it.plusDays(1) }.takeWhile { !it.isAfter(a) }.toList()
        var id = System.currentTimeMillis()
        val nuove = giorni.map { Assenza(id++, it, tipo, note) }
        d.copy(assenze = d.assenze.filterNot { it.data in giorni } + nuove)
    }

    /** Sposta l'assenza nel cestino. */
    fun eliminaAssenza(ctx: Context, id: Long) = modifica(ctx) { it.eliminaAssenza(id, LocalDateTime.now()) }

    /**
     * Sposta un riposo da [da] a [a] (es. quando quel giorno si lavora in straordinario).
     * Riuscito solo se [da] ha un riposo e [a] è libero: nessun servizio e nessuna assenza.
     * Ritorna true se lo spostamento è avvenuto.
     */
    fun spostaRiposo(ctx: Context, da: LocalDate, a: LocalDate): Boolean {
        val d = caricaTutto(ctx)
        val riposo = d.assenze.firstOrNull { it.data == da && it.tipo.riposo } ?: return false
        val occupato = d.assenze.any { it.data == a } ||
            d.servizi.any { it.turno.inizio.toLocalDate() == a || it.turno.fine.toLocalDate() == a }
        if (occupato) return false
        salvaTutto(ctx, d.copy(
            assenze = d.assenze.filterNot { it.data == da } +
                Assenza(System.currentTimeMillis(), a, riposo.tipo, "spostato dal $da")
        ))
        return true
    }

    fun ripristina(ctx: Context, voce: VoceCestino) = modifica(ctx) { it.ripristina(voce) }

    fun svuotaCestino(ctx: Context) = modifica(ctx) { it.copy(cestino = emptyList()) }

    fun salvaImpostazioni(ctx: Context, imp: Impostazioni) = modifica(ctx) { it.copy(impostazioni = imp) }

    fun salvaPreferito(ctx: Context, p: Preferito) = modifica(ctx) { d ->
        d.copy(preferiti = d.preferiti.filterNot { it.id == p.id || it.nome == p.nome } + p)
    }

    fun eliminaPreferito(ctx: Context, id: Long) = modifica(ctx) { d ->
        d.copy(preferiti = d.preferiti.filterNot { it.id == id })
    }

    fun salvaStazione(ctx: Context, s: Stazione) = modifica(ctx) { d ->
        d.copy(stazioni = d.stazioni.filterNot { it.id == s.id } + s)
    }

    fun eliminaStazione(ctx: Context, id: Long) = modifica(ctx) { d ->
        d.copy(stazioni = d.stazioni.filterNot { it.id == id })
    }

    fun salvaPerimetro(ctx: Context, p: Perimetro) = modifica(ctx) { d ->
        d.copy(perimetri = d.perimetri.filterNot { it.id == p.id } + p)
    }

    /** Aggiunge cedolini e CU letti dai PDF; uno stesso mese o anno viene sostituito. */
    /** Salva il cartellino SAP di un mese; uno stesso mese viene sostituito. */
    fun aggiungiCartellino(ctx: Context, c: CartellinoSap) = modifica(ctx) { d ->
        d.copy(cartellini = (d.cartellini.filterNot { it.anno == c.anno && it.mese == c.mese } + c)
            .sortedWith(compareBy({ it.anno }, { it.mese })))
    }

    fun eliminaCartellino(ctx: Context, anno: Int, mese: Int) = modifica(ctx) { d ->
        d.copy(cartellini = d.cartellini.filterNot { it.anno == anno && it.mese == mese })
    }

    fun aggiungiCedolini(ctx: Context, cedolini: List<Cedolino>, cu: List<CertificazioneUnica>) = modifica(ctx) { d ->
        val mesi = cedolini.map { it.anno to it.mese }.toSet()
        val anni = cu.map { it.anno }.toSet()
        d.copy(
            cedolini = (d.cedolini.filterNot { (it.anno to it.mese) in mesi } + cedolini)
                .sortedWith(compareBy({ it.anno ?: 0 }, { it.mese ?: 0 })),
            certificazioniUniche = (d.certificazioniUniche.filterNot { it.anno in anni } + cu)
                .sortedBy { it.anno ?: 0 }
        )
    }

    fun eliminaCedolini(ctx: Context) = modifica(ctx) {
        it.copy(cedolini = emptyList(), certificazioniUniche = emptyList())
    }

    fun eliminaPerimetro(ctx: Context, id: Long) = modifica(ctx) { d ->
        d.copy(perimetri = d.perimetri.filterNot { it.id == id })
    }

    // ---------- conversione JSON ----------

    fun aTesto(dati: Dati): String = JSONObject().apply {
        put("app", "ferromicon")
        put("formato", VERSIONE_FORMATO)
        put("servizi", JSONArray().apply {
            dati.servizi.sortedBy { it.turno.inizio }.forEach { put(servizioAJson(it)) }
        })
        put("assenze", JSONArray().apply { dati.assenze.sortedBy { it.data }.forEach { put(assenzaAJson(it)) } })
        put("impostazioni", impostazioniAJson(dati.impostazioni))
        put("preferiti", JSONArray().apply { dati.preferiti.forEach { put(preferitoAJson(it)) } })
        put("stazioni", JSONArray().apply {
            dati.stazioni.sortedBy { it.nome.lowercase() }.forEach { s ->
                put(JSONObject().apply {
                    put("id", s.id); put("nome", s.nome); put("sigla", s.sigla)
                    put("telefono", s.telefono); put("note", s.note)
                })
            }
        })
        put("perimetri", JSONArray().apply {
            dati.perimetri.forEach { p ->
                put(JSONObject().apply {
                    put("id", p.id); put("nome", p.nome); put("attivo", p.attivo)
                    put("stazioni", JSONArray().apply { p.stazioni.forEach { put(it) } })
                })
            }
        })
        dati.turno?.let { put("turno", turnoAJson(it)) }
        put("cedolini", JSONArray().apply { dati.cedolini.forEach { put(CedoliniJson.cedolino(it)) } })
        put("cartellini", JSONArray().apply { dati.cartellini.forEach { put(CartellinoSapJson.aJson(it)) } })
        put("cu", JSONArray().apply { dati.certificazioniUniche.forEach { put(CedoliniJson.cu(it)) } })
        put("cestino", JSONArray().apply {
            dati.cestino.forEach { v ->
                put(JSONObject().apply {
                    put("eliminatoIl", v.eliminatoIl.toString())
                    v.servizio?.let { put("servizio", servizioAJson(it)) }
                    v.assenza?.let { put("assenza", assenzaAJson(it)) }
                })
            }
        })
    }.toString(1)

    /** Legge il formato attuale e quelli delle versioni precedenti. Solleva eccezione se non valido. */
    fun daTesto(testo: String): Dati {
        val t = testo.trim()
        if (t.startsWith("[")) return Dati(lista(JSONArray(t)) { servizioDa(it) }, emptyList())
        val o = JSONObject(t)
        require(o.has("servizi") || o.has("assenze")) { "Il file non contiene dati di Ferromicon." }
        return Dati(
            servizi = lista(o.optJSONArray("servizi")) { servizioDa(it) }.sortedBy { it.turno.inizio },
            assenze = lista(o.optJSONArray("assenze")) { assenzaDa(it) }.sortedBy { it.data },
            impostazioni = o.optJSONObject("impostazioni")?.let { impostazioniDa(it) } ?: Impostazioni(),
            preferiti = lista(o.optJSONArray("preferiti")) { preferitoDa(it) },
            stazioni = lista(o.optJSONArray("stazioni")) {
                Stazione(it.getLong("id"), it.optString("nome", ""), it.optString("sigla", ""),
                    it.optString("telefono", ""), it.optString("note", ""))
            },
            perimetri = lista(o.optJSONArray("perimetri")) { p ->
                Perimetro(
                    id = p.getLong("id"), nome = p.optString("nome", ""), attivo = p.optBoolean("attivo", true),
                    stazioni = p.optJSONArray("stazioni")?.let { a -> List(a.length()) { a.getString(it) } } ?: emptyList()
                )
            },
            turno = o.optJSONObject("turno")?.let { turnoDa(it) },
            cedolini = lista(o.optJSONArray("cedolini")) { CedoliniJson.cedolinoDa(it) },
            cartellini = lista(o.optJSONArray("cartellini")) { CartellinoSapJson.daJson(it) },
            certificazioniUniche = lista(o.optJSONArray("cu")) { CedoliniJson.cuDa(it) },
            cestino = lista(o.optJSONArray("cestino")) {
                VoceCestino(
                    eliminatoIl = LocalDateTime.parse(it.getString("eliminatoIl")),
                    servizio = it.optJSONObject("servizio")?.let { s -> servizioDa(s) },
                    assenza = it.optJSONObject("assenza")?.let { a -> assenzaDa(a) }
                )
            }
        )
    }

    private fun <T> lista(array: JSONArray?, leggi: (JSONObject) -> T): List<T> =
        if (array == null) emptyList() else List(array.length()) { leggi(array.getJSONObject(it)) }

    private fun data(o: JSONObject, chiave: String): LocalDateTime? =
        o.optString(chiave, "").takeIf { it.isNotBlank() }?.let { LocalDateTime.parse(it) }

    private fun righeAJson(righe: List<RigaTreno>) = JSONArray().apply {
        righe.forEach { r ->
            put(JSONObject().apply {
                put("numero", r.numero); put("da", r.da); put("a", r.a)
                put("partenza", r.partenza.toString()); put("arrivo", r.arrivo.toString())
                put("tipo", r.tipo.name)
                put("partenzaReale", r.partenzaReale?.toString() ?: "")
                put("arrivoReale", r.arrivoReale?.toString() ?: "")
                put("collegamento", r.collegamento)
                put("vettura", r.vettura)
            })
        }
    }

    private fun righeDa(array: JSONArray?): List<RigaTreno> = lista(array) {
        RigaTreno(
            numero = it.optString("numero", ""), da = it.optString("da", ""), a = it.optString("a", ""),
            partenza = LocalDateTime.parse(it.getString("partenza")),
            arrivo = LocalDateTime.parse(it.getString("arrivo")),
            tipo = runCatching { TipoRiga.valueOf(it.getString("tipo")) }.getOrDefault(TipoRiga.ALTRO),
            partenzaReale = data(it, "partenzaReale"),
            arrivoReale = data(it, "arrivoReale"),
            collegamento = it.optString("collegamento", ""),
            vettura = it.optBoolean("vettura", false)
        )
    }

    internal fun servizioAJson(s: Servizio) = JSONObject().apply {
        put("id", s.id)
        put("inizio", s.turno.inizio.toString())
        put("fine", s.turno.fine.toString())
        put("profilo", s.turno.profilo.name)
        put("condotta", s.turno.minutiCondotta)
        put("consecutiva", s.turno.maxCondottaConsecutiva)
        put("zeta", s.turno.turnoAZeta)
        put("rfrDopo", s.rfrDopo)
        put("note", s.note)
        put("regolarizzazioni", s.regolarizzazioni)
        put("sovrattasse", s.sovrattasse)
        put("riserva", s.riserva)
        put("fuoriResidenza", s.minutiFuoriResidenza)
        put("materiale", s.materiale)
        put("secondoAgente", s.secondoAgente)
        put("origineTurno", s.origineTurno)
        put("righe", righeAJson(s.righe))
        put("straordinario", s.straordinario)
    }

    internal fun servizioDa(o: JSONObject) = Servizio(
        id = o.getLong("id"),
        turno = Turno(
            inizio = LocalDateTime.parse(o.getString("inizio")),
            fine = LocalDateTime.parse(o.getString("fine")),
            profilo = runCatching { Profilo.valueOf(o.getString("profilo")) }.getOrDefault(Profilo.MACCHINISTA),
            minutiCondotta = o.optInt("condotta", 0),
            maxCondottaConsecutiva = o.optInt("consecutiva", 0),
            turnoAZeta = o.optBoolean("zeta", false)
        ),
        rfrDopo = o.optBoolean("rfrDopo", false),
        note = o.optString("note", ""),
        regolarizzazioni = o.optInt("regolarizzazioni", 0),
        sovrattasse = o.optDouble("sovrattasse", 0.0),
        riserva = o.optBoolean("riserva", false),
        minutiFuoriResidenza = o.optInt("fuoriResidenza", 0),
        materiale = o.optString("materiale", ""),
        secondoAgente = o.optString("secondoAgente", ""),
        origineTurno = o.optString("origineTurno", ""),
        righe = righeDa(o.optJSONArray("righe")),
        straordinario = o.optBoolean("straordinario", false)
    )

    internal fun turnoAJson(t: TurnoImpianto) = JSONObject().apply {
        put("nome", t.nome); put("impianto", t.impianto)
        put("giornate", JSONArray().apply { t.giornate.forEach { put(it) } })
        put("riferimento", t.dataRiferimento.toString()); put("posizione", t.posizione)
        put("validoFino", t.validoFino.toString())
    }

    internal fun turnoDa(o: JSONObject): TurnoImpianto? = runCatching {
        TurnoImpianto(
            nome = o.optString("nome", ""), impianto = o.optString("impianto", ""),
            giornate = o.optJSONArray("giornate")?.let { a -> List(a.length()) { a.getString(it) } } ?: emptyList(),
            dataRiferimento = LocalDate.parse(o.getString("riferimento")),
            posizione = o.optInt("posizione", 0),
            validoFino = LocalDate.parse(o.getString("validoFino"))
        )
    }.getOrNull()

    fun salvaTurno(ctx: Context, turno: TurnoImpianto?) = modifica(ctx) { it.copy(turno = turno) }

    /** Applica al database una compilazione del turno (giorni compilati o giornata di default). */
    fun applicaCompilazione(ctx: Context, c: Compilazione) =
        modifica(ctx) { PianoTurno.applica(it, c, LocalDateTime.now()) }

    internal fun assenzaAJson(a: Assenza) = JSONObject().apply {
        put("id", a.id); put("data", a.data.toString()); put("tipo", a.tipo.name); put("note", a.note)
    }

    internal fun assenzaDa(o: JSONObject) = Assenza(
        id = o.getLong("id"),
        data = LocalDate.parse(o.getString("data")),
        tipo = runCatching { TipoAssenza.valueOf(o.getString("tipo")) }.getOrDefault(TipoAssenza.ALTRO),
        note = o.optString("note", "")
    )

    internal fun preferitoAJson(p: Preferito) = JSONObject().apply {
        put("id", p.id); put("nome", p.nome)
        put("oraInizio", p.oraInizio.toString()); put("oraFine", p.oraFine.toString())
        put("profilo", p.profilo.name); put("condotta", p.minutiCondotta)
        put("consecutiva", p.maxCondottaConsecutiva); put("zeta", p.turnoAZeta)
        put("rfrDopo", p.rfrDopo); put("riserva", p.riserva)
        put("fuoriResidenza", p.minutiFuoriResidenza); put("note", p.note)
        put("materiale", p.materiale); put("secondoAgente", p.secondoAgente)
        put("righe", righeAJson(p.righe))
        put("giornoRiferimento", p.giornoRiferimento?.toString() ?: "")
    }

    internal fun preferitoDa(o: JSONObject) = Preferito(
        id = o.getLong("id"),
        nome = o.optString("nome", "Servizio"),
        oraInizio = LocalTime.parse(o.getString("oraInizio")),
        oraFine = LocalTime.parse(o.getString("oraFine")),
        profilo = runCatching { Profilo.valueOf(o.getString("profilo")) }.getOrDefault(Profilo.MACCHINISTA),
        minutiCondotta = o.optInt("condotta", 0),
        maxCondottaConsecutiva = o.optInt("consecutiva", 0),
        turnoAZeta = o.optBoolean("zeta", false),
        rfrDopo = o.optBoolean("rfrDopo", false),
        riserva = o.optBoolean("riserva", false),
        minutiFuoriResidenza = o.optInt("fuoriResidenza", 0),
        note = o.optString("note", ""),
        materiale = o.optString("materiale", ""),
        secondoAgente = o.optString("secondoAgente", ""),
        righe = righeDa(o.optJSONArray("righe")),
        giornoRiferimento = o.optString("giornoRiferimento", "").takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) }
    )

    internal fun impostazioniAJson(i: Impostazioni) = JSONObject().apply {
        put("dataAssunzione", i.dataAssunzione?.toString() ?: "")
        i.livello?.let { put("livello", it.name) }
        put("adPersonam", i.adPersonam)
        put("orarioSuSeiGiorni", i.orarioSuSeiGiorni)
        put("ferieResidueAnnoPrecedente", i.ferieResidueAnnoPrecedente)
        put("permessiAggiuntivi", i.permessiAggiuntivi)
        put("parentaliSpettanti", i.parentaliSpettanti)
        put("permessiSpettanti", i.permessiSpettanti)
        put("abilitazioniLinee", JSONArray(i.abilitazioniLinee))
        put("abilitazioniMacchine", JSONArray(i.abilitazioniMacchine))
        put("ultimoViaggio", JSONObject().apply { i.ultimoViaggio.forEach { (k, v) -> put(k, v.toString()) } })
        put("giorniSogliaAbilitazioni", i.giorniSogliaAbilitazioni)
        put("promemoriaMinuti", i.promemoriaMinuti)
        put("avvisoRecuperoAttivo", i.avvisoRecuperoAttivo)
        put("giorniAvvisoRecupero", i.giorniAvvisoRecupero)
        put("letteraGruppoFerie", i.letteraGruppoFerie?.toString() ?: "")
        i.limitiOrario?.let { put("limitiOrario", JSONObject().apply {
            put("diurno", it.diurnoMinuti); put("notturno", it.notturnoMinuti)
        }) }
        put("redditoAnnuo", i.redditoAnnuo)
        put("inpsPct", i.inpsPct)
        put("addComunalePct", i.addComunalePct)
        put("profilo", i.profilo?.name ?: "")
        put("cid", i.cid)
        put("impianto", i.impianto)
        put("nome", i.nome)
        put("festiviForzati", JSONArray().apply {
            i.festiviForzati.forEach { (data, valore) ->
                put(JSONObject().apply { put("data", data.toString()); put("recuperabile", valore) })
            }
        })
    }

    internal fun impostazioniDa(o: JSONObject) = Impostazioni(
        dataAssunzione = o.optString("dataAssunzione", "").takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) },
        livello = runCatching { Livello.valueOf(o.getString("livello")) }.getOrNull(),
        adPersonam = o.optDouble("adPersonam", 0.0),
        orarioSuSeiGiorni = o.optBoolean("orarioSuSeiGiorni", false),
        ferieResidueAnnoPrecedente = o.optInt("ferieResidueAnnoPrecedente", 0),
        permessiAggiuntivi = o.optInt("permessiAggiuntivi", 0),
        parentaliSpettanti = o.optInt("parentaliSpettanti", 0),
        permessiSpettanti = o.optInt("permessiSpettanti", 0),
        abilitazioniLinee = o.optJSONArray("abilitazioniLinee")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
        abilitazioniMacchine = o.optJSONArray("abilitazioniMacchine")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
        ultimoViaggio = o.optJSONObject("ultimoViaggio")?.let { j ->
            j.keys().asSequence().associateWith { LocalDate.parse(j.getString(it)) }
        } ?: emptyMap(),
        giorniSogliaAbilitazioni = o.optInt("giorniSogliaAbilitazioni", 90),
        promemoriaMinuti = o.optInt("promemoriaMinuti", 60),
        avvisoRecuperoAttivo = o.optBoolean("avvisoRecuperoAttivo", true),
        giorniAvvisoRecupero = o.optInt("giorniAvvisoRecupero", 7).coerceIn(1, 30),
        letteraGruppoFerie = o.optString("letteraGruppoFerie", "").firstOrNull()?.uppercaseChar(),
        limitiOrario = o.optJSONObject("limitiOrario")?.let {
            LimitiOrario(it.optInt("diurno", 510), it.optInt("notturno", 420))
        },
        redditoAnnuo = o.optDouble("redditoAnnuo", 0.0),
        inpsPct = o.optDouble("inpsPct", 9.19),
        addComunalePct = o.optDouble("addComunalePct", 0.8),
        profilo = runCatching { Profilo.valueOf(o.getString("profilo")) }.getOrNull(),
        cid = o.optString("cid", ""),
        impianto = o.optString("impianto", ""),
        nome = o.optString("nome", ""),
        festiviForzati = o.optJSONArray("festiviForzati")?.let { a ->
            (0 until a.length()).associate {
                val x = a.getJSONObject(it)
                LocalDate.parse(x.getString("data")) to x.getBoolean("recuperabile")
            }
        } ?: emptyMap()
    )
}
