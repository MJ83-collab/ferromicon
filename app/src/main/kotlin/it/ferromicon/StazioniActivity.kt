package it.ferromicon

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Rubrica personale delle stazioni: nome, sigla, telefono (DM, deposito, biglietteria) e note.
 * I nomi vengono suggeriti quando si inseriscono le righe treno.
 */
class StazioniActivity : Activity() {

    private lateinit var elenco: LinearLayout
    private lateinit var filtro: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Rubrica stazioni"
        val radice = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(8))
        }
        filtro = EditText(this).apply { hint = "Cerca per nome o sigla" }
        filtro.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(e: Editable?) = aggiorna()
            override fun beforeTextChanged(t: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(t: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        radice.addView(filtro)
        radice.addView(Button(this).apply { text = "+ Stazione"; setOnClickListener { modifica(null) } })
        elenco = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        radice.addView(ScrollView(this).apply { addView(elenco) }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(radice)
        aggiorna()
    }

    private fun aggiorna() {
        elenco.removeAllViews()
        val cerca = filtro.text.toString().trim().lowercase()
        val stazioni = Archivio.caricaTutto(this).stazioni
            .filter { cerca.isEmpty() || it.nome.lowercase().contains(cerca) || it.sigla.lowercase().contains(cerca) }
            .sortedBy { it.nome.lowercase() }
        if (stazioni.isEmpty()) elenco.addView(TextView(this).apply {
            text = if (cerca.isEmpty()) "Nessuna stazione. Aggiungi quelle che usi, con i numeri di telefono utili."
                else "Nessuna stazione trovata."
            setTextColor(Stile.secondario)
        })
        stazioni.forEach { s ->
            elenco.addView(TextView(this).apply {
                text = buildString {
                    append(s.nome)
                    if (s.sigla.isNotBlank()) append("  (${s.sigla})")
                    if (s.telefono.isNotBlank()) append("\n    📞 ${s.telefono}")
                    if (s.note.isNotBlank()) append("\n    ${s.note}")
                }
                textSize = 16f
                setPadding(0, dp(10), 0, dp(10))
                setOnClickListener { azioni(s) }
            })
            elenco.addView(View(this).apply { setBackgroundColor(Stile.linea) }, LinearLayout.LayoutParams(-1, 1))
        }
    }

    private fun azioni(s: Stazione) {
        val opzioni = mutableListOf<Pair<String, () -> Unit>>()
        if (s.telefono.isNotBlank()) opzioni += "Chiama ${s.telefono}" to {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + s.telefono.filter { it.isDigit() || it == '+' })))
        }
        opzioni += "Modifica" to { modifica(s) }
        opzioni += "Elimina" to {
            AlertDialog.Builder(this)
                .setMessage("Eliminare ${s.nome} dalla rubrica?")
                .setPositiveButton("Elimina") { _, _ -> Archivio.eliminaStazione(this, s.id); aggiorna() }
                .setNegativeButton("Annulla", null)
                .show()
        }
        AlertDialog.Builder(this)
            .setTitle(s.nome)
            .setItems(opzioni.map { it.first }.toTypedArray()) { _, i -> opzioni[i].second() }
            .show()
    }

    private fun modifica(esistente: Stazione?) {
        val nome = EditText(this).apply { hint = "Nome (es. Milano Cadorna)"; setText(esistente?.nome ?: "") }
        val sigla = EditText(this).apply { hint = "Sigla (facoltativa)"; setText(esistente?.sigla ?: "") }
        val telefono = EditText(this).apply {
            hint = "Telefono (DM, deposito…)"; inputType = InputType.TYPE_CLASS_PHONE; setText(esistente?.telefono ?: "")
        }
        val note = EditText(this).apply { hint = "Note"; setText(esistente?.note ?: "") }
        AlertDialog.Builder(this)
            .setTitle(if (esistente == null) "Nuova stazione" else "Modifica stazione")
            .setView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(20), dp(8), dp(20), 0)
                addView(nome); addView(sigla); addView(telefono); addView(note)
            })
            .setPositiveButton("Salva") { _, _ ->
                val n = nome.text.toString().trim()
                if (n.isNotEmpty()) {
                    Archivio.salvaStazione(this, Stazione(
                        id = esistente?.id ?: System.currentTimeMillis(),
                        nome = n,
                        sigla = sigla.text.toString().trim().uppercase(),
                        telefono = telefono.text.toString().trim(),
                        note = note.text.toString().trim()
                    ))
                    aggiorna()
                }
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        window.decorView.post { Stile.rifinisci(window.decorView) }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
