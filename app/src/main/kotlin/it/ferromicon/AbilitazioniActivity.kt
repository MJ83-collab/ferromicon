package it.ferromicon

import android.app.Activity
import android.app.DatePickerDialog
import android.os.Bundle
import android.text.InputType
import android.text.TextWatcher
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Abilitazioni del macchinista: linee e tipi di treno (materiale rotabile).
 * Per ogni voce mostra la data dell'ultimo viaggio e segnala quelle da rinnovare oltre la soglia.
 * Per un tipo di treno la data viene dai servizi registrati con quel materiale; per una linea si inserisce a mano.
 */
class AbilitazioniActivity : Activity() {

    private lateinit var dati: Dati
    private lateinit var soglia: EditText
    private val lineeBox = LinkedHashMap<String, CheckBox>()
    private val macchineBox = LinkedHashMap<String, CheckBox>()
    private val info = HashMap<String, TextView>()
    private val manuale = HashMap<String, LocalDate>()
    private val fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Abilitazioni"
        dati = Archivio.caricaTutto(this)
        val imp = dati.impostazioni
        manuale.putAll(imp.ultimoViaggio)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        col.addView(TextView(this).apply {
            text = "Spunta le abilitazioni che possiedi. Sotto ogni voce c'è la data dell'ultimo viaggio: " +
                "tocca la riga per inserirla a mano."
            textSize = 14f
        })
        soglia = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(imp.giorniSogliaAbilitazioni.toString())
            hint = "Giorni"
        }
        col.addView(TextView(this).apply { text = "Rinnova dopo quanti giorni senza viaggi"; setPadding(0, dp(12), 0, 0) })
        col.addView(soglia)
        soglia.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) { aggiornaTutte() }
        })
        sezione(col, "Tipi di treno (macchine)", MACCHINE_BASE, imp.abilitazioniMacchine, macchineBox, "M")
        sezione(col, "Linee", LINEE_BASE, imp.abilitazioniLinee, lineeBox, "L")
        col.addView(TextView(this).apply {
            text = "Elenco linee ricavato dalle pagine Trenord e da fonti pubbliche: controllalo e aggiungi le tue."
            textSize = 12f
            setPadding(0, dp(12), 0, 0)
        })
        col.addView(Button(this).apply { text = "Salva"; setOnClickListener { salva() } })
        setContentView(ScrollView(this).apply { addView(col) })
    }

    private fun sezione(
        col: LinearLayout, titolo: String, base: List<String>, scelte: List<String>,
        mappa: MutableMap<String, CheckBox>, prefisso: String
    ) {
        col.addView(TextView(this).apply { text = titolo; textSize = 17f; setPadding(0, dp(18), 0, dp(6)) })
        val contenitore = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(contenitore)
        (base + scelte).distinct().forEach { aggiungiVoce(contenitore, it, it in scelte, mappa, prefisso) }
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
                aggiungiVoce(contenitore, nome, true, mappa, prefisso)
                campo.setText("")
            }
        })
    }

    private fun aggiungiVoce(
        contenitore: LinearLayout, nome: String, spuntata: Boolean,
        mappa: MutableMap<String, CheckBox>, prefisso: String
    ) {
        val box = CheckBox(this).apply { text = nome; isChecked = spuntata; textSize = 16f }
        val riga = TextView(this).apply {
            textSize = 13f
            setPadding(dp(32), 0, dp(8), dp(8))
            setOnClickListener { scegliData(prefisso, nome) }
        }
        mappa[nome] = box
        info["$prefisso:$nome"] = riga
        aggiorna(prefisso, nome)
        val largo = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        contenitore.addView(box, largo)
        contenitore.addView(riga, largo)
    }

    /** Ultimo viaggio: il più recente tra la data inserita a mano e i servizi già registrati con quel materiale. */
    private fun ultimo(prefisso: String, nome: String): LocalDate? {
        val a = manuale["$prefisso:$nome"]
        val b = if (prefisso == "M") {
            val ora = java.time.LocalDateTime.now()
            dati.servizi
                .filter { it.materiale.equals(nome, ignoreCase = true) && !it.turno.inizio.isAfter(ora) }
                .maxOfOrNull { it.turno.inizio.toLocalDate() }
        } else null
        return listOfNotNull(a, b).maxOrNull()
    }

    private fun aggiorna(prefisso: String, nome: String) {
        val riga = info["$prefisso:$nome"] ?: return
        val d = ultimo(prefisso, nome)
        val limite = soglia.text.toString().toIntOrNull() ?: 90
        riga.text = if (d == null) {
            "ultimo viaggio: nessuno registrato · tocca per inserirlo"
        } else {
            val giorni = ChronoUnit.DAYS.between(d, LocalDate.now())
            "ultimo viaggio: ${fmt.format(d)} ($giorni giorni fa)" +
                if (giorni > limite) " · DA RINNOVARE" else ""
        }
    }

    private fun aggiornaTutte() {
        lineeBox.keys.forEach { aggiorna("L", it) }
        macchineBox.keys.forEach { aggiorna("M", it) }
    }

    private fun scegliData(prefisso: String, nome: String) {
        val oggi = LocalDate.now()
        DatePickerDialog(this, { _, y, m, g ->
            manuale["$prefisso:$nome"] = LocalDate.of(y, m + 1, g)
            aggiorna(prefisso, nome)
        }, oggi.year, oggi.monthValue - 1, oggi.dayOfMonth).show()
    }

    private fun salva() {
        val imp = Archivio.caricaTutto(this).impostazioni
        Archivio.salvaImpostazioni(this, imp.copy(
            abilitazioniLinee = lineeBox.filterValues { it.isChecked }.keys.toList(),
            abilitazioniMacchine = macchineBox.filterValues { it.isChecked }.keys.toList(),
            ultimoViaggio = manuale.toMap(),
            giorniSogliaAbilitazioni = soglia.text.toString().toIntOrNull() ?: 90
        ))
        Toast.makeText(this, "Abilitazioni salvate", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        /** Tipi di treno già noti; se ne servono altri si aggiungono dalla schermata. */
        private val MACCHINE_BASE = listOf("TSR", "Coradia", "Caravaggio", "Vivalto")

        /** Linee ricavate dalle pagine Trenord (linee S, R, RE, aeroportuali) e da fonti pubbliche. */
        private val LINEE_BASE = listOf(
            "S1", "S2", "S3", "S4", "S5", "S6", "S7", "S8", "S11", "S12", "S13", "S19", "S31",
            "RE1", "RE2", "Malpensa Express",
            "R1", "R2", "R3", "R4", "R5", "R6", "R7", "R8", "R9", "R11", "R12", "R13", "R14", "R15", "R16", "R17",
            "R24", "R25", "R27", "R28", "R31", "R32", "R33", "R36", "R37", "R38", "R39", "R40", "R41"
        )
    }
}
