package it.ferromicon

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Perimetri di residenza: gruppi di stazioni che contano come "in residenza".
 * Con le stazioni sulle righe treno, l'app calcola da sola le ore fuori residenza del servizio.
 */
class PerimetriActivity : Activity() {

    private lateinit var elenco: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Perimetri di residenza"
        val radice = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(8))
        }
        radice.addView(TextView(this).apply {
            text = "Le stazioni dei perimetri attivi contano come residenza. Quando salvi un servizio con le " +
                "righe treno (da e a) e lasci vuote le ore fuori residenza, l'app le calcola dalla partenza " +
                "della prima riga che esce dal perimetro al rientro."
            textSize = 12f; setTextColor(Stile.secondario)
        })
        radice.addView(Button(this).apply { text = "+ Perimetro"; setOnClickListener { modifica(null) } })
        elenco = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        radice.addView(ScrollView(this).apply { addView(elenco) }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(radice)
    }

    override fun onResume() {
        super.onResume()
        window.decorView.post { Stile.rifinisci(window.decorView) }
        aggiorna()
    }

    private fun aggiorna() {
        elenco.removeAllViews()
        val perimetri = Archivio.caricaTutto(this).perimetri.sortedBy { it.nome.lowercase() }
        if (perimetri.isEmpty()) elenco.addView(TextView(this).apply {
            text = "Nessun perimetro. Aggiungi la tua residenza, per esempio \"Varese\" con la stazione di Varese."
            setTextColor(Stile.secondario)
        })
        perimetri.forEach { p ->
            elenco.addView(TextView(this).apply {
                text = "${p.nome}  ·  ${if (p.attivo) "attivo" else "disattivato"}\n" +
                    "    ${p.stazioni.size} stazioni: ${p.stazioni.joinToString(", ").ifEmpty { "nessuna" }}"
                textSize = 16f
                setPadding(0, dp(10), 0, dp(10))
                setOnClickListener { azioni(p) }
            })
            elenco.addView(View(this).apply { setBackgroundColor(Stile.linea) }, LinearLayout.LayoutParams(-1, 1))
        }
    }

    private fun azioni(p: Perimetro) {
        val opzioni = listOf<Pair<String, () -> Unit>>(
            "Modifica" to { modifica(p) },
            (if (p.attivo) "Disattiva" else "Attiva") to {
                Archivio.salvaPerimetro(this, p.copy(attivo = !p.attivo)); aggiorna()
            },
            "Elimina" to {
                AlertDialog.Builder(this)
                    .setMessage("Eliminare il perimetro ${p.nome}?")
                    .setPositiveButton("Elimina") { _, _ -> Archivio.eliminaPerimetro(this, p.id); aggiorna() }
                    .setNegativeButton("Annulla", null)
                    .show()
            }
        )
        AlertDialog.Builder(this)
            .setTitle(p.nome)
            .setItems(opzioni.map { it.first }.toTypedArray()) { _, i -> opzioni[i].second() }
            .show()
    }

    private fun modifica(esistente: Perimetro?) {
        val nome = EditText(this).apply { hint = "Nome (es. Varese)"; setText(esistente?.nome ?: "") }
        val stazioni = EditText(this).apply {
            hint = "Stazioni, una per riga"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 4
            setText(esistente?.stazioni?.joinToString("\n") ?: "")
        }
        val dallaRubrica = Button(this).apply {
            text = "Aggiungi dalla rubrica stazioni"
            setOnClickListener {
                val nomi = Archivio.caricaTutto(this@PerimetriActivity).stazioni.map { it.nome }.sorted()
                if (nomi.isEmpty()) {
                    android.widget.Toast.makeText(this@PerimetriActivity,
                        "La rubrica è vuota", android.widget.Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val scelte = BooleanArray(nomi.size)
                AlertDialog.Builder(this@PerimetriActivity)
                    .setTitle("Stazioni della rubrica")
                    .setMultiChoiceItems(nomi.toTypedArray(), scelte) { _, i, v -> scelte[i] = v }
                    .setPositiveButton("Aggiungi") { _, _ ->
                        val gia = stazioni.text.toString().lines().map { it.trim() }.filter { it.isNotEmpty() }
                        val nuove = nomi.filterIndexed { i, _ -> scelte[i] }.filter { n -> gia.none { it.equals(n, true) } }
                        stazioni.setText((gia + nuove).joinToString("\n"))
                    }
                    .setNegativeButton("Annulla", null)
                    .show()
            }
        }
        AlertDialog.Builder(this)
            .setTitle(if (esistente == null) "Nuovo perimetro" else "Modifica perimetro")
            .setView(ScrollView(this).apply {
                addView(LinearLayout(this@PerimetriActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(20), dp(8), dp(20), 0)
                    addView(nome); addView(stazioni); addView(dallaRubrica)
                })
            })
            .setPositiveButton("Salva") { _, _ ->
                val n = nome.text.toString().trim()
                val lista = stazioni.text.toString().lines().map { it.trim() }.filter { it.isNotEmpty() }.distinct()
                if (n.isNotEmpty()) {
                    Archivio.salvaPerimetro(this, Perimetro(
                        id = esistente?.id ?: System.currentTimeMillis(),
                        nome = n, stazioni = lista, attivo = esistente?.attivo ?: true
                    ))
                    aggiorna()
                }
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
