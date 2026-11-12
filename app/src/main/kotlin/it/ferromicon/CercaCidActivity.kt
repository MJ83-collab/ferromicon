package it.ferromicon

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import java.io.File

/**
 * Cerca CID: nome e impianto da un elenco CSV importato dal telefono.
 * L'elenco resta solo nello spazio privato dell'app, non nel codice.
 */
class CercaCidActivity : Activity() {

    private lateinit var campo: EditText
    private lateinit var risultati: LinearLayout
    private lateinit var stato: TextView
    private var elenco: List<VoceCid> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Stile.inizializza(this)
        title = "Cerca CID"
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        stato = TextView(this).apply { textSize = 13f; setTextColor(Stile.secondario) }
        col.addView(stato)
        col.addView(Button(this).apply {
            text = "Importa elenco (CSV)"
            setOnClickListener { importa() }
        })
        col.addView(Button(this).apply {
            text = "Cancella elenco importato"
            setOnClickListener {
                AlertDialog.Builder(this@CercaCidActivity)
                    .setTitle("Cancellare l'elenco?")
                    .setMessage("L'elenco importato verrà rimosso dal telefono. Puoi importarlo di nuovo quando vuoi.")
                    .setPositiveButton("Cancella") { _, _ ->
                        file().delete()
                        risultati.removeAllViews()
                        carica()
                    }
                    .setNegativeButton("Annulla", null)
                    .show()
            }
        })
        campo = EditText(this).apply {
            hint = "CID o cognome e nome"
            inputType = InputType.TYPE_CLASS_TEXT
            maxLines = 1
        }
        col.addView(campo)
        col.addView(Button(this).apply {
            text = "Cerca"
            setOnClickListener { mostra() }
        })
        risultati = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(risultati)
        setContentView(ScrollView(this).apply { addView(col) })
        carica()
    }

    private fun file() = File(filesDir, NOME_FILE)

    private fun carica() {
        elenco = if (file().exists()) ElencoCid.leggi(file().readText()) else emptyList()
        stato.text = if (elenco.isEmpty())
            "Nessun elenco importato. Scegli un file CSV con colonne CID;Cognome e nome;Impianto."
        else "Elenco: ${elenco.size} persone."
    }

    private fun importa() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }, RICHIESTA_IMPORTA)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data ?: return
        if (resultCode != RESULT_OK || requestCode != RICHIESTA_IMPORTA) return
        runCatching {
            val testo = contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?: error("file non leggibile")
            val letti = ElencoCid.leggi(testo)
            require(letti.isNotEmpty()) { "nessuna riga valida" }
            val scartate = testo.lines().drop(1).count { it.isNotBlank() } - letti.size
            AlertDialog.Builder(this)
                .setTitle("Importare l'elenco?")
                .setMessage("Righe valide: ${letti.size}. Righe scartate: $scartate.\n" +
                    "Sostituisce l'elenco già importato.")
                .setPositiveButton("Importa") { _, _ ->
                    file().writeText(testo)
                    Toast.makeText(this, "Importate ${letti.size} persone", Toast.LENGTH_SHORT).show()
                    carica()
                }
                .setNegativeButton("Annulla", null)
                .show()
        }.onFailure {
            Toast.makeText(this, "Importazione non riuscita: ${it.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun mostra() {
        risultati.removeAllViews()
        val trovate = ElencoCid.cerca(elenco, campo.text.toString())
        if (trovate.isEmpty()) {
            risultati.addView(TextView(this).apply { text = "Nessun risultato" })
            return
        }
        trovate.forEach { v ->
            risultati.addView(TextView(this).apply {
                text = "${v.cid} · ${v.nominativo}" + if (v.impianto.isNotBlank()) "\n${v.impianto}" else ""
                textSize = 15f
                setTextColor(Stile.testo)
                setPadding(0, dp(8), 0, dp(8))
            })
            risultati.addView(Button(this).apply {
                text = "È il mio CID"
                setOnClickListener { chiediConferma(v) }
            })
        }
    }

    /** Chiede se i dati sono giusti: il CID è già proposto, l'impianto si può modificare. */
    private fun chiediConferma(v: VoceCid) {
        val imp = Archivio.caricaTutto(this).impostazioni
        val impianti = Impianti.tutti
        val scelta = Spinner(this).apply {
            adapter = ArrayAdapter(this@CercaCidActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("Scegli l'impianto") + impianti)
            val posizione = impianti.indexOf(v.impianto).let { if (it < 0) 0 else it + 1 }
            setSelection(posizione)
        }
        val contenuto = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), 0)
            addView(TextView(this@CercaCidActivity).apply {
                text = "CID ${v.cid}\n${v.nominativo}\n\nÈ giusto? Se l'impianto non è corretto, cambialo qui sotto."
                textSize = 15f
                setTextColor(Stile.testo)
            })
            addView(scelta)
        }
        AlertDialog.Builder(this)
            .setTitle("Confermi i tuoi dati?")
            .setView(contenuto)
            .setPositiveButton("Sì, è giusto") { _, _ ->
                val nuovoImpianto = if (scelta.selectedItemPosition == 0) "" else impianti[scelta.selectedItemPosition - 1]
                Archivio.salvaImpostazioni(this, imp.copy(cid = v.cid.toString(), impianto = nuovoImpianto))
                Toast.makeText(this, "CID e impianto salvati", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val RICHIESTA_IMPORTA = 1
        private const val NOME_FILE = "elenco_cid.csv"
    }
}
