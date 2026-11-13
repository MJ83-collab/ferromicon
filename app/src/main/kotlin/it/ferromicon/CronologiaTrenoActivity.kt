package it.ferromicon

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.time.YearMonth
import java.time.LocalDate
import java.time.DateTimeException
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Cronologia dei treni per numero: cerca il numero nelle righe di tutti i servizi
 * e mostra quante volte il treno è stato fatto, in quali mesi e in quale turno.
 */
class CronologiaTrenoActivity : Activity() {

    private lateinit var dati: Dati
    private lateinit var campo: EditText
    private lateinit var risultati: LinearLayout

    private val fmtData = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ITALIAN)
    private val fmtMese = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ITALIAN)
    private val fmtOra = DateTimeFormatter.ofPattern("HH:mm")

    private class Occorrenza(val servizio: Servizio, val riga: RigaTreno)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Cerca treno"
        dati = Archivio.caricaTutto(this)

        val radice = ScrollView(this)
        val modulo = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        campo = EditText(this).apply {
            hint = "Numero treno, es. 2514, oppure data gg/mm"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
        }
        val cerca = Button(this).apply {
            text = "Cerca"
            setOnClickListener { avviaRicerca() }
        }
        risultati = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        modulo.addView(campo)
        modulo.addView(cerca)
        modulo.addView(risultati)
        radice.addView(modulo)
        setContentView(radice)

        mostra(TextView(this).apply { text = "Inserisci un numero treno per vedere le volte che lo hai fatto." })
    }

    private fun normalizza(s: String) = s.filter { !it.isWhitespace() }.uppercase()

    /** Data digitata come gg/mm o gg/mm/aaaa (anno corrente se omesso). */
    private fun cercaData(testo: String): LocalDate? {
        val m = Regex("""(\d{1,2})/(\d{1,2})(?:/(\d{4}))?""").matchEntire(testo.trim()) ?: return null
        val anno = m.groupValues[3].ifEmpty { LocalDate.now().year.toString() }.toInt()
        return try { LocalDate.of(anno, m.groupValues[2].toInt(), m.groupValues[1].toInt()) }
        catch (e: DateTimeException) { null }
    }

    private fun mostraServiziDel(data: LocalDate) {
        val trovati = dati.servizi.filter { it.turno.inizio.toLocalDate() == data }.sortedBy { it.turno.inizio }
        val intestazione = data.format(fmtData)
        if (trovati.isEmpty()) {
            mostra(TextView(this).apply { text = "Nessun servizio il $intestazione." })
            return
        }
        mostra(TextView(this).apply {
            text = trovati.joinToString("\n\n") { s ->
                val treni = s.righe.map { it.numero }.filter { it.isNotBlank() }.distinct().joinToString(", ")
                "${fmtOra.format(s.turno.inizio)}–${fmtOra.format(s.turno.fine)}" +
                    (if (treni.isEmpty()) "" else "  ·  $treni")
            }
            textSize = 15f
        })
    }

    private fun avviaRicerca() {
        cercaData(campo.text.toString())?.let { mostraServiziDel(it); return }
        val chiave = normalizza(campo.text.toString())
        if (chiave.isEmpty()) {
            mostra(TextView(this).apply { text = "Inserisci un numero treno." })
            return
        }
        val occorrenze = dati.servizi
            .flatMap { s -> s.righe.filter { normalizza(it.numero) == chiave }.map { Occorrenza(s, it) } }
            .sortedByDescending { it.riga.partenza }

        if (occorrenze.isEmpty()) {
            mostra(TextView(this).apply { text = "Il treno $chiave non compare nei servizi registrati." })
            return
        }

        val vetture = occorrenze.count { it.riga.vettura }
        val riepilogo = TextView(this).apply {
            textSize = 16f
            text = buildString {
                append("Treno $chiave: ${occorrenze.size} volte")
                if (vetture > 0) append(" (di cui $vetture come vettura)")
            }
            setPadding(0, dp(8), 0, dp(8))
        }
        val righeMesi = TextView(this).apply {
            textSize = 15f
            text = occorrenze.groupBy { YearMonth.from(it.riga.partenza) }
                .toSortedMap(compareByDescending { it })
                .map { (mese, lista) -> "${fmtMese.format(mese)}: ${lista.size}" }
                .joinToString("\n")
            setPadding(0, 0, 0, dp(12))
        }
        val elenco = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        occorrenze.groupBy { YearMonth.from(it.riga.partenza) }
            .toSortedMap(compareByDescending { it })
            .forEach { (mese, lista) ->
                elenco.addView(TextView(this).apply {
                    textSize = 15f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    text = fmtMese.format(mese).replaceFirstChar { it.uppercase() }
                    setPadding(0, dp(10), 0, dp(4))
                })
                lista.forEach { o -> elenco.addView(voce(o)) }
            }

        mostra(riepilogo, righeMesi, elenco)
    }

    private fun voce(o: Occorrenza): TextView {
        val r = o.riga
        val turno = o.servizio.origineTurno.ifBlank { "inserito a mano" }
        val tratta = listOf(r.da, r.a).filter { it.isNotBlank() }.joinToString(" → ")
        return TextView(this).apply {
            textSize = 14f
            text = buildString {
                append(fmtData.format(r.partenza.toLocalDate()))
                append("  ${fmtOra.format(r.partenza)}–${fmtOra.format(r.arrivo)}")
                if (r.vettura) append("  [V]")
                if (tratta.isNotEmpty()) append("\n    $tratta")
                append("\n    turno: $turno")
                if (o.servizio.materiale.isNotBlank()) append(" · materiale: ${o.servizio.materiale}")
            }
            setPadding(0, dp(4), 0, dp(4))
        }
    }

    private fun mostra(vararg viste: View) {
        risultati.removeAllViews()
        viste.forEach { risultati.addView(it) }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
