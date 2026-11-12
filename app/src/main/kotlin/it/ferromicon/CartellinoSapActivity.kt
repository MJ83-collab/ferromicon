package it.ferromicon

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

/**
 * Cartellino SAP: si sceglie il PDF stampato dal portale, l'app legge le timbrature e la
 * flessibilità di ogni giorno, mostra il risultato e lo salva solo dopo conferma.
 */
class CartellinoSapActivity : Activity() {

    private companion object {
        const val RICHIESTA_PDF = 41
    }

    private lateinit var corpo: LinearLayout
    private var letto: CartellinoSap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Cartellino SAP"
        corpo = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        setContentView(ScrollView(this).apply { addView(corpo) })
        mostraElenco()
    }

    private fun mostraElenco() {
        letto = null
        corpo.removeAllViews()
        testo("Cartellino orologio SAP", 18f, bold = true)
        testo("Scegli il PDF stampato dal cartellino. L'app legge timbrature e flessibilità di ogni giorno, " +
            "ti mostra il risultato e salva solo quando confermi.", secondario = true)
        pulsante("Scegli il PDF del cartellino") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/pdf"
            }, RICHIESTA_PDF)
        }
        val salvati = Archivio.caricaTutto(this).cartellini
        testo("Cartellini salvati", 15f, bold = true)
        if (salvati.isEmpty()) testo("Nessuno ancora.", secondario = true)
        salvati.reversed().forEach { c ->
            testo("${"%02d".format(c.mese)}/${c.anno}: ${c.giornate.size} giorni letti, " +
                "flex del mese ${numero(c.giornate.mapNotNull { it.flex }.sum())}")
            pulsante("Elimina ${"%02d".format(c.mese)}/${c.anno}") {
                Archivio.eliminaCartellino(this, c.anno, c.mese)
                mostraElenco()
            }
        }
    }

    private fun mostraAnteprima(c: CartellinoSap) {
        letto = c
        corpo.removeAllViews()
        testo("Letto: ${"%02d".format(c.mese)}/${c.anno}", 18f, bold = true)
        testo("Giorni trovati: ${c.giornate.size}. Flessibilità mese precedente: " +
            "${c.flexMesePrecedente?.let { numero(it) } ?: "non trovata"}.")
        val conFlex = c.giornate.filter { it.flex != null }
        testo("Flessibilità dei giorni (somma ${numero(conFlex.sumOf { it.flex ?: 0.0 })}):", bold = true)
        if (conFlex.isEmpty()) testo("Nessuna flessibilità riconosciuta: controlla il testo letto.", secondario = true)
        conFlex.forEach { g ->
            testo("${"%02d".format(g.giorno)} ${g.sigla}  ${g.prima ?: "--:--"} – ${g.ultima ?: "--:--"}  " +
                "flex ${numero(g.flex ?: 0.0)}")
        }
        pulsante("Salva questo mese") {
            Archivio.aggiungiCartellino(this, c)
            Toast.makeText(this, "Cartellino salvato", Toast.LENGTH_SHORT).show()
            mostraElenco()
        }
        pulsante("Mostra il testo letto") {
            testo(c.righeGrezze.joinToString("\n"), 12f, monospazio = true)
        }
        pulsante("Annulla") { mostraElenco() }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != RICHIESTA_PDF || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            val righe = mutableListOf<String>()
            PdfTesto.pagine(this, uri).forEach { righe += righeDa(it) }
            val cartellino = LettoreCartellino.analizza(righe)
            if (cartellino == null) {
                Toast.makeText(this, "Non riconosco questo PDF come cartellino SAP (manca il periodo).",
                    Toast.LENGTH_LONG).show()
            } else {
                mostraAnteprima(cartellino)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "PDF non letto: ${e.javaClass.simpleName}", Toast.LENGTH_LONG).show()
        }
    }

    /** Raggruppa i pezzi di testo di una pagina in righe, ordinate da sinistra a destra. */
    private fun righeDa(pezzi: List<Pezzo>): List<String> {
        val gruppi = mutableListOf<MutableList<Pezzo>>()
        for (p in pezzi.sortedWith(compareBy({ it.y }, { it.x }))) {
            val ultimo = gruppi.lastOrNull()
            if (ultimo != null && kotlin.math.abs(ultimo.first().y - p.y) < 3.0) ultimo += p
            else gruppi += mutableListOf(p)
        }
        return gruppi.map { g -> g.sortedBy { it.x }.joinToString(" ") { it.str.trim() } }
    }

    private fun numero(v: Double) = "%.2f".format(Locale.ITALY, v)

    private fun testo(t: String, size: Float = 14f, bold: Boolean = false, secondario: Boolean = false,
                      monospazio: Boolean = false) {
        corpo.addView(TextView(this).apply {
            text = t
            textSize = size
            setTextColor(if (secondario) Stile.secondario else Stile.testo)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            if (monospazio) typeface = Typeface.MONOSPACE
            setPadding(0, dp(8), 0, dp(4))
        })
    }

    private fun pulsante(t: String, azione: () -> Unit) {
        corpo.addView(Button(this).apply {
            text = t
            setOnClickListener { azione() }
            layoutParams = LinearLayout.LayoutParams(View.LayoutParams.MATCH_PARENT,
                View.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(6), 0, 0) }
        })
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
