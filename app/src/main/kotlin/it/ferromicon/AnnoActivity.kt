package it.ferromicon

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.time.format.TextStyle
import java.util.Locale

/** Riepilogo dell'anno: un riga per mese e i totali, con saldo ferie. */
class AnnoActivity : Activity() {

    companion object {
        const val EXTRA_ANNO = "anno"
    }

    private var anno = java.time.LocalDate.now().year
    private lateinit var contenuto: LinearLayout
    private lateinit var titolo: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        anno = intent.getIntExtra(EXTRA_ANNO, anno)
        title = "Riepilogo dell'anno"

        val radice = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(8))
        }
        val barra = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        titolo = TextView(this).apply {
            textSize = 20f; setTypeface(typeface, Typeface.BOLD); gravity = Gravity.CENTER
        }
        barra.addView(Button(this).apply { text = "‹"; setOnClickListener { anno--; aggiorna() } })
        barra.addView(titolo, LinearLayout.LayoutParams(0, -2, 1f))
        barra.addView(Button(this).apply { text = "›"; setOnClickListener { anno++; aggiorna() } })
        radice.addView(barra)

        contenuto = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        radice.addView(ScrollView(this).apply { addView(contenuto) }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(radice)
        aggiorna()
    }

    private fun aggiorna() {
        titolo.text = anno.toString()
        contenuto.removeAllViews()
        val dati = Archivio.caricaTutto(this)
        val r = Annuale.riepilogo(dati, anno)

        sezione("Totali")
        testo(
            "Servizi: ${r.servizi}   Ore: ${MainActivity.ore(r.minutiLavoro)}\n" +
                "Condotta/scorta: ${MainActivity.ore(r.minutiCondotta)}\n" +
                "Notturni: ${r.notturni}/79   RFR: ${r.rfr}   Ferie godute: ${r.ferie}\n" +
                "Competenze variabili stimate: ${Competenze.euro(r.stimaCompetenze)}" +
                if (r.violazioni > 0) "\n⛔ Servizi con violazioni: ${r.violazioni}" else ""
        )

        val saldo = Ferie.saldo(dati, anno)
        sezione("Ferie e permessi")
        testo("Ferie: ${saldo.ferieGodute} godute su ${saldo.ferieSpettanti + saldo.ferieAnnoPrecedente}, " +
            "restano ${saldo.ferieResidue}\n" +
            "Permessi ex festività: ${saldo.permessiExFestivitaGoduti}/${saldo.permessiExFestivitaSpettanti}")

        sezione("Mese per mese")
        r.mesi.forEach { m ->
            val nome = m.mese.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.ITALIAN)
                .replaceFirstChar { it.titlecase(Locale.ITALIAN) }
            if (m.servizi == 0 && m.ferie == 0) {
                testo("$nome: nessun servizio", Stile.secondario, 14f)
            } else {
                testo("$nome" + if (m.violazioni > 0) "  ⛔ ${m.violazioni}" else "", null, 16f, true)
                testo("${m.servizi} servizi · ${MainActivity.ore(m.minutiLavoro)} · notturni ${m.notturni} · " +
                    "RFR ${m.rfr} · ferie ${m.ferie} · stima ${Competenze.euro(m.stimaCompetenze)}", Stile.secondario, 13f)
            }
        }
    }

    private fun sezione(t: String) = contenuto.addView(TextView(this).apply {
        text = t; textSize = 17f; setTypeface(typeface, Typeface.BOLD); setPadding(0, dp(16), 0, dp(4))
    })

    private fun testo(t: String, colore: Int? = null, dimensione: Float = 15f, grassetto: Boolean = false) =
        contenuto.addView(TextView(this).apply {
            text = t; textSize = dimensione; setPadding(0, dp(3), 0, dp(3))
            colore?.let { setTextColor(it) }
            if (grassetto) setTypeface(typeface, Typeface.BOLD)
        })

    override fun onResume() {
        super.onResume()
        window.decorView.post { Stile.rifinisci(window.decorView) }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
