package it.ferromicon

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition

/** Estrae da un PDF il testo con le posizioni, che il lettore dei cedolini usa per ricostruire le tabelle. */
object PdfTesto {

    private var pronto = false

    private class Raccogli : PDFTextStripper() {
        val pezzi = ArrayList<Pezzo>()

        override fun writeString(text: String, textPositions: List<TextPosition>) {
            if (textPositions.isEmpty() || text.isBlank()) return
            val a = textPositions.first()
            val z = textPositions.last()
            val verticale = a.dir % 180f != 0f
            if (verticale) {
                // Testo ruotato (come i numeri dei treni nei turni): posizione reale sulla pagina;
                // y è il bordo inferiore della parola e w la sua lunghezza.
                val ys = textPositions.map { it.y.toDouble() }
                pezzi += Pezzo(text, textPositions.minOf { it.x.toDouble() }, ys.max(), ys.max() - ys.min(),
                    a.fontSizeInPt.toDouble())
                return
            }
            pezzi += Pezzo(
                str = text,
                x = a.xDirAdj.toDouble(),
                y = a.yDirAdj.toDouble(),
                w = (z.xDirAdj + z.widthDirAdj - a.xDirAdj).toDouble(),
                h = a.fontSizeInPt.toDouble()
            )
        }
    }

    /**
     * Legge il PDF pagina per pagina e passa i pezzi a [perPagina] (numero di pagina da 1 e pezzi), senza
     * tenere tutto in memoria: serve per i PDF molto lunghi. [avanzamento] riceve (pagina, totale).
     */
    fun scorri(ctx: Context, uri: Uri, avanzamento: ((Int, Int) -> Unit)? = null, perPagina: (Int, List<Pezzo>) -> Unit) {
        if (!pronto) {
            PDFBoxResourceLoader.init(ctx.applicationContext)
            pronto = true
        }
        val flusso = ctx.contentResolver.openInputStream(uri) ?: throw IllegalArgumentException("File non leggibile")
        flusso.use { dati ->
            PDDocument.load(dati).use { doc ->
                val totale = doc.numberOfPages
                for (numero in 1..totale) {
                    val lettore = Raccogli()
                    lettore.startPage = numero
                    lettore.endPage = numero
                    lettore.getText(doc)
                    perPagina(numero, lettore.pezzi)
                    avanzamento?.invoke(numero, totale)
                }
            }
        }
    }

    /** Una lista di pezzi per ogni pagina del PDF. */
    fun pagine(ctx: Context, uri: Uri): List<List<Pezzo>> {
        if (!pronto) {
            PDFBoxResourceLoader.init(ctx.applicationContext)
            pronto = true
        }
        val flusso = ctx.contentResolver.openInputStream(uri) ?: throw IllegalArgumentException("File non leggibile")
        return flusso.use { dati ->
            PDDocument.load(dati).use { doc ->
                val risultato = ArrayList<List<Pezzo>>()
                for (numero in 1..doc.numberOfPages) {
                    val lettore = Raccogli()
                    lettore.startPage = numero
                    lettore.endPage = numero
                    lettore.getText(doc)
                    risultato.add(lettore.pezzi)
                }
                risultato
            }
        }
    }

    /** Elenco leggibile dei pezzi (posizione e testo): serve a capire perché un PDF non è stato letto bene. */
    fun diagnostica(pagine: List<List<Pezzo>>, nomeFile: String): String = buildString {
        appendLine("File: $nomeFile (${pagine.size} pagine)")
        pagine.take(2).forEachIndexed { i, pagina ->
            appendLine("--- pagina ${i + 1}: ${pagina.size} pezzi (y, x, larghezza, corpo, testo)")
            pagina.sortedWith(compareBy({ it.y }, { it.x })).take(400).forEach {
                appendLine("%.0f %.0f %.0f %.1f | %s".format(it.y, it.x, it.w, it.h, it.str))
            }
        }
    }
}
