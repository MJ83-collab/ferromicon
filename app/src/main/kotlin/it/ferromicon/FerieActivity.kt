package it.ferromicon

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Pianificazione ferie: dato il numero di giorni di ferie che vuoi usare, propone i periodi
 * che, uniti a riposi e festivi del tuo turno, ti danno più giorni consecutivi a casa.
 */
class FerieActivity : Activity() {

    private val fmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ITALIAN)
    private val fmtData = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private lateinit var risultati: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Pianificazione ferie"
        val dati = Archivio.caricaTutto(this)

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
        }
        col.addView(TextView(this).apply {
            text = "Quanti giorni di ferie vuoi usare? Cerco i periodi migliori nei prossimi 12 mesi, " +
                "contando come liberi i riposi del tuo turno, quelli in agenda e i festivi."
            textSize = 14f; setTextColor(Stile.testo)
        })
        val giorni = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER; setText("5"); hint = "Giorni di ferie"
        }
        col.addView(giorni)
        col.addView(Button(this).apply {
            text = "Cerca i periodi migliori"
            setOnClickListener { cerca(dati, giorni.text.toString().toIntOrNull() ?: 0) }
        })
        risultati = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(risultati)

        col.addView(TextView(this).apply {
            text = "Periodo fisso di ferie"; textSize = 16f; setTextColor(Stile.testo)
            setPadding(0, dp(24), 0, dp(4))
        })
        val dal = EditText(this).apply { hint = "Dal gg/mm/aaaa"; inputType = InputType.TYPE_CLASS_DATETIME }
        val al = EditText(this).apply { hint = "Al gg/mm/aaaa"; inputType = InputType.TYPE_CLASS_DATETIME }
        col.addView(dal); col.addView(al)
        col.addView(Button(this).apply {
            text = "Inserisci periodo come ferie"
            setOnClickListener {
                val inizio = runCatching { LocalDate.parse(dal.text.toString().trim(), fmtData) }.getOrNull()
                val fine = runCatching { LocalDate.parse(al.text.toString().trim(), fmtData) }.getOrNull()
                when {
                    inizio == null || fine == null -> Toast.makeText(this@FerieActivity, "Date non valide: usa gg/mm/aaaa", Toast.LENGTH_LONG).show()
                    fine.isBefore(inizio) -> Toast.makeText(this@FerieActivity, "La data finale precede l'iniziale", Toast.LENGTH_LONG).show()
                    else -> periodoFisso(inizio, fine)
                }
            }
        })
        setContentView(ScrollView(this).apply { addView(col) })
    }

    private fun cerca(dati: Dati, n: Int) {
        risultati.removeAllViews()
        if (n !in 1..40) {
            risultati.addView(riga("Inserisci un numero di giorni tra 1 e 40.")); return
        }
        val libero = liberoPer(dati)
        val oggi = LocalDate.now()
        val trovati = PianoFerie.migliori(oggi.plusDays(1), oggi.plusMonths(12), n, 6, libero)
        if (trovati.isEmpty()) { risultati.addView(riga("Nessun periodo trovato.")); return }
        risultati.addView(riga("Periodi consigliati:").apply { textSize = 16f })
        trovati.sortedByDescending { it.giorniTotali }.forEach { p ->
            val festivi = generateSequence(p.da) { it.plusDays(1) }.takeWhile { !it.isAfter(p.a) }
                .mapNotNull { Festivi.nome(it) }.toList()
            val occupati = dati.servizi.count {
                val g = it.turno.inizio.toLocalDate(); !g.isBefore(p.da) && !g.isAfter(p.a)
            }
            risultati.addView(riga("${fmt.format(p.da)} → ${fmt.format(p.a)}\n" +
                "${p.giorniTotali} giorni a casa con ${p.giorniFerie} di ferie" +
                (if (festivi.isNotEmpty()) "\nFestivi inclusi: ${festivi.joinToString()}" else "") +
                (if (occupati > 0) "\n⚠ $occupati servizi già in agenda in questo periodo" else "")).apply {
                background = Stile.scheda()
                setPadding(dp(14), dp(10), dp(14), dp(10))
            })
            risultati.addView(Button(this).apply {
                text = "Inserisci come ferie"
                setOnClickListener {
                    AlertDialog.Builder(this@FerieActivity)
                        .setMessage("Inserisco ${p.giorniFerie} giorni di ferie tra ${fmt.format(p.da)} e ${fmt.format(p.a)} " +
                            "nei giorni che non sono già di riposo?")
                        .setPositiveButton("Inserisci") { _, _ ->
                            var d = p.da
                            val lista = mutableListOf<Assenza>()
                            var id = System.currentTimeMillis()
                            while (!d.isAfter(p.a)) {
                                if (!libero(d)) lista += Assenza(id++, d, TipoAssenza.FERIE, "Pianificazione ferie")
                                d = d.plusDays(1)
                            }
                            Archivio.copiaDiSicurezza(this@FerieActivity)
                            Archivio.aggiungi(this@FerieActivity, emptyList(), lista)
                            finish()
                        }
                        .setNegativeButton("Annulla", null).show()
                }
            })
        }
    }

    /** Giorni che non vanno contati come ferie: riposi, festivi contrattuali e riposi del turno. */
    private fun liberoPer(dati: Dati): (LocalDate) -> Boolean {
        val riposi = dati.assenze.filter { it.tipo.riposo }.map { it.data }.toSet()
        val turno = dati.turno
        return { d: LocalDate ->
            d in riposi || Festivi.èFestivoContrattuale(d) ||
                (turno?.elementoPer(d)?.let { Generatore.sigla(it)?.riposo == true }
                    ?: (turno == null && d.dayOfWeek.value >= 6))
        }
    }

    /** Inserisce un periodo fisso come ferie, escludendo i giorni liberi, dopo conferma. */
    private fun periodoFisso(da: LocalDate, a: LocalDate) {
        val dati = Archivio.caricaTutto(this)
        val libero = liberoPer(dati)
        val giorni = generateSequence(da) { it.plusDays(1) }.takeWhile { !it.isAfter(a) }.toList()
        val daInserire = giorni.filterNot(libero)
        AlertDialog.Builder(this)
            .setMessage("Inserisco ${daInserire.size} giorni di ferie dal ${fmtData.format(da)} al ${fmtData.format(a)}. " +
                "Riposi e festivi del turno restano come sono e non vengono contati.")
            .setPositiveButton("Inserisci") { _, _ ->
                var id = System.currentTimeMillis()
                val lista = daInserire.map { Assenza(id++, it, TipoAssenza.FERIE, "Periodo ferie") }
                Archivio.copiaDiSicurezza(this)
                Archivio.aggiungi(this, emptyList(), lista)
                finish()
            }
            .setNegativeButton("Annulla", null).show()
    }

    private fun riga(t: String) = TextView(this).apply {
        text = t; textSize = 15f; setTextColor(Stile.testo); setPadding(0, dp(12), 0, dp(2))
    }

    override fun onResume() {
        super.onResume()
        window.decorView.post { Stile.rifinisci(window.decorView) }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
