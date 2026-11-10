package it.ferromicon

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Recupero delle festività: per ogni festività dell'anno la scadenza (90 giorni), i giorni che
 * mancano e un interruttore per forzare a mano "recuperabile" o "non recuperabile".
 */
class RecuperoFestiviActivity : Activity() {

    private var anno = LocalDate.now().year
    private lateinit var elenco: LinearLayout
    private lateinit var titolo: TextView
    private val fmt = DateTimeFormatter.ofPattern("EEE d MMMM", Locale.ITALIAN)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Recupero festivi"
        val radice = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(8))
        }
        val barra = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        barra.addView(Button(this).apply { text = "◀"; setOnClickListener { anno--; aggiorna() } })
        titolo = TextView(this).apply {
            textSize = 20f; gravity = Gravity.CENTER; setTypeface(typeface, Typeface.BOLD)
        }
        barra.addView(titolo, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        barra.addView(Button(this).apply { text = "▶"; setOnClickListener { anno++; aggiorna() } })
        radice.addView(barra)
        radice.addView(TextView(this).apply {
            text = "Il recupero va richiesto entro 90 giorni dalla festività. L'interruttore segue lo stato " +
                "calcolato; se lo cambi, la tua scelta resta salvata (ON = recuperabile, OFF = non recuperabile)."
            textSize = 12f; setTextColor(Stile.secondario)
        })
        elenco = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        radice.addView(ScrollView(this).apply { addView(elenco) }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(radice)
        aggiorna()
    }

    private fun aggiorna() {
        titolo.text = anno.toString()
        elenco.removeAllViews()
        val dati = Archivio.caricaTutto(this)
        val imp = dati.impostazioni
        val oggi = LocalDate.now()
        val stati = RecuperoFestivi.stati(anno, oggi, imp.festiviForzati)

        // Festività lavorate ancora da recuperare
        val daRecuperare = RecuperoFestivi.daRecuperare(anno, oggi, imp.festiviForzati, dati.servizi)
        if (daRecuperare.isNotEmpty()) elenco.addView(TextView(this).apply {
            text = "Lavorate e ancora recuperabili: " +
                daRecuperare.joinToString { "${it.nome} (${it.data.dayOfMonth}/${it.data.monthValue})" }
            textSize = 14f; setPadding(0, dp(12), 0, dp(4))
        })

        stati.forEach { s ->
            val colore = if (s.recuperabile && !s.scaduto) Stile.primario else Stile.errore
            val riga = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            riga.addView(TextView(this).apply {
                text = "${s.nome}\n${fmt.format(s.data)}"
                textSize = 16f
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            val interruttore = Switch(this).apply { isChecked = s.recuperabile }
            interruttore.setOnCheckedChangeListener { _, acceso ->
                // Se la scelta coincide con lo stato calcolato non serve salvare un'eccezione.
                val naturale = s.giorniAllaScadenza >= 0
                val nuovi = imp.festiviForzati.toMutableMap()
                if (acceso == naturale) nuovi.remove(s.data) else nuovi[s.data] = acceso
                Archivio.salvaImpostazioni(this, imp.copy(festiviForzati = nuovi))
                aggiorna()
            }
            riga.addView(interruttore)
            elenco.addView(riga, LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(12)
            })
            elenco.addView(TextView(this).apply {
                text = RecuperoFestivi.descrizione(s, oggi)
                textSize = 13f; setTextColor(Stile.secondario)
            })
            elenco.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                progress = (s.avanzamento * 100).toInt().coerceIn(0, 100)
                progressTintList = ColorStateList.valueOf(colore)
            }, LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT))
            elenco.addView(View(this).apply { setBackgroundColor(Stile.linea) }, LinearLayout.LayoutParams(-1, 1).apply {
                topMargin = dp(8)
            })
        }
    }

    override fun onResume() {
        super.onResume()
        window.decorView.post { Stile.rifinisci(window.decorView) }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
