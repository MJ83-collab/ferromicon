package it.ferromicon

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Agenda mensile: servizi, assenze, riepilogo e controlli del mese. */
class MainActivity : Activity() {

    private var mese: YearMonth = YearMonth.now()
    private var selezionePeriodo = false
    private var periodoInizio: LocalDate? = null
    private var periodoFine: LocalDate? = null
    private lateinit var gesti: android.view.GestureDetector

    /** Lo swipe orizzontale nella scheda Agenda cambia mese; non sostituisce i tocchi sulle celle. */
    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        if (::gesti.isInitialized) gesti.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }
    private lateinit var contenuto: LinearLayout
    private lateinit var titolo: TextView

    private enum class Scheda { AGENDA, RIEPILOGO, STRUMENTI }
    private var scheda = Scheda.AGENDA
    /** Dove finiscono testi e voci: la scheda (card) della sezione corrente. */
    private lateinit var destinazione: LinearLayout
    private lateinit var intestazioneMese: LinearLayout
    private lateinit var barraAzioni: LinearLayout
    private val tabs = mutableListOf<TextView>()
    private val locale = Locale.ITALIAN
    private val fmtGiorno = DateTimeFormatter.ofPattern("EEE d", Locale.ITALIAN)
    private val fmtGiornoLungo = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ITALIAN)
    private val fmtOra = DateTimeFormatter.ofPattern("HH:mm")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!BenvenutoActivity.visto(this)) {
            startActivity(Intent(this, BenvenutoActivity::class.java))
            finish()
            return
        }
        if (savedInstanceState == null) Aggiornamenti.controllaAllAvvio(this)
        savedInstanceState?.getString("mese")?.let { mese = YearMonth.parse(it) }
        savedInstanceState?.getString("scheda")?.let { nome ->
            scheda = Scheda.values().firstOrNull { it.name == nome } ?: Scheda.AGENDA
        }

        gesti = android.view.GestureDetector(this, object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: android.view.MotionEvent?, e2: android.view.MotionEvent, vx: Float, vy: Float): Boolean {
                val a = e1 ?: return false
                val dx = e2.x - a.x
                val dy = e2.y - a.y
                if (scheda != Scheda.AGENDA || Math.abs(dx) < dp(96) || Math.abs(dx) < Math.abs(dy) * 1.5f) return false
                mese = if (dx < 0) mese.plusMonths(1) else mese.minusMonths(1)
                aggiorna()
                return true
            }
        })
        runCatching { AvvisoPermessi.controllaMensile(this) }
        runCatching { CalendarioTelefono.riallineaDopoAggiornamento(this) }
        val radice = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        radice.setOnApplyWindowInsetsListener { v, insets ->
            v.setPadding(0, insetAlto(insets), 0, insetBasso(insets))
            insets
        }

        // Barra del mese: tocca il titolo per tornare a oggi
        intestazioneMese = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(4))
        }
        titolo = TextView(this).apply {
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setOnClickListener { mese = YearMonth.now(); aggiorna() }
        }
        intestazioneMese.addView(Button(this).apply { text = "‹"; setOnClickListener { mese = mese.minusMonths(1); aggiorna() } })
        intestazioneMese.addView(titolo, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        intestazioneMese.addView(Button(this).apply { text = "›"; setOnClickListener { mese = mese.plusMonths(1); aggiorna() } })
        radice.addView(intestazioneMese)

        // Azioni principali, sempre a portata di pollice nella scheda Agenda
        barraAzioni = LinearLayout(this).apply { setPadding(dp(12), 0, dp(12), dp(4)) }
        listOf(
            "+ Servizio" to { nuovoServizio() },
            "+ Assenza" to { nuovaAssenza() },
            "Periodo" to { attivaPeriodo() }
        ).forEach { (etichetta, azione) ->
            barraAzioni.addView(Button(this).apply { text = etichetta; setOnClickListener { azione() } },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(dp(4), 0, dp(4), 0)
                })
        }
        radice.addView(barraAzioni)

        contenuto = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(4), dp(16), dp(16))
        }
        destinazione = contenuto
        radice.addView(ScrollView(this).apply { addView(contenuto) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        radice.addView(costruisciBarraTab())

        setContentView(radice)

        // Android 13+: permesso per mostrare i promemoria
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        Promemoria.programma(this)
        primaConfigurazione()
        segnalaErroreSePresente()
    }

    // ---------- menu: impostazioni, backup, ripristino ----------

    // ---------- navigazione: barra in basso e scheda Strumenti ----------

    @Suppress("DEPRECATION")
    private fun insetAlto(i: android.view.WindowInsets): Int =
        if (Build.VERSION.SDK_INT >= 30) i.getInsets(android.view.WindowInsets.Type.statusBars()).top
        else i.systemWindowInsetTop

    private fun insetBasso(i: android.view.WindowInsets): Int =
        if (Build.VERSION.SDK_INT >= 30) i.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom
        else i.systemWindowInsetBottom

    private fun costruisciBarraTab(): View {
        val barra = LinearLayout(this).apply {
            setBackgroundColor(Stile.superficie)
            elevation = dp(8).toFloat()
        }
        tabs.clear()
        val voci: List<Triple<String, String, () -> Unit>> = listOf(
            Triple("📅", "Agenda", { scheda = Scheda.AGENDA; aggiorna() }),
            Triple("📊", "Riepilogo", { scheda = Scheda.RIEPILOGO; aggiorna() }),
            Triple("🧰", "Strumenti", { scheda = Scheda.STRUMENTI; aggiorna() }),
            Triple("⚙️", "Impostazioni", { startActivity(Intent(this, ImpostazioniActivity::class.java)) })
        )
        voci.forEach { (icona, nome, azione) ->
            val voce = TextView(this).apply {
                text = "$icona\n$nome"
                gravity = Gravity.CENTER
                textSize = 11f
                setPadding(0, dp(8), 0, dp(8))
                setOnClickListener { azione() }
            }
            tabs += voce
            barra.addView(voce, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        return barra
    }

    private fun aggiornaBarre() {
        intestazioneMese.visibility = if (scheda == Scheda.STRUMENTI) View.GONE else View.VISIBLE
        barraAzioni.visibility = if (scheda == Scheda.AGENDA) View.VISIBLE else View.GONE
        val ordine = listOf(Scheda.AGENDA, Scheda.RIEPILOGO, Scheda.STRUMENTI)
        tabs.forEachIndexed { i, voce ->
            val scelta = i < ordine.size && ordine[i] == scheda
            voce.setTextColor(if (scelta) Stile.primario else Stile.secondario)
            voce.setTypeface(null, if (scelta) Typeface.BOLD else Typeface.NORMAL)
        }
        title = when (scheda) {
            Scheda.AGENDA -> Archivio.caricaTutto(this).impostazioni.nome.let { if (it.isBlank()) "Ciao" else "Ciao, $it" }
            Scheda.RIEPILOGO -> "Riepilogo del mese"
            Scheda.STRUMENTI -> "Strumenti"
        }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (scheda != Scheda.AGENDA) { scheda = Scheda.AGENDA; aggiorna() } else super.onBackPressed()
    }

    private class Strumento(val icona: String, val titolo: String, val nota: String, val id: Int)

    private fun strumenti() {
        gruppo("Agenda", listOf(
            Strumento("📍", "Vai a oggi", "Torna al mese corrente", 4),
            Strumento("🧭", "Il mio turno", "Compila fino al cambio turno", 19),
            Strumento("👤", "Calendario agente", "CID o nome: giornate, riposi, ferie", 21),
            Strumento("📲", "Calendario del telefono", "Sincronizza l'agenda con Google Calendar", 22),
            Strumento("🏖️", "Pianifica ferie", "I periodi più lunghi a casa", 20),
            Strumento("⭐", "Servizi preferiti", "Modelli da inserire con un tocco", 6),
            Strumento("📥", "Importa turno", "Da testo incollato", 7),
            Strumento("📂", "Importa file", "Zip, .ics, backup .json o PDF: riconosce il tipo", 31),
            Strumento("🔁", "Turno ciclico", "Genera più settimane", 8)
        ))
        gruppo("Paga e regole", listOf(
            Strumento("📆", "Riepilogo dell'anno", "Mese per mese", 5),
            Strumento("🎌", "Recupero festivi", "Scadenze a 90 giorni", 14),
            Strumento("🧾", "Cedolini e CU", "Importa i PDF e confronta", 18),
            Strumento("📔", "Diario di bordo", "Controlla diario, turno e cedolino", 23),
            Strumento("⬆️", "Aggiornamenti", Aggiornamenti.riga(this), 24),
            Strumento("💶", "Straordinario netto", "Quanto resta in tasca", 16),
            Strumento("🏠", "Perimetri di residenza", "Ore fuori residenza", 15),
            Strumento("📖", "Wiki Ferrovia", "Tempi accessori, abbreviazioni, permessi: senza connessione", 40),
            Strumento("🧮", "Cartellino SAP", "Importa il PDF: timbrature e flessibilità", 41)
        ))
        gruppo("Orari e rubrica", listOf(
            Strumento("🎫", "Abilitazioni", "Linee e tipi di treno per cui sei abilitato", 43),
            Strumento("🔎", "Cerca treno", "Cronologia per numero: mesi e turni", 44),
            Strumento("🚆", "Orario Azienda", "Treni e fermate", 13),
            Strumento("📒", "Rubrica stazioni", "Numeri utili", 12),
            Strumento("🗓️", "Esporta calendario", "File .ics: scegli cosa, anche su Drive", 9),
            Strumento("📥", "Importa calendario", "File .ics da Drive o telefono", 30),
            Strumento("📄", "Esporta in CSV", "Servizi e assenze", 17)
        ))
        gruppo("Backup", listOf(
            Strumento("💾", "Backup su file", "Salva una copia", 2),
            Strumento("♻️", "Ripristina da file", "Riporta una copia", 3),
            Strumento("🕒", "Backup automatici", "Una copia al giorno", 10),
            Strumento("🗑️", "Cestino", "Voci eliminate di recente", 11),
            Strumento("📑", "Report e dati", "PDF mensile o annuale, CSV da importare ed esportare", 42)
        ))
    }

    /** Voci legate a turni e riposi: il personale di ufficio non le usa. */
    private val vociSoloTurno = setOf(7, 8, 19, 21)

    private fun gruppo(nome: String, vociTutte: List<Strumento>) {
        val ufficio = Archivio.caricaTutto(this).impostazioni.profiloAttivo == Profilo.UFFICIO
        val voci = vociTutte.filterNot { ufficio && it.id in vociSoloTurno }
        intestazione(nome)
        voci.chunked(2).forEach { coppia ->
            val riga = LinearLayout(this)
            coppia.forEachIndexed { i, st ->
                riga.addView(cardStrumento(st), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                    setMargins(if (i == 0) 0 else dp(4), dp(4), if (i == 0) dp(4) else 0, dp(4))
                })
            }
            if (coppia.size == 1) riga.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
            destinazione.addView(riga, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun cardStrumento(st: Strumento): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = Stile.contornoVerde()
        setPadding(dp(14), dp(14), dp(14), dp(14))
        isClickable = true
        addView(TextView(this@MainActivity).apply { text = st.icona; textSize = 24f })
        addView(TextView(this@MainActivity).apply {
            text = st.titolo; textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Stile.testo)
            setPadding(0, dp(6), 0, 0)
        })
        addView(TextView(this@MainActivity).apply {
            text = st.nota; textSize = 12f
            setTextColor(Stile.secondario)
        })
        setOnClickListener { esegui(st.id) }
    }

    private var opzioniIcs = OpzioniCalendario()

    /** Esegue una voce della scheda Strumenti (i numeri sono quelli storici del menu). */
    private fun esegui(id: Int) {
        when (id) {
            40 -> startActivity(Intent(this, WikiActivity::class.java))
            43 -> startActivity(Intent(this, AbilitazioniActivity::class.java))
            44 -> startActivity(Intent(this, CronologiaTrenoActivity::class.java))
            41 -> startActivity(Intent(this, CartellinoSapActivity::class.java))
            42 -> startActivity(Intent(this, EsportaActivity::class.java))
            2 -> startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
                putExtra(Intent.EXTRA_TITLE, "ferromicon-${LocalDate.now()}.json")
            }, RICHIESTA_BACKUP)
            3 -> startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }, RICHIESTA_RIPRISTINO)
            4 -> { mese = YearMonth.now(); scheda = Scheda.AGENDA; aggiorna() }
            5 -> startActivity(Intent(this, AnnoActivity::class.java).putExtra(AnnoActivity.EXTRA_ANNO, mese.year))
            6 -> gestisciPreferiti()
            7 -> importaDaTesto()
            31 -> startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }, RICHIESTA_IMPORTA_FILE)
            8 -> generaTurno()
            9 -> ScegliContenuto.mostra(this, "Cosa esportare", "Continua") { opzioni ->
                opzioniIcs = opzioni
                startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "text/calendar"
                    putExtra(Intent.EXTRA_TITLE, "ferromicon-${LocalDate.now()}.ics")
                }, RICHIESTA_ICS)
            }
            30 -> startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }, RICHIESTA_IMPORTA_ICS)
            10 -> backupAutomatici()
            11 -> cestino()
            12 -> startActivity(Intent(this, StazioniActivity::class.java))
            13 -> orarioAltarail()
            14 -> startActivity(Intent(this, RecuperoFestiviActivity::class.java))
            15 -> startActivity(Intent(this, PerimetriActivity::class.java))
            16 -> startActivity(Intent(this, StraordinarioActivity::class.java))
            17 -> esporta()
            18 -> startActivity(Intent(this, CedoliniActivity::class.java))
            19 -> startActivity(Intent(this, TurnoActivity::class.java))
            20 -> startActivity(Intent(this, FerieActivity::class.java))
            21 -> startActivity(Intent(this, AgenteActivity::class.java))
            22 -> startActivity(Intent(this, CalendarioTelefonoActivity::class.java))
            23 -> startActivity(Intent(this, DiarioActivity::class.java))
            24 -> Aggiornamenti.controllaSubito(this)
        }
    }

    private fun segnalaErroreSePresente() {
        val dettagli = Errori.leggi(this) ?: return
        AlertDialog.Builder(this)
            .setTitle("L'app si è chiusa per un errore")
            .setMessage("I tuoi dati non sono stati toccati. Se vuoi, posso preparare i dettagli da inviare a chi sviluppa l'app.")
            .setPositiveButton("Invia dettagli") { _, _ ->
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Ferromicon - errore")
                    putExtra(Intent.EXTRA_TEXT, dettagli)
                }, "Invia dettagli"))
                Errori.cancella(this)
            }
            .setNegativeButton("Ignora") { _, _ -> Errori.cancella(this) }
            .show()
    }

    @Deprecated("API di sistema semplice, senza librerie aggiuntive")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) return
        when (requestCode) {
            RICHIESTA_BACKUP -> try {
                contentResolver.openOutputStream(uri)?.use {
                    it.write(Archivio.aTesto(Archivio.caricaTutto(this)).toByteArray())
                }
                Toast.makeText(this, "Backup salvato", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Backup non riuscito: ${e.message}", Toast.LENGTH_LONG).show()
            }
            RICHIESTA_GTFS -> importaOrario(uri)
            RICHIESTA_IMPORTA_FILE -> smistaFile(uri)
            RICHIESTA_ICS -> try {
                contentResolver.openOutputStream(uri)?.use {
                    it.write(CalendarioIcs.genera(Archivio.caricaTutto(this), opzioni = opzioniIcs).toByteArray())
                }
                AlertDialog.Builder(this)
                    .setTitle("Calendario esportato")
                    .setMessage("Apri il file .ics dal telefono o caricalo in Google Calendar (da computer: " +
                        "Impostazioni › Importa ed esporta) per vedere servizi e assenze nel tuo calendario.")
                    .setPositiveButton("OK", null)
                    .show()
            } catch (e: Exception) {
                Toast.makeText(this, "Esportazione non riuscita: ${e.message}", Toast.LENGTH_LONG).show()
            }
            RICHIESTA_IMPORTA_ICS -> importaIcs(uri)
            RICHIESTA_RIPRISTINO -> importaRipristino(uri)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("mese", mese.toString())
        outState.putString("scheda", scheda.name)
    }

    override fun onResume() {
        super.onResume()
        Aggiornamenti.controllaInSilenzio(this)
        aggiorna()
    }

    private fun aggiorna() {
        aggiornaBarre()
        val nomeMese = mese.month.getDisplayName(TextStyle.FULL_STANDALONE, locale)
        titolo.text = "${nomeMese.replaceFirstChar { c -> c.titlecase(locale) }} ${mese.year}"
        contenuto.removeAllViews()
        destinazione = contenuto
        runCatching { AvvisoPermessi.testo(Archivio.caricaTutto(this)) }.getOrNull()?.let { avviso ->
            contenuto.addView(TextView(this).apply {
                text = avviso
                setTextColor(Stile.testo)
                textSize = 14f
                background = Stile.arrotondato(Stile.primarioChiaro, 12)
                setPadding(dp(14), dp(10), dp(14), dp(10))
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, dp(12)) })
        }
        if (runCatching { Archivio.caricaTutto(this).impostazioni.livello == null }.getOrDefault(false)) {
            contenuto.addView(TextView(this).apply {
                text = "Imposta il livello in Impostazioni: serve per la paga oraria. Tocca per aprirle."
                setTextColor(Stile.testo)
                textSize = 14f
                background = Stile.arrotondato(Stile.primarioChiaro, 12)
                setPadding(dp(14), dp(10), dp(14), dp(10))
                setOnClickListener { startActivity(Intent(this@MainActivity, ImpostazioniActivity::class.java)) }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, dp(12)) })
        }
        Aggiornamenti.novitaDisponibile(this)?.let { nome ->
            contenuto.addView(TextView(this).apply {
                text = "Disponibile Ferromicon $nome · tocca per installare"
                setTextColor(Stile.testo)
                textSize = 15f
                background = Stile.arrotondato(Stile.primarioChiaro, 12)
                setPadding(dp(14), dp(12), dp(14), dp(12))
                setOnClickListener { Aggiornamenti.controllaSubito(this@MainActivity) }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, dp(12)) })
        }
        try {
            when (scheda) {
                Scheda.STRUMENTI -> strumenti()
                Scheda.AGENDA -> schedaAgenda(Archivio.caricaTutto(this))
                Scheda.RIEPILOGO -> schedaRiepilogo(Archivio.caricaTutto(this))
            }
        } catch (e: Exception) {
            contenuto.removeAllViews()
            destinazione = contenuto
            testo("Non riesco a mostrare questa schermata (${e.javaClass.simpleName}). " +
                "I dati sono al sicuro: riprova o torna all'Agenda.", Stile.errore)
        }
    }

    /** Scheda Agenda: prossimo servizio, calendario e l'elenco di servizi e assenze del mese. */
    private fun schedaAgenda(dati: Dati) {
        val r = Analisi.riepilogoMese(dati.servizi, mese, dati.assenze)
        pannelloFineGiornata(dati)
        riepilogoPeriodo(dati)
        prossimoServizio(dati)
        bannerTurno(dati)
        val gravi = r.esiti.count { it.gravita == Gravita.VIOLAZIONE }
        if (gravi > 0) {
            sezione("Attenzione")
            voce("⛔ $gravi controlli non rispettati in questo mese. Tocca per vederli.", Stile.errore) {
                scheda = Scheda.RIEPILOGO; aggiorna()
            }
        }
        // Agenda: servizi e assenze in ordine di data
        val ordinati = dati.servizi.sortedBy { s -> s.turno.inizio }
        sezione("Calendario")
        griglia(dati, ordinati)
        sezione("Servizi e assenze")
        testo("Tocca per i controlli, tieni premuto per duplicare o salvare come preferito.", Stile.secondario, 12f)
        val voci = mutableListOf<Pair<LocalDate, () -> Unit>>()
        ordinati.withIndex()
            .filter { (_, s) -> YearMonth.from(s.turno.inizio) == mese }
            .forEach { (i, s) ->
                voci += s.turno.inizio.toLocalDate() to { rigaServizio(s, Analisi.verificaServizio(ordinati, i, dati.assenze, dati.impostazioni.limitiOrario)) }
            }
        dati.assenze.filter { YearMonth.from(it.data) == mese }
            .forEach { assenza -> voci += assenza.data to { rigaAssenza(assenza) } }

        if (voci.isEmpty()) testo("Nessuna voce in questo mese. Usa \"+ Servizio\" o \"+ Assenza\".")
        voci.sortedBy { it.first }.forEach { it.second() }
    }

    /** Scheda Riepilogo: ore, presenze, condotta, riposi, controlli, ferie e stima delle competenze. */
    private fun schedaRiepilogo(dati: Dati) {
        val r = Analisi.riepilogoMese(dati.servizi, mese, dati.assenze)
        // Riepilogo del mese
        sezione("Riepilogo")
        val righe = mutableListOf(
            "Servizi: ${r.servizi}   Ore: ${ore(r.minutiLavoro)}",
            "Condotta: ${ore(r.minutiCondotta)}   Notturni: ${r.notturni}   RFR: ${r.rfr}"
        )
        if (r.assenzeMese.isNotEmpty()) righe += "Giornate: " +
            r.assenzeMese.entries.sortedBy { it.key.ordinal }.joinToString { "${it.key.etichetta} ${it.value}" }
        if (r.festiviLavorati.isNotEmpty()) righe += "Festivi lavorati: " +
            r.festiviLavorati.joinToString { "${it.first.dayOfMonth} ${it.second}" }
        testo(righe.joinToString("\n"))
        riepilogoEsteso(dati)

        // Qualità dei riposi giornalieri del mese
        val qualita = Analisi.qualitaRiposi(dati.servizi.sortedBy { it.turno.inizio }, dati.assenze, mese)
        if (qualita.isNotEmpty()) testo("Riposi giornalieri: " +
            qualita.entries.joinToString(" · ") { "${it.value} ${it.key.etichetta}" }, Stile.secondario, 13f)

        // Parentali: presi nell'anno e quelli che restano
        val parentaliPresi = r.assenzeAnno[TipoAssenza.PARENTALE] ?: 0
        val parentaliSpettanti = dati.impostazioni.parentaliSpettanti
        testo(if (parentaliSpettanti > 0)
            "Parentali: presi $parentaliPresi su $parentaliSpettanti, restano ${(parentaliSpettanti - parentaliPresi).coerceAtLeast(0)}"
            else "Parentali: presi $parentaliPresi (imposta i giorni spettanti nelle Impostazioni)", Stile.secondario)

        // Da inizio anno: 52 riposi settimanali e 52 giornate di riposo (Altarail art. 17.6.1)
        val a = r.assenzeAnno
        testo(
            "Da inizio anno: riposi settimanali ${a[TipoAssenza.RIPOSO_SETTIMANALE] ?: 0}/52, " +
                "giornate di riposo ${a[TipoAssenza.RIPOSO] ?: 0}/52, ferie ${a[TipoAssenza.FERIE] ?: 0}",
            Stile.secondario
        )

        // Controlli del mese
        if (r.esiti.isNotEmpty()) {
            sezione("Controlli del mese")
            r.esiti.forEach { testo(formatta(it), colore(it.gravita)) }
        }

        // Ferie e permessi dell'anno, fino alla fine del mese mostrato
        val saldo = Ferie.saldo(dati, mese.year, mese.atEndOfMonth())
        sezione("Ferie e permessi ${mese.year}")
        testo(buildString {
            append("Ferie: ${saldo.ferieSpettanti} spettanti")
            if (saldo.ferieAnnoPrecedente > 0) append(" + ${saldo.ferieAnnoPrecedente} dell'anno prima")
            append(", ${saldo.ferieGodute} godute, restano ${saldo.ferieResidue}")
            append("\nPermessi ex festività: ${saldo.permessiExFestivitaGoduti}/${saldo.permessiExFestivitaSpettanti}")
            if (saldo.permessiAggiuntivi > 0 || saldo.altriPermessiGoduti > 0)
                append("\nAltri permessi: ${saldo.altriPermessiGoduti}/${saldo.permessiAggiuntivi}")
            if (saldo.recuperiFestiviMaturati > 0 || saldo.recuperiFestiviGoduti > 0)
                append("\nRecuperi festività lavorate: ${numero(saldo.recuperiFestiviMaturati)} maturati, " +
                    "${saldo.recuperiFestiviGoduti} goduti, da godere ${numero(saldo.recuperiFestiviResidui)}")
        })
        saldo.note.forEach { testo(it, Stile.secondario, 12f) }

        // Stima delle competenze variabili del mese
        val stima = Competenze.stimaMese(dati, mese)
        if (stima.voci.isNotEmpty()) {
            sezione("Stima competenze variabili")
            stima.voci.forEach { v ->
                testo("${v.etichetta}: ${Competenze.euro(v.importo)}")
                testo(v.dettaglio + if (v.codice.isEmpty()) "" else " · codici ${v.codice}", Stile.secondario, 12f)
            }
            testo("Totale stimato: ${Competenze.euro(stima.totale)}", null, 16f, grassetto = true)
            stima.note.forEach { testo(it, Stile.secondario, 12f) }
        }
    }

    private fun rigaServizio(s: Servizio, esiti: List<Esito>) {
        val t = s.turno
        val peggiore = esiti.maxOfOrNull { it.gravita } ?: Gravita.OK
        val simbolo = when (peggiore) {
            Gravita.VIOLAZIONE -> "⛔"
            Gravita.ATTENZIONE -> "⚠️"
            else -> "✅"
        }
        val giorno = t.inizio.toLocalDate()
        val festivo = Festivi.nome(giorno)?.let { " · $it" } ?: ""
        val etichette = buildList {
            if (RegoleAltarail.èNotturno(t)) add("notturno")
            if (s.rfrDopo) add("RFR dopo")
            if (t.turnoAZeta) add("zeta")
            if (s.riserva) add("riserva/formazione")
            if (s.righe.isNotEmpty()) add(s.righe.mapNotNull { it.numero.takeIf { n -> n.isNotBlank() } }
                .joinToString(" ").ifBlank { "${s.righe.size} righe" })
            if (s.materiale.isNotBlank()) add(s.materiale)
            if (s.secondoAgente.isNotBlank()) add("con ${s.secondoAgente}")
            s.ritardoFinale.takeIf { it > 0 }?.let { add("ritardo +$it'") }
        }.joinToString(" · ")
        val durata = Duration.between(t.inizio, t.fine).toMinutes()

        voce(
            "$simbolo ${fmtGiorno.format(giorno)}  ${fmtOra.format(t.inizio)}–${fmtOra.format(t.fine)}" +
                "  (${ore(durata)})$festivo" +
                (if (etichette.isEmpty()) "" else "\n    $etichette") +
                (if (s.note.isBlank()) "" else "\n    ${s.note}")
        , lungo = { azioniServizio(s) }) { dettaglio(s, esiti) }
    }

    /** Riquadro in cima: prossimo servizio, quanto manca ed esito dei controlli. */
    private fun prossimoServizio(dati: Dati) {
        val adesso = LocalDateTime.now()
        val ordinati = dati.servizi.sortedBy { it.turno.inizio }
        val i = ordinati.indexOfFirst { it.turno.fine.isAfter(adesso) }
        if (i < 0) return
        val s = ordinati[i]
        val t = s.turno
        val inCorso = !t.inizio.isAfter(adesso)
        val manca = Duration.between(adesso, if (inCorso) t.fine else t.inizio).toMinutes()
        val quando = when {
            inCorso -> "in corso, termina tra ${ore(manca)}"
            manca < 24 * 60 -> "tra ${ore(manca)}"
            else -> "tra ${manca / (24 * 60)} g ${(manca % (24 * 60)) / 60} h"
        }
        val problemi = Analisi.verificaServizio(ordinati, i, dati.assenze, dati.impostazioni.limitiOrario)
            .filter { it.gravita == Gravita.VIOLAZIONE || it.gravita == Gravita.ATTENZIONE }
        sezione("Prossimo servizio")
        voce("${fmtGiornoLungo.format(t.inizio)}  ${fmtOra.format(t.inizio)}–${fmtOra.format(t.fine)} · $quando" +
            if (problemi.isEmpty()) "\n✅ Riposo e limiti in regola" else "\n" + problemi.joinToString("\n") { formatta(it) },
            if (problemi.any { it.gravita == Gravita.VIOLAZIONE }) colore(Gravita.VIOLAZIONE) else null
        ) { dettaglio(s, Analisi.verificaServizio(ordinati, i, dati.assenze, dati.impostazioni.limitiOrario)) }
    }

    /** Pressione lunga su un servizio: duplica, salva come preferito, elimina. */
    private fun azioniServizio(s: Servizio) {
        val opzioni = arrayOf("Duplica al giorno dopo", "Duplica alla settimana dopo", "Salva come preferito", "Elimina")
        AlertDialog.Builder(this)
            .setTitle("${fmtGiorno.format(s.turno.inizio)} ${fmtOra.format(s.turno.inizio)}–${fmtOra.format(s.turno.fine)}")
            .setItems(opzioni) { _, scelta ->
                when (scelta) {
                    0, 1 -> {
                        val giorni = if (scelta == 0) 1L else 7L
                        val copia = s.spostato(giorni, System.currentTimeMillis())
                        Archivio.aggiornaOInserisci(this, copia)
                        Toast.makeText(this, "Copiato a ${fmtGiornoLungo.format(copia.turno.inizio)}", Toast.LENGTH_SHORT).show()
                        aggiorna()
                    }
                    2 -> chiediNome(s.note.ifBlank { "${fmtOra.format(s.turno.inizio)}–${fmtOra.format(s.turno.fine)}" }) { nome ->
                        Archivio.salvaPreferito(this, Preferito.da(s, nome, System.currentTimeMillis()))
                        Toast.makeText(this, "Preferito \"$nome\" salvato", Toast.LENGTH_SHORT).show()
                    }
                    3 -> AlertDialog.Builder(this)
                        .setMessage("Eliminare questo servizio?")
                        .setPositiveButton("Elimina") { _, _ -> eliminaConAnnulla(servizio = s) }
                        .setNegativeButton("Annulla", null)
                        .show()
                }
            }
            .show()
    }

    /** "+ Servizio": modulo vuoto oppure inserimento rapido da un preferito. */
    private fun nuovoServizio() {
        val preferiti = Archivio.caricaTutto(this).preferiti.sortedBy { it.nome }
        if (preferiti.isEmpty()) {
            startActivity(Intent(this, ServizioActivity::class.java))
            return
        }
        val opzioni = arrayOf("Nuovo servizio vuoto") +
            preferiti.map { "⭐ ${it.nome}  ${fmtOra.format(it.oraInizio)}–${fmtOra.format(it.oraFine)}" }
        AlertDialog.Builder(this)
            .setTitle("Nuovo servizio")
            .setItems(opzioni) { _, scelta ->
                if (scelta == 0) startActivity(Intent(this, ServizioActivity::class.java))
                else {
                    val p = preferiti[scelta - 1]
                    val oggi = if (YearMonth.now() == mese) LocalDate.now() else mese.atDay(1)
                    DatePickerDialog(this, { _, y, m, g ->
                        val servizio = p.applica(LocalDate.of(y, m + 1, g), System.currentTimeMillis())
                        Archivio.aggiornaOInserisci(this, servizio)
                        Toast.makeText(this, "${p.nome} inserito il ${fmtGiornoLungo.format(servizio.turno.inizio)}",
                            Toast.LENGTH_SHORT).show()
                        aggiorna()
                    }, oggi.year, oggi.monthValue - 1, oggi.dayOfMonth).show()
                }
            }
            .show()
    }

    private fun gestisciPreferiti() {
        val preferiti = Archivio.caricaTutto(this).preferiti.sortedBy { it.nome }
        if (preferiti.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Servizi preferiti")
                .setMessage("Non hai ancora preferiti. Tieni premuto un servizio in agenda e scegli " +
                    "\"Salva come preferito\", oppure usa il pulsante nel modulo del servizio.")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        val usati = Archivio.caricaTutto(this).turno?.giornate.orEmpty()
        AlertDialog.Builder(this)
            .setTitle("Tocca un preferito per eliminarlo")
            .setItems(preferiti.map {
                "${it.nome}  ${fmtOra.format(it.oraInizio)}–${fmtOra.format(it.oraFine)}" +
                    if (inTurno(it, usati)) "  · nel turno" else ""
            }.toTypedArray()) { _, i ->
                val p = preferiti[i]
                AlertDialog.Builder(this)
                    .setMessage("Eliminare il preferito \"${p.nome}\"?" +
                        if (inTurno(p, usati)) "\n\nÈ una giornata del tuo turno: se la elimini, quei giorni " +
                            "restano vuoti alla prossima compilazione." else "")
                    .setPositiveButton("Elimina") { _, _ -> Archivio.eliminaPreferito(this, p.id) }
                    .setNegativeButton("Annulla", null)
                    .show()
            }
            .setNegativeButton("Chiudi", null)
            .show()
    }

    private fun inTurno(p: Preferito, usati: List<String>) = usati.any { it.equals(p.nome, ignoreCase = true) }

    private fun chiediNome(iniziale: String, ok: (String) -> Unit) {
        val campo = EditText(this).apply { setText(iniziale); hint = "Nome del preferito" }
        val contenitore = LinearLayout(this).apply { setPadding(dp(20), dp(8), dp(20), 0); addView(campo) }
        AlertDialog.Builder(this)
            .setTitle("Salva come preferito")
            .setView(contenitore)
            .setPositiveButton("Salva") { _, _ -> campo.text.toString().trim().ifBlank { iniziale }.let(ok) }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun rigaAssenza(a: Assenza) {
        val festivo = Festivi.nome(a.data)?.let { " · $it" } ?: ""
        voce(
            "${a.tipo.simbolo} ${fmtGiorno.format(a.data)}  ${a.tipo.etichetta}$festivo" +
                (if (a.note.isBlank()) "" else "\n    ${a.note}"),
            Stile.primario
        ) {
            AlertDialog.Builder(this)
                .setTitle("${a.tipo.etichetta} · ${fmtGiornoLungo.format(a.data)}")
                .setMessage(a.note.ifBlank { "Nessuna nota." })
                .setPositiveButton("Elimina") { _, _ -> eliminaConAnnulla(assenza = a) }
                .setNegativeButton("Chiudi", null)
                .show()
        }
    }

    private fun voce(testo: String, colore: Int? = null, lungo: (() -> Unit)? = null, click: () -> Unit) {
        destinazione.addView(TextView(this).apply {
            text = testo
            textSize = 16f
            setTextColor(colore ?: Stile.testo)
            setPadding(0, dp(10), 0, dp(10))
            setOnClickListener { click() }
            if (lungo != null) setOnLongClickListener { lungo(); true }
        })
        destinazione.addView(View(this).apply { setBackgroundColor(Stile.linea) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))
    }

    private fun dettaglio(s: Servizio, esiti: List<Esito>) {
        val ripresa = RegoleAltarail.ripresaMinima(s.turno, s.rfrDopo)
        val testoRipresa = "🕑 Servizio successivo possibile dalle ${fmtOra.format(ripresa)} di " +
            fmtGiornoLungo.format(ripresa) + if (s.rfrDopo) " (riposo fuori residenza)" else " (riposo in residenza)"
        AlertDialog.Builder(this)
            .setTitle("${fmtGiorno.format(s.turno.inizio)} ${fmtOra.format(s.turno.inizio)}–${fmtOra.format(s.turno.fine)}")
            .setMessage(esiti.joinToString("\n\n") { formatta(it) } + "\n\n" + testoRipresa)
            .setNeutralButton("Altro…") { _, _ -> azioniServizio(s) }
            .setPositiveButton("Modifica") { _, _ ->
                startActivity(Intent(this, ServizioActivity::class.java).putExtra(ServizioActivity.EXTRA_ID, s.id))
            }
            .setNegativeButton("Chiudi", null)
            .show()
    }

    /** Inserimento di una o più giornate senza servizio (da ... a ...). */
    /** Sposta il riposo di [giorno] in un'altra data scelta dal calendario. */
    private fun sceltaNuovoGiornoRiposo(giorno: LocalDate) {
        DatePickerDialog(this, { _, y, m, d ->
            val nuovo = LocalDate.of(y, m + 1, d)
            if (Archivio.spostaRiposo(this, giorno, nuovo)) {
                Toast.makeText(this, "Riposo spostato al ${fmtGiornoLungo.format(nuovo)}", Toast.LENGTH_LONG).show()
                aggiorna()
            } else {
                Toast.makeText(this, "Non posso spostarlo: quel giorno ha già un servizio o un'assenza.",
                    Toast.LENGTH_LONG).show()
            }
        }, giorno.year, giorno.monthValue - 1, giorno.dayOfMonth).show()
    }

    private fun nuovaAssenza(giorno: LocalDate? = null) {
        val oggi = giorno ?: if (YearMonth.now() == mese) LocalDate.now() else mese.atDay(1)
        var da = oggi
        var a = oggi

        val tipo = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                TipoAssenza.values().map { "${it.simbolo} ${it.etichetta}" })
            setSelection(TipoAssenza.FERIE.ordinal)
        }
        val btnDa = Button(this)
        val btnA = Button(this)
        fun aggiornaDate() {
            if (a.isBefore(da)) a = da
            btnDa.text = "Dal ${fmtGiornoLungo.format(da)}"
            btnA.text = "Al ${fmtGiornoLungo.format(a)}"
        }
        btnDa.setOnClickListener {
            DatePickerDialog(this, { _, y, m, d -> da = LocalDate.of(y, m + 1, d); aggiornaDate() },
                da.year, da.monthValue - 1, da.dayOfMonth).show()
        }
        btnA.setOnClickListener {
            DatePickerDialog(this, { _, y, m, d -> a = LocalDate.of(y, m + 1, d); aggiornaDate() },
                a.year, a.monthValue - 1, a.dayOfMonth).show()
        }
        aggiornaDate()
        val note = EditText(this).apply { hint = "Note (facoltative)" }

        val modulo = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(tipo); addView(btnDa); addView(btnA); addView(note)
        }

        AlertDialog.Builder(this)
            .setTitle("Nuova assenza o riposo")
            .setView(modulo)
            .setPositiveButton("Salva") { _, _ ->
                val giorni = Duration.between(da.atStartOfDay(), a.plusDays(1).atStartOfDay()).toDays()
                Archivio.inserisciAssenze(this, da, a, TipoAssenza.values()[tipo.selectedItemPosition],
                    note.text.toString().trim())
                Toast.makeText(this, "Salvate $giorni giornate", Toast.LENGTH_SHORT).show()
                aggiorna()
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    // ---------- griglia del mese ----------

    /** Calendario a griglia: un riquadro per giorno, colorato secondo servizio, assenza o problemi. */
    private fun griglia(dati: Dati, ordinati: List<Servizio>) {
        val primo = mese.atDay(1)
        val vuoti = primo.dayOfWeek.value - 1
        val violazioni = ordinati.withIndex()
            .filter { (_, s) -> YearMonth.from(s.turno.inizio) == mese }
            .filter { (i, _) -> Analisi.verificaServizio(ordinati, i, dati.assenze, dati.impostazioni.limitiOrario).any { it.gravita == Gravita.VIOLAZIONE } }
            .map { it.value.turno.inizio.toLocalDate() }.toSet()
        val serviziPerGiorno = ordinati.groupBy { it.turno.inizio.toLocalDate() }
        val assenzePerGiorno = dati.assenze.associateBy { it.data }
        val oggi = LocalDate.now()
        val dormite = Riepilogo.giorniConDormita(dati, mese)

        val intestazione = LinearLayout(this)
        listOf("L", "M", "M", "G", "V", "S", "D").forEach { g ->
            intestazione.addView(TextView(this).apply {
                text = g; gravity = Gravity.CENTER; textSize = 12f; setTextColor(Stile.secondario)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        destinazione.addView(intestazione)

        var riga: LinearLayout? = null
        val celle = vuoti + mese.lengthOfMonth()
        val totale = ((celle + 6) / 7) * 7
        for (c in 0 until totale) {
            if (c % 7 == 0) {
                riga = LinearLayout(this)
                destinazione.addView(riga)
            }
            val giorno = if (c >= vuoti && c < celle) primo.plusDays((c - vuoti).toLong()) else null
            val cella = TextView(this).apply {
                gravity = Gravity.CENTER
                textSize = 12f
                setPadding(0, dp(6), 0, dp(6))
            }
            if (giorno != null) {
                val servizi = serviziPerGiorno[giorno].orEmpty()
                val assenza = assenzePerGiorno[giorno]
                val seconda = when {
                    servizi.isNotEmpty() -> fmtOra.format(servizi.first().turno.inizio) +
                        if (servizi.any { it.straordinario }) " S" else ""
                    assenza != null -> assenza.tipo.codice
                    else -> " "
                }
                val dormita = if (giorno in dormite) "💤" else ""
                cella.text = "${giorno.dayOfMonth}$dormita\n$seconda"
                val sfondo = when {
                    giorno in violazioni -> Stile.erroreChiaro
                    inPeriodo(giorno) -> Stile.primarioChiaro
                    servizi.isNotEmpty() -> Stile.okChiaro
                    assenza?.tipo == TipoAssenza.FERIE -> Stile.infoChiaro
                    assenza != null && assenza.tipo.riposo -> Stile.neutroChiaro
                    assenza != null -> Stile.attenzioneChiaro
                    else -> null
                }
                sfondo?.let { cella.background = Stile.arrotondato(it, 8); cella.setTextColor(Stile.testo) }
                if (Festivi.èFestivo(giorno) || giorno.dayOfWeek.value == 7)
                    cella.setTypeface(cella.typeface, Typeface.BOLD)
                if (giorno == oggi) {
                    cella.text = "${giorno.dayOfMonth}$dormita\n$seconda"
                    cella.background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(sfondo ?: Stile.superficie)
                        cornerRadius = dp(8).toFloat()
                        setStroke(dp(2), Stile.primario)
                    }
                    cella.setTextColor(Stile.testo)
                }
                cella.setOnClickListener { tocco(giorno, dati, ordinati) }
            }
            riga?.addView(cella, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(dp(1), dp(1), dp(1), dp(1))
            })
        }
        testo("Legenda: M malattia · I infortunio · F ferie · P permesso · PG giorno parentale · " +
            "R riposo · RS riposo settimanale · RF recupero festivo · D disponibilità · CA chiusura aziendale · " +
            "A altra assenza · " +
            "S straordinario (sul servizio).", Stile.secondario, 12f)
        testo("Verde servizio · rosso con violazioni · azzurro ferie · grigio riposo · giallo altre assenze · bordo blu oggi. " +
            "Tocca un giorno per aggiungere o vedere le voci.", Stile.secondario, 12f)
    }

    // ---------- selezione di date ----------

    private fun attivaPeriodo() {
        selezionePeriodo = !selezionePeriodo
        if (!selezionePeriodo) { periodoInizio = null; periodoFine = null }
        aggiorna()
    }

    /** Con la selezione attiva: primo tocco inizio, secondo tocco fine; altrimenti apre la giornata. */
    private fun tocco(giorno: LocalDate, dati: Dati, ordinati: List<Servizio>) {
        if (!selezionePeriodo) { giornata(giorno, dati, ordinati); return }
        val inizio = periodoInizio
        if (inizio == null || periodoFine != null) {
            periodoInizio = giorno
            periodoFine = null
        } else {
            periodoFine = giorno
            if (giorno.isBefore(inizio)) { periodoInizio = giorno; periodoFine = inizio }
        }
        aggiorna()
    }

    private fun inPeriodo(giorno: LocalDate): Boolean {
        val a = periodoInizio ?: return false
        val b = periodoFine ?: a
        return !giorno.isBefore(a) && !giorno.isAfter(b)
    }

    private fun riepilogoPeriodo(dati: Dati) {
        val a = periodoInizio ?: return
        val b = periodoFine ?: a
        val servizi = dati.servizi.filter { s ->
            val d = s.turno.inizio.toLocalDate()
            !d.isBefore(a) && !d.isAfter(b)
        }
        val minuti = servizi.sumOf { Duration.between(it.turno.inizio, it.turno.fine).toMinutes() }
        val assenze = dati.assenze.count { !it.data.isBefore(a) && !it.data.isAfter(b) }
        sezione("Periodo selezionato")
        testo("Dal ${fmtGiornoLungo.format(a)}" + (if (b != a) " al ${fmtGiornoLungo.format(b)}" else "") +
            "\n${servizi.size} servizi · ${ore(minuti)} di lavoro · $assenze assenze", Stile.testo, 14f)
        voce("Chiudi la selezione", Stile.secondario) { attivaPeriodo() }
    }

    // ---------- fine della giornata ----------

    private fun arrivoReale(giorno: LocalDate): LocalDateTime? {
        val v = getSharedPreferences("arrivi-reali", MODE_PRIVATE).getString(giorno.toString(), null) ?: return null
        val ora = runCatching { java.time.LocalTime.parse(v) }.getOrNull() ?: return null
        return giorno.atTime(ora)
    }

    private fun salvaArrivoReale(giorno: LocalDate, ora: java.time.LocalTime) {
        getSharedPreferences("arrivi-reali", MODE_PRIVATE).edit().putString(giorno.toString(), ora.toString()).apply()
    }

    /** Countdown alla fine prescritta della giornata di oggi, poi arrivo reale e ritardo da confermare. */
    private fun pannelloFineGiornata(dati: Dati) {
        val oggi = LocalDate.now()
        val servizio = dati.servizi.filter { it.turno.inizio.toLocalDate() == oggi }
            .minByOrNull { it.turno.inizio } ?: return
        val prescritta = servizio.turno.fine
        val mancano = Duration.between(LocalDateTime.now(), prescritta).toMinutes()
        sezione("Fine giornata")
        testo(if (mancano > 0) "Fine prescritta ${fmtOra.format(prescritta)} · mancano ${ore(mancano)}"
            else "Fine prescritta ${fmtOra.format(prescritta)} · già passata", Stile.testo, 15f, true)
        val reale = arrivoReale(oggi)
        if (reale == null) {
            voce("Inserisci l'arrivo reale del treno", Stile.primario) { chiediArrivoReale(oggi) }
        } else {
            val scarto = Duration.between(prescritta, reale).toMinutes()
            testo("Fine reale ${fmtOra.format(reale)} · " + when {
                scarto > 0 -> "ritardo di $scarto min"
                scarto < 0 -> "anticipo di ${-scarto} min"
                else -> "in orario"
            } + ". Conferma o modifica.", Stile.testo, 14f)
            voce("Modifica l'arrivo reale", Stile.secondario) { chiediArrivoReale(oggi) }
        }
        voce("Cerca il treno su Trenord", Stile.secondario) { cercaTrenord() }
    }

    private fun chiediArrivoReale(giorno: LocalDate) {
        val campo = EditText(this).apply {
            hint = "es. 16:42"
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            setText(arrivoReale(giorno)?.toLocalTime()?.toString() ?: "")
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("Arrivo reale del treno")
            .setView(campo)
            .setPositiveButton("Conferma") { _, _ ->
                val ora = runCatching { java.time.LocalTime.parse(campo.text.toString().trim()) }.getOrNull()
                if (ora == null) Toast.makeText(this, "Orario non valido: scrivi, per esempio, 16:42.", Toast.LENGTH_LONG).show()
                else { salvaArrivoReale(giorno, ora); aggiorna() }
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    /** Copia il numero del treno e apre la pagina Trenord: la ricerca la fai tu, l'app non legge il sito. */
    private fun cercaTrenord() {
        val campo = EditText(this).apply { hint = "Numero del treno, es. 25592" }
        android.app.AlertDialog.Builder(this)
            .setTitle("Cerca il treno su Trenord")
            .setView(campo)
            .setPositiveButton("Apri Trenord") { _, _ ->
                val numero = campo.text.toString().trim()
                val appunti = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                appunti.setPrimaryClip(android.content.ClipData.newPlainText("numero treno", numero))
                Toast.makeText(this, "Numero copiato: incollalo nella ricerca Trenord.", Toast.LENGTH_LONG).show()
                startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://www.trenord.it/linee-e-orari/circolazione/tempo-reale/")))
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    /** Dialogo di una giornata: voci presenti e inserimento con la data già impostata. */
    private fun giornata(giorno: LocalDate, dati: Dati, ordinati: List<Servizio>) {
        val servizi = ordinati.filter { it.turno.inizio.toLocalDate() == giorno }
        val assenza = dati.assenze.firstOrNull { it.data == giorno }
        val voci = mutableListOf<Pair<String, () -> Unit>>()
        servizi.forEach { s ->
            val i = ordinati.indexOf(s)
            voci += "Servizio ${fmtOra.format(s.turno.inizio)}–${fmtOra.format(s.turno.fine)}" to
                { dettaglio(s, Analisi.verificaServizio(ordinati, i, dati.assenze, dati.impostazioni.limitiOrario)) }
        }
        assenza?.let { a ->
            voci += "${a.tipo.simbolo} ${a.tipo.etichetta} (tocca per eliminare)" to {
                eliminaConAnnulla(assenza = a)
            }
        }
        voci += "📊 Riepilogo del giorno" to { riepilogoGiorno(giorno, dati) }
        servizi.filter { it.righe.isNotEmpty() }.forEach { s ->
            voci += "⏱ Ritardo all'arrivo (${fmtOra.format(s.turno.inizio)})" to { registraRitardo(s) }
        }
        servizi.filter { it.origineTurno.isNotEmpty() }.forEach { s ->
            val pid = dati.preferiti.firstOrNull { it.nome.equals(s.origineTurno, ignoreCase = true) }?.id
                ?: System.currentTimeMillis()
            voci += "⭐ Usa questa come giornata \"${s.origineTurno}\"" to {
                Archivio.salvaPreferito(this, Preferito.da(s, s.origineTurno, pid))
                Toast.makeText(this, "Giornata \"${s.origineTurno}\" aggiornata nel turno", Toast.LENGTH_SHORT).show()
                aggiorna()
            }
        }
        dati.turno?.let { turno ->
            val elemento = turno.elementoPer(giorno)
            if (elemento != null && elemento != "-") {
                val presente = servizi.any { it.origineTurno.isNotEmpty() }
                voci += (if (presente) "🔄 Ripristina la giornata di turno" else "🧭 Carica la giornata di turno") +
                    " (n. ${turno.numeroGiornata(giorno)}: $elemento)" to { caricaGiornataDiTurno(turno, giorno, dati) }
            }
            val altre = turno.giornate.filter { it != "-" && it != "." }.distinct()
            if (altre.size > 1) voci += "🔀 Carica un'altra giornata del turno…" to {
                AlertDialog.Builder(this).setTitle("Quale giornata?")
                    .setItems(altre.toTypedArray()) { _, i -> caricaGiornataDiTurno(turno, giorno, dati, altre[i]) }
                    .setNegativeButton("Annulla", null).show()
            }
        }
        voci += "+ Servizio" to {
            startActivity(Intent(this, ServizioActivity::class.java).putExtra(ServizioActivity.EXTRA_DATA, giorno.toString()))
        }
        dati.preferiti.sortedBy { it.nome }.forEach { p ->
            voci += "⭐ ${p.nome} ${fmtOra.format(p.oraInizio)}–${fmtOra.format(p.oraFine)}" to {
                Archivio.aggiornaOInserisci(this, p.applica(giorno, System.currentTimeMillis())); aggiorna()
            }
        }
        servizi.forEach { s ->
            val etichetta = if (s.straordinario) "⏱ Togli straordinario" else "⏱ Segna come straordinario"
            voci += etichetta to {
                Archivio.aggiornaOInserisci(this, s.copy(straordinario = !s.straordinario)); aggiorna()
            }
        }
        if (dati.assenze.any { it.data == giorno && it.tipo.riposo }) {
            voci += "🔄 Sposta questo riposo" to { sceltaNuovoGiornoRiposo(giorno) }
        }
        voci += "+ Assenza o riposo" to { nuovaAssenza(giorno) }

        val titoloGiorno = fmtGiornoLungo.format(giorno) + (Festivi.nome(giorno)?.let { " · $it" } ?: "") +
            (dati.turno?.numeroGiornata(giorno)?.let { " · turno n. $it" } ?: "")
        AlertDialog.Builder(this)
            .setTitle(titoloGiorno)
            .setItems(voci.map { it.first }.toTypedArray()) { _, i -> voci[i].second() }
            .setNegativeButton("Chiudi", null)
            .show()
    }

    /** Giornata di turno di oggi e avviso quando il turno sta per finire o è finito. */
    private fun bannerTurno(dati: Dati) {
        val t = dati.turno ?: return
        val oggi = LocalDate.now()
        val giorni = t.giorniAllaFine(oggi)
        val el = t.elementoPer(oggi)
        if (el == null && giorni >= 0) return
        sezione("Il mio turno")
        if (el != null) testo("Oggi: giornata ${t.numeroGiornata(oggi)} · $el", null, 14f, true)
        val seguenti = PianoTurno.prossimiGiorni(t, oggi.plusDays(1), 6)
        if (seguenti.isNotEmpty()) testo(seguenti.joinToString("  ·  ") {
            "${it.data.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ITALIAN)} ${it.data.dayOfMonth}: ${it.nome}"
        }, Stile.secondario, 12f)
        voce("🧭 Apri il turno", null) { startActivity(Intent(this, TurnoActivity::class.java)) }
        if (giorni < 0) voce("⛔ Il turno è scaduto. Tocca per prolungarlo al prossimo cambio orario.", Stile.errore) {
            prolungaTurno(t)
        } else if (giorni <= 21) voce("🧭 Il turno finisce tra $giorni giorni (${fmtGiornoLungo.format(t.validoFino)}). " +
            "Tocca per prolungarlo.", Stile.errore) { prolungaTurno(t) }
    }

    private fun prolungaTurno(t: TurnoImpianto) {
        val nuovo = t.prolungato()
        AlertDialog.Builder(this).setTitle("Prolungare il turno?")
            .setMessage("Il ciclo continua senza salti fino al ${fmtGiornoLungo.format(nuovo.validoFino)}. " +
                "Se il nuovo turno è diverso, modificalo da Strumenti > Il mio turno.")
            .setPositiveButton("Prolunga e compila") { _, _ ->
                Archivio.copiaDiSicurezza(this)
                Archivio.salvaTurno(this, nuovo)
                val d = Archivio.caricaTutto(this)
                val dopo = t.validoFino.plusDays(1)
                val da = if (dopo.isAfter(LocalDate.now())) dopo else LocalDate.now()
                Archivio.applicaCompilazione(this, PianoTurno.compila(nuovo, da, d.preferiti, d, System.currentTimeMillis()))
                aggiorna()
            }
            .setNegativeButton("Annulla", null).show()
    }

    /** Registra il ritardo (o anticipo) all'arrivo dell'ultimo treno del servizio. */
    private fun registraRitardo(s: Servizio) {
        val campo = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
            hint = "Minuti (negativo = anticipo)"
        }
        AlertDialog.Builder(this).setTitle("Ritardo all'arrivo").setView(campo)
            .setPositiveButton("Salva") { _, _ ->
                val min = campo.text.toString().toLongOrNull() ?: return@setPositiveButton
                val ultima = s.righe.maxByOrNull { it.arrivo } ?: return@setPositiveButton
                val nuove = s.righe.map { if (it === ultima) it.copy(arrivoReale = it.arrivo.plusMinutes(min)) else it }
                Archivio.aggiornaOInserisci(this, s.copy(righe = nuove)); aggiorna()
            }
            .setNegativeButton("Annulla", null).show()
    }

    /** Carica la giornata di default del turno per il giorno; poi si può correggere aggiungendo o togliendo treni. */
    private fun caricaGiornataDiTurno(turno: TurnoImpianto, giorno: LocalDate, dati: Dati, sostituto: String? = null) {
        val c = PianoTurno.giornata(turno, giorno, dati.preferiti, dati, System.currentTimeMillis(), sostituto)
        if (c.sconosciuti.isNotEmpty()) {
            Toast.makeText(this, "\"${c.sconosciuti.first()}\" non è tra i preferiti", Toast.LENGTH_LONG).show()
            return
        }
        Archivio.applicaCompilazione(this, c)
        aggiorna()
        val nuovo = c.servizi.singleOrNull()
        if (nuovo != null) {
            Toast.makeText(this, "Giornata caricata: modifica i treni se serve", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, ServizioActivity::class.java).putExtra(ServizioActivity.EXTRA_ID, nuovo.id))
        }
    }

    /** Riepilogo esteso del mese: ore e monte ore, presenze, dormite, notturni, condotta, straordinario, diaria. */
    private fun riepilogoEsteso(dati: Dati) {
        val r = Riepilogo.mese(dati, mese)
        if (r.presenzeComplessive == 0) return
        val etichettaCondotta = if (r.profilo == Profilo.MACCHINISTA) "Condotta" else "Scorta"
        testo("Ore riconosciute ${Riepilogo.ore(r.oreRiconosciuteMinuti)} su un monte ore di " +
            "${Riepilogo.ore(r.monteOreMinuti)}", null, 14f)
        destinazione.addView(android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progress = (r.progresso * 1000).toInt().coerceIn(0, 1000)
        }, LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT))
        val righe = mutableListOf(
            "Presenze ${r.presenzeEffettive} effettive, ${r.presenzeComplessive} complessive su ${r.giorniDelMese} giorni" +
                " · Dormite ${r.dormite}",
            "Notturni ${r.serviziNotturni} servizi · ore tra le 22 e le 6: ${Riepilogo.ore(r.minutiFascia22_6)}"
        )
        if (r.condottaMinuti > 0) righe += "$etichettaCondotta ${Riepilogo.ore(r.condottaMinuti)} " +
            "(fino a 3 h ${Riepilogo.ore(r.condottaFinoA3hMinuti)} · oltre ${Riepilogo.ore(r.condottaOltre3hMinuti)})"
        if (r.straordinarioTotaleMinuti > 0) righe += "Straordinario ${Riepilogo.ore(r.straordinarioTotaleMinuti)} " +
            "(normale ${Riepilogo.ore(r.straordinarioNormaleMinuti)} · in RC ${Riepilogo.ore(r.straordinarioInRcMinuti)}" +
            " · in RS ${Riepilogo.ore(r.straordinarioInRsMinuti)})"
        if (r.diariaSenzaRfrMinuti + r.diariaConRfrMinuti > 0) righe += "Fuori residenza (almeno 3 h al giorno): " +
            "senza RFR ${Riepilogo.ore(r.diariaSenzaRfrMinuti)} · con RFR ${Riepilogo.ore(r.diariaConRfrMinuti)}"
        righe += "Ferie: mese ${r.ferieMese} · anno ${r.ferieAnno} · residue ${r.ferieResidue}"
        righe += "Festività soppresse ${r.festivitaSoppresseGodute}/${r.festivitaSoppresseSpettanti}" +
            " · Permessi ${r.permessiGoduti}/${r.permessiSpettanti}"
        testo(righe.joinToString("\n"), Stile.secondario, 13f)
    }

    private fun riepilogoGiorno(giorno: LocalDate, dati: Dati) {
        val g = Riepilogo.giorno(dati, giorno)
        val righe = mutableListOf<String>()
        if (g.servizi.isEmpty() && g.assenza == null) righe += "Nessun dato per questo giorno."
        g.assenza?.let { righe += "${it.tipo.simbolo} ${it.tipo.etichetta}" }
        if (g.servizi.isNotEmpty()) {
            righe += "Turno: ${Riepilogo.ore(g.durataMinuti)}" + if (g.notturno) " (notturno)" else ""
            if (g.condottaMinuti > 0) righe += "Condotta o scorta: ${Riepilogo.ore(g.condottaMinuti)}" +
                if (g.condottaContinuativaMinuti > 0) " (continuativa ${Riepilogo.ore(g.condottaContinuativaMinuti)})" else ""
            if (g.straordinarioMinuti > 0) righe += "Straordinario: ${Riepilogo.ore(g.straordinarioMinuti)}"
            g.riposoDopoMinuti?.let {
                righe += "Riposo dopo: ${Riepilogo.ore(it)}" + if (g.riposoSettimanale) " (settimanale)" else " (giornaliero)"
            }
            if (g.dormita) righe += "💤 Dormita fuori residenza"
            righe += "Settimana (da lunedì): ${Riepilogo.ore(g.settimanaMinuti)} di lavoro"
        }
        AlertDialog.Builder(this)
            .setTitle(fmtGiornoLungo.format(giorno))
            .setMessage(righe.joinToString("\n"))
            .setPositiveButton("OK", null)
            .show()
    }

    // ---------- importazione, generatore, backup automatici ----------

    private fun importaDaTesto() {
        val campo = EditText(this).apply {
            hint = "10/11 06:12-14:40 cond 4:10\n11/11 22:00 04:30 RFR\n13/11 RS\n14/11 ferie"
            minLines = 6
            gravity = Gravity.TOP
        }
        val contenitore = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(TextView(this@MainActivity).apply {
                text = "Incolla il turno, una giornata per riga: data e orari di inizio e fine, oppure data e " +
                    "sigla (R, RS, ferie, malattia, D). Puoi aggiungere \"cond 4:10\" e \"RFR\"."
                textSize = 13f
            })
            addView(campo)
        }
        val profilo = Archivio.caricaTutto(this).servizi.lastOrNull()?.turno?.profilo ?: Profilo.MACCHINISTA
        AlertDialog.Builder(this)
            .setTitle("Importa turno da testo")
            .setView(contenitore)
            .setPositiveButton("Anteprima") { _, _ ->
                val r = Importa.analizza(campo.text.toString(), mese.year, profilo, System.currentTimeMillis())
                confermaInserimento("Importazione", r.servizi, r.assenze,
                    if (r.nonRiconosciute.isEmpty()) "" else "Righe non riconosciute:\n" + r.nonRiconosciute.joinToString("\n"))
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun generaTurno() {
        val dati = Archivio.caricaTutto(this)
        var da = if (YearMonth.now() == mese) LocalDate.now() else mese.atDay(1)
        val sequenza = EditText(this).apply { hint = "Mattina, Mattina, Pomeriggio, Notte, R, RS" }
        val settimane = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER; setText("4"); hint = "Settimane"
        }
        val btnDa = Button(this).apply { text = "Dal ${fmtGiornoLungo.format(da)}" }
        btnDa.setOnClickListener {
            DatePickerDialog(this, { _, y, m, g ->
                da = LocalDate.of(y, m + 1, g); btnDa.text = "Dal ${fmtGiornoLungo.format(da)}"
            }, da.year, da.monthValue - 1, da.dayOfMonth).show()
        }
        val nomi = if (dati.preferiti.isEmpty()) "nessuno: salvane prima qualcuno"
            else dati.preferiti.joinToString { it.nome }
        val contenitore = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(TextView(this@MainActivity).apply {
                text = "Scrivi la sequenza del turno, giorno per giorno, separata da virgole. Usa i nomi dei " +
                    "preferiti ($nomi) e le sigle R, RS, FE, D; \"-\" lascia il giorno vuoto."
                textSize = 13f
            })
            addView(sequenza); addView(btnDa)
            addView(TextView(this@MainActivity).apply { text = "Per quante settimane"; textSize = 13f })
            addView(settimane)
        }
        AlertDialog.Builder(this)
            .setTitle("Genera turno ciclico")
            .setView(contenitore)
            .setPositiveButton("Anteprima") { _, _ ->
                val giorni = (settimane.text.toString().toIntOrNull() ?: 4).coerceIn(1, 52) * 7
                val r = Generatore.genera(sequenza.text.toString(), da, giorni, dati.preferiti, System.currentTimeMillis())
                confermaInserimento("Turno ciclico", r.servizi, r.assenze,
                    if (r.sconosciuti.isEmpty()) "" else "Non riconosciuti (ignorati): " + r.sconosciuti.joinToString())
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    /** Anteprima con i controlli sui nuovi servizi, poi conferma dell'inserimento. */
    private fun confermaInserimento(titolo: String, servizi: List<Servizio>, assenze: List<Assenza>, avviso: String) {
        if (servizi.isEmpty() && assenze.isEmpty()) {
            AlertDialog.Builder(this).setTitle(titolo)
                .setMessage("Non ho trovato nulla da inserire." + if (avviso.isEmpty()) "" else "\n\n$avviso")
                .setPositiveButton("OK", null).show()
            return
        }
        val dati = Archivio.caricaTutto(this)
        val tutti = (dati.servizi + servizi).sortedBy { it.turno.inizio }
        val nuoviId = servizi.map { it.id }.toSet()
        val assenzeTutte = dati.assenze.filterNot { a -> assenze.any { it.data == a.data } } + assenze
        val conViolazioni = tutti.withIndex().count { (i, s) ->
            s.id in nuoviId && Analisi.verificaServizio(tutti, i, assenzeTutte).any { it.gravita == Gravita.VIOLAZIONE }
        }
        val primo = (servizi.map { it.turno.inizio.toLocalDate() } + assenze.map { it.data }).minOrNull()
        val ultimo = (servizi.map { it.turno.inizio.toLocalDate() } + assenze.map { it.data }).maxOrNull()
        val messaggio = buildString {
            append("${servizi.size} servizi e ${assenze.size} giornate di assenza o riposo")
            if (primo != null && ultimo != null) append(", dal ${fmtGiornoLungo.format(primo)} al ${fmtGiornoLungo.format(ultimo)}")
            append(".")
            if (conViolazioni > 0) append("\n\n⛔ $conViolazioni servizi hanno violazioni di riposo o limiti: " +
                "potrai vederle in agenda.")
            if (avviso.isNotEmpty()) append("\n\n$avviso")
        }
        AlertDialog.Builder(this)
            .setTitle(titolo)
            .setMessage(messaggio)
            .setPositiveButton("Inserisci") { _, _ ->
                val aggiunti = Archivio.aggiungi(this, servizi, assenze)
                primo?.let { mese = YearMonth.from(it) }
                aggiorna()
                val doppioni = servizi.size - aggiunti
                Toast.makeText(this, "Inseriti $aggiunti servizi e ${assenze.size} giornate" +
                    if (doppioni > 0) " ($doppioni servizi già presenti saltati)" else "", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    // ---------- orario Altarail da Regione Lombardia ----------

    private fun orarioAltarail() {
        val orario = OrarioAltarail.carica(this)
        val fmt = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ITALIAN)
        val stato = if (orario == null) "Nessun orario importato." else buildString {
            append("Orario importato: ${orario.numeroCorse} corse, ${orario.stazioni.size} stazioni.")
            if (orario.validoDal != null && orario.validoAl != null)
                append("\nValido dal ${fmt.format(orario.validoDal)} al ${fmt.format(orario.validoAl)}.")
            if (orario.validoAl?.isBefore(LocalDate.now()) == true) append("\n⚠️ Scaduto: importa quello nuovo.")
        }
        AlertDialog.Builder(this)
            .setTitle("Orario Azienda")
            .setMessage("$stato\n\nL'orario ufficiale dei treni dell'Azienda è pubblicato da Regione Lombardia sul " +
                "portale Open Data in formato GTFS. Scarica lo zip dal portale, poi importalo: nelle righe " +
                "del servizio potrai cercare un treno per numero e avere stazioni e orari del giorno.\n\n" +
                "Fonte: ${OrarioAltarail.FONTE}.")
            .setPositiveButton("Importa lo zip") { _, _ ->
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }, RICHIESTA_GTFS)
            }
            .setNeutralButton("Scarica dal portale") { _, _ ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(OrarioAltarail.DOWNLOAD_ZIP)))
            }
            .setNegativeButton(if (orario == null) "Chiudi" else "Stazioni in rubrica") { _, _ ->
                if (orario != null) stazioniDallOrario(orario)
            }
            .show()
    }

    private fun importaIcs(uri: Uri) {
        try {
                val testo = contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                    ?: throw IllegalArgumentException("File vuoto")
                val letti = CalendarioIcs.leggi(testo, System.currentTimeMillis())
                if (letti.servizi.isEmpty() && letti.assenze.isEmpty()) {
                    Toast.makeText(this, "Nel file non ci sono servizi o assenze di Ferromicon", Toast.LENGTH_LONG).show()
                } else AlertDialog.Builder(this)
                    .setTitle("Aggiungere all'agenda?")
                    .setMessage("Il file contiene ${letti.servizi.size} servizi e ${letti.assenze.size} giornate di " +
                        "assenza o riposo. Si aggiungono a quelli che hai; i riposi e le assenze negli stessi giorni " +
                        "vengono sostituiti.")
                    .setPositiveButton("Aggiungi") { _, _ ->
                        val nuovi = Archivio.aggiungi(this, letti.servizi, letti.assenze)
                        aggiorna()
                        Toast.makeText(this, "Aggiunti $nuovi servizi", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Annulla", null)
                    .show()
            } catch (e: Exception) {
                Toast.makeText(this, "Importazione non riuscita: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun importaRipristino(uri: Uri) {
        try {
                val testo = contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                    ?: throw IllegalArgumentException("File vuoto")
                val dati = Archivio.daTesto(testo)
                AlertDialog.Builder(this)
                    .setTitle("Ripristinare il backup?")
                    .setMessage("Il file contiene ${dati.servizi.size} servizi e ${dati.assenze.size} giornate " +
                        "di assenza o riposo. I dati attuali sul telefono verranno sostituiti.")
                    .setPositiveButton("Ripristina") { _, _ ->
                        Archivio.salvaTutto(this, dati); aggiorna()
                        Toast.makeText(this, "Backup ripristinato", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Annulla", null)
                    .show()
            } catch (e: Exception) {
                Toast.makeText(this, "File non valido: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    /** Un solo punto di ingresso: il tipo di file si riconosce dall'estensione. */
    private fun smistaFile(uri: Uri) {
        val nome = nomeFile(uri).lowercase()
        when {
            nome.endsWith(".zip") -> importaOrario(uri)
            nome.endsWith(".ics") -> importaIcs(uri)
            nome.endsWith(".json") -> importaRipristino(uri)
            nome.endsWith(".pdf") -> AlertDialog.Builder(this)
                .setTitle("Che PDF è?")
                .setMessage("Dal nome non si capisce il tipo. Scegli dove importarlo: nella schermata " +
                    "tocca «Importa» e scegli di nuovo il file.")
                .setPositiveButton("Cedolino") { _, _ -> startActivity(Intent(this, CedoliniActivity::class.java)) }
                .setNeutralButton("Diario") { _, _ -> startActivity(Intent(this, DiarioActivity::class.java)) }
                .setNegativeButton("Calendario agente") { _, _ -> startActivity(Intent(this, AgenteActivity::class.java)) }
                .show()
            else -> Toast.makeText(this, "Formato non riconosciuto: usa .zip, .ics, .json o .pdf", Toast.LENGTH_LONG).show()
        }
    }

    private fun nomeFile(uri: Uri): String {
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            val colonna = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (colonna >= 0 && c.moveToFirst()) return c.getString(colonna) ?: ""
        }
        return uri.lastPathSegment ?: ""
    }

    /** Importazione in un thread separato: l'orario regionale è grande. */
    private fun importaOrario(uri: Uri) {
        val attesa = AlertDialog.Builder(this)
            .setTitle("Importazione dell'orario")
            .setMessage("Lettura del file in corso, può richiedere qualche secondo…")
            .setCancelable(false)
            .show()
        Thread {
            val esito = runCatching { OrarioAltarail.importa(applicationContext, uri) }
            runOnUiThread {
                attesa.dismiss()
                esito.onSuccess { o ->
                    AlertDialog.Builder(this)
                        .setTitle("Orario importato")
                        .setMessage("${o.numeroCorse} corse e ${o.stazioni.size} stazioni. Ora puoi cercare i treni per " +
                            "numero nelle righe del servizio.\n\nVuoi aggiungere le stazioni alla rubrica?")
                        .setPositiveButton("Aggiungi") { _, _ -> stazioniDallOrario(o) }
                        .setNegativeButton("No", null)
                        .show()
                }.onFailure { e ->
                    AlertDialog.Builder(this)
                        .setTitle("Importazione non riuscita")
                        .setMessage(e.message ?: "File non valido.")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }.start()
    }

    /** Aggiunge alla rubrica le stazioni dell'orario che mancano (solo il nome). */
    private fun stazioniDallOrario(orario: Orario) {
        val dati = Archivio.caricaTutto(this)
        val presenti = dati.stazioni.map { it.nome.lowercase() }.toSet()
        var id = System.currentTimeMillis()
        val nuove = orario.stazioni.distinct().filter { it.lowercase() !in presenti }.map { Stazione(id++, it) }
        if (nuove.isNotEmpty()) Archivio.salvaTutto(this, dati.copy(stazioni = dati.stazioni + nuove))
        Toast.makeText(this, "Aggiunte ${nuove.size} stazioni alla rubrica", Toast.LENGTH_SHORT).show()
    }

    // ---------- cestino ----------

    /** Sposta nel cestino e offre subito l'annullamento. */
    private fun eliminaConAnnulla(servizio: Servizio? = null, assenza: Assenza? = null) {
        servizio?.let { Archivio.elimina(this, it.id) }
        assenza?.let { Archivio.eliminaAssenza(this, it.id) }
        aggiorna()
        val voce = Archivio.caricaTutto(this).cestino.lastOrNull { v ->
            (servizio != null && v.servizio?.id == servizio.id) || (assenza != null && v.assenza?.id == assenza.id)
        } ?: return
        AlertDialog.Builder(this)
            .setMessage("${voce.descrizione} spostato nel cestino.")
            .setPositiveButton("Annulla") { _, _ -> Archivio.ripristina(this, voce); aggiorna() }
            .setNegativeButton("OK", null)
            .show()
    }

    private fun cestino() {
        val voci = Archivio.caricaTutto(this).cestino.sortedByDescending { it.eliminatoIl }
        if (voci.isEmpty()) {
            AlertDialog.Builder(this).setTitle("Cestino")
                .setMessage("Il cestino è vuoto. Gli elementi eliminati restano qui per 30 giorni.")
                .setPositiveButton("OK", null).show()
            return
        }
        val fmt = DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.ITALIAN)
        AlertDialog.Builder(this)
            .setTitle("Tocca per ripristinare")
            .setItems(voci.map { "${it.descrizione}  ·  eliminato ${fmt.format(it.eliminatoIl)}" }.toTypedArray()) { _, i ->
                Archivio.ripristina(this, voci[i]); aggiorna()
                Toast.makeText(this, "${voci[i].descrizione} ripristinato", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("Svuota") { _, _ ->
                AlertDialog.Builder(this)
                    .setMessage("Eliminare definitivamente ${voci.size} elementi?")
                    .setPositiveButton("Svuota") { _, _ -> Archivio.svuotaCestino(this) }
                    .setNegativeButton("Annulla", null)
                    .show()
            }
            .setNegativeButton("Chiudi", null)
            .show()
    }

    private fun backupAutomatici() {
        val copie = Archivio.backupDisponibili(this)
        if (copie.isEmpty()) {
            AlertDialog.Builder(this).setTitle("Backup automatici")
                .setMessage("L'app salva una copia al giorno, ad ogni modifica, e conserva le ultime 14. " +
                    "Non ci sono ancora copie.")
                .setPositiveButton("OK", null).show()
            return
        }
        val etichette = copie.map { f ->
            val d = runCatching { Archivio.leggiBackup(f) }.getOrNull()
            f.name.removePrefix("servizi-").removeSuffix(".json") +
                (d?.let { "  ·  ${it.servizi.size} servizi, ${it.assenze.size} assenze" } ?: "  ·  non leggibile")
        }
        AlertDialog.Builder(this)
            .setTitle("Ripristina una copia automatica")
            .setItems(etichette.toTypedArray()) { _, i ->
                val dati = runCatching { Archivio.leggiBackup(copie[i]) }.getOrNull() ?: return@setItems
                AlertDialog.Builder(this)
                    .setMessage("Sostituire i dati attuali con la copia del ${etichette[i]}?")
                    .setPositiveButton("Ripristina") { _, _ -> Archivio.salvaTutto(this, dati); aggiorna() }
                    .setNegativeButton("Annulla", null)
                    .show()
            }
            .setNegativeButton("Chiudi", null)
            .show()
    }

    /** Alla prima apertura: invito a impostare data di assunzione, livello e promemoria. */
    private fun primaConfigurazione() {
        val dati = Archivio.caricaTutto(this)
        if (dati.impostazioni.profilo == null) {
            // Chi ha già dei servizi: si propone il profilo dell'ultimo; altrimenti si chiede.
            val ultimo = dati.servizi.maxByOrNull { it.turno.inizio }?.turno?.profilo
            if (ultimo != null) {
                Archivio.salvaImpostazioni(this, dati.impostazioni.copy(profilo = ultimo))
            } else {
                scegliProfilo(dati)
                return
            }
        }
        val prefs = getPreferences(MODE_PRIVATE)
        if (prefs.getInt("configurato", 0) == 1) return
        prefs.edit().putInt("configurato", 1).apply()
        if (dati.servizi.isNotEmpty() || dati.impostazioni.dataAssunzione != null) return
        proponiTurno()
    }

    /** Prima scelta obbligatoria: nome e profilo (macchinista o capotreno). Da qui si personalizzano regole e limiti. */
    private fun scegliProfilo(dati: Dati, nomeProposto: String = "") {
        val campo = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            hint = "Il tuo nome e cognome"
            setText(nomeProposto)
        }
        val contenitore = LinearLayout(this).apply {
            setPadding((24 * resources.displayMetrics.density).toInt(), 0, (24 * resources.displayMetrics.density).toInt(), 0)
            addView(campo, LinearLayout.LayoutParams(-1, -2))
        }
        fun conNome(azione: (String) -> Unit) {
            val nome = campo.text.toString().trim()
            if (nome.isEmpty()) {
                Toast.makeText(this, "Scrivi prima il tuo nome", Toast.LENGTH_SHORT).show()
                scegliProfilo(dati, "")
            } else azione(nome)
        }
        AlertDialog.Builder(this)
            .setTitle("Benvenuto in Ferromicon")
            .setMessage("Scrivi il tuo nome: sarà il nome del tuo profilo. Poi scegli se sei macchinista o capotreno: " +
                "da questa scelta dipendono limiti di prestazione, condotta o scorta, riposi e indennità. " +
                "Puoi anche caricare un cedolino in PDF: l'app legge da sola profilo, livello, CID, assunzione e ferie. " +
                "Potrai cambiare tutto dalle Impostazioni.")
            .setView(contenitore)
            .setCancelable(false)
            .setPositiveButton(Profilo.MACCHINISTA.etichetta) { _, _ -> conNome { salvaProfilo(dati, Profilo.MACCHINISTA, it) } }
            .setNegativeButton(Profilo.CAPOTRENO.etichetta) { _, _ -> conNome { salvaProfilo(dati, Profilo.CAPOTRENO, it) } }
            .setNeutralButton(Profilo.UFFICIO.etichetta) { _, _ -> conNome { salvaProfilo(dati, Profilo.UFFICIO, it) } }
            .show()
    }

    private fun salvaProfilo(dati: Dati, p: Profilo, nome: String) {
        Archivio.salvaImpostazioni(this, Archivio.caricaTutto(this).impostazioni.copy(profilo = p, nome = nome))
        getPreferences(MODE_PRIVATE).edit().putInt("configurato", 1).apply()
        proponiTurno()
    }

    private fun proponiTurno() {
        AlertDialog.Builder(this)
            .setTitle("Il tuo turno")
            .setMessage("Inserisci le giornate del turno del tuo impianto: il calendario si compila da solo " +
                "fino al prossimo cambio turno, e ogni giorno puoi ricaricare la giornata di default e " +
                "correggerla. Ti servono le giornate salvate come servizi preferiti: puoi farlo anche più tardi " +
                "da Strumenti > Il mio turno.\n\nPer ferie e scatti imposta anche data di assunzione e livello.")
            .setPositiveButton("Il mio CID e calendario") { _, _ -> startActivity(Intent(this, AgenteActivity::class.java)) }
            .setNeutralButton("Leggi da un cedolino") { _, _ ->
                startActivity(Intent(this, CedoliniActivity::class.java).putExtra(CedoliniActivity.EXTRA_PRIMO, true))
            }
            .setNegativeButton("Più tardi", null)
            .show()
    }

    private fun esporta() {
        val csv = Csv.genera(Archivio.caricaTutto(this))
        val invio = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Ferromicon - servizi e assenze")
            putExtra(Intent.EXTRA_TEXT, csv)
        }
        startActivity(Intent.createChooser(invio, "Esporta agenda"))
    }

    /** Titolo di gruppo con il triangolino verde. */
    private fun intestazione(t: String) = contenuto.addView(TextView(this).apply {
        val sp = android.text.SpannableString("▶ $t")
        sp.setSpan(android.text.style.ForegroundColorSpan(Stile.primario), 0, 1, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        text = sp
        textSize = 17f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(Stile.testo)
        setPadding(dp(4), dp(18), 0, dp(6))
    })

    /** Nuova sezione: titolo e scheda bianca in cui finiscono i testi e le voci che seguono. */
    private fun sezione(t: String) {
        intestazione(t)
        val scheda = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Stile.scheda()
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        contenuto.addView(scheda, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        destinazione = scheda
    }

    private fun testo(t: String, colore: Int? = null, dimensione: Float = 15f, grassetto: Boolean = false) =
        destinazione.addView(TextView(this).apply {
            text = t
            textSize = dimensione
            setPadding(0, dp(2), 0, dp(2))
            setTextColor(colore ?: Stile.testo)
            if (grassetto) setTypeface(typeface, Typeface.BOLD)
        })

    private fun numero(v: Double) = if (v % 1.0 == 0.0) v.toInt().toString() else v.toString().replace('.', ',')

    private fun colore(g: Gravita): Int? = when (g) {
        Gravita.VIOLAZIONE -> Stile.errore
        Gravita.ATTENZIONE -> Stile.attenzione
        else -> null
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val RICHIESTA_BACKUP = 10
        private const val RICHIESTA_RIPRISTINO = 11
        private const val RICHIESTA_ICS = 12
        private const val RICHIESTA_GTFS = 13
        private const val RICHIESTA_IMPORTA_ICS = 14
        private const val RICHIESTA_IMPORTA_FILE = 15

        fun ore(min: Long) = String.format("%d h %02d'", min / 60, min % 60)

        fun formatta(e: Esito): String {
            val simbolo = when (e.gravita) {
                Gravita.OK -> "✅"
                Gravita.INFO -> "ℹ️"
                Gravita.ATTENZIONE -> "⚠️"
                Gravita.VIOLAZIONE -> "⛔"
            }
            return "$simbolo ${e.messaggio}"
        }
    }
}
