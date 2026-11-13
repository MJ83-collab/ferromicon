package it.ferromicon

import android.app.Activity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Vademecum del personale mobile (testo di riferimento 2.0, agg. 2025): indice delle sezioni
 * e ricerca di parole. Il testo è in assets/vademecum.txt, senza nomi di organizzazioni o contatti.
 */
class VademecumActivity : Activity() {

    private data class Sezione(val titolo: String, val righe: List<String>)

    private val righe: List<String> by lazy {
        assets.open("vademecum.txt").bufferedReader().use { it.readLines() }
            .map { it.trim() }.filter { it.isNotEmpty() }
    }

    private val sezioni: List<Sezione> by lazy { dividi(righe) }

    private lateinit var corpo: LinearLayout
    private lateinit var cerca: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Vademecum"
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        cerca = EditText(this).apply { hint = "Cerca una parola" }
        col.addView(cerca)
        corpo = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(corpo)
        setContentView(ScrollView(this).apply { addView(col) })

        cerca.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = mostra()
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        mostra()
    }

    /** Con la ricerca: righe che contengono la parola. Senza: indice delle sezioni. */
    private fun mostra() {
        corpo.removeAllViews()
        val parola = cerca.text.toString().trim()
        if (parola.isEmpty()) {
            sezioni.forEach { s ->
                corpo.addView(Button(this).apply {
                    text = s.titolo; isAllCaps = false
                    setOnClickListener { apriSezione(s) }
                })
            }
        } else {
            val trovate = righe.filter { it.contains(parola, ignoreCase = true) }
            corpo.addView(TextView(this).apply {
                text = if (trovate.isEmpty()) "Nessun risultato." else "${trovate.size} righe trovate"
                textSize = 13f; setPadding(0, dp(8), 0, dp(8))
            })
            trovate.take(200).forEach { r ->
                corpo.addView(TextView(this).apply { text = r; textSize = 14f; setPadding(0, dp(4), 0, dp(4)) })
            }
        }
    }

    private fun apriSezione(s: Sezione) {
        corpo.removeAllViews()
        corpo.addView(Button(this).apply {
            text = "← Indice"; isAllCaps = false
            setOnClickListener { cerca.setText(""); mostra() }
        })
        corpo.addView(TextView(this).apply {
            text = s.titolo; textSize = 17f; setPadding(0, dp(12), 0, dp(8))
        })
        s.righe.forEach { r ->
            corpo.addView(TextView(this).apply { text = r; textSize = 14f; setPadding(0, dp(2), 0, dp(2)) })
        }
    }

    /** Titoli di sezione: righe in maiuscolo di almeno 6 caratteri, con lettere e non solo numeri. */
    private fun dividi(linee: List<String>): List<Sezione> {
        val risultato = mutableListOf<Sezione>()
        var titolo = "Inizio"
        var accumulo = mutableListOf<String>()
        linee.forEach { r ->
            val maiuscolo = r == r.uppercase() && r.length in 6..80 && r.count { it.isLetter() } >= 4
            if (maiuscolo) {
                if (accumulo.isNotEmpty()) risultato += Sezione(titolo, accumulo)
                titolo = r
                accumulo = mutableListOf()
            } else accumulo += r
        }
        if (accumulo.isNotEmpty()) risultato += Sezione(titolo, accumulo)
        return risultato
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
