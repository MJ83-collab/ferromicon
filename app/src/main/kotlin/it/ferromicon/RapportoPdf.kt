package it.ferromicon

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.OutputStream
import java.time.Duration
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Report PDF del mese o dell'anno (formato A4, creato con la libreria del sistema, senza dipendenze).
 * Contiene agenda, assenze, cartellino SAP, cedolini e l'archivio (stazioni, perimetri, preferiti, CU).
 * Con l'opzione «giorno per giorno» aggiunge una riga per ogni giornata.
 */
object RapportoPdf {

    private const val LARGHEZZA = 595
    private const val ALTEZZA = 842
    private const val MARGINE = 40f
    private val DATA: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val ORA: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val MESI = listOf("gennaio", "febbraio", "marzo", "aprile", "maggio", "giugno",
        "luglio", "agosto", "settembre", "ottobre", "novembre", "dicembre")

    /** Gestisce pagine e posizione di scrittura. */
    private class Foglio {
        private val doc = PdfDocument()
        private var pagina: PdfDocument.Page? = null
        private var numero = 0
        private var y = 0f
        val normale = Paint().apply { isAntiAlias = true; textSize = 10f }
        val grassetto = Paint().apply { isAntiAlias = true; textSize = 10f; isFakeBoldText = true }
        val titolo = Paint().apply { isAntiAlias = true; textSize = 16f; isFakeBoldText = true }
        val sezione = Paint().apply { isAntiAlias = true; textSize = 12f; isFakeBoldText = true }

        /** Restituisce la tela della pagina attuale, aprendo una pagina nuova se la riga non ci sta. */
        private fun tela(altezza: Float): Canvas {
            val p = pagina
            if (p == null || y + altezza > ALTEZZA - MARGINE) {
                pagina?.let { doc.finishPage(it) }
                numero++
                pagina = doc.startPage(PdfDocument.PageInfo.Builder(LARGHEZZA, ALTEZZA, numero).create())
                y = MARGINE + 10f
            }
            return pagina?.canvas ?: throw IllegalStateException("pagina non creata")
        }

        fun scrivi(testo: String, carattere: Paint = normale) {
            val altezza = carattere.textSize + 6f
            tela(altezza).drawText(testo, MARGINE, y + carattere.textSize, carattere)
            y += altezza
        }

        fun vuoto(punti: Float = 8f) {
            y += punti
        }

        fun tabella(intestazione: List<String>, colonne: List<Float>, righe: List<List<String>>) {
            fun stampa(celle: List<String>, carattere: Paint) {
                val altezza = carattere.textSize + 6f
                val c = tela(altezza)
                celle.forEachIndexed { i, t ->
                    c.drawText(t.take(40), MARGINE + colonne.getOrElse(i) { 0f }, y + carattere.textSize, carattere)
                }
                y += altezza
            }
            stampa(intestazione, grassetto)
            righe.forEach { stampa(it, normale) }
            vuoto()
        }

        fun chiudi(out: OutputStream) {
            pagina?.let { doc.finishPage(it) }
            pagina = null
            doc.writeTo(out)
            doc.close()
        }
    }

    private fun num(v: Double) = "%.2f".format(Locale.ITALY, v)
    private fun durata(minuti: Long) = "%d:%02d".format(minuti / 60, minuti % 60)
    private fun minuti(s: Servizio): Long = Duration.between(s.turno.inizio, s.turno.fine).toMinutes()
    private fun ora(t: LocalDateTime): String = t.format(ORA)

    private fun servizi(d: Dati, m: YearMonth) =
        d.servizi.filter { YearMonth.from(it.turno.inizio.toLocalDate()) == m }.sortedBy { it.turno.inizio }

    private fun assenze(d: Dati, m: YearMonth) =
        d.assenze.filter { YearMonth.from(it.data) == m }.sortedBy { it.data }

    private fun cartellino(d: Dati, m: YearMonth): CartellinoSap? =
        d.cartellini.firstOrNull { it.anno == m.year && it.mese == m.monthValue }

    private fun cedolino(d: Dati, m: YearMonth): Cedolino? =
        d.cedolini.firstOrNull { it.anno == m.year && it.mese == m.monthValue }

    private fun flexTotale(c: CartellinoSap?): Double? = c?.giornate?.mapNotNull { it.flex }?.sum()

    /** Gruppo di un tipo di assenza, per i conteggi del report. */
    private fun gruppo(t: TipoAssenza): String = when (t) {
        TipoAssenza.FERIE -> "Ferie"
        TipoAssenza.PERMESSO, TipoAssenza.PERMESSO_EX_FESTIVITA -> "Permessi"
        TipoAssenza.MALATTIA -> "Malattia"
        TipoAssenza.PARENTALE -> "Parentali"
        TipoAssenza.RIPOSO, TipoAssenza.RIPOSO_SETTIMANALE -> "Riposi"
        else -> "Altre"
    }

    private fun intestazione(f: Foglio, d: Dati) {
        val nome = d.impostazioni.nome.takeIf { it.isNotBlank() }?.let { "$it · " } ?: ""
        f.scrivi(nome + d.impostazioni.profiloAttivo.etichetta)
        f.vuoto()
    }

    private fun riepilogoMese(f: Foglio, d: Dati, m: YearMonth) {
        val s = servizi(d, m)
        val a = assenze(d, m)
        f.scrivi("Servizi: ${s.size} · ore totali ${durata(s.sumOf { minuti(it) })}")
        val perGruppo = a.groupBy { gruppo(it.tipo) }
        f.scrivi("Assenze: " + (if (perGruppo.isEmpty()) "nessuna"
            else perGruppo.entries.joinToString(" · ") { "${it.key} ${it.value.size}" }))
        cartellino(d, m)?.let { c ->
            f.scrivi("Cartellino SAP: flex del mese ${flexTotale(c)?.let { num(it) } ?: "-"} · " +
                "flex mese precedente ${c.flexMesePrecedente?.let { num(it) } ?: "-"}")
        }
        cedolino(d, m)?.let { c -> f.scrivi("Cedolino: netto ${c.netto?.let { num(it) } ?: "-"} €") }
        f.vuoto()
    }

    private fun dettaglioMese(f: Foglio, d: Dati, m: YearMonth) {
        val s = servizi(d, m)
        if (s.isNotEmpty()) {
            f.scrivi("Servizi", f.sezione)
            f.tabella(listOf("Data", "Inizio", "Fine", "Durata", "Profilo"), listOf(0f, 90f, 160f, 230f, 300f),
                s.map { v ->
                    listOf(v.turno.inizio.toLocalDate().format(DATA), ora(v.turno.inizio), ora(v.turno.fine),
                        durata(minuti(v)), v.turno.profilo.etichetta)
                })
        }
        val a = assenze(d, m)
        if (a.isNotEmpty()) {
            f.scrivi("Assenze", f.sezione)
            f.tabella(listOf("Data", "Tipo", "Note"), listOf(0f, 90f, 260f),
                a.map { listOf(it.data.format(DATA), it.tipo.etichetta, it.note) })
        }
    }

    /** Una riga per giornata: servizi, assenze e flessibilità del cartellino SAP. */
    private fun giornoPerGiorno(f: Foglio, d: Dati, m: YearMonth) {
        val flex: Map<Int, Double?> = cartellino(d, m)?.giornate?.associate { it.giorno to it.flex } ?: emptyMap()
        val righe = (1..m.lengthOfMonth()).map { g ->
            val data = m.atDay(g)
            val serv = d.servizi.filter { it.turno.inizio.toLocalDate() == data }.sortedBy { it.turno.inizio }
                .joinToString(" ") { "${ora(it.turno.inizio)}-${ora(it.turno.fine)}" }
            val ass = d.assenze.filter { it.data == data }.joinToString(" ") { it.tipo.etichetta }
            listOf(data.format(DATA), serv, ass, flex[g]?.let { num(it) } ?: "")
        }
        f.scrivi("Giorno per giorno", f.sezione)
        f.tabella(listOf("Data", "Servizio", "Assenza", "Flex SAP"), listOf(0f, 80f, 260f, 430f), righe)
    }

    /** Report di un mese. */
    fun mensile(d: Dati, m: YearMonth, giorni: Boolean, out: OutputStream) {
        val f = Foglio()
        f.scrivi("Ferromicon · Report mensile", f.titolo)
        f.scrivi("${MESI[m.monthValue - 1].replaceFirstChar { it.uppercase() }} ${m.year}", f.sezione)
        intestazione(f, d)
        riepilogoMese(f, d, m)
        dettaglioMese(f, d, m)
        if (giorni) giornoPerGiorno(f, d, m)
        f.chiudi(out)
    }

    /** Report di un anno: riepilogo per mese, poi l'archivio e, se richiesto, il dettaglio giorno per giorno. */
    fun annuale(d: Dati, anno: Int, giorni: Boolean, out: OutputStream) {
        val f = Foglio()
        f.scrivi("Ferromicon · Report annuale $anno", f.titolo)
        intestazione(f, d)
        val imp = d.impostazioni
        f.scrivi("Permessi spettanti ${imp.permessiSpettanti} · parentali spettanti ${imp.parentaliSpettanti} · " +
            "ferie anno precedente ${imp.ferieResidueAnnoPrecedente}")
        f.vuoto()
        f.scrivi("Riepilogo per mese", f.sezione)
        val mesi = (1..12).map { YearMonth.of(anno, it) }
        f.tabella(
            listOf("Mese", "Servizi", "Ore", "Ferie", "Permessi", "Malattia", "Parentali", "Riposi", "Flex SAP", "Netto"),
            listOf(0f, 60f, 115f, 165f, 215f, 285f, 350f, 410f, 460f, 515f),
            mesi.map { m ->
                val s = servizi(d, m)
                val a = assenze(d, m)
                listOf(
                    MESI[m.monthValue - 1].take(3),
                    s.size.toString(),
                    durata(s.sumOf { minuti(it) }),
                    a.count { gruppo(it.tipo) == "Ferie" }.toString(),
                    a.count { gruppo(it.tipo) == "Permessi" }.toString(),
                    a.count { gruppo(it.tipo) == "Malattia" }.toString(),
                    a.count { gruppo(it.tipo) == "Parentali" }.toString(),
                    a.count { gruppo(it.tipo) == "Riposi" }.toString(),
                    flexTotale(cartellino(d, m))?.let { num(it) } ?: "",
                    cedolino(d, m)?.netto?.let { num(it) } ?: ""
                )
            })

        f.vuoto()
        f.scrivi("Stazioni (${d.stazioni.size})", f.sezione)
        if (d.stazioni.isNotEmpty()) f.tabella(listOf("Nome", "Sigla", "Telefono"), listOf(0f, 220f, 290f),
            d.stazioni.sortedBy { it.nome }.map { listOf(it.nome, it.sigla, it.telefono) })
        f.scrivi("Perimetri di residenza (${d.perimetri.size})", f.sezione)
        if (d.perimetri.isNotEmpty()) f.tabella(listOf("Nome", "Stazioni", "Attivo"), listOf(0f, 220f, 290f),
            d.perimetri.map { listOf(it.nome, it.stazioni.size.toString(), if (it.attivo) "sì" else "no") })
        f.scrivi("Preferiti (${d.preferiti.size})", f.sezione)
        if (d.preferiti.isNotEmpty()) f.tabella(listOf("Nome", "Orario", "Profilo"), listOf(0f, 220f, 290f),
            d.preferiti.map { listOf(it.nome, "${it.oraInizio.format(ORA)}-${it.oraFine.format(ORA)}", it.profilo.etichetta) })
        f.scrivi("Certificazioni uniche (${d.certificazioniUniche.size})", f.sezione)
        if (d.certificazioniUniche.isNotEmpty()) f.tabella(listOf("Anno", "File"), listOf(0f, 80f),
            d.certificazioniUniche.map { listOf(it.anno?.toString() ?: "", it.file) })

        if (giorni) {
            for (m in mesi) {
                f.vuoto()
                f.scrivi("${MESI[m.monthValue - 1].replaceFirstChar { it.uppercase() }} $anno", f.sezione)
                giornoPerGiorno(f, d, m)
            }
        }
        f.chiudi(out)
    }
}
