package it.ferromicon

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.time.Month
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * Cedolini e CU: si caricano i PDF dal telefono o dal PC, l'app li legge sul telefono (nulla esce dal telefono),
 * mostra il riepilogo per anno, il dettaglio di ogni mese e confronta le voci con la stima.
 */
class CedoliniActivity : Activity() {

    private lateinit var contenuto: LinearLayout
    private lateinit var stato: TextView
    private lateinit var barra: ProgressBar
    private lateinit var btnImporta: Button
    private var inCorso = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Cedolini e CU"
        val radice = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        radice.setOnApplyWindowInsetsListener { v, insets ->
            v.setPadding(0, if (android.os.Build.VERSION.SDK_INT >= 30) insets.getInsets(android.view.WindowInsets.Type.statusBars()).top else insets.systemWindowInsetTop, 0, if (android.os.Build.VERSION.SDK_INT >= 30)
                insets.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom else 0)
            insets
        }
        contenuto = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(16))
        }
        radice.addView(ScrollView(this).apply { addView(contenuto) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(radice)
        // Dalla scelta del profilo iniziale: si apre subito la scelta del PDF
        if (savedInstanceState == null && intent.getBooleanExtra(EXTRA_PRIMO, false)) {
            window.decorView.post { scegliFile() }
        }
    }

    override fun onResume() {
        super.onResume()
        window.decorView.post { Stile.rifinisci(window.decorView) }
        mostra()
    }

    // ---------- schermata ----------

    private fun mostra() {
        contenuto.removeAllViews()
        val dati = Archivio.caricaTutto(this)

        contenuto.addView(testo("I PDF restano sul telefono: l'app li legge qui, senza inviarli da nessuna parte. " +
            "Puoi scegliere più file insieme (cedolini e CU).", 13f, Stile.secondario))
        btnImporta = Button(this).apply {
            text = "Carica cedolini o CU (PDF)"
            setOnClickListener { scegliFile() }
        }
        contenuto.addView(btnImporta, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        barra = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { visibility = View.GONE }
        contenuto.addView(barra)
        stato = testo("", 13f, Stile.secondario).also { contenuto.addView(it) }

        if (dati.cedolini.isEmpty() && dati.certificazioniUniche.isEmpty()) {
            contenuto.addView(titolo("Ancora niente"))
            contenuto.addView(scheda().apply {
                addView(testo("Tocca \"Carica\" e scegli uno o più cedolini (o CU) in PDF dal telefono o dalla memoria. " +
                    "L'app ricava netto, imposte, voci e ore di straordinario di ogni mese, e controlla che le somme tornino.", 15f, Stile.testo))
            })
            return
        }

        contenuto.addView(Button(this).apply {
            text = "Usa questi dati per la stima del netto"
            setOnClickListener { proponiDatiFiscali(dati) }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        val ultimoConDati = dati.cedolini.filter { !it.anagrafica.vuota }
            .maxWithOrNull(compareBy<Cedolino>({ it.anno ?: 0 }, { it.mese ?: 0 }))
        if (ultimoConDati != null) {
            contenuto.addView(Button(this).apply {
                text = "Imposta profilo e dati personali dal cedolino"
                setOnClickListener { proponiProfilo(ultimoConDati.anagrafica) }
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        }

        // riepilogo per anno, dal più recente
        val anni = (dati.cedolini.mapNotNull { it.anno } + dati.certificazioniUniche.mapNotNull { it.anno }).toSet().sortedDescending()
        anni.forEach { anno ->
            val delAnno = dati.cedolini.filter { it.anno == anno }
            contenuto.addView(titolo(anno.toString()))
            val card = scheda()
            contenuto.addView(card, LinearLayout.LayoutParams(-1, -2))

            if (delAnno.isNotEmpty()) {
                val ordinari = delAnno.filter { it.ordinario }
                val lordo = delAnno.sumOf { it.lordo }
                val netto = delAnno.sumOf { it.netto ?: 0.0 }
                val trattenute = delAnno.sumOf { it.trattenute }
                val straord = delAnno.flatMap { it.voci }.filter { Regex("STRAOR", RegexOption.IGNORE_CASE).containsMatchIn(it.descrizione) }
                card.addView(testo("${ordinari.size} cedolini mensili" +
                    (if (delAnno.size > ordinari.size) " + ${delAnno.size - ordinari.size} mensilità aggiuntive" else ""), 13f, Stile.secondario))
                card.addView(testo("Lordo ${euro(lordo)}  ·  Netto ${euro(netto)}", 16f, Stile.testo, true))
                card.addView(testo("Trattenute ${euro(trattenute)}", 14f, Stile.testo))
                if (straord.isNotEmpty()) card.addView(testo(
                    "Straordinario: ${ore(straord.sumOf { it.param ?: 0.0 })} ore · ${euro(straord.sumOf { it.competenza ?: 0.0 })} lordi",
                    14f, Stile.testo))
                card.addView(riga())
                delAnno.forEach { c -> card.addView(rigaMese(c, dati)) }
            }
            dati.certificazioniUniche.firstOrNull { it.anno == anno }?.let { cu ->
                if (delAnno.isNotEmpty()) card.addView(riga())
                card.addView(testo("CU ${cu.annoCu ?: ""}: reddito ${cu.reddito?.let { euro(it) } ?: "non letto"}" +
                    (cu.numeri["ritenuteIrpef"]?.let { " · IRPEF trattenuta ${euro(it)}" } ?: ""), 14f, Stile.testo))
                cu.numeri["totDetrazioni"]?.let { card.addView(testo("Detrazioni ${euro(it)}", 13f, Stile.secondario)) }
            }
        }

        contenuto.addView(Button(this).apply {
            text = "Cancella cedolini e CU dal telefono"
            setOnClickListener {
                AlertDialog.Builder(this@CedoliniActivity)
                    .setMessage("Cancellare tutti i cedolini e le CU importati? I servizi e le assenze non vengono toccati.")
                    .setPositiveButton("Cancella") { _, _ -> Archivio.eliminaCedolini(this@CedoliniActivity); mostra() }
                    .setNegativeButton("Annulla", null)
                    .show()
            }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(24) })
    }

    private fun rigaMese(c: Cedolino, dati: Dati): View {
        val mese = c.mese
        val nome = when {
            mese == null -> "Mese ?"
            mese in 1..12 -> Month.of(mese).getDisplayName(TextStyle.FULL_STANDALONE, Locale.ITALIAN)
                .replaceFirstChar { it.titlecase(Locale.ITALIAN) }
            mese == 13 -> "Tredicesima"
            else -> "Mensilità aggiuntiva ($mese)"
        }
        return TextView(this).apply {
            text = "${if (c.controlloOk) "✅" else "⚠️"} $nome   netto ${c.netto?.let { euro(it) } ?: "—"}"
            textSize = 16f
            setTextColor(Stile.testo)
            setPadding(0, dp(10), 0, dp(10))
            setOnClickListener { dettaglio(c, dati) }
        }
    }

    private fun dettaglio(c: Cedolino, dati: Dati) {
        val t = StringBuilder()
        t.appendLine("Netto ${c.netto?.let { euro(it) } ?: "non letto"}")
        t.appendLine("Lordo ${euro(c.lordo)} · Trattenute ${euro(c.trattenute)}")
        val imposte = listOf("inps" to "INPS", "irpef" to "IRPEF", "tassSep" to "Tass. separata", "fondi" to "Fondi")
            .mapNotNull { (k, n) -> c.n(k)?.let { "$n ${euro(it)}" } }
        if (imposte.isNotEmpty()) t.appendLine(imposte.joinToString(" · "))
        if (c.livello.isNotBlank()) t.appendLine("Livello ${c.livello}" + if (c.profilo.isNotBlank()) " · ${c.profilo}" else "")
        t.appendLine()
        if (c.controlloOk) t.appendLine("✅ Le somme delle voci tornano con i totali e con il netto.")
        else t.appendLine("⚠️ Le somme non tornano: voci ${euro(c.sommaCompetenze)} competenze e ${euro(c.sommaTrattenute)} " +
            "trattenute, totali stampati ${c.n("totCompetenze")?.let { euro(it) } ?: "?"} e ${c.n("totTrattenute")?.let { euro(it) } ?: "?"}. " +
            "Qualche voce potrebbe non essere stata letta.")
        t.appendLine()
        t.appendLine("Voci:")
        c.voci.forEach { v ->
            val valore = v.competenza?.let { "+" + euro(it) } ?: v.trattenuta?.let { "−" + euro(it) } ?: ""
            val parametro = v.param?.let { " (${ore(it)})" } ?: ""
            t.appendLine("${v.codice} ${v.descrizione}$parametro  $valore".trim())
        }
        // confronto con la stima dell'app (indicativo: le variabili spesso sono pagate nel mese dopo)
        val anno = c.anno
        val mese = c.mese
        if (anno != null && mese != null && mese in 1..12) {
            val ym = YearMonth.of(anno, mese)
            val stimaMese = Competenze.stimaMese(dati, ym).totale
            val stimaPrec = Competenze.stimaMese(dati, ym.minusMonths(1)).totale
            if (stimaMese > 0 || stimaPrec > 0) {
                val variabili = c.voci.filter {
                    Regex("STRAOR|INDENN|INCENT|DIARIA|NOTT|FEST|RISERV|SCORT|COND", RegexOption.IGNORE_CASE)
                        .containsMatchIn(it.descrizione)
                }.sumOf { it.competenza ?: 0.0 }
                t.appendLine()
                t.appendLine("Confronto indicativo con l'app:")
                t.appendLine("  voci variabili in busta ${euro(variabili)}")
                t.appendLine("  stima dell'app: mese precedente ${euro(stimaPrec)}, stesso mese ${euro(stimaMese)}")
            }
        }
        AlertDialog.Builder(this)
            .setTitle(c.periodoTesto.ifBlank { "Cedolino" })
            .setView(ScrollView(this).apply {
                addView(TextView(this@CedoliniActivity).apply {
                    text = t.toString(); textSize = 14f; setTextColor(Stile.testo)
                    setPadding(dp(20), dp(8), dp(20), dp(8))
                })
            })
            .setPositiveButton("Chiudi", null)
            .show()
    }

    /** Propone di impostare profilo, livello, CID, assunzione e ferie con i dati letti dal cedolino. */
    private fun proponiProfilo(a: AnagraficaCedolino) {
        val righe = a.descrizione()
        if (righe.isEmpty()) {
            Toast.makeText(this, "Nel cedolino non ho trovato dati personali", Toast.LENGTH_LONG).show()
            return
        }
        val attuale = Archivio.caricaTutto(this).impostazioni
        val cambia = a.profilo != null && attuale.profilo != null && a.profilo != attuale.profilo
        AlertDialog.Builder(this)
            .setTitle("Dati letti dal cedolino")
            .setMessage(righe.joinToString("\n") { "• $it" } +
                (if (cambia) "\n\nAttenzione: ora nelle Impostazioni hai ${attuale.profilo?.etichetta.orEmpty()}." else "") +
                "\n\nImposto regole, limiti e parametri di conseguenza? Puoi cambiarli quando vuoi dalle Impostazioni.")
            .setPositiveButton("Imposta") { _, _ ->
                val nuove = a.applica(Archivio.caricaTutto(this).impostazioni)
                Archivio.salvaImpostazioni(this, nuove)
                getPreferences(MODE_PRIVATE).edit().putInt("configurato", 1).apply()
                Toast.makeText(this, "Impostazioni aggiornate", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun proponiDatiFiscali(dati: Dati) {
        val p = DatiFiscali.daCedolini(dati.cedolini, dati.certificazioniUniche)
        if (p == null || (p.reddito == null && p.inpsPct == null && p.addComunalePct == null)) {
            Toast.makeText(this, "Servono almeno un cedolino o una CU letti bene", Toast.LENGTH_LONG).show()
            return
        }
        val msg = buildString {
            p.reddito?.let { appendLine("Reddito imponibile ${p.anno}: ${euro(it)} (${p.fonteReddito})") }
            p.inpsPct?.let { appendLine("INPS a carico del lavoratore: ${"%.2f".format(it)}%") }
            p.addComunalePct?.let { appendLine("Addizionale comunale: ${"%.2f".format(it)}%") }
            append("\nUsare questi valori per la stima del netto? Puoi sempre cambiarli nelle Impostazioni.")
        }
        AlertDialog.Builder(this)
            .setTitle("Dati per la stima del netto")
            .setMessage(msg)
            .setPositiveButton("Usa") { _, _ ->
                val imp = dati.impostazioni
                Archivio.salvaImpostazioni(this, imp.copy(
                    redditoAnnuo = p.reddito ?: imp.redditoAnnuo,
                    inpsPct = p.inpsPct ?: imp.inpsPct,
                    addComunalePct = p.addComunalePct ?: imp.addComunalePct
                ))
                Toast.makeText(this, "Impostazioni aggiornate", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    // ---------- importazione ----------

    private fun scegliFile() {
        if (inCorso) return
        val intento = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/pdf"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        startActivityForResult(intento, RICHIESTA_PDF)
    }

    @Deprecated("API di sistema semplice, senza librerie aggiuntive")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != RICHIESTA_PDF || resultCode != RESULT_OK || data == null) return
        val uris = mutableListOf<Uri>()
        data.clipData?.let { c -> for (i in 0 until c.itemCount) uris += c.getItemAt(i).uri }
        data.data?.let { if (it !in uris) uris += it }
        if (uris.isNotEmpty()) importa(uris)
    }

    private class Esito(val cedolini: MutableList<Cedolino> = mutableListOf(),
                        val cu: MutableList<CertificazioneUnica> = mutableListOf(),
                        val errori: MutableList<String> = mutableListOf(),
                        val dubbi: MutableList<String> = mutableListOf(),
                        var diagnosi: String? = null)

    private fun importa(uris: List<Uri>) {
        inCorso = true
        btnImporta.isEnabled = false
        barra.visibility = View.VISIBLE
        barra.max = uris.size
        barra.progress = 0
        stato.text = "Leggo i file…"
        Thread {
            val esito = Esito()
            uris.forEachIndexed { i, uri ->
                val nome = nomeFile(uri)
                try {
                    val pagine = PdfTesto.pagine(this, uri)
                    if (CuParser.èCu(pagine)) {
                        val cu = CuParser.parse(pagine, nome)
                        if (cu.valida) esito.cu += cu else {
                            esito.dubbi += "$nome (CU: reddito non trovato)"
                            if (esito.diagnosi == null) esito.diagnosi = PdfTesto.diagnostica(pagine, nome)
                        }
                    } else {
                        val c = CedolinoParser.parse(pagine, nome)
                        if (c.anno == null || c.mese == null || c.voci.isEmpty()) {
                            esito.errori += "$nome (${motivo(pagine, c)})"
                            if (esito.diagnosi == null) esito.diagnosi = PdfTesto.diagnostica(pagine, nome)
                        } else {
                            esito.cedolini += c
                            if (!c.controlloOk) {
                                esito.dubbi += "$nome (le somme non tornano)"
                                if (esito.diagnosi == null) esito.diagnosi = PdfTesto.diagnostica(pagine, nome)
                            }
                        }
                    }
                } catch (e: Exception) {
                    esito.errori += "$nome (${e.javaClass.simpleName}: ${e.message ?: "errore"})"
                }
                runOnUiThread { barra.progress = i + 1; stato.text = "Letti ${i + 1} file su ${uris.size}…" }
            }
            if (esito.cedolini.isNotEmpty() || esito.cu.isNotEmpty())
                Archivio.aggiungiCedolini(this, esito.cedolini, esito.cu)
            runOnUiThread {
                inCorso = false
                mostra()
                riepilogoImportazione(uris.size, esito)
            }
        }.start()
    }

    /** Perché un cedolino non è stato letto: testo estratto dal PDF e campi mancanti. */
    private fun motivo(pagine: List<List<Pezzo>>, c: Cedolino): String {
        val parti = mutableListOf("testo estratto: ${pagine.sumOf { it.size }} elementi su ${pagine.size} pagine")
        if (c.anno == null) parti += "anno non trovato"
        if (c.mese == null) parti += "mese non trovato"
        if (c.voci.isEmpty()) parti += "nessuna voce"
        return parti.joinToString(" · ")
    }

    private fun riepilogoImportazione(totale: Int, e: Esito) {
        val m = buildString {
            appendLine("Su $totale file: ${e.cedolini.size} cedolini e ${e.cu.size} CU letti.")
            if (e.dubbi.isNotEmpty()) appendLine("\nDa controllare:\n" + e.dubbi.joinToString("\n") { "• $it" })
            if (e.errori.isNotEmpty()) appendLine("\nNon letti:\n" + e.errori.joinToString("\n") { "• $it" })
        }
        val dialogo = AlertDialog.Builder(this)
            .setTitle("Importazione finita")
            .setMessage(m)
            .setPositiveButton("OK", null)
        val ultimo = e.cedolini.filter { !it.anagrafica.vuota }
            .maxWithOrNull(compareBy<Cedolino>({ it.anno ?: 0 }, { it.mese ?: 0 }))
        if (ultimo != null) dialogo.setOnDismissListener { proponiProfilo(ultimo.anagrafica) }
        e.diagnosi?.let { dettagli ->
            dialogo.setNeutralButton("Invia i dettagli") { _, _ ->
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Ferromicon - lettura PDF")
                    putExtra(Intent.EXTRA_TEXT,
                        "Attenzione: questo testo contiene i dati del documento. Invialo solo se vuoi.\n\n$dettagli")
                }, "Invia i dettagli"))
            }
        }
        dialogo.show()
    }

    private fun nomeFile(uri: Uri): String {
        try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) return c.getString(0) ?: "documento.pdf"
            }
        } catch (e: Exception) {
            // si usa il nome di riserva
        }
        return uri.lastPathSegment ?: "documento.pdf"
    }

    // ---------- piccoli pezzi di grafica ----------

    private fun euro(v: Double) = String.format(Locale.ITALIAN, "%,.2f €", v)
    private fun ore(v: Double) = String.format(Locale.ITALIAN, "%.1f h", v).replace(",0 h", " h")

    private fun testo(t: String, dimensione: Float, colore: Int, grassetto: Boolean = false) = TextView(this).apply {
        text = t; textSize = dimensione; setTextColor(colore)
        setPadding(0, dp(2), 0, dp(2))
        if (grassetto) setTypeface(typeface, Typeface.BOLD)
    }

    private fun titolo(t: String) = TextView(this).apply {
        val sp = SpannableString("▶ $t")
        sp.setSpan(ForegroundColorSpan(Stile.primario), 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        text = sp
        textSize = 17f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(Stile.testo)
        setPadding(dp(4), dp(18), 0, dp(6))
    }

    private fun scheda() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = Stile.scheda()
        setPadding(dp(14), dp(10), dp(14), dp(10))
    }

    private fun riga() = View(this).apply {
        setBackgroundColor(Stile.linea)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).apply {
            topMargin = dp(6); bottomMargin = dp(2)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_PRIMO = "primo_cedolino"
        private const val RICHIESTA_PDF = 31
    }
}
