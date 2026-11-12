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
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.time.Month
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Diario di bordo: si importa il PDF mensile, l'app lo legge sul telefono e lo confronta con l'agenda
 * (quello che era previsto) e col cedolino dello stesso mese (quello che è stato segnato e pagato).
 */
class DiarioActivity : Activity() {

    private lateinit var contenuto: LinearLayout
    private var selezionato: YearMonth? = null
    private var inCorso = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Diario di bordo"
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
    }

    override fun onResume() {
        super.onResume()
        window.decorView.post { Stile.rifinisci(window.decorView) }
        mostra()
    }

    private fun nomeMese(m: YearMonth) =
        Month.of(m.monthValue).getDisplayName(TextStyle.FULL_STANDALONE, Locale.ITALIAN)
            .replaceFirstChar { it.titlecase(Locale.ITALIAN) } + " " + m.year

    private fun ora(m: Int?) = if (m == null) "—" else ControlloDiario.ora(m)
    private fun ore(m: Int) = ControlloDiario.durata(m)

    private fun mostra() {
        contenuto.removeAllViews()
        contenuto.addView(testo("Il PDF del diario resta sul telefono: l'app lo legge qui, senza inviarlo da nessuna parte.",
            13f, Stile.secondario))
        contenuto.addView(Button(this).apply {
            text = "Importa il diario di bordo (PDF)"
            setOnClickListener { scegliFile() }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        val diari = DiarioArchivio.elenco(this)
        if (diari.isEmpty()) {
            contenuto.addView(titolo("Ancora niente"))
            contenuto.addView(scheda().apply {
                addView(testo("Importa il diario di bordo del mese: l'app ricava giornate, orari prescritti e reali, " +
                    "condotta o scorta, lavoro notturno e confronta tutto con la tua agenda e con il cedolino.", 15f, Stile.testo))
            })
            return
        }
        val diario = diari.firstOrNull { it.mese == selezionato } ?: diari.first()
        selezionato = diario.mese
        val dati = Archivio.caricaTutto(this)

        if (diari.size > 1) {
            contenuto.addView(titolo("Mesi importati"))
            val card = scheda()
            diari.forEach { d ->
                val m = d.mese ?: return@forEach
                card.addView(TextView(this).apply {
                    text = (if (d == diario) "● " else "○ ") + nomeMese(m) + "  ·  ${d.riepilogo().giorniServizio} servizi"
                    textSize = 16f
                    setTextColor(if (d == diario) Stile.primario else Stile.testo)
                    setPadding(0, dp(8), 0, dp(8))
                    setOnClickListener { selezionato = m; mostra() }
                })
            }
            contenuto.addView(card)
        }

        val r = diario.riepilogo()
        val meseTesto = diario.mese?.let { nomeMese(it) } ?: "Diario"
        contenuto.addView(titolo(meseTesto))
        contenuto.addView(scheda().apply {
            addView(testo("${diario.agente} · matricola ${diario.matricola} · ${diario.settore} ${diario.residenza}", 13f, Stile.secondario))
            addView(testo("${r.giorniServizio} giorni di servizio · ${r.riposi} riposi" +
                (if (r.ferie > 0) " · ${r.ferie} ferie" else "") +
                (if (r.malattia > 0) " · ${r.malattia} malattia" else "") +
                (if (r.altreAssenze > 0) " · ${r.altreAssenze} altre assenze" else ""), 15f, Stile.testo, true))
            val nomeCond = if (diario.profilo == Profilo.CAPOTRENO) "Scorta" else "Condotta"
            addView(testo("$nomeCond ${ore(r.condotta)}  ·  Lavoro ${ore(r.lavoro)}", 14f, Stile.testo))
            addView(testo("Notturno ${ore(r.lavoroNotturno)}  ·  Straordinario ${ore(r.straordinario)}", 14f, Stile.testo))
            addView(testo("Fuori residenza ${ore(r.fuoriResidenza)}  ·  Refezioni ${r.refezioni}", 14f, Stile.testo))
            if (r.giorniInRitardo > 0) addView(testo("${r.giorniInRitardo} giornate finite con almeno 15' di ritardo", 13f, Stile.secondario))
            if (!diario.totaliTornano) addView(testo("⚠️ I totali delle giornate non coincidono con quelli stampati nel diario: " +
                "controlla le righe prima di fidarti dei confronti.", 13f, Stile.attenzione))
            val prof = dati.impostazioni.profilo
            if (diario.profilo != null && prof != null && diario.profilo != prof)
                addView(testo("⚠️ Il diario è di un ${diario.profilo!!.etichetta}, ma nelle Impostazioni hai ${prof.etichetta}.",
                    13f, Stile.attenzione))
        })

        // confronto con l'agenda
        contenuto.addView(titolo("Diario contro agenda"))
        val conAgenda = ControlloDiario.confrontaConAgenda(diario, dati)
        contenuto.addView(scheda().apply {
            if (conAgenda.isEmpty()) addView(testo("✅ Tutte le giornate coincidono con l'agenda.", 15f, Stile.testo))
            else {
                addView(testo("${conAgenda.size} differenze da guardare", 15f, Stile.testo, true))
                conAgenda.forEach { addView(riga(it)) }
            }
        })
        val (mancServizi, mancAssenze) = ControlloDiario.giornateMancanti(diario, dati,
            dati.impostazioni.profiloAttivo, System.currentTimeMillis())
        if (mancServizi.isNotEmpty() || mancAssenze.isNotEmpty()) {
            contenuto.addView(Button(this).apply {
                text = "Completa l'agenda dal diario (${mancServizi.size} servizi, ${mancAssenze.size} giornate)"
                setOnClickListener {
                    AlertDialog.Builder(this@DiarioActivity)
                        .setTitle("Completare l'agenda?")
                        .setMessage("Aggiungo ${mancServizi.size} servizi (con gli orari reali del diario) e " +
                            "${mancAssenze.size} giornate di riposo o assenza che in agenda non ci sono. " +
                            "Le giornate già in agenda non cambiano.")
                        .setPositiveButton("Aggiungi") { _, _ ->
                            Archivio.copiaDiSicurezza(this@DiarioActivity)
                            Archivio.aggiungi(this@DiarioActivity, mancServizi, mancAssenze)
                            Toast.makeText(this@DiarioActivity, "Agenda completata", Toast.LENGTH_SHORT).show()
                            mostra()
                        }
                        .setNegativeButton("Annulla", null)
                        .show()
                }
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        }

        // confronto con il cedolino
        contenuto.addView(titolo("Diario contro cedolino"))
        val m = diario.mese
        val ced = if (m == null) null else dati.cedolini.firstOrNull { it.anno == m.year && it.mese == m.monthValue }
        contenuto.addView(scheda().apply {
            if (ced == null) {
                addView(testo("Nessun cedolino di ${m?.let { nomeMese(it) } ?: "questo mese"} tra quelli importati. " +
                    "Caricalo da Cedolini e CU per controllare presenze, riposi, ferie e ticket pasto.", 14f, Stile.testo))
                addView(Button(this@DiarioActivity).apply {
                    text = "Apri Cedolini e CU"
                    setOnClickListener { startActivity(Intent(this@DiarioActivity, CedoliniActivity::class.java)) }
                })
            } else {
                val dif = ControlloDiario.confrontaConCedolino(diario, ced)
                if (dif.isEmpty()) addView(testo("✅ Presenze, riposi, ferie e ticket coincidono con il cedolino.", 15f, Stile.testo))
                else {
                    addView(testo("${dif.size} differenze da guardare", 15f, Stile.testo, true))
                    dif.forEach { addView(riga(it)) }
                }
                addView(testo("Nel cedolino: ${ced.presenze["presenze"]?.toInt() ?: "—"} presenze, " +
                    "${ced.presenze["riposi"]?.toInt() ?: "—"} riposi, ${ced.presenze["ferie"]?.toInt() ?: "—"} ferie.",
                    13f, Stile.secondario))
            }
        })

        // giornate
        contenuto.addView(titolo("Giornate"))
        val giorno = DateTimeFormatter.ofPattern("EEE dd/MM", Locale.ITALIAN)
        contenuto.addView(scheda().apply {
            diario.giorni.forEach { g ->
                val riassunto = if (g.èServizio) {
                    "${ora(g.inizioReale)}–${ora(g.fineReale)} · lavoro ${ore(g.lavoroEffettivo)}" +
                        (if (g.condotta > 0) " · ${ore(g.condotta)}" else "") +
                        (g.ritardoFinale?.takeIf { it >= 5 }?.let { " · +$it'" } ?: "")
                } else ""
                addView(TextView(this@DiarioActivity).apply {
                    text = "${giorno.format(g.data)}   ${g.etichetta}" + (if (riassunto.isNotEmpty()) "\n$riassunto" else "")
                    textSize = 14f
                    setTextColor(Stile.testo)
                    setPadding(0, dp(8), 0, dp(8))
                    if (g.èServizio) setOnClickListener { dettaglioGiorno(g) }
                })
            }
        })

        contenuto.addView(Button(this).apply {
            text = "Cancella i diari dal telefono"
            setOnClickListener {
                AlertDialog.Builder(this@DiarioActivity)
                    .setMessage("Cancellare tutti i diari di bordo importati? Agenda e cedolini non vengono toccati.")
                    .setPositiveButton("Cancella") { _, _ -> DiarioArchivio.cancellaTutti(this@DiarioActivity); mostra() }
                    .setNegativeButton("Annulla", null)
                    .show()
            }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(24) })
    }

    private fun riga(d: Discrepanza) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(6), 0, dp(6))
        val simbolo = when (d.gravita) { Gravita.VIOLAZIONE -> "⛔"; Gravita.ATTENZIONE -> "⚠️"; else -> "ℹ️" }
        addView(testo("$simbolo ${d.titolo}", 14f, Stile.testo, true))
        addView(testo(d.dettaglio, 13f, Stile.secondario))
    }

    private fun dettaglioGiorno(g: GiornoDiario) {
        val t = buildString {
            appendLine("Lavoro ${ore(g.lavoroEffettivo)} · ${if (g.condotta > 0) "condotta/scorta ${ore(g.condotta)}" else "senza condotta"}" +
                (if (g.lavoroNotturno > 0) " · notturno ${ore(g.lavoroNotturno)}" else ""))
            appendLine()
            g.attivita.forEach { a ->
                append(a.tipo)
                if (a.treno.isNotEmpty()) append(" ${a.treno}")
                if (a.da.isNotEmpty()) append("  ${a.da} → ${a.a}")
                appendLine()
                append("   previsto ${ora(a.prescrittoPartenza)}–${ora(a.prescrittoArrivo)}, reale ${ora(a.realePartenza)}–${ora(a.realeArrivo)}")
                a.ritardoArrivo?.takeIf { it != 0 }?.let { append(" (${if (it > 0) "+" else ""}$it')") }
                if (a.accessoriePartenza + a.accessorieArrivo > 0) append(" · accessorie ${a.accessoriePartenza}+${a.accessorieArrivo}'")
                appendLine()
            }
        }
        AlertDialog.Builder(this)
            .setTitle("${g.turno} del ${DateTimeFormatter.ofPattern("dd/MM").format(g.data)}")
            .setMessage(t)
            .setPositiveButton("Chiudi", null)
            .show()
    }

    // ---------- importazione ----------

    private fun scegliFile() {
        if (inCorso) return
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/pdf"
        }, RICHIESTA_PDF)
    }

    @Deprecated("API di sistema semplice, senza librerie aggiuntive")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != RICHIESTA_PDF || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        importa(uri)
    }

    private fun importa(uri: Uri) {
        inCorso = true
        Toast.makeText(this, "Leggo il diario…", Toast.LENGTH_SHORT).show()
        Thread {
            var esito = ""
            var mese: YearMonth? = null
            try {
                val pagine = PdfTesto.pagine(this, uri)
                if (!DiarioBordo.èDiario(pagine)) {
                    esito = "Il file ${nomeFile(uri)} non sembra un diario di bordo."
                } else {
                    val d = DiarioBordo.leggi(pagine)
                    if (d == null || d.mese == null) esito = "Non riesco a leggere le giornate di questo diario."
                    else {
                        DiarioArchivio.salva(this, d)
                        mese = d.mese
                        esito = "Diario di ${nomeMese(d.mese!!)} letto: ${d.riepilogo().giorniServizio} giorni di servizio." +
                            if (d.totaliTornano) "" else "\nAttenzione: i totali non tornano con quelli stampati."
                    }
                }
            } catch (e: Exception) {
                esito = "Lettura non riuscita: ${e.javaClass.simpleName}: ${e.message ?: "errore"}"
            }
            runOnUiThread {
                inCorso = false
                if (mese != null) selezionato = mese
                mostra()
                Toast.makeText(this, esito, Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    private fun nomeFile(uri: Uri): String {
        try {
            contentResolver.query(uri, null, null, null, null)?.use { c ->
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i >= 0 && c.moveToFirst()) return c.getString(i) ?: "il file"
            }
        } catch (e: Exception) {
            android.util.Log.w("Ferromicon", "Nome del file non letto", e)
        }
        return "il file"
    }

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

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val RICHIESTA_PDF = 41
    }
}
