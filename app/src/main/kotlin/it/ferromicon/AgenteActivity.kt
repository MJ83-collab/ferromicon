package it.ferromicon

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Calendario di un agente: si caricano i PDF del calendario degli agenti e dei turni, si scrive il CID
 * o il nome, e l'app genera giornate di turno, riposi e periodi di ferie estivo e invernale.
 */
class AgenteActivity : Activity() {

    private val fmt = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ITALIAN)
    private lateinit var contenuto: LinearLayout
    private lateinit var stato: TextView
    private lateinit var ricerca: EditText
    private lateinit var risultati: LinearLayout
    private lateinit var estivo: Spinner
    private lateinit var btnFino: Button
    private lateinit var btnInvDal: Button
    private lateinit var btnInvAl: Button
    private var fino: LocalDate = CambioOrario.ultimoGiornoDelTurno(LocalDate.now())
    private var invDal: LocalDate? = null
    private var invAl: LocalDate? = null
    private var occupato = false
    private var daEsportare = ""
    private var nomeEsportazione = "calendario.ics"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Calendario agente"
        contenuto = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
        }
        setContentView(ScrollView(this).apply { addView(contenuto) })

        contenuto.addView(testo("Carica i PDF dell'impianto, scrivi il CID o il nome dell'agente e genero il suo " +
            "calendario: giornate di turno con orari e treni, riposi e ferie. I PDF restano sul telefono.", 14f))
        // I tre caricamenti stanno in una scheda unica, con titolo e spazio tra i pulsanti.
        val caricamenti = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Stile.scheda(14)
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        caricamenti.addView(titolo("Carica i PDF").apply { setPadding(0, 0, 0, dp(8)) })
        fun pulsante(etichetta: String, richiesta: Int) = Button(this).apply {
            text = etichetta
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp(8)) }
            setOnClickListener { scegliPdf(richiesta) }
        }
        caricamenti.addView(pulsante("1. Calendario degli agenti (PDF)", RICHIESTA_CALENDARIO))
        caricamenti.addView(pulsante("2. Turni del personale (PDF, orari e treni)", RICHIESTA_TURNI))
        caricamenti.addView(pulsante("3. Calendario rotativo dei riposi (PDF)", RICHIESTA_ROTATIVO))
        contenuto.addView(caricamenti, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, dp(8), 0, dp(4)) })
        stato = testo("", 13f)
        contenuto.addView(stato)

        contenuto.addView(titolo("Agente"))
        ricerca = EditText(this).apply {
            hint = "CID (es. 1234567) o cognome"
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setSingleLine(true)
            setOnEditorActionListener { _, _, _ -> cerca(); true }
        }
        contenuto.addView(ricerca)
        contenuto.addView(Button(this).apply { text = "Cerca"; setOnClickListener { cerca() } })
        val mioCid = Archivio.caricaTutto(this).impostazioni.cid
        if (mioCid.isNotBlank()) {
            contenuto.addView(Button(this).apply {
                text = "Genera il mio calendario (CID $mioCid)"
                setOnClickListener { ricerca.setText(mioCid); cerca(true) }
            })
        } else contenuto.addView(testo("Suggerimento: nelle Impostazioni puoi salvare il tuo CID, così trovi il tuo calendario con un tocco.", 12f))
        risultati = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        contenuto.addView(risultati)

        contenuto.addView(titolo("Periodo e ferie"))
        contenuto.addView(testo("Le ferie vanno da riposo a riposo: iniziano dal primo riposo del periodo assegnato " +
            "e finiscono al riposo successivo. Le giornate di lavoro in mezzo diventano ferie.", 13f))
        btnFino = Button(this).apply {
            setOnClickListener {
                DatePickerDialog(this@AgenteActivity, { _, y, m, g -> fino = LocalDate.of(y, m + 1, g); aggiornaTesti() },
                    fino.year, fino.monthValue - 1, fino.dayOfMonth).show()
            }
        }
        contenuto.addView(etichetta("Genera fino al (di norma il cambio turno)")); contenuto.addView(btnFino)

        val estiviOggi = PeriodiFerie.estivi(LocalDate.now().year)
        estivo = Spinner(this).apply {
            adapter = ArrayAdapter(this@AgenteActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("Nessuno") + estiviOggi.sortedBy { it.nome }.map {
                    "${it.nome} (${it.dal.dayOfMonth}/${it.dal.monthValue} – ${it.al.dayOfMonth}/${it.al.monthValue})"
                })
        }
        contenuto.addView(etichetta("Periodo ferie estivo assegnato"))
        contenuto.addView(testo("Date indicative: controllale ogni anno.", 12f))
        contenuto.addView(estivo)

        contenuto.addView(etichetta("Periodo ferie invernale (se scegli la lettera estiva si calcola da solo ogni anno)"))
        btnInvDal = Button(this).apply {
            setOnClickListener {
                val b = invDal ?: LocalDate.now()
                DatePickerDialog(this@AgenteActivity, { _, y, m, g ->
                    val da = LocalDate.of(y, m + 1, g)
                    invDal = da
                    if (invAl?.isBefore(da) != false) invAl = da.plusDays(6)
                    aggiornaTesti()
                },
                    b.year, b.monthValue - 1, b.dayOfMonth).show()
            }
        }
        btnInvAl = Button(this).apply {
            setOnClickListener {
                val b = invAl ?: invDal ?: LocalDate.now()
                DatePickerDialog(this@AgenteActivity, { _, y, m, g -> invAl = LocalDate.of(y, m + 1, g); aggiornaTesti() },
                    b.year, b.monthValue - 1, b.dayOfMonth).show()
            }
        }
        contenuto.addView(btnInvDal); contenuto.addView(btnInvAl)
        contenuto.addView(Button(this).apply {
            text = "Togli il periodo invernale"
            setOnClickListener { invDal = null; invAl = null; aggiornaTesti() }
        })

        contenuto.addView(Button(this).apply {
            text = "Cancella i PDF importati"
            setOnClickListener {
                AlertDialog.Builder(this@AgenteActivity)
                    .setMessage("Cancello dal telefono i calendari e i turni importati (nomi dei colleghi compresi)? L'agenda non cambia.")
                    .setPositiveButton("Cancella") { _, _ -> AgentiArchivio.cancella(this@AgenteActivity); aggiornaTesti() }
                    .setNegativeButton("Annulla", null).show()
            }
        })
        aggiornaTesti()
    }

    override fun onResume() {
        super.onResume()
        window.decorView.post { Stile.rifinisci(window.decorView) }
    }

    private fun aggiornaTesti() {
        btnFino.text = fmt.format(fino)
        btnInvDal.text = "Inizio: " + (invDal?.let { fmt.format(it) } ?: "non impostato")
        btnInvAl.text = "Fine: " + (invAl?.let { fmt.format(it) } ?: "non impostata")
        val cal = AgentiArchivio.caricaCalendari(this)
        val giornate = AgentiArchivio.caricaGiornate(this)
        val rot = AgentiArchivio.caricaRotativo(this)
        stato.text = buildString {
            append(if (cal.isEmpty()) "Calendario agenti: non caricato." else
                "Calendario agenti: ${cal.size} turni, dal ${fmt.format(cal.minOf { it.dal })} al ${fmt.format(cal.maxOf { it.al })}.")
            append("\n")
            append(if (giornate.isEmpty()) "Turni: non caricati (senza, i giorni di servizio non hanno orari)." else
                "Turni: ${giornate.size} giornate di ${giornate.map { it.turno }.distinct().size} turni.")
            append("\n")
            append(if (rot == null) "Rotativo riposi: non caricato (dopo la settimana del PDF i riposi non sono noti)." else
                "Rotativo riposi: ${rot.primo?.let { p -> rot.ultimo?.let { u -> "dal ${fmt.format(p)} al ${fmt.format(u)}" } } ?: "date non leggibili"}.")
        }
    }

    // ---------- importazione dei PDF ----------

    private fun scegliPdf(richiesta: Int) {
        if (occupato) return
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "application/pdf"
        }, richiesta)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) return
        if (requestCode == RICHIESTA_ICS) {
            try {
                contentResolver.openOutputStream(uri)?.use { it.write(daEsportare.toByteArray()) }
                AlertDialog.Builder(this).setTitle("Calendario esportato")
                    .setMessage("Il file $nomeEsportazione è stato salvato dove hai scelto (anche su Google Drive, se l'hai selezionato). " +
                        "Aprilo da Drive o dal telefono per aggiungerlo a Google Calendar.")
                    .setPositiveButton("OK", null).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Esportazione non riuscita: ${e.message}", Toast.LENGTH_LONG).show()
            }
            return
        }
        if (requestCode != RICHIESTA_CALENDARIO && requestCode != RICHIESTA_TURNI && requestCode != RICHIESTA_ROTATIVO) return
        occupato = true
        stato.text = "Lettura del PDF in corso…"
        Thread {
            val esito = runCatching {
                if (requestCode == RICHIESTA_ROTATIVO) {
                    val pagine = ArrayList<List<Pezzo>>()
                    PdfTesto.scorri(this, uri, { p, t -> runOnUiThread { stato.text = "Lettura pagina $p di $t…" } }) { _, pezzi -> pagine += pezzi }
                    val rot = RotativoRiposi.leggi(pagine)
                    if (rot == null) "Non ho riconosciuto un calendario rotativo in questo PDF." else {
                        AgentiArchivio.salvaRotativo(this, rot)
                        "Rotativo letto: ${rot.primo?.let { p -> rot.ultimo?.let { u -> "dal ${fmt.format(p)} al ${fmt.format(u)}" } } ?: "date non leggibili"}."
                    }
                } else if (requestCode == RICHIESTA_CALENDARIO) {
                    val pagine = ArrayList<List<Pezzo>>()
                    PdfTesto.scorri(this, uri, { p, t -> runOnUiThread { stato.text = "Lettura pagina $p di $t…" } }) { _, pezzi -> pagine += pezzi }
                    val cal = CalendarioAgenti.leggi(pagine)
                    if (cal.isEmpty()) "Non ho riconosciuto un calendario degli agenti in questo PDF." else {
                        AgentiArchivio.salvaCalendari(this, cal)
                        "Calendario letto: ${cal.size} turni, ${cal.sumOf { it.righe.size }} agenti."
                    }
                } else {
                    val giornate = ArrayList<GiornataDefinita>()
                    PdfTesto.scorri(this, uri, { p, t -> if (p % 5 == 0) runOnUiThread { stato.text = "Lettura pagina $p di $t…" } }) { _, pezzi ->
                        giornate += TurniPdf.leggiPagina(pezzi)
                    }
                    if (giornate.isEmpty()) "Non ho riconosciuto giornate di turno in questo PDF." else {
                        AgentiArchivio.salvaGiornate(this, giornate)
                        "Turni letti: ${giornate.size} giornate di ${giornate.map { it.turno }.distinct().size} turni."
                    }
                }
            }.getOrElse { "Errore nella lettura: ${it.message ?: it.javaClass.simpleName}" }
            runOnUiThread { occupato = false; aggiornaTesti(); Toast.makeText(this, esito, Toast.LENGTH_LONG).show() }
        }.start()
    }

    // ---------- ricerca e generazione ----------

    private fun cerca(automatico: Boolean = false) {
        risultati.removeAllViews()
        val calendari = AgentiArchivio.caricaCalendari(this)
        if (calendari.isEmpty()) { risultati.addView(testo("Carica prima il calendario degli agenti.", 14f)); return }
        val trovati = CalendarioAgenti.cerca(calendari, ricerca.text.toString())
        if (trovati.isEmpty()) { risultati.addView(testo("Nessun agente trovato.", 14f)); return }
        if (automatico && trovati.size == 1) { genera(trovati[0]); return }
        trovati.take(30).forEach { t ->
            risultati.addView(Button(this).apply {
                isAllCaps = false
                text = "${t.riga.nome} · ${t.riga.cid}\n${t.calendario.turno} · posizione ${t.riga.posizione}"
                setOnClickListener { genera(t) }
            })
        }
        if (trovati.size > 30) risultati.addView(testo("Altri ${trovati.size - 30} risultati: scrivi più lettere.", 12f))
        Stile.rifinisci(risultati)
    }

    private fun ferieScelte(cal: CalendarioTurnoAgenti, da: LocalDate): List<PeriodoFerie> {
        val lista = ArrayList<PeriodoFerie>()
        val sel = estivo.selectedItemPosition
        if (sel > 0) {
            // La lettera scelta vale per l'anno base (2026); negli anni successivi scorre nella sequenza.
            val base = PeriodiFerie.estivi(PeriodiFerie.ANNO_BASE).sortedBy { it.nome }[sel - 1].nome
                .substringAfter("Estivo ").trim()
            for (anno in da.year..fino.year) {
                PeriodiFerie.estivoPerAnno(base, anno)?.let {
                    if (!it.al.isBefore(da) && !it.dal.isAfter(fino)) lista += it
                }
            }
            for (stagione in (da.year - 1)..fino.year) {
                // Date invernali da FerieInvernali: senza tabella dell'anno non compare nulla.
                val lettera = FerieInvernali.letteraPerAnno(base.first(), stagione)
                FerieInvernali.invernale(lettera, stagione)?.let {
                    val p = PeriodoFerie("Invernale ${it.lettera} (${it.nome})", it.da, it.a)
                    if (!p.al.isBefore(da) && !p.dal.isAfter(fino)) lista += p
                }
            }
        } else {
            val a = invDal; val b = invAl
            if (a != null && b != null && !b.isBefore(a)) lista += PeriodoFerie("Invernale", a, b)
        }
        return lista
    }

    private fun genera(t: CalendarioAgenti.Trovato) {
        val cal = t.calendario
        val oggi = LocalDate.now()
        val da = if (oggi.isAfter(cal.dal)) oggi else cal.dal
        val giornate = AgentiArchivio.caricaGiornate(this)
        val ferie = ferieScelte(cal, da)
        val gen = CalendarioAgente.genera(cal, t.riga, giornate, da, fino, ferie, System.currentTimeMillis(),
            AgentiArchivio.caricaRotativo(this))

        val messaggio = buildString {
            append("${t.riga.nome} (${t.riga.cid}) · turno ${cal.turno}\n")
            append("Dal ${fmt.format(da)} al ${fmt.format(gen.finoA)}:\n")
            append("• ${gen.servizi.size} giornate di servizio\n")
            append("• ${gen.assenze.count { it.tipo.riposo }} giorni di riposo\n")
            append("• ${gen.assenze.count { it.tipo == TipoAssenza.DISPONIBILITA }} disponibilità\n")
            if (gen.giorniFerie > 0) append("• ${gen.giorniFerie} giorni di ferie (${ferie.joinToString { it.nome }})\n")
            if (ferie.isNotEmpty() && gen.giorniFerie == 0)
                append("• Nessun giorno di ferie: nel periodo scelto non trovo un riposo.\n")
            if (gen.finoA.isBefore(fino))
                append("\n⚠ Arrivo solo fino al ${fmt.format(gen.finoA)}: " +
                    if (cal.estendibile) "il ciclo non copre oltre." else "il ciclo di questo turno non è ricostruibile, ho solo la settimana del PDF.")
            else if (gen.estesoOltreLaSettimana)
                append("\nOltre la settimana del PDF è una previsione dal ciclo del turno: se un giorno cambia, correggilo in agenda.")
            val finoTurno = CambioOrario.ultimoGiornoDelTurno(da)
            if (gen.finoA.isAfter(finoTurno))
                append("\n⚠ Il turno attuale finisce il ${fmt.format(finoTurno)} (cambio orario): dopo quella data " +
                    "le giornate sono solo una previsione e cambieranno con il nuovo turno.")
            if (gen.senzaDettaglio.isNotEmpty())
                append("\n\n⚠ Senza orari (carica il PDF dei turni): ${gen.senzaDettaglio.take(8).joinToString()}" +
                    if (gen.senzaDettaglio.size > 8) "…" else "")
        }
        AlertDialog.Builder(this).setTitle("Calendario generato").setMessage(messaggio)
            .setPositiveButton("Inserisci in agenda") { _, _ -> inserisci(gen, cal, t.riga) }
            .setNeutralButton("Esporta .ics") { _, _ ->
                daEsportare = CalendarioIcs.genera(Dati(gen.servizi, gen.assenze))
                nomeEsportazione = "calendario-${t.riga.cid}.ics"
                startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE); type = "text/calendar"
                    putExtra(Intent.EXTRA_TITLE, nomeEsportazione)
                }, RICHIESTA_ICS)
            }
            .setNegativeButton("Annulla", null).show()
    }

    private fun inserisci(gen: CalendarioGenerato, cal: CalendarioTurnoAgenti, riga: RigaAgente) {
        val d = Archivio.caricaTutto(this)
        val giorni = (gen.servizi.map { it.turno.inizio.toLocalDate() } + gen.assenze.map { it.data }).toSet()
        // I giorni con servizi inseriti a mano restano com'erano; i servizi generati prima si rigenerano.
        val manuali = d.servizi.filter { it.origineTurno.isEmpty() && it.turno.inizio.toLocalDate() in giorni }
            .map { it.turno.inizio.toLocalDate() }.toSet()
        val vecchi = d.servizi.filter { it.origineTurno.isNotEmpty() && it.turno.inizio.toLocalDate() in giorni }.toSet()
        val nuoviS = gen.servizi.filter { it.turno.inizio.toLocalDate() !in manuali }
        val nuoveA = gen.assenze.filter { it.data !in manuali }
        Archivio.copiaDiSicurezza(this)
        var imp = d.impostazioni
        if (imp.profilo == null)
            imp = imp.copy(profilo = if (cal.profilo.equals("Condotta", true)) Profilo.MACCHINISTA else Profilo.CAPOTRENO)
        // Se si è cercato per CID e non ce n'è uno salvato, lo ricordo come il CID dell'utente.
        if (imp.cid.isBlank() && ricerca.text.toString().trim() == riga.cid) imp = imp.copy(cid = riga.cid)
        Archivio.salvaTutto(this, d.copy(
            servizi = (d.servizi - vecchi + nuoviS).sortedBy { it.turno.inizio },
            assenze = (d.assenze.filterNot { it.data in giorni && it.data !in manuali } + nuoveA).sortedBy { it.data },
            impostazioni = imp
        ))
        Toast.makeText(this, "Calendario inserito: ${nuoviS.size} servizi, ${nuoveA.size} giorni" +
            if (manuali.isNotEmpty()) " (${manuali.size} giorni tuoi lasciati com'erano)" else "", Toast.LENGTH_LONG).show()
        AlertDialog.Builder(this).setTitle("Calendario inserito")
            .setMessage("Vuoi anche scriverlo nel calendario del telefono (Google Calendar)?")
            .setPositiveButton("Sì, sincronizza") { _, _ ->
                startActivity(Intent(this, CalendarioTelefonoActivity::class.java)); finish()
            }
            .setNegativeButton("No, grazie") { _, _ -> finish() }
            .setCancelable(false).show()
    }

    // ---------- piccoli aiuti ----------

    private fun testo(t: String, size: Float) = TextView(this).apply {
        text = t; textSize = size; setTextColor(Stile.testo); setPadding(0, dp(6), 0, dp(2))
    }

    private fun titolo(t: String) = TextView(this).apply {
        text = "▶ $t"; textSize = 17f; setTypeface(typeface, Typeface.BOLD); setTextColor(Stile.testo)
        setPadding(0, dp(20), 0, dp(4))
    }

    private fun etichetta(t: String) = TextView(this).apply {
        text = t; textSize = 15f; setTextColor(Stile.testo); setPadding(0, dp(12), 0, 0)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val RICHIESTA_CALENDARIO = 41
        private const val RICHIESTA_TURNI = 42
        private const val RICHIESTA_ICS = 43
        private const val RICHIESTA_ROTATIVO = 44
    }
}
