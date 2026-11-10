package it.ferromicon

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.content.Intent
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * "Il mio turno": il personale inserisce le giornate del turno del suo impianto, indica a che
 * punto del ciclo si trova oggi e fino a quando vale il turno (cambio orario). L'agenda si
 * compila da sola fino a quella data; ogni giorno si può ricaricare la giornata di default
 * e correggerla (treni in più o in meno), e tutto si ricalcola.
 */
class TurnoActivity : Activity() {

    private val fmt = DateTimeFormatter.ofPattern("EEE d MMMM yyyy", Locale.ITALIAN)
    private lateinit var contenuto: LinearLayout
    private val giornate = mutableListOf<String>()
    private lateinit var nome: EditText
    private lateinit var impianto: EditText
    private lateinit var posizione: EditText
    private lateinit var btnFine: Button
    private var fine: LocalDate = CambioOrario.ultimoGiornoDelTurno(LocalDate.now())
    private lateinit var elenco: LinearLayout
    private var preferiti: List<Preferito> = emptyList()
    private var daData: LocalDate = LocalDate.now()
    private val fmtOra = DateTimeFormatter.ofPattern("HH:mm")
    private lateinit var btnDa: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Il mio turno"
        val dati = Archivio.caricaTutto(this)
        val t = dati.turno
        preferiti = dati.preferiti
        if (t != null) {
            giornate += t.giornate
            fine = t.validoFino
        }

        contenuto = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
        }
        setContentView(ScrollView(this).apply { addView(contenuto) })

        contenuto.addView(testo("Profilo: ${dati.impostazioni.profiloAttivo.etichetta}", 15f, true))
        if (t != null) {
            val g = t.giorniAllaFine(LocalDate.now())
            contenuto.addView(testo(
                if (g < 0) "⛔ Il turno è scaduto il ${fmt.format(t.validoFino)}."
                else "Il turno finisce tra $g giorni (${fmt.format(t.validoFino)}).", 13f))
        }
        contenuto.addView(Button(this).apply {
            text = "Come funziona"
            setOnClickListener {
                AlertDialog.Builder(this@TurnoActivity).setTitle("Come funziona")
                    .setMessage("1. Salva ogni giornata del tuo turno come servizio preferito, con i suoi treni.\n\n" +
                        "2. Qui elenca le giornate nell'ordine del ciclo e indica a che giornata sei oggi.\n\n" +
                        "3. Controlla il ciclo: verifica riposi e limiti, anche nel rientro dall'ultima alla prima giornata.\n\n" +
                        "4. Compila il calendario fino al cambio turno. I giorni modificati da te restano come sono.\n\n" +
                        "5. Ogni giorno, dall'agenda, puoi caricare la giornata di turno o un'altra del ciclo, " +
                        "correggere i treni e ricalcolare tutto.")
                    .setPositiveButton("OK", null).show()
            }
        })
        contenuto.addView(testo(
            "Inserisci le giornate del turno del tuo impianto nell'ordine in cui si ripetono. Ogni giornata " +
                "è un servizio preferito (con i suoi treni) oppure una sigla: R riposo, RS riposo settimanale, " +
                "FE ferie, D disponibilità, - vuota. Per creare una giornata: apri un servizio in agenda, " +
                "aggiungi i treni e tocca \"Salva come preferito\" con il nome della giornata.", 13f))

        contenuto.addView(etichetta("Nome del turno"))
        nome = EditText(this).apply { hint = "Es. Turno PdM 2026"; setText(t?.nome ?: "") }
        contenuto.addView(nome)
        contenuto.addView(etichetta("Impianto"))
        impianto = EditText(this).apply { hint = "Es. Milano Centrale"; setText(t?.impianto ?: "") }
        contenuto.addView(impianto)

        contenuto.addView(etichetta("Giornate del turno"))
        elenco = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Stile.scheda()
            setPadding(dp(10), dp(6), dp(10), dp(6))
        }
        contenuto.addView(elenco)
        contenuto.addView(Button(this).apply {
            text = "+ Aggiungi giornata"
            setOnClickListener { aggiungi(preferiti) }
        })
        contenuto.addView(Button(this).apply {
            text = "Crea giornata da un servizio in agenda"
            setOnClickListener { daServizio() }
        })
        contenuto.addView(Button(this).apply {
            text = "Incolla una sequenza"
            setOnClickListener { incolla() }
        })
        contenuto.addView(Button(this).apply {
            text = "Condividi il ciclo"
            setOnClickListener {
                if (giornate.isEmpty()) return@setOnClickListener
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, giornate.joinToString(", "))
                }, "Condividi il ciclo"))
            }
        })

        contenuto.addView(etichetta("Oggi sono alla giornata n°"))
        val oggi = LocalDate.now()
        posizione = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText((t?.numeroGiornata(oggi) ?: 1).toString())
        }
        contenuto.addView(posizione)

        contenuto.addView(etichetta("Il turno vale fino al"))
        contenuto.addView(testo("Predefinito: il giorno prima del prossimo cambio orario " +
            "(seconda domenica di giugno o di dicembre). Puoi cambiarlo.", 12f))
        btnFine = Button(this).apply {
            setOnClickListener {
                DatePickerDialog(this@TurnoActivity, { _, y, m, g ->
                    fine = LocalDate.of(y, m + 1, g); aggiornaFine()
                }, fine.year, fine.monthValue - 1, fine.dayOfMonth).show()
            }
        }
        contenuto.addView(btnFine)
        contenuto.addView(Button(this).apply {
            text = "Prolunga fino al prossimo cambio orario"
            setOnClickListener {
                fine = CambioOrario.ultimoGiornoDelTurno(fine.plusDays(1)); aggiornaFine()
            }
        })

        contenuto.addView(Button(this).apply {
            text = "Controlla il ciclo"
            setOnClickListener { controlla(dati) }
        })
        contenuto.addView(etichetta("Compila a partire dal"))
        btnDa = Button(this).apply {
            setOnClickListener {
                DatePickerDialog(this@TurnoActivity, { _, y, m, g ->
                    daData = LocalDate.of(y, m + 1, g); aggiornaDa()
                }, daData.year, daData.monthValue - 1, daData.dayOfMonth).show()
            }
        }
        contenuto.addView(btnDa)
        contenuto.addView(Button(this).apply {
            text = "Compila il calendario"
            setOnClickListener { compila(dati) }
        })
        contenuto.addView(Button(this).apply {
            text = "Rimuovi i giorni generati dal turno"
            setOnClickListener { rimuovi(dati) }
        })
        contenuto.addView(testo("I giorni già inseriti o modificati a mano non vengono toccati. " +
            "Se cambi il turno o una giornata, ricompila: i giorni non modificati si aggiornano.", 12f))
        if (t != null) contenuto.addView(Button(this).apply {
            text = "Elimina il turno"
            setOnClickListener {
                AlertDialog.Builder(this@TurnoActivity)
                    .setMessage("Elimino il turno salvato? I servizi già in agenda restano.")
                    .setPositiveButton("Elimina") { _, _ -> Archivio.salvaTurno(this@TurnoActivity, null); finish() }
                    .setNegativeButton("Annulla", null).show()
            }
        })
        mostraGiornate()
        aggiornaFine()
        aggiornaDa()
    }

    private fun ignota(g: String) = g != "-" && g != "." && Generatore.sigla(g) == null &&
        preferiti.none { it.nome.equals(g, ignoreCase = true) }

    /** Salva un servizio già in agenda come giornata del turno (preferito) e la aggiunge al ciclo. */
    private fun daServizio() {
        val recenti = Archivio.caricaTutto(this).servizi.sortedByDescending { it.turno.inizio }.take(40)
        if (recenti.isEmpty()) {
            Toast.makeText(this, "Non ci sono servizi in agenda", Toast.LENGTH_SHORT).show(); return
        }
        val f = DateTimeFormatter.ofPattern("dd/MM HH:mm", Locale.ITALIAN)
        val h = DateTimeFormatter.ofPattern("HH:mm", Locale.ITALIAN)
        AlertDialog.Builder(this).setTitle("Scegli il servizio")
            .setItems(recenti.map { "${f.format(it.turno.inizio)}–${h.format(it.turno.fine)} · ${it.righe.size} righe" }
                .toTypedArray()) { _, i ->
                val servizio = recenti[i]
                val campo = EditText(this).apply { hint = "Nome della giornata"; setText("Giornata ${giornate.size + 1}") }
                AlertDialog.Builder(this).setTitle("Nome della giornata").setView(campo)
                    .setPositiveButton("Salva e aggiungi") { _, _ ->
                        val nomeG = campo.text.toString().trim()
                        if (nomeG.isEmpty()) return@setPositiveButton
                        Archivio.salvaPreferito(this, Preferito.da(servizio, nomeG, System.currentTimeMillis()))
                        preferiti = Archivio.caricaTutto(this).preferiti
                        giornate += nomeG; mostraGiornate()
                    }.setNegativeButton("Annulla", null).show()
            }.show()
    }

    private fun incolla() {
        val campo = EditText(this).apply { hint = "Mattina, Mattina, Notte, R, RS" }
        AlertDialog.Builder(this).setTitle("Incolla la sequenza").setView(campo)
            .setPositiveButton("Sostituisci il ciclo") { _, _ ->
                val e = Generatore.elementi(campo.text.toString())
                if (e.isNotEmpty()) { giornate.clear(); giornate += e; mostraGiornate() }
            }.setNegativeButton("Annulla", null).show()
    }

    private fun aggiornaFine() { btnFine.text = fmt.format(fine) }
    private fun aggiornaDa() { btnDa.text = fmt.format(daData) }

    private fun mostraGiornate() {
        elenco.removeAllViews()
        if (giornate.isEmpty()) elenco.addView(testo("Nessuna giornata inserita.", 13f))
        giornate.forEachIndexed { i, g ->
            elenco.addView(Button(this).apply {
                val orari = preferiti.firstOrNull { it.nome.equals(g, ignoreCase = true) }
                    ?.let { "   ${fmtOra.format(it.oraInizio)}–${fmtOra.format(it.oraFine)}" } ?: ""
                text = "${i + 1}.  ${if (ignota(g)) "⚠ " else ""}$g$orari"
                isAllCaps = false
                setOnClickListener {
                    AlertDialog.Builder(this@TurnoActivity).setTitle("Giornata ${i + 1}: $g")
                        .setItems(arrayOf("Sposta su", "Sposta giù", "Rimuovi")) { _, w ->
                            when (w) {
                                0 -> if (i > 0) { giornate.add(i - 1, giornate.removeAt(i)) }
                                1 -> if (i < giornate.size - 1) { giornate.add(i + 1, giornate.removeAt(i)) }
                                2 -> giornate.removeAt(i)
                            }
                            mostraGiornate()
                        }.show()
                }
            })
        }
    }

    private fun aggiungi(preferiti: List<Preferito>) {
        val voci = preferiti.sortedBy { it.nome }.map { it.nome } +
            listOf("R", "RS", "FE", "D", "-")
        val etichette = voci.map {
            when (it) {
                "R" -> "R · Giornata di riposo"; "RS" -> "RS · Riposo settimanale"
                "FE" -> "FE · Ferie"; "D" -> "D · Disponibilità"; "-" -> "- · Giornata vuota"
                else -> "⭐ $it"
            }
        }
        AlertDialog.Builder(this).setTitle("Aggiungi giornata")
            .setItems(etichette.toTypedArray()) { _, i -> giornate += voci[i]; mostraGiornate() }
            .show()
    }

    private fun costruisci(): TurnoImpianto? {
        if (giornate.isEmpty()) {
            Toast.makeText(this, "Aggiungi almeno una giornata", Toast.LENGTH_SHORT).show(); return null
        }
        val n = (posizione.text.toString().toIntOrNull() ?: 1).coerceIn(1, giornate.size)
        return TurnoImpianto(nome.text.toString().trim(), impianto.text.toString().trim(),
            giornate.toList(), LocalDate.now(), n - 1, fine)
    }

    private fun compila(dati: Dati) {
        val turno = costruisci() ?: return
        Archivio.salvaTurno(this, turno)
        val c = PianoTurno.compila(turno, daData, dati.preferiti, dati, System.currentTimeMillis())
        anteprima(dati, turno, c, forzato = false)
    }

    private fun anteprima(dati: Dati, turno: TurnoImpianto, c: Compilazione, forzato: Boolean) {
        val tutti = (dati.servizi - c.rimuoviServizi.toSet() + c.servizi).sortedBy { it.turno.inizio }
        val assenzeTutte = dati.assenze - c.rimuoviAssenze.toSet() + c.assenze
        val nuovi = c.servizi.map { it.id }.toSet()
        val violazioni = tutti.withIndex().count { (i, sv) ->
            sv.id in nuovi && Analisi.verificaServizio(tutti, i, assenzeTutte, dati.impostazioni.limitiOrario)
                .any { it.gravita == Gravita.VIOLAZIONE }
        }
        val profilo = dati.impostazioni.profiloAttivo
        val altroProfilo = c.servizi.count { it.turno.profilo != profilo }
        val msg = buildString {
            append("Dal ${fmt.format(daData)} al ${fmt.format(turno.validoFino)}:\n")
            append("• ${c.servizi.size} servizi da inserire\n• ${c.assenze.size} giornate di riposo o assenza\n")
            if (c.rimuoviServizi.isNotEmpty() || c.rimuoviAssenze.isNotEmpty())
                append("• ${c.rimuoviServizi.size + c.rimuoviAssenze.size} voci da aggiornare\n")
            if (c.saltati > 0) append("• ${c.saltati} giorni lasciati com'erano (inseriti o modificati da te)\n")
            if (violazioni > 0) append("\n⛔ $violazioni servizi non rispettano riposi o limiti: li vedrai in agenda.")
            if (altroProfilo > 0) append("\n⚠ $altroProfilo servizi sono di profilo diverso dal tuo (${profilo.etichetta}).")
            if (c.sconosciuti.isNotEmpty())
                append("\nNon trovo nei preferiti: ${c.sconosciuti.joinToString()}. Questi giorni restano vuoti.")
            if (forzato) append("\n\nI servizi modificati finiscono nel cestino.")
        }
        val dialogo = AlertDialog.Builder(this).setTitle("Compila il calendario")
            .setMessage(msg)
            .setPositiveButton("Compila") { _, _ ->
                Archivio.copiaDiSicurezza(this)
                Archivio.applicaCompilazione(this, c)
                Toast.makeText(this, "Calendario compilato", Toast.LENGTH_SHORT).show()
                finish()
            }
            .setNegativeButton("Annulla", null)
        if (!forzato && c.saltati > 0) dialogo.setNeutralButton("Rigenera anche i modificati") { _, _ ->
            val tutto = PianoTurno.compila(turno, daData, dati.preferiti, dati,
                System.currentTimeMillis(), forza = true)
            anteprima(dati, turno, tutto, forzato = true)
        }
        dialogo.show()
    }

    private fun controlla(dati: Dati) {
        val turno = costruisci() ?: return
        val r = PianoTurno.riepilogoCiclo(turno, dati.preferiti, dati.impostazioni.limitiOrario)
        val ore = r.minutiPresenza / 60.0
        val msg = buildString {
            append("${r.giornate} giornate: ${r.lavorative} di servizio, ${r.riposi} di riposo.\n")
            append("Presenza nel ciclo: ${Riepilogo.ore(r.minutiPresenza.toLong())}")
            if (r.lavorative > 0) append(" (media ${"%.1f".format(Locale.ITALIAN, ore / r.lavorative)} ore per servizio)")
            append(".\nGiornate con notte: ${r.notturne}.\n")
            if (r.problemi.isEmpty()) append("\n✅ Nessuna violazione di riposo o limiti nel ciclo, rientro compreso.")
            else append("\n⛔ Da correggere:\n" + r.problemi.joinToString("\n") { "• $it" })
            val ignote = turno.sconosciute(dati.preferiti)
            if (ignote.isNotEmpty()) append("\n\n⚠ Non trovo nei preferiti: ${ignote.joinToString()}.")
        }
        AlertDialog.Builder(this).setTitle("Controllo del ciclo").setMessage(msg)
            .setPositiveButton("OK", null).show()
    }

    private fun rimuovi(dati: Dati) {
        val c = PianoTurno.rimuoviGenerati(dati.turno, daData, dati.preferiti, dati)
        val n = c.rimuoviServizi.size + c.rimuoviAssenze.size
        AlertDialog.Builder(this).setTitle("Rimuovi i giorni generati")
            .setMessage(if (n == 0) "Non ci sono giorni generati dal turno da ${fmt.format(daData)} in poi."
                else "Tolgo $n voci generate dal turno dal ${fmt.format(daData)} in poi. " +
                    "I giorni inseriti o modificati da te restano.")
            .setPositiveButton(if (n == 0) "OK" else "Rimuovi") { _, _ ->
                if (n > 0) {
                    Archivio.copiaDiSicurezza(this)
                    Archivio.applicaCompilazione(this, c)
                    Toast.makeText(this, "Giorni rimossi", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Annulla", null).show()
    }

    private fun testo(t: String, size: Float, grassetto: Boolean = false) = TextView(this).apply {
        text = t; textSize = size; setTextColor(Stile.testo)
        if (grassetto) setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(6), 0, dp(2))
    }

    private fun etichetta(t: String) = TextView(this).apply {
        text = t; textSize = 16f; setTextColor(Stile.testo); setPadding(0, dp(16), 0, 0)
    }

    override fun onResume() {
        super.onResume()
        window.decorView.post { Stile.rifinisci(window.decorView) }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
