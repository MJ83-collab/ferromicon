package it.ferromicon

import android.app.Activity
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.content.Intent
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import java.time.LocalDate
import java.time.YearMonth

/**
 * Confronto turno senza e con straordinario. L'utente sceglie profilo, base oraria,
 * fonte delle ore e voci da mostrare. Netto con ricalcolo annuo (Fisco.nettoExtra).
 * Stima indicativa, non il conteggio del cedolino.
 */
class ConfrontoActivity : Activity() {

    private data class Tipo(val nome: String, val magg: Double)
    private val tipi = listOf(
        Tipo("Feriale diurno (+18%)", 0.18),
        Tipo("Feriale notturno o festivo diurno (+35%)", 0.35),
        Tipo("Festivo notturno (+50%)", 0.50)
    )
    private val profili = listOf(Profilo.CAPOTRENO, Profilo.MACCHINISTA, Profilo.UFFICIO)
    private val nomiProfili = listOf("Capotreno", "Macchinista", "Ufficio")
    private val nomiDivisori = listOf("160 ore fisse", "Ore effettive del mese")
    private val nomiFonti = listOf("Ore inserite a mano", "Dai servizi segnati nel mese")

    private lateinit var profilo: Spinner
    private lateinit var divisore: Spinner
    private lateinit var fonte: Spinner
    private lateinit var tipo: Spinner
    private lateinit var oreMano: EditText
    private lateinit var oreNotteMano: EditText
    private lateinit var regolarizzazioni: EditText
    private lateinit var senzaEsazione: EditText
    private lateinit var sovrattassa: EditText
    private lateinit var verbali: EditText
    private lateinit var lungaPercorrenza: CheckBox
    private lateinit var risultato: TextView
    private lateinit var cTurno: CheckBox
    private lateinit var cConStra: CheckBox
    private lateinit var cOra: CheckBox
    private lateinit var cImpegno: CheckBox
    private lateinit var cNotturne: CheckBox
    private lateinit var cTasse: CheckBox
    private lateinit var cPremio: CheckBox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Confronto turno e straordinario"
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        col.addView(TextView(this).apply {
            text = "Stima indicativa. Il netto ricalcola l'anno con detrazioni, partendo dal reddito imponibile " +
                "in Impostazioni → Stima del netto."
            textSize = 12f; setTextColor(Stile.secondario)
        })

        col.addView(label("Profilo"))
        profilo = spinner(nomiProfili); col.addView(profilo)
        col.addView(label("Valore dell'ora"))
        divisore = spinner(nomiDivisori); col.addView(divisore)
        col.addView(label("Ore di straordinario"))
        fonte = spinner(nomiFonti); col.addView(fonte)
        col.addView(label("Ore di straordinario inserite a mano"))
        oreMano = numero("0"); col.addView(oreMano)
        col.addView(label("di cui notturne"))
        oreNotteMano = numero("0"); col.addView(oreNotteMano)
        col.addView(label("Maggiorazione (solo per ore a mano)"))
        tipo = spinner(tipi.map { it.nome }); col.addView(tipo)

        col.addView(label("Cosa confrontare"))
        cTurno = check("Solo turno", true, col)
        cConStra = check("Turno con straordinario", true, col)
        cOra = check("Valore dell'ora: normale e straordinaria", true, col)
        cImpegno = check("Impegno: ore di straordinario", true, col)
        cNotturne = check("Ore notturne dello straordinario", true, col)
        cTasse = check("Trattenute sullo straordinario", true, col)
        cPremio = check("Premio biglietti (solo capotreno)", false, col)

        col.addView(label("Premio biglietti del mese"))
        col.addView(hint("Regolarizzazioni a bordo (2 € ciascuna)"))
        regolarizzazioni = numero("0"); col.addView(regolarizzazioni)
        senzaEsazione = numero("0"); col.addView(hint("Biglietti emessi senza esazione, importo (25%)")); col.addView(senzaEsazione)
        sovrattassa = numero("0"); col.addView(hint("Sovrattassa riscossa, importo (25%)")); col.addView(sovrattassa)
        verbali = numero("0"); col.addView(hint("Verbali pagati a posteriori (2 € ciascuno)")); col.addView(verbali)
        lungaPercorrenza = check("Lunga percorrenza (+7% sulla regolarizzazione)", false, col)

        risultato = TextView(this).apply { textSize = 15f; setPadding(0, dp(16), 0, 0) }
        col.addView(risultato)
        col.addView(TextView(this).apply {
            text = "Vademecum"
            textSize = 15f; setPadding(0, dp(20), 0, dp(8))
            setOnClickListener { startActivity(android.content.Intent(this@ConfrontoActivity, VademecumActivity::class.java)) }
        })
        col.addView(Button(this).apply {
            text = "Condividi il confronto"; isAllCaps = false
            setOnClickListener { condividi() }
        })
        caricaPremio()
        setContentView(ScrollView(this).apply { addView(col) })

        val aggiorna = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = calcola()
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        }
        listOf(oreMano, oreNotteMano, regolarizzazioni, senzaEsazione, sovrattassa, verbali)
            .forEach { it.addTextChangedListener(aggiorna) }
        listOf(profilo, divisore, fonte, tipo).forEach { sp ->
            sp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) = calcola()
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
        }
        listOf(cTurno, cConStra, cOra, cImpegno, cNotturne, cTasse, cPremio, lungaPercorrenza).forEach { c ->
            c.setOnCheckedChangeListener { _, _ -> calcola() }
        }
        calcola()
    }

    private fun calcola() {
        val dati = Archivio.caricaTutto(this)
        val imp = dati.impostazioni
        val oggi = LocalDate.now()
        val mese = YearMonth.from(oggi)
        salvaPremio(mese)
        if (imp.livello == null) {
            risultato.text = "Per calcolare la paga imposta il livello in Impostazioni."
            return
        }
        if (imp.redditoAnnuo <= 0) {
            risultato.text = "Per il netto inserisci il reddito imponibile annuo in Impostazioni → Stima del netto."
            return
        }
        val prof = profili[profilo.selectedItemPosition.coerceAtLeast(0)]
        val divisoreScelto = if (divisore.selectedItemPosition == 1) ConfrontoStraordinario.Divisore.ORE_EFFETTIVE
            else ConfrontoStraordinario.Divisore.FISSE_160
        val mensile = Competenze.pagaOraria(imp, prof, oggi) * 160.0

        val serviziMese = dati.servizi.filter { YearMonth.from(it.turno.inizio) == mese }
        val turni = serviziMese.map { it.turnoEffettivo() }
        val oreEffettive = ConfrontoStraordinario.oreTurni(turni, mese)
        val somma = ConfrontoStraordinario.sommaStraordinario(turni)
        val a = somma.a; val b = somma.b; val c = somma.c; val notte = somma.notturni

        val daServizi = fonte.selectedItemPosition == 1
        val oreStra: Double
        val magg: Double
        val oreNotturne: Double
        if (daServizi) {
            oreStra = (a + b + c) / 60.0
            magg = ConfrontoStraordinario.maggiorazioneMedia(a, b, c)
            oreNotturne = notte / 60.0
        } else {
            oreStra = numeroIn(oreMano)
            magg = tipi[tipo.selectedItemPosition.coerceAtLeast(0)].magg
            oreNotturne = numeroIn(oreNotteMano).coerceAtMost(oreStra)
        }

        val p = imp.parametriFisco()
        val anno = oggi.year
        val r = ConfrontoStraordinario.calcola(ConfrontoStraordinario.Dati(
            mensile = mensile, oreEffettiveMese = oreEffettive, divisore = divisoreScelto,
            reddito = imp.redditoAnnuo, anno = anno, parametri = p,
            oreStra = oreStra, maggiorazione = magg, oreNotturneStra = oreNotturne,
            tariffaNotturna = Competenze.tariffaNotturna(oggi)))

        val voci = ConfrontoStraordinario.Scelte(
            turnoSolo = cTurno.isChecked, turnoConStraordinario = cConStra.isChecked,
            valoreOra = cOra.isChecked, impegno = cImpegno.isChecked,
            notturne = cNotturne.isChecked, tasse = cTasse.isChecked)
        val premioAttivo = cPremio.isChecked && prof == Profilo.CAPOTRENO

        risultato.text = buildString {
            if (voci.turnoSolo) {
                appendLine("SOLO TURNO")
                appendLine("Lordo ${Competenze.euro(r.lordoTurno)} → netto ${Competenze.euro(r.nettoTurno)}")
                appendLine()
            }
            if (voci.turnoConStraordinario) {
                appendLine("TURNO CON STRAORDINARIO")
                appendLine("Lordo ${Competenze.euro(r.lordoTurno + r.lordoStra)} " +
                    "(straordinario ${Competenze.euro(r.lordoStra)}) → netto ${Competenze.euro(r.nettoConStra)}")
                appendLine("Differenza netta: +${Competenze.euro(r.nettoStra)}")
                appendLine()
            }
            if (voci.valoreOra) {
                appendLine("VALORE DELL'ORA")
                appendLine("Normale: lordo ${Competenze.euro(r.lordoOraNormale)} → netto ${Competenze.euro(r.nettoOraNormale)}")
                appendLine("Straordinaria: lordo ${Competenze.euro(r.lordoOraStra)} → netto ${Competenze.euro(r.nettoOraStra)}")
                appendLine()
            }
            if (voci.impegno) {
                appendLine("IMPEGNO")
                appendLine("${"%.1f".format(r.oreStra)} ore di straordinario nel mese")
                appendLine()
            }
            if (voci.notturne) {
                appendLine("NOTTURNE")
                appendLine("${"%.1f".format(r.oreNotturne)} ore notturne nello straordinario: " +
                    "indennità netta ${Competenze.euro(r.nettoNotturno)}")
                appendLine()
            }
            if (voci.tasse && r.lordoStra > 0) {
                appendLine("TRATTENUTE SULLO STRAORDINARIO")
                appendLine("${Competenze.euro(r.trattenuteStra)} su ${Competenze.euro(r.lordoStra)} lordi " +
                    "(${"%.0f".format(r.trattenuteStra / r.lordoStra * 100)}%)")
                appendLine()
            }
            val oreMese = turni.sumOf { java.time.Duration.between(it.inizio, it.fine).toMinutes() } / 60.0
            val limiteMese = LimitiOre.limiteMensile(mese.lengthOfMonth())
            appendLine("LIMITI DI ORE")
            appendLine("Ore del mese ${"%.1f".format(oreMese)} di ${"%.1f".format(limiteMese)} (media 38 h × giorni / 7)" +
                if (oreMese > limiteMese) " → oltre il limite" else "")
            val settimane = LimitiOre.settimaneOltre(
                turni.map { it.inizio to it.fine }, LimitiOre.LIMITE_SETTIMANALE_PROGRAMMAZIONE)
            if (settimane.isEmpty()) appendLine("Nessuna settimana oltre 44 h (programmazione).")
            settimane.forEach { (lunedi, ore) ->
                appendLine("Settimana dal ${lunedi.dayOfMonth}/${lunedi.monthValue}: ${"%.1f".format(ore)} h, oltre 44 h")
            }
            appendLine()
            if (premioAttivo) {
                val d = PremioBiglietti.Dati(
                    regolarizzazioni = numeroIn(regolarizzazioni).toInt(),
                    bigliettiSenzaEsazione = numeroIn(senzaEsazione),
                    sovrattassaRiscossa = numeroIn(sovrattassa),
                    verbaliPagati = numeroIn(verbali).toInt(),
                    lungaPercorrenza = lungaPercorrenza.isChecked)
                val lista = PremioBiglietti.calcola(d)
                appendLine("PREMIO BIGLIETTI (capotreno)")
                lista.forEach { appendLine("${it.codice} ${it.descrizione}: ${Competenze.euro(it.importo)}") }
                val tot = lista.sumOf { it.importo }
                appendLine("Totale lordo ${Competenze.euro(tot)} → netto " +
                    Competenze.euro(Fisco.nettoExtra(imp.redditoAnnuo, tot, anno, p).netto))
            }
        }.trimEnd()
    }

    /** Gli importi del premio si ricordano per ogni mese, così non vanno reinseriti ogni volta. */
    private fun chiavePremio(mese: YearMonth, campo: String) = "premio_${mese}_$campo"

    private fun caricaPremio() {
        val p = getSharedPreferences("confronto", MODE_PRIVATE)
        val mese = YearMonth.now()
        regolarizzazioni.setText(p.getString(chiavePremio(mese, "reg"), "0"))
        senzaEsazione.setText(p.getString(chiavePremio(mese, "senza"), "0"))
        sovrattassa.setText(p.getString(chiavePremio(mese, "sovr"), "0"))
        verbali.setText(p.getString(chiavePremio(mese, "verb"), "0"))
        lungaPercorrenza.isChecked = p.getBoolean(chiavePremio(mese, "lp"), false)
    }

    private fun salvaPremio(mese: YearMonth) {
        getSharedPreferences("confronto", MODE_PRIVATE).edit()
            .putString(chiavePremio(mese, "reg"), regolarizzazioni.text.toString())
            .putString(chiavePremio(mese, "senza"), senzaEsazione.text.toString())
            .putString(chiavePremio(mese, "sovr"), sovrattassa.text.toString())
            .putString(chiavePremio(mese, "verb"), verbali.text.toString())
            .putBoolean(chiavePremio(mese, "lp"), lungaPercorrenza.isChecked)
            .apply()
    }

    private fun condividi() {
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, risultato.text.toString())
        }, "Condividi il confronto"))
    }

    private fun numeroIn(e: EditText): Double =
        e.text.toString().replace(',', '.').toDoubleOrNull() ?: 0.0

    private fun spinner(voci: List<String>) = Spinner(this).apply {
        adapter = ArrayAdapter(this@ConfrontoActivity, android.R.layout.simple_spinner_dropdown_item, voci)
    }

    private fun numero(valore: String) = EditText(this).apply {
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        setText(valore)
    }

    private fun check(testo: String, attivo: Boolean, col: LinearLayout) = CheckBox(this).apply {
        text = testo; isChecked = attivo
        col.addView(this)
    }

    private fun hint(t: String) = TextView(this).apply {
        text = t; textSize = 12f; setTextColor(Stile.secondario); setPadding(0, dp(6), 0, 0)
    }

    private fun label(t: String) = TextView(this).apply {
        text = t; textSize = 14f; setPadding(0, dp(12), 0, dp(2))
    }

    override fun onResume() {
        super.onResume()
        window.decorView.post { Stile.rifinisci(window.decorView) }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
