package it.ferromicon

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.AdapterView
import android.widget.AutoCompleteTextView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Inserimento e modifica di un servizio, con verifica immediata. */
class ServizioActivity : Activity() {

    companion object {
        const val EXTRA_ID = "id"
        const val EXTRA_DATA = "data"
        private const val PREF_PROFILO = "profilo"
    }

    private var esistente: Servizio? = null
    private var data: LocalDate = LocalDate.now()
    private var oraInizio: LocalTime = LocalTime.of(6, 0)
    private var oraFine: LocalTime = LocalTime.of(14, 0)

    private val fmtData = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ITALIAN)
    private val fmtOra = DateTimeFormatter.ofPattern("HH:mm")

    private lateinit var profilo: Spinner
    private lateinit var btnData: Button
    private lateinit var btnInizio: Button
    private lateinit var btnFine: Button
    private lateinit var condotta: EditText
    private lateinit var consecutiva: EditText
    private lateinit var zeta: CheckBox
    private lateinit var rfr: CheckBox
    private lateinit var riserva: CheckBox
    private lateinit var fuoriResidenza: EditText
    private lateinit var note: EditText
    private lateinit var esito: TextView
    private lateinit var etichettaCondotta: TextView
    private lateinit var bloccoMacchinista: LinearLayout
    private lateinit var bloccoBordo: LinearLayout
    private lateinit var regolarizzazioni: EditText
    private lateinit var sovrattasse: EditText
    private lateinit var materiale: EditText
    private lateinit var secondoAgente: EditText
    private lateinit var etichettaCollega: TextView
    private lateinit var elencoRighe: LinearLayout
    private lateinit var notaCondotta: TextView

    /** Righe treno e attività del servizio, in ordine di partenza. */
    private val righe = mutableListOf<RigaTreno>()
    private var nomiStazioni: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getPreferences(MODE_PRIVATE)
        val archivio = Archivio.caricaTutto(this)
        nomiStazioni = (archivio.stazioni.flatMap { listOf(it.nome, it.sigla) } +
            (OrarioAltarail.carica(this)?.stazioni ?: emptyList())).filter { it.isNotBlank() }.distinct()

        intent.getStringExtra(EXTRA_DATA)?.let { runCatching { data = LocalDate.parse(it) } }
        val id = intent.getLongExtra(EXTRA_ID, -1L)
        esistente = if (id >= 0) Archivio.carica(this).firstOrNull { it.id == id } else null

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        profilo = Spinner(this).apply {
            adapter = ArrayAdapter(this@ServizioActivity,
                android.R.layout.simple_spinner_dropdown_item, Profilo.values().map { it.etichetta })
        }
        col.addView(etichetta("Profilo")); col.addView(profilo)

        btnData = Button(this).apply { setOnClickListener { scegliData() } }
        btnInizio = Button(this).apply { setOnClickListener { scegliOra(true) } }
        btnFine = Button(this).apply { setOnClickListener { scegliOra(false) } }
        col.addView(etichetta("Giorno di inizio")); col.addView(btnData)
        val orari = LinearLayout(this)
        orari.addView(colonna("Inizio", btnInizio), LinearLayout.LayoutParams(0, -2, 1f))
        orari.addView(colonna("Fine", btnFine), LinearLayout.LayoutParams(0, -2, 1f))
        col.addView(orari)
        col.addView(TextView(this).apply {
            text = "Se la fine è prima dell'inizio, il servizio termina il giorno dopo."
            textSize = 12f
        })

        // Treni e attività: se presenti, condotta o scorta si calcolano da qui
        col.addView(etichetta("Treni e attività"))
        col.addView(TextView(this).apply {
            text = "Facoltativo. Inserisci i treni con orari previsti e, se vuoi, reali: condotta, " +
                "condotta continuativa e ritardi si calcolano da soli. Tocca una riga per modificarla, " +
                "tienila premuta per eliminarla."
            textSize = 12f
        })
        elencoRighe = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(elencoRighe)
        col.addView(Button(this).apply { text = "+ Treno o attività"; setOnClickListener { modificaRiga(null) } })

        // Condotta (macchinista) o scorta (capo treno): accetta "330" oppure "5:30"
        condotta = durata(); consecutiva = durata()
        etichettaCondotta = etichetta("Condotta totale (minuti o ore:minuti)")
        notaCondotta = TextView(this).apply { textSize = 12f; visibility = View.GONE }
        col.addView(etichettaCondotta); col.addView(notaCondotta); col.addView(condotta)

        bloccoMacchinista = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        bloccoMacchinista.addView(etichetta("Tratto di condotta consecutiva più lungo"))
        bloccoMacchinista.addView(consecutiva)
        col.addView(bloccoMacchinista)

        regolarizzazioni = EditText(this).apply { inputType = InputType.TYPE_CLASS_NUMBER; hint = "0" }
        sovrattasse = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL; hint = "0,00"
        }
        bloccoBordo = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        bloccoBordo.addView(etichetta("Regolarizzazioni effettuate")); bloccoBordo.addView(regolarizzazioni)
        bloccoBordo.addView(etichetta("Sovrattasse riscosse (€)")); bloccoBordo.addView(sovrattasse)
        col.addView(bloccoBordo)

        profilo.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) = adattaAlProfilo()
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        zeta = CheckBox(this).apply { text = "Turno a zeta" }
        rfr = CheckBox(this).apply { text = "Dopo questo servizio: riposo fuori residenza (RFR)" }
        riserva = CheckBox(this).apply { text = "Riserva o formazione" }
        col.addView(zeta); col.addView(rfr); col.addView(riserva)

        fuoriResidenza = durata()
        col.addView(etichetta("Ore fuori residenza (facoltativo)"))
        col.addView(TextView(this).apply {
            text = "Dalla partenza del treno al rientro in residenza. Serve per il compenso di assenza " +
                "dalla residenza, che spetta da 3 ore in su (CCNL art. 77.2)."
            textSize = 12f
        })
        col.addView(fuoriResidenza)

        materiale = EditText(this).apply { hint = "es. Caravaggio, TSR, Coradia" }
        col.addView(etichetta("Materiale rotabile")); col.addView(materiale)
        secondoAgente = EditText(this).apply { hint = "Nome del collega" }
        etichettaCollega = etichetta("Capo treno")
        col.addView(etichettaCollega); col.addView(secondoAgente)

        note = EditText(this).apply { hint = "Note" }
        col.addView(etichetta("Note")); col.addView(note)

        val bottoni = LinearLayout(this)
        bottoni.addView(Button(this).apply { text = "Verifica"; setOnClickListener { verifica() } },
            LinearLayout.LayoutParams(0, -2, 1f))
        bottoni.addView(Button(this).apply { text = "Salva"; setOnClickListener { salva() } },
            LinearLayout.LayoutParams(0, -2, 1f))
        col.addView(bottoni)
        val altri = LinearLayout(this)
        if (esistente == null && Archivio.caricaTutto(this).preferiti.isNotEmpty())
            altri.addView(Button(this).apply { text = "⭐ Da preferito"; setOnClickListener { daPreferito() } },
                LinearLayout.LayoutParams(0, -2, 1f))
        altri.addView(Button(this).apply { text = "Salva come preferito"; setOnClickListener { salvaPreferito() } },
            LinearLayout.LayoutParams(0, -2, 1f))
        col.addView(altri)
        if (esistente != null) col.addView(Button(this).apply {
            text = "Elimina"
            setOnClickListener { conferma() }
        })

        esito = TextView(this).apply { textSize = 15f; setPadding(0, dp(12), 0, 0); setTextIsSelectable(true) }
        col.addView(esito)

        // Valori iniziali
        val s = esistente
        if (s != null) {
            data = s.turno.inizio.toLocalDate()
            oraInizio = s.turno.inizio.toLocalTime()
            oraFine = s.turno.fine.toLocalTime()
            profilo.setSelection(s.turno.profilo.ordinal)
            condotta.setText(oreMinuti(s.turno.minutiCondotta))
            consecutiva.setText(oreMinuti(s.turno.maxCondottaConsecutiva))
            if (s.regolarizzazioni > 0) regolarizzazioni.setText(s.regolarizzazioni.toString())
            if (s.sovrattasse > 0) sovrattasse.setText(s.sovrattasse.toString().replace('.', ','))
            zeta.isChecked = s.turno.turnoAZeta
            rfr.isChecked = s.rfrDopo
            riserva.isChecked = s.riserva
            fuoriResidenza.setText(oreMinuti(s.minutiFuoriResidenza))
            note.setText(s.note)
            materiale.setText(s.materiale)
            secondoAgente.setText(s.secondoAgente)
            righe += s.righe.sortedBy { it.partenza }
        } else {
            profilo.setSelection(Archivio.caricaTutto(this).impostazioni.profiloAttivo.ordinal)
        }
        aggiornaBottoni()
        adattaAlProfilo()
        mostraRighe()

        setContentView(ScrollView(this).apply { addView(col) })
        title = if (s == null) "Nuovo servizio" else "Modifica servizio"
    }

    private fun costruisci(): Servizio {
        val inizio = LocalDateTime.of(data, oraInizio)
        var fine = LocalDateTime.of(data, oraFine)
        if (!fine.isAfter(inizio)) fine = fine.plusDays(1)
        val servizio = Servizio(
            id = esistente?.id ?: System.currentTimeMillis(),
            turno = Turno(
                inizio = inizio,
                fine = fine,
                profilo = Profilo.values()[profilo.selectedItemPosition],
                minutiCondotta = minuti(condotta),
                maxCondottaConsecutiva = if (macchinista()) minuti(consecutiva) else 0,
                turnoAZeta = zeta.isChecked
            ),
            rfrDopo = rfr.isChecked,
            riserva = riserva.isChecked,
            minutiFuoriResidenza = minuti(fuoriResidenza).takeIf { it > 0 }
                ?: Residenza.minutiFuori(righe, Archivio.caricaTutto(this).perimetri),
            note = note.text.toString().trim(),
            regolarizzazioni = if (macchinista()) 0 else regolarizzazioni.text.toString().toIntOrNull() ?: 0,
            sovrattasse = if (macchinista()) 0.0
                else sovrattasse.text.toString().replace(',', '.').toDoubleOrNull() ?: 0.0,
            materiale = materiale.text.toString().trim(),
            secondoAgente = secondoAgente.text.toString().trim(),
            origineTurno = esistente?.origineTurno ?: "",
            righe = righe.sortedBy { it.partenza }
        )
        return servizio.conRigheApplicate()
    }

    private fun inizioServizio() = LocalDateTime.of(data, oraInizio)

    // ---------- righe treno ----------

    private fun mostraRighe() {
        elencoRighe.removeAllViews()
        righe.sortBy { it.partenza }
        righe.forEachIndexed { i, r ->
            val ritardo = r.arrivoReale?.let { java.time.Duration.between(r.arrivo, it).toMinutes() }
            val tratta = listOf(r.da, r.a).filter { it.isNotBlank() }.joinToString(" → ")
            elencoRighe.addView(TextView(this).apply {
                text = buildString {
                    append(if (r.numero.isBlank()) r.tipo.etichetta else "${r.numero} · ${r.tipo.etichetta.lowercase()}")
                    append("  ${fmtOra.format(r.partenza)}–${fmtOra.format(r.arrivo)}")
                    if (tratta.isNotEmpty()) append("\n    $tratta")
                    if (ritardo != null) append(when {
                        ritardo > 0 -> "\n    arrivo reale ${fmtOra.format(r.arrivoReale)} (+$ritardo')"
                        ritardo < 0 -> "\n    arrivo reale ${fmtOra.format(r.arrivoReale)} ($ritardo')"
                        else -> "\n    in orario"
                    })
                }
                textSize = 15f
                setPadding(0, dp(6), 0, dp(6))
                setOnClickListener { modificaRiga(i) }
                setOnLongClickListener {
                    AlertDialog.Builder(this@ServizioActivity)
                        .setMessage("Eliminare questa riga?")
                        .setPositiveButton("Elimina") { _, _ -> righe.removeAt(i); mostraRighe() }
                        .setNegativeButton("Annulla", null)
                        .show()
                    true
                }
            })
        }
        aggiornaCondottaDaRighe()
    }

    /** Con righe presenti, condotta e condotta continuativa sono calcolate e non si scrivono a mano. */
    private fun aggiornaCondottaDaRighe() {
        val calcolate = righe.isNotEmpty()
        condotta.isEnabled = !calcolate
        consecutiva.isEnabled = !calcolate
        notaCondotta.visibility = if (calcolate) View.VISIBLE else View.GONE
        if (!calcolate) return
        val s = Servizio(0, Turno(inizioServizio(), inizioServizio().plusHours(1), Profilo.values()[profilo.selectedItemPosition]),
            righe = righe.toList()).conRigheApplicate()
        condotta.setText(oreMinuti(s.turno.minutiCondotta).ifEmpty { "0:00" })
        consecutiva.setText(oreMinuti(s.turno.maxCondottaConsecutiva).ifEmpty { "0:00" })
        notaCondotta.text = "Calcolata dalle righe (con gli orari reali, se inseriti)."
    }

    /** Dialogo di inserimento o modifica di una riga; [indice] null per una riga nuova. */
    private fun modificaRiga(indice: Int?) {
        val esistente = indice?.let { righe[it] }
        val base = inizioServizio()
        var partenza = esistente?.partenza?.toLocalTime() ?: righe.lastOrNull()?.arrivo?.toLocalTime() ?: oraInizio
        var arrivo = esistente?.arrivo?.toLocalTime() ?: partenza.plusHours(1)
        var partenzaReale = esistente?.partenzaReale?.toLocalTime()
        var arrivoReale = esistente?.arrivoReale?.toLocalTime()

        val numero = EditText(this).apply { hint = "Numero treno (facoltativo)"; setText(esistente?.numero ?: "") }
        val tipo = Spinner(this).apply {
            adapter = ArrayAdapter(this@ServizioActivity, android.R.layout.simple_spinner_dropdown_item,
                TipoRiga.values().map { it.etichetta })
            setSelection((esistente?.tipo ?: if (macchinista()) TipoRiga.CONDOTTA else TipoRiga.SCORTA).ordinal)
        }
        val da = stazione("Da (stazione)", esistente?.da ?: righe.lastOrNull()?.a ?: "")
        val a = stazione("A (stazione)", esistente?.a ?: "")
        val btnPartenza = Button(this)
        val btnArrivo = Button(this)
        val btnPartenzaReale = Button(this)
        val btnArrivoReale = Button(this)
        fun aggiorna() {
            btnPartenza.text = "Partenza ${fmtOra.format(partenza)}"
            btnArrivo.text = "Arrivo ${fmtOra.format(arrivo)}"
            btnPartenzaReale.text = "Partenza reale " + (partenzaReale?.let { fmtOra.format(it) } ?: "—")
            btnArrivoReale.text = "Arrivo reale " + (arrivoReale?.let { fmtOra.format(it) } ?: "—")
        }
        fun ora(iniziale: LocalTime, imposta: (LocalTime) -> Unit) =
            TimePickerDialog(this, { _, h, m -> imposta(LocalTime.of(h, m)); aggiorna() },
                iniziale.hour, iniziale.minute, true).show()
        btnPartenza.setOnClickListener { ora(partenza) { partenza = it } }
        btnArrivo.setOnClickListener { ora(arrivo) { arrivo = it } }
        btnPartenzaReale.setOnClickListener { ora(partenzaReale ?: partenza) { partenzaReale = it } }
        btnArrivoReale.setOnClickListener { ora(arrivoReale ?: arrivo) { arrivoReale = it } }
        val azzera = Button(this).apply {
            text = "Togli orari reali"
            setOnClickListener { partenzaReale = null; arrivoReale = null; aggiorna() }
        }
        // Ricerca nell'orario ufficiale (Regione Lombardia): compila stazioni e orari del treno
        val cerca = Button(this).apply {
            text = "🔎 Cerca il treno nell'orario"
            setOnClickListener {
                cercaNellOrario(numero.text.toString()) { r ->
                    numero.setText(r.numero); da.setText(r.da); a.setText(r.a)
                    partenza = r.partenza.toLocalTime(); arrivo = r.arrivo.toLocalTime()
                    aggiorna()
                }
            }
        }
        aggiorna()

        val modulo = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(numero); addView(cerca); addView(tipo); addView(da); addView(a)
            addView(btnPartenza); addView(btnArrivo); addView(btnPartenzaReale); addView(btnArrivoReale); addView(azzera)
        }
        AlertDialog.Builder(this)
            .setTitle(if (esistente == null) "Nuovo treno o attività" else "Modifica riga")
            .setView(ScrollView(this).apply { addView(modulo) })
            .setPositiveButton("OK") { _, _ ->
                val p = Righe.ancora(base, partenza)
                var arr = Righe.ancora(base, arrivo)
                if (!arr.isAfter(p)) arr = arr.plusDays(1)
                fun reale(t: LocalTime?, rif: LocalDateTime) = t?.let {
                    // l'orario reale è il più vicino a quello programmato (anche a cavallo della mezzanotte)
                    listOf(rif.toLocalDate().minusDays(1), rif.toLocalDate(), rif.toLocalDate().plusDays(1))
                        .map { d -> d.atTime(it) }
                        .minByOrNull { c -> kotlin.math.abs(java.time.Duration.between(rif, c).toMinutes()) }
                }
                val nuova = RigaTreno(
                    numero = numero.text.toString().trim(),
                    da = da.text.toString().trim(),
                    a = a.text.toString().trim(),
                    partenza = p, arrivo = arr,
                    tipo = TipoRiga.values()[tipo.selectedItemPosition],
                    partenzaReale = reale(partenzaReale, p),
                    arrivoReale = reale(arrivoReale, arr)
                )
                if (indice == null) righe += nuova else righe[indice] = nuova
                mostraRighe()
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    /**
     * Cerca il treno nell'orario importato per il giorno del servizio (o il giorno dopo, per i
     * treni dopo mezzanotte), poi fa scegliere la stazione di salita e quella di discesa.
     */
    private fun cercaNellOrario(numero: String, usa: (RigaTreno) -> Unit) {
        val orario = OrarioAltarail.carica(this)
        if (orario == null) {
            AlertDialog.Builder(this)
                .setMessage("Nessun orario importato. Dal menu principale scegli \"Orario Altarail (Regione " +
                    "Lombardia)\" e importa lo zip GTFS dal portale Open Data.")
                .setPositiveButton("OK", null).show()
            return
        }
        if (numero.isBlank()) {
            Toast.makeText(this, "Scrivi prima il numero del treno", Toast.LENGTH_SHORT).show()
            return
        }
        val corse = orario.cerca(numero, data).ifEmpty { orario.cerca(numero, data.plusDays(1)) }
        if (corse.isEmpty()) {
            val motivo = if (orario.esiste(numero)) "non circola il ${fmtData.format(data)}" else "non è nell'orario"
            AlertDialog.Builder(this).setMessage("Il treno $numero $motivo.").setPositiveButton("OK", null).show()
            return
        }
        fun scegliTratta(c: Corsa) {
            val voci = c.fermate.map { "${it.stazione}  ${fmtOra.format(c.orario(it.partenza))}" }
            AlertDialog.Builder(this)
                .setTitle("Treno ${c.numero}: dove sali?")
                .setItems(voci.dropLast(1).toTypedArray()) { _, salita ->
                    val dopo = c.fermate.drop(salita + 1)
                    AlertDialog.Builder(this)
                        .setTitle("Dove scendi?")
                        .setItems(dopo.map { "${it.stazione}  ${fmtOra.format(c.orario(it.arrivo))}" }.toTypedArray()) { _, i ->
                            usa(c.inRiga(salita, salita + 1 + i, TipoRiga.CONDOTTA))
                        }
                        .setNeutralButton("Fino al capolinea") { _, _ -> usa(c.inRiga(salita, c.fermate.size - 1, TipoRiga.CONDOTTA)) }
                        .show()
                }
                .setNeutralButton("Tutta la corsa") { _, _ -> usa(c.inRiga(0, c.fermate.size - 1, TipoRiga.CONDOTTA)) }
                .show()
        }
        if (corse.size == 1) scegliTratta(corse.single())
        else AlertDialog.Builder(this)
            .setTitle("Più corse con il numero $numero")
            .setItems(corse.map { "${it.origine} → ${it.capolinea} ${fmtOra.format(it.orario(it.fermate.first().partenza))}" }
                .toTypedArray()) { _, i -> scegliTratta(corse[i]) }
            .show()
    }

    /** Campo stazione con i suggerimenti della rubrica. */
    private fun stazione(suggerimento: String, valore: String) = AutoCompleteTextView(this).apply {
        hint = suggerimento
        setText(valore)
        threshold = 1
        setAdapter(ArrayAdapter(this@ServizioActivity, android.R.layout.simple_dropdown_item_1line, nomiStazioni))
    }

    private fun macchinista() = Profilo.values()[profilo.selectedItemPosition] == Profilo.MACCHINISTA

    private fun adattaAlProfilo() {
        val m = macchinista()
        etichettaCondotta.text = if (m) "Condotta totale (minuti o ore:minuti)"
            else "Scorta totale (minuti o ore:minuti)"
        bloccoMacchinista.visibility = if (m) View.VISIBLE else View.GONE
        bloccoBordo.visibility = if (m) View.GONE else View.VISIBLE
        etichettaCollega.text = if (m) "Capo treno" else "Macchinista"
        if (::elencoRighe.isInitialized) aggiornaCondottaDaRighe()
    }

    /** Legge "330", "5:30" o "5.30" come minuti. */
    private fun minuti(campo: EditText): Int {
        val t = campo.text.toString().trim().replace('.', ':').replace(',', ':')
        if (t.isEmpty()) return 0
        if (':' in t) {
            val (h, m) = t.split(':', limit = 2)
            return (h.toIntOrNull() ?: 0) * 60 + (m.toIntOrNull() ?: 0)
        }
        return t.toIntOrNull() ?: 0
    }

    private fun oreMinuti(min: Int) = if (min == 0) "" else String.format("%d:%02d", min / 60, min % 60)

    /** Compila il modulo con un servizio preferito, mantenendo il giorno scelto. */
    private fun daPreferito() {
        val preferiti = Archivio.caricaTutto(this).preferiti.sortedBy { it.nome }
        AlertDialog.Builder(this)
            .setTitle("Scegli un preferito")
            .setItems(preferiti.map { "${it.nome}  ${fmtOra.format(it.oraInizio)}–${fmtOra.format(it.oraFine)}" }
                .toTypedArray()) { _, i ->
                val p = preferiti[i]
                oraInizio = p.oraInizio
                oraFine = p.oraFine
                profilo.setSelection(p.profilo.ordinal)
                condotta.setText(oreMinuti(p.minutiCondotta))
                consecutiva.setText(oreMinuti(p.maxCondottaConsecutiva))
                zeta.isChecked = p.turnoAZeta
                rfr.isChecked = p.rfrDopo
                riserva.isChecked = p.riserva
                fuoriResidenza.setText(oreMinuti(p.minutiFuoriResidenza))
                note.setText(p.note)
                materiale.setText(p.materiale)
                secondoAgente.setText(p.secondoAgente)
                righe.clear()
                righe += p.applica(data, 0).righe
                aggiornaBottoni()
                adattaAlProfilo()
                mostraRighe()
            }
            .show()
    }

    private fun salvaPreferito() {
        val s = costruisci()
        val iniziale = s.note.ifBlank { "${fmtOra.format(oraInizio)}–${fmtOra.format(oraFine)}" }
        val campo = EditText(this).apply { setText(iniziale); hint = "Nome del preferito" }
        AlertDialog.Builder(this)
            .setTitle("Salva come preferito")
            .setView(LinearLayout(this).apply { setPadding(dp(20), dp(8), dp(20), 0); addView(campo) })
            .setPositiveButton("Salva") { _, _ ->
                val nome = campo.text.toString().trim().ifBlank { iniziale }
                Archivio.salvaPreferito(this, Preferito.da(s, nome, System.currentTimeMillis()))
                Toast.makeText(this, "Preferito \"$nome\" salvato", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    /** Verifica il servizio nel contesto dell'agenda, senza salvarlo. */
    private fun verifica() {
        val nuovo = costruisci()
        val dati = Archivio.caricaTutto(this)
        val lista = (dati.servizi.filterNot { it.id == nuovo.id } + nuovo).sortedBy { it.turno.inizio }
        val indice = lista.indexOfFirst { it.id == nuovo.id }
        esito.text = Analisi.verificaServizio(lista, indice, dati.assenze, dati.impostazioni.limitiOrario)
            .joinToString("\n\n") { MainActivity.formatta(it) }
    }

    private fun salva() {
        val nuovo = costruisci()
        Archivio.aggiornaOInserisci(this, nuovo)
        getPreferences(MODE_PRIVATE).edit().putInt(PREF_PROFILO, nuovo.turno.profilo.ordinal).apply()
        Toast.makeText(this, "Servizio salvato", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun conferma() {
        val s = esistente ?: return
        AlertDialog.Builder(this)
            .setMessage("Eliminare questo servizio?")
            .setPositiveButton("Elimina") { _, _ ->
                Archivio.elimina(this, s.id)
                Toast.makeText(this, "Spostato nel cestino: si recupera dal menu per 30 giorni", Toast.LENGTH_LONG).show()
                finish()
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun scegliData() {
        DatePickerDialog(this, { _, a, m, g ->
            val nuova = LocalDate.of(a, m + 1, g)
            // le righe seguono il servizio nel nuovo giorno
            val giorni = java.time.temporal.ChronoUnit.DAYS.between(data, nuova)
            if (giorni != 0L) {
                val spostate = Servizio(0, Turno(inizioServizio(), inizioServizio(), Profilo.MACCHINISTA), righe = righe.toList())
                    .spostato(giorni, 0).righe
                    .zip(righe) { n, v -> n.copy(
                        partenzaReale = v.partenzaReale?.plusDays(giorni), arrivoReale = v.arrivoReale?.plusDays(giorni)) }
                righe.clear(); righe += spostate
            }
            data = nuova; aggiornaBottoni(); mostraRighe()
        }, data.year, data.monthValue - 1, data.dayOfMonth).show()
    }

    private fun scegliOra(inizio: Boolean) {
        val ora = if (inizio) oraInizio else oraFine
        TimePickerDialog(this, { _, h, m ->
            if (inizio) oraInizio = LocalTime.of(h, m) else oraFine = LocalTime.of(h, m)
            aggiornaBottoni()
        }, ora.hour, ora.minute, true).show()
    }

    private fun aggiornaBottoni() {
        btnData.text = fmtData.format(data)
        btnInizio.text = fmtOra.format(oraInizio)
        btnFine.text = fmtOra.format(oraFine) + if (!oraFine.isAfter(oraInizio)) " (+1)" else ""
    }

    private fun etichetta(t: String) = TextView(this).apply { text = t; setPadding(0, dp(12), 0, 0) }

    private fun colonna(t: String, v: Button) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(etichetta(t)); addView(v)
    }

    private fun durata() = EditText(this).apply {
        inputType = InputType.TYPE_CLASS_TEXT
        hint = "es. 5:30"
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
