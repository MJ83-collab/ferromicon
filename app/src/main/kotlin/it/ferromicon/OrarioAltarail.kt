package it.ferromicon

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Orario ufficiale Altarail (GTFS) pubblicato da Regione Lombardia sul portale Open Data.
 * L'utente scarica lo zip dal portale e lo importa; l'app ne salva un indice compatto.
 */
object OrarioAltarail {

    /** Pagina del dataset sul portale Open Data di Regione Lombardia. */
    const val PAGINA_DATASET = "https://www.dati.lombardia.it/d/3z4k-mxz9"
    /** Scaricamento diretto dello zip GTFS. */
    const val DOWNLOAD_ZIP = "https://www.dati.lombardia.it/download/3z4k-mxz9/application%2Fzip"
    const val FONTE = "Regione Lombardia – Open Data, orario Azienda in formato GTFS"

    private const val FILE_INDICE = "orario-altarail.idx"
    @Volatile private var inMemoria: Orario? = null

    private fun fileIndice(ctx: Context) = File(ctx.filesDir, FILE_INDICE)

    fun presente(ctx: Context) = fileIndice(ctx).exists()

    /** Orario importato, o null se non c'è; si legge dal disco una volta sola. */
    fun carica(ctx: Context): Orario? {
        inMemoria?.let { return it }
        val f = fileIndice(ctx)
        if (!f.exists()) return null
        return runCatching { f.bufferedReader().use { Orario.daIndice(it) } }.getOrNull()?.also { inMemoria = it }
    }

    /** Importa lo zip GTFS scelto dall'utente. Da chiamare fuori dal thread dell'interfaccia. */
    fun importa(ctx: Context, uri: Uri): Orario {
        val temporaneo = File(ctx.cacheDir, "gtfs-import.zip")
        try {
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                temporaneo.outputStream().use { input.copyTo(it) }
            } ?: throw Gtfs.FormatoNonValido("Impossibile leggere il file scelto.")
            val orario = Gtfs.leggiZip(temporaneo, FONTE)
            if (orario.numeroCorse == 0) throw Gtfs.FormatoNonValido("Il file non contiene corse.")
            val tmp = File(ctx.filesDir, "$FILE_INDICE.tmp")
            tmp.writeText(orario.aIndice())
            tmp.renameTo(fileIndice(ctx))
            inMemoria = orario
            return orario
        } finally {
            temporaneo.delete()
        }
    }

    fun elimina(ctx: Context) {
        fileIndice(ctx).delete()
        inMemoria = null
    }
}
