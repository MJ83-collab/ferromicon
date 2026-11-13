package it.ferromicon

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
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
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Impostazioni personali: anzianità, livello, ferie e promemoria. */
class ImpostazioniActivity : Activity() {

    private val opzioniPromemoria = listOf(0, 30, 60, 90, 120, 180)
    private val fmt = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ITALIAN)
    private var assunzione: LocalDate? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Impostazioni"
        val imp = Archivio.caricaTutto(this).impostazioni
        assunzione = imp.dataAssunzione

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        val profiloScelto = Spinner(this).apply {
            adapter = ArrayAdapter(this@ImpostazioniActivity,
                android.R.layout.simple_spinner_dropdown_item, Profilo.values().map { it.etichetta })
            setSelection(imp.profiloAttivo.ordinal)
        }
        val nomeCampo = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            hint = "Nome e cognome"; setText(imp.nome)
        }
        val impiantoScelto = Spinner(this).apply {
            adapter = ArrayAdapter(this@ImpostazioniActivity,
                android.R.layout.simple_spinner_dropdown_item, listOf("Non impostato") + Impianti.tutti)
            setSelection(Impianti.tutti.indexOf(imp.impianto).let { if (it < 0) 0 else it + 1 })
        }
        col.addView(etichetta("Il mio nome"))
        col.addView(nota("Dà il nome al tuo profilo: compare nell'agenda e nei calendari che esporti."))
        col.addView(nomeCampo)
        col.addView(etichetta("Impianto di appartenenza")); col.addView(impiantoScelto)
        if (imp.cid.isNotBlank()) {
            col.addView(nota("CID ${imp.cid}" + if (imp.impianto.isNotBlank()) " · ${imp.impianto}" else ""))
            col.addView(Button(this).apply {
                text = "Controlla CID e impianto"
                setOnClickListener {
                    AlertDialog.Builder(this@ImpostazioniActivity)
                        .setTitle("CID e impianto sono giusti?")
                        .setMessage("CID ${imp.cid}" + if (imp.impianto.isNotBlank()) "\nImpianto attuale: ${imp.impianto}" else "")
                        .setPositiveButton("Sì, è giusto", null)
                        .setNegativeButton("Modifica impianto") { _, _ -> impiantoScelto.performClick() }
                        .show()
                }
            })
        }
        col.addView(android.widget.Button(this).apply {
            text = "Informazioni e responsabilità"
            setOnClickListener {
                startActivity(Intent(this@ImpostazioniActivity, BenvenutoActivity::class.java)
                    .putExtra(BenvenutoActivity.EXTRA_INFO, true))
            }
        })
        col.addView(etichetta("Il mio profilo"))
        col.addView(nota("Macchinista o capo treno: decide limiti di prestazione, condotta o scorta e indennità."))
        col.addView(profiloScelto)
        val cidCampo = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER; hint = "Es. 1234567"; setText(imp.cid)
        }
        col.addView(etichetta("Il mio CID"))
        col.addView(nota("La tua matricola: serve a trovare il tuo calendario nei PDF dell'impianto."))
        col.addView(cidCampo)

        val btnAssunzione = Button(this)
        fun aggiornaAssunzione() {
            btnAssunzione.text = assunzione?.let { fmt.format(it) } ?: "Non impostata"
        }
        btnAssunzione.setOnClickListener {
            val d = assunzione ?: LocalDate.now().minusYears(5)
            DatePickerDialog(this, { _, y, m, g ->
                assunzione = LocalDate.of(y, m + 1, g); aggiornaAssunzione()
            }, d.year, d.monthValue - 1, d.dayOfMonth).show()
        }
        aggiornaAssunzione()
        col.addView(etichetta("Data di assunzione"))
        col.addView(nota("Serve per ferie (oltre 8 anni: 25 giorni) e scatti di anzianità."))
        col.addView(btnAssunzione)

        val livello = Spinner(this).apply {
            adapter = ArrayAdapter(this@ImpostazioniActivity,
                android.R.layout.simple_spinner_dropdown_item, listOf("Non impostato") + Livello.values().map { it.name })
            setSelection((imp.livello?.ordinal ?: -1) + 1)
        }
        col.addView(etichetta("Livello / posizione retributiva"))
        col.addView(nota("Lo trovi in busta paga. Serve solo per stimare l'indennità festiva."))
        col.addView(livello)

        val adPersonam = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(if (imp.adPersonam == 0.0) "" else imp.adPersonam.toString())
            hint = "0"
        }
        col.addView(etichetta("Assegno ad personam mensile (€)")); col.addView(adPersonam)

        val seiGiorni = CheckBox(this).apply {
            text = "Orario settimanale su 6 giorni (ferie 24/29 invece di 20/25)"
            isChecked = imp.orarioSuSeiGiorni
        }
        col.addView(seiGiorni)

        val ferieResidue = numero(imp.ferieResidueAnnoPrecedente)
        col.addView(etichetta("Ferie residue dell'anno precedente"))
        col.addView(nota("Vanno godute entro il 30 settembre (CCNL art. 30.4)."))
        col.addView(ferieResidue)

        val permessi = numero(imp.permessiAggiuntivi)
        col.addView(etichetta("Permessi retribuiti aggiuntivi nell'anno"))
        col.addView(nota("Da 1 a 3 per il personale ex FS (Altarail art. 20). Lascia 0 se non ne hai."))
        col.addView(permessi)

        val parentali = numero(imp.parentaliSpettanti)
        col.addView(etichetta("Giorni parentali spettanti nell'anno"))
        col.addView(nota("Il riepilogo mostra quanti ne hai presi e quanti ne restano. Lascia 0 se non lo sai."))
        col.addView(parentali)

        val permessiSpettantiCampo = numero(imp.permessiSpettanti)
        col.addView(etichetta("Giorni di permesso (ex festività e altri) spettanti nell'anno"))
        col.addView(nota("La home e il widget mostrano quanti ne hai presi e quanti restano. Lascia 0 se non lo sai."))
        col.addView(permessiSpettantiCampo)

        val promemoria = Spinner(this).apply {
            adapter = ArrayAdapter(this@ImpostazioniActivity, android.R.layout.simple_spinner_dropdown_item,
                opzioniPromemoria.map { if (it == 0) "Disattivato" else "$it minuti prima" })
            setSelection(opzioniPromemoria.indexOf(imp.promemoriaMinuti).coerceAtLeast(0))
        }
        col.addView(etichetta("Promemoria prima del servizio")); col.addView(promemoria)

        val avvisoRecupero = CheckBox(this).apply {
            text = "Avviso per il recupero festività"
            isChecked = imp.avvisoRecuperoAttivo
        }
        val giorniRecupero = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(imp.giorniAvvisoRecupero.toString())
            hint = "7"
        }
        col.addView(etichetta("Recupero festività"))
        col.addView(nota("Ti avvisa ogni mattina alle 9 quando la scadenza più vicina è entro il numero di giorni indicato."))
        col.addView(avvisoRecupero)
        col.addView(TextView(this).apply { text = "Giorni prima della scadenza (1–30)"; textSize = 12f })
        col.addView(giorniRecupero)

        col.addView(etichetta("Copia di sicurezza"))
        col.addView(nota("Alla prima apertura di una nuova versione salva i dati in questa cartella. " +
            "Dopo una reinstallazione propone il ripristino dalla copia."))
        col.addView(Button(this).apply {
            text = if (CopiaSicurezza.cartella(this@ImpostazioniActivity) == null) "Scegli cartella" else "Cambia cartella"
            setOnClickListener { CopiaSicurezza.scegliCartella(this@ImpostazioniActivity) }
        })

        // Aspetto: chiaro, scuro o come il telefono (la scelta manuale richiede Android 12 o successivo)
        col.addView(etichetta("Aspetto"))
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            val modi = getSystemService(android.app.UiModeManager::class.java)
            val memoria = getSharedPreferences("aspetto", MODE_PRIVATE)
            val valori = listOf(
                android.app.UiModeManager.MODE_NIGHT_AUTO to "Come il telefono",
                android.app.UiModeManager.MODE_NIGHT_NO to "Chiaro",
                android.app.UiModeManager.MODE_NIGHT_YES to "Scuro"
            )
            col.addView(Spinner(this).apply {
                adapter = ArrayAdapter(this@ImpostazioniActivity, android.R.layout.simple_spinner_dropdown_item,
                    valori.map { it.second })
                setSelection(valori.indexOfFirst { it.first == memoria.getInt("modo", android.app.UiModeManager.MODE_NIGHT_AUTO) }.coerceAtLeast(0))
                onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(p: android.widget.AdapterView<*>?, v: android.view.View?, pos: Int, id: Long) {
                        if (valori[pos].first != memoria.getInt("modo", android.app.UiModeManager.MODE_NIGHT_AUTO)) {
                            memoria.edit().putInt("modo", valori[pos].first).apply()
                            modi.setApplicationNightMode(valori[pos].first)
                        }
                    }
                    override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
                }
            })
        } else {
            col.addView(nota("L'app segue il tema chiaro o scuro del telefono."))
        }

        val profiloOrario = Spinner(this).apply {
            adapter = ArrayAdapter(this@ImpostazioniActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("Contratto Altarail (limiti standard)", "Personalizzato"))
            setSelection(if (imp.limitiOrario == null) 0 else 1)
        }
        val diurno = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = "Ore massime di giorno (es. 8,5)"
            setText(minutiAOre(imp.limitiOrario?.diurnoMinuti ?: 510))
        }
        val notturno = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = "Ore massime di notte (es. 7)"
            setText(minutiAOre(imp.limitiOrario?.notturnoMinuti ?: 420))
        }
        col.addView(etichetta("Profilo orario"))
        col.addView(nota("Il contratto Altarail prevede 8 h 30' (macchina) o 10 h (bordo) di giorno, 7 h o 8 h di notte. " +
            "Con il profilo personalizzato i controlli di durata usano i tuoi limiti, uguali per tutti i servizi."))
        col.addView(profiloOrario); col.addView(diurno); col.addView(notturno)

        val reddito = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = "Reddito imponibile annuo (es. 32000)"
            if (imp.redditoAnnuo > 0) setText(imp.redditoAnnuo.toString().removeSuffix(".0"))
        }
        val inpsPct = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = "INPS a carico del lavoratore, % (es. 9,19)"
            setText(imp.inpsPct.toString().replace('.', ','))
        }
        val comunale = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = "Addizionale comunale, % (es. 0,8)"
            setText(imp.addComunalePct.toString().replace('.', ','))
        }
        col.addView(etichetta("Stima del netto"))
        col.addView(nota("Serve a calcolare quanto resta in tasca di straordinario e competenze variabili. " +
            "Il reddito imponibile lo trovi nel CU o nel cedolino di dicembre; le percentuali nel cedolino."))
        col.addView(reddito); col.addView(inpsPct); col.addView(comunale)

        col.addView(Button(this).apply {
            text = "Salva"
            setOnClickListener {
                Archivio.salvaImpostazioni(this@ImpostazioniActivity, imp.copy(
                    profilo = Profilo.values()[profiloScelto.selectedItemPosition],
                    impianto = if (impiantoScelto.selectedItemPosition == 0) "" else Impianti.tutti[impiantoScelto.selectedItemPosition - 1],
                    cid = cidCampo.text.toString().trim(),
                    nome = nomeCampo.text.toString().trim(),
                    dataAssunzione = assunzione,
                    livello = livello.selectedItemPosition.let { if (it == 0) null else Livello.values()[it - 1] },
                    adPersonam = adPersonam.text.toString().replace(',', '.').toDoubleOrNull() ?: 0.0,
                    orarioSuSeiGiorni = seiGiorni.isChecked,
                    ferieResidueAnnoPrecedente = ferieResidue.text.toString().toIntOrNull() ?: 0,
                    permessiAggiuntivi = permessi.text.toString().toIntOrNull() ?: 0,
                    parentaliSpettanti = parentali.text.toString().toIntOrNull() ?: 0,
                    permessiSpettanti = permessiSpettantiCampo.text.toString().toIntOrNull() ?: 0,
                    promemoriaMinuti = opzioniPromemoria[promemoria.selectedItemPosition],
                    avvisoRecuperoAttivo = avvisoRecupero.isChecked,
                    giorniAvvisoRecupero = giorniRecupero.text.toString().toIntOrNull()?.coerceIn(1, 30) ?: 7,
                    redditoAnnuo = reddito.text.toString().replace(',', '.').toDoubleOrNull() ?: 0.0,
                    inpsPct = inpsPct.text.toString().replace(',', '.').toDoubleOrNull() ?: 9.19,
                    addComunalePct = comunale.text.toString().replace(',', '.').toDoubleOrNull() ?: 0.8,
                    limitiOrario = if (profiloOrario.selectedItemPosition == 0) null else LimitiOrario(
                        diurnoMinuti = oreAMinuti(diurno.text.toString(), 510),
                        notturnoMinuti = oreAMinuti(notturno.text.toString(), 420)
                    )
                ))
                Toast.makeText(this@ImpostazioniActivity,
                    if (Profilo.values()[profiloScelto.selectedItemPosition] != imp.profiloAttivo)
                        "Profilo cambiato: i servizi già in agenda restano invariati" else "Impostazioni salvate",
                    Toast.LENGTH_SHORT).show()
                finish()
            }
        })

        col.addView(Button(this).apply {
            text = "Licenze open source"
            setOnClickListener { startActivity(Intent(this@ImpostazioniActivity, LicenzeActivity::class.java)) }
        })

        setContentView(ScrollView(this).apply { addView(col) })
    }

    private fun minutiAOre(min: Int) = (min / 60.0).toString().removeSuffix(".0").replace('.', ',')

    private fun oreAMinuti(testo: String, predefinito: Int): Int =
        testo.replace(',', '.').toDoubleOrNull()?.let { Math.round(it * 60).toInt() }?.takeIf { it in 60..24 * 60 }
            ?: predefinito

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data ?: return
        if (resultCode == RESULT_OK && requestCode == CopiaSicurezza.RICHIESTA_CARTELLA) {
            CopiaSicurezza.dopoSceltaCartella(this, uri)
        }
    }

    private fun etichetta(t: String) = TextView(this).apply {
        text = t; textSize = 16f; setPadding(0, dp(16), 0, 0)
    }

    private fun nota(t: String) = TextView(this).apply { text = t; textSize = 12f }

    private fun numero(valore: Int) = EditText(this).apply {
        inputType = InputType.TYPE_CLASS_NUMBER
        setText(if (valore == 0) "" else valore.toString())
        hint = "0"
    }

    override fun onResume() {
        super.onResume()
        window.decorView.post { Stile.rifinisci(window.decorView) }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
