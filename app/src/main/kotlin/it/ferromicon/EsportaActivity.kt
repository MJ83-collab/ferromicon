package it.ferromicon

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.time.LocalDate
import java.time.YearMonth

/** Report PDF (mese o anno), esportazione di tutti i dati in CSV e importazione del file CSV. */
class EsportaActivity : Activity() {

    private companion object {
        const val PDF_MESE = 51
        const val PDF_ANNO = 52
        const val ZIP_ESPORTA = 53
        const val ZIP_IMPORTA = 54
        const val RIEPILOGO_CSV = 55
    }

    private lateinit var corpo: LinearLayout
    private var conGiorni = false
    private var meseScelto: YearMonth = YearMonth.now()
    private var annoScelto: Int = LocalDate.now().year

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Report e dati"
        corpo = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        setContentView(ScrollView(this).apply { addView(corpo) })

        testo("Report PDF", 16f, grassetto = true)
        testo("Il PDF contiene agenda, assenze, cartellino SAP, cedolini e l'archivio (stazioni, perimetri, " +
            "preferiti, CU). Il dettaglio giorno per giorno è facoltativo.", secondario = true)
        corpo.addView(CheckBox(this).apply {
            text = "Includi il dettaglio giorno per giorno"
            setOnCheckedChangeListener { _, v -> conGiorni = v }
        })
        pulsante("PDF di un mese…") {
            scegliData { data ->
                meseScelto = YearMonth.from(data)
                crea(PDF_MESE, "application/pdf", "ferromicon-mese-$meseScelto.pdf")
            }
        }
        pulsante("PDF di un anno…") {
            scegliData { data ->
                annoScelto = data.year
                crea(PDF_ANNO, "application/pdf", "ferromicon-anno-$annoScelto.pdf")
            }
        }

        testo("Dati in CSV", 16f, grassetto = true)
        testo("Un file zip con un CSV per ogni tipo di dato. Ogni riga ha le colonne leggibili e il dettaglio " +
            "completo, così il file si reimporta senza perdere nulla. Si apre anche con Excel.", secondario = true)
        pulsante("Riepilogo mensile in CSV…") {
            scegliData { data ->
                meseScelto = YearMonth.from(data)
                crea(RIEPILOGO_CSV, "text/csv", "ferromicon-riepilogo-$meseScelto.csv")
            }
        }
        pulsante("Esporta tutti i dati (zip con CSV)") {
            crea(ZIP_ESPORTA, "application/zip", ScambioCsv.NOME_ZIP)
        }
        pulsante("Importa dati da un file zip CSV") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }, ZIP_IMPORTA)
        }
    }

    private fun scegliData(azione: (LocalDate) -> Unit) {
        val oggi = LocalDate.now()
        DatePickerDialog(this, { _, anno, mese, giorno -> azione(LocalDate.of(anno, mese + 1, giorno)) },
            oggi.year, oggi.monthValue - 1, oggi.dayOfMonth).show()
    }

    private fun crea(codice: Int, tipo: String, nome: String) {
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = tipo
            putExtra(Intent.EXTRA_TITLE, nome)
        }, codice)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            when (requestCode) {
                PDF_MESE -> contentResolver.openOutputStream(uri)?.use {
                    RapportoPdf.mensile(Archivio.caricaTutto(this), meseScelto, conGiorni, it)
                }
                PDF_ANNO -> contentResolver.openOutputStream(uri)?.use {
                    RapportoPdf.annuale(Archivio.caricaTutto(this), annoScelto, conGiorni, it)
                }
                ZIP_ESPORTA -> contentResolver.openOutputStream(uri)?.use {
                    ScambioCsv.zip(ScambioCsv.tabelle(Archivio.caricaTutto(this)), it)
                }
                ZIP_IMPORTA -> importa(uri)
                RIEPILOGO_CSV -> contentResolver.openOutputStream(uri)?.use {
                    it.write(riepilogoCsv(Archivio.caricaTutto(this), meseScelto).toByteArray(Charsets.UTF_8))
                }
                else -> return
            }
            if (requestCode != ZIP_IMPORTA) Toast.makeText(this, "File salvato", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Operazione non riuscita: ${e.javaClass.simpleName}", Toast.LENGTH_LONG).show()
        }
    }

    /** Riepilogo del mese come CSV a due colonne (voce; valore), leggibile anche in Excel. */
    private fun riepilogoCsv(dati: Dati, mese: YearMonth): String {
        val r = Riepilogo.mese(dati, mese)
        val assenze = Analisi.riepilogoMese(dati.servizi, mese, dati.assenze).assenzeMese
        val righe = mutableListOf(
            "Voce;Valore",
            "Mese;$mese",
            "Monte ore;${Riepilogo.ore(r.monteOreMinuti)}",
            "Lavoro;${Riepilogo.ore(r.lavoroMinuti)}",
            "Ore riconosciute;${Riepilogo.ore(r.oreRiconosciuteMinuti)}",
            "Presenze effettive;${r.presenzeEffettive}",
            "Servizi notturni;${r.serviziNotturni}",
            "Ore fascia 22-6;${Riepilogo.ore(r.minutiFascia22_6)}",
            "Condotta o scorta;${Riepilogo.ore(r.condottaMinuti)}",
            "Condotta fino a 3 h;${Riepilogo.ore(r.condottaFinoA3hMinuti)}",
            "Condotta oltre 3 h;${Riepilogo.ore(r.condottaOltre3hMinuti)}",
            "Straordinario normale;${Riepilogo.ore(r.straordinarioNormaleMinuti)}",
            "Straordinario in RC;${Riepilogo.ore(r.straordinarioInRcMinuti)}",
            "Straordinario in RS;${Riepilogo.ore(r.straordinarioInRsMinuti)}",
            "Ferie nel mese;${r.ferieMese}",
            "Ferie residue;${r.ferieResidue}",
            "Festività soppresse godute;${r.festivitaSoppresseGodute}/${r.festivitaSoppresseSpettanti}"
        )
        assenze.forEach { (tipo, n) -> righe += "Giornate ${tipo.etichetta.lowercase()};$n" }
        return righe.joinToString("\n")
    }

    private fun importa(uri: Uri) {
        val file = contentResolver.openInputStream(uri)?.use { ScambioCsv.leggiZip(it) }
            ?: throw IllegalArgumentException("File non leggibile")
        val riconosciuti = listOf("servizi.csv", "assenze.csv", "impostazioni.csv", "cedolini.csv", "cartellini.csv")
        if (riconosciuti.none { file.containsKey(it) }) {
            Toast.makeText(this, "Lo zip non contiene i CSV di Ferromicon.", Toast.LENGTH_LONG).show()
            return
        }
        val nuovi = ScambioCsv.importa(Archivio.caricaTutto(this), file)
        AlertDialog.Builder(this)
            .setTitle("Importare i dati?")
            .setMessage("Il file contiene ${nuovi.servizi.size} servizi, ${nuovi.assenze.size} assenze, " +
                "${nuovi.cedolini.size} cedolini e ${nuovi.cartellini.size} cartellini SAP. Le voci con lo stesso " +
                "identificativo vengono sostituite, le altre restano.")
            .setPositiveButton("Importa") { _, _ ->
                Archivio.salvaTutto(this, nuovi)
                Toast.makeText(this, "Dati importati", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun testo(t: String, size: Float = 14f, grassetto: Boolean = false, secondario: Boolean = false) {
        corpo.addView(TextView(this).apply {
            text = t
            textSize = size
            setTextColor(if (secondario) Stile.secondario else Stile.testo)
            if (grassetto) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(10), 0, dp(4))
        })
    }

    private fun pulsante(t: String, azione: () -> Unit) {
        corpo.addView(Button(this).apply {
            text = t
            setOnClickListener { azione() }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(6), 0, 0) }
        })
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
