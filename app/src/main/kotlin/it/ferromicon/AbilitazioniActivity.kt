package it.ferromicon

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Abilitazioni del macchinista: linee e tipi di treno (materiale rotabile) per cui è abilitato. */
class AbilitazioniActivity : Activity() {

    private val linee = LinkedHashMap<String, CheckBox>()
    private val macchine = LinkedHashMap<String, CheckBox>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Abilitazioni"
        val imp = Archivio.caricaTutto(this).impostazioni
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        col.addView(TextView(this).apply {
            text = "Spunta le linee e i tipi di treno per cui sei abilitato. Puoi aggiungerne di nuovi."
            textSize = 14f
        })
        sezione(col, "Tipi di treno (macchine)", MACCHINE_BASE, imp.abilitazioniMacchine, macchine)
        sezione(col, "Linee", emptyList(), imp.abilitazioniLinee, linee)
        col.addView(Button(this).apply { text = "Salva"; setOnClickListener { salva() } })
        setContentView(ScrollView(this).apply { addView(col) })
    }

    private fun sezione(
        col: LinearLayout, titolo: String, base: List<String>, scelte: List<String>,
        mappa: MutableMap<String, CheckBox>
    ) {
        col.addView(TextView(this).apply { text = titolo; textSize = 17f; setPadding(0, dp(18), 0, dp(6)) })
        val contenitore = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(contenitore)
        (base + scelte).distinct().forEach { aggiungiVoce(contenitore, it, it in scelte, mappa) }
        val campo = EditText(this).apply { hint = "Nuovo"; inputType = InputType.TYPE_CLASS_TEXT; maxLines = 1 }
        col.addView(campo)
        col.addView(Button(this).apply {
            text = "Aggiungi"
            setOnClickListener {
                val nome = campo.text.toString().trim()
                if (nome.isEmpty()) return@setOnClickListener
                if (mappa.containsKey(nome)) {
                    Toast.makeText(this@AbilitazioniActivity, "\"$nome\" è già nell'elenco", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                aggiungiVoce(contenitore, nome, true, mappa)
                campo.setText("")
            }
        })
    }

    private fun aggiungiVoce(
        contenitore: LinearLayout, nome: String, spuntata: Boolean, mappa: MutableMap<String, CheckBox>
    ) {
        val box = CheckBox(this).apply { text = nome; isChecked = spuntata; textSize = 16f }
        mappa[nome] = box
        contenitore.addView(box, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun salva() {
        val imp = Archivio.caricaTutto(this).impostazioni
        Archivio.salvaImpostazioni(this, imp.copy(
            abilitazioniLinee = linee.filterValues { it.isChecked }.keys.toList(),
            abilitazioniMacchine = macchine.filterValues { it.isChecked }.keys.toList()
        ))
        Toast.makeText(this, "Abilitazioni salvate", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        /** Tipi di treno già noti; se ne servono altri si aggiungono dalla schermata. */
        private val MACCHINE_BASE = listOf("TSR", "Coradia", "Caravaggio", "Vivalto")
    }
}
