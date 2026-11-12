package it.ferromicon

import android.app.Activity
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import java.time.LocalDate

/**
 * Quanto rende davvero lo straordinario: lordo orario (paga oraria + maggiorazione) e netto stimato
 * con IRPEF, addizionali e INPS, partendo dal reddito imponibile annuo delle impostazioni.
 */
class StraordinarioActivity : Activity() {

    private data class Tipo(val nome: String, val magg: Double)
    private val tipi = listOf(
        Tipo("Feriale diurno (+18%)", 0.18),
        Tipo("Feriale notturno o festivo diurno (+35%)", 0.35),
        Tipo("Festivo notturno (+50%)", 0.50)
    )

    private lateinit var risultato: TextView
    private lateinit var ore: EditText
    private lateinit var tipo: Spinner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Straordinario netto"
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        col.addView(TextView(this).apply {
            text = "Stima indicativa: parte dal reddito imponibile annuo e dalle percentuali che imposti in Impostazioni → Stima del netto."
            textSize = 12f; setTextColor(Stile.secondario)
        })
        col.addView(label("Ore di straordinario nell'anno"))
        ore = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText("50")
        }
        col.addView(ore)
        col.addView(label("Tipo di straordinario"))
        tipo = Spinner(this).apply {
            adapter = ArrayAdapter(this@StraordinarioActivity, android.R.layout.simple_spinner_dropdown_item, tipi.map { it.nome })
        }
        col.addView(tipo)
        risultato = TextView(this).apply { textSize = 16f; setPadding(0, dp(16), 0, 0) }
        col.addView(risultato)
        setContentView(ScrollView(this).apply { addView(col) })

        ore.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = calcola()
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        tipo.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) = calcola()
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        calcola()
    }

    private fun calcola() {
        val dati = Archivio.caricaTutto(this)
        val imp = dati.impostazioni
        val profilo = dati.servizi.maxByOrNull { it.turno.inizio }?.turno?.profilo ?: Profilo.MACCHINISTA
        val oggi = LocalDate.now()
        val t = tipi[tipo.selectedItemPosition.coerceAtLeast(0)]
        val h = ore.text.toString().replace(',', '.').toDoubleOrNull() ?: 0.0
        if (imp.livello == null) {
            risultato.text = "Per calcolare la paga oraria imposta il livello in Impostazioni."
            return
        }
        val oraria = Competenze.pagaOraria(imp, profilo, oggi)
        val lordoOra = oraria * (1 + t.magg)
        if (imp.redditoAnnuo <= 0) {
            risultato.text = "Lordo per un'ora: ${Competenze.euro(lordoOra)}\n\n" +
                "Per vedere il netto inserisci il reddito imponibile annuo in Impostazioni → Stima del netto."
            return
        }
        val p = imp.parametriFisco()
        val una = Fisco.nettoExtra(imp.redditoAnnuo, lordoOra, oggi.year, p)
        val tot = Fisco.nettoExtra(imp.redditoAnnuo, lordoOra * h, oggi.year, p)
        val marginale = Fisco.marginale(imp.redditoAnnuo, oggi.year, p)
        risultato.text = buildString {
            appendLine("Un'ora: ${Competenze.euro(lordoOra)} lordi → ${Competenze.euro(una.netto)} netti")
            appendLine("Aliquota marginale effettiva: ${"%.0f".format(marginale * 100)}%")
            appendLine()
            appendLine("${h.toString().removeSuffix(".0")} ore, ${t.nome.lowercase()}:")
            appendLine("  Lordo ${Competenze.euro(tot.lordo)}")
            appendLine("  INPS −${Competenze.euro(tot.inps)}")
            appendLine("  Imposte −${Competenze.euro(tot.tasse)}")
            append("  Netto ${Competenze.euro(tot.netto)}")
            if (tot.lordo > 0) append("  (${"%.0f".format(tot.netto / tot.lordo * 100)}% del lordo)")
        }
    }

    private fun label(t: String) = TextView(this).apply {
        text = t; textSize = 14f; setPadding(0, dp(12), 0, dp(2))
    }

    override fun onResume() {
        super.onResume()
        window.decorView.post { Stile.rifinisci(window.decorView) }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
