package it.ferromicon

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast

/**
 * Pagina di benvenuto al primo avvio: presenta le funzioni, chiede il nome e contiene
 * le informazioni legali (non ufficiale, nessuna affiliazione, possibili errori).
 * Con l'extra [EXTRA_INFO] mostra solo le informazioni, senza chiedere il nome.
 */
class BenvenutoActivity : Activity() {

    private val info by lazy { intent.getBooleanExtra(EXTRA_INFO, false) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Stile.inizializza(this)
        val colonna = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }
        val scroll = ScrollView(this).apply { addView(colonna) }
        setContentView(scroll)

        if (info) {
            title = "Informazioni"
            colonna.addView(titolo("Informazioni e responsabilità"))
        } else {
            colonna.addView(titolo("Benvenuto in Ferromicon"))
            colonna.addView(testo("Ferromicon ti aiuta a tenere in ordine turni, servizi, assenze e cedolini."))
            colonna.addView(intestazione("Cosa puoi fare"))
            FUNZIONI.forEach { colonna.addView(voce(it)) }
            colonna.addView(intestazione("Il tuo profilo"))
            colonna.addView(testo("Serve a personalizzare l'app. Resta solo su questo telefono."))
            val attuale = Archivio.caricaTutto(this@BenvenutoActivity).impostazioni
            colonna.addView(testo("Nome e cognome"))
            val campo = EditText(this).apply {
                hint = "Nome e cognome"
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
                maxLines = 1
                setText(attuale.nome)
            }
            colonna.addView(campo)
            colonna.addView(testo("CID"))
            val cidCampo = EditText(this).apply {
                hint = "CID"
                inputType = InputType.TYPE_CLASS_TEXT
                maxLines = 1
                setText(attuale.cid)
            }
            colonna.addView(cidCampo)
            colonna.addView(testo("Impianto di appartenenza"))
            val impianto = Spinner(this).apply {
                adapter = ArrayAdapter(this@BenvenutoActivity, android.R.layout.simple_spinner_dropdown_item,
                    listOf("Scegli l'impianto") + Impianti.tutti)
                setSelection(Impianti.tutti.indexOf(attuale.impianto).let { if (it < 0) 0 else it + 1 })
            }
            colonna.addView(impianto)
            colonna.addView(testo("Qualifica"))
            val qualifica = Spinner(this).apply {
                adapter = ArrayAdapter(this@BenvenutoActivity, android.R.layout.simple_spinner_dropdown_item,
                    listOf("Scegli la qualifica") + Profilo.values().map { it.etichetta })
                setSelection(attuale.profilo?.let { Profilo.values().indexOf(it) + 1 } ?: 0)
            }
            colonna.addView(qualifica)
            colonna.addView(testo("Livello / posizione retributiva (facoltativo, lo trovi in busta paga)"))
            val livello = Spinner(this).apply {
                adapter = ArrayAdapter(this@BenvenutoActivity, android.R.layout.simple_spinner_dropdown_item,
                    listOf("Non impostato") + Livello.values().map { it.name })
                setSelection((attuale.livello?.ordinal ?: -1) + 1)
            }
            colonna.addView(livello)
            colonna.addView(Button(this).apply {
                text = "Inizia"
                setOnClickListener {
                    val nome = campo.text.toString().trim().take(60)
                    if (nome.isEmpty() || impianto.selectedItemPosition == 0 || qualifica.selectedItemPosition == 0) {
                        Toast.makeText(this@BenvenutoActivity, "Inserisci nome, impianto e qualifica", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    salvaProfilo(nome, cidCampo.text.toString().trim().take(20),
                        Impianti.tutti[impianto.selectedItemPosition - 1],
                        Profilo.values()[qualifica.selectedItemPosition - 1],
                        if (livello.selectedItemPosition == 0) null else Livello.values()[livello.selectedItemPosition - 1])
                    segnaVisto(this@BenvenutoActivity)
                    startActivity(Intent(this@BenvenutoActivity, MainActivity::class.java))
                    finish()
                }
            })
        }

        colonna.addView(intestazione("Informazioni e responsabilità"))
        colonna.addView(testo(AVVISO))
        colonna.addView(testo("Autore: Marco Jannino."))
        if (info) colonna.addView(Button(this).apply {
            text = "Chiudi"
            setOnClickListener { finish() }
        })
    }

    private fun salvaProfilo(nome: String, cid: String, impianto: String, profilo: Profilo, livello: Livello?) {
        runCatching {
            val imp = Archivio.caricaTutto(this).impostazioni
            Archivio.salvaImpostazioni(this, imp.copy(nome = nome, cid = cid, impianto = impianto,
                profilo = profilo, livello = livello))
        }.onFailure {
            Toast.makeText(this, "Il profilo non è stato salvato. Riprova dalle Impostazioni.", Toast.LENGTH_LONG).show()
        }
    }

    private fun titolo(t: String) = TextView(this).apply {
        text = t
        textSize = 24f
        setTypeface(null, Typeface.BOLD)
        setTextColor(Stile.primario)
        setPadding(0, 0, 0, dp(12))
    }

    private fun intestazione(t: String) = TextView(this).apply {
        text = t
        textSize = 16f
        setTypeface(null, Typeface.BOLD)
        setTextColor(Stile.testo)
        setPadding(0, dp(20), 0, dp(8))
    }

    private fun testo(t: String) = TextView(this).apply {
        text = t
        textSize = 14f
        setTextColor(Stile.testo)
        setPadding(0, 0, 0, dp(10))
    }

    private fun voce(t: String) = TextView(this).apply {
        text = "•  $t"
        textSize = 14f
        setTextColor(Stile.testo)
        setPadding(0, dp(3), 0, dp(3))
        gravity = Gravity.START
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_INFO = "info"
        private const val PREFERENZE = "ferromicon-benvenuto"
        private const val CHIAVE_VISTO = "visto"

        private val FUNZIONI = listOf(
            "Agenda del mese: turni, servizi, assenze e ferie",
            "Riepilogo e stime della paga",
            "Strumenti: straordinario, recupero festività, calendario agente",
            "Cedolini e CU, salvati come dati letti",
            "Cartellino SAP: importazione dal PDF",
            "Report PDF mensili e annuali, esportazione CSV",
            "Calendario del telefono sincronizzato con i turni"
        )

        private const val AVVISO =
            "Ferromicon non è un'app ufficiale e non è affiliata, approvata né collegata ad alcuna azienda " +
                "di trasporto. È un progetto personale. Calcoli, letture di cartellini e cedolini e le " +
                "stime possono contenere errori: verifica sempre i dati sui documenti ufficiali. " +
                "L'autore non risponde delle decisioni prese in base ai dati mostrati dall'app."

        /** Vero se la pagina di benvenuto è già stata vista, oppure se l'utente ha già un nome salvato. */
        fun visto(ctx: Context): Boolean {
            val sp = ctx.getSharedPreferences(PREFERENZE, Context.MODE_PRIVATE)
            if (sp.getBoolean(CHIAVE_VISTO, false)) return true
            return runCatching { Archivio.caricaTutto(ctx).impostazioni.nome.isNotBlank() }.getOrDefault(true)
        }

        fun segnaVisto(ctx: Context) {
            ctx.getSharedPreferences(PREFERENZE, Context.MODE_PRIVATE).edit().putBoolean(CHIAVE_VISTO, true).apply()
        }
    }
}
