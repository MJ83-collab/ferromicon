package it.ferromicon

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import java.time.LocalDate

/**
 * Copia di sicurezza in una cartella scelta dall'utente.
 * Alla prima apertura di una nuova versione salva i dati attuali nella cartella;
 * se i dati mancano (reinstallazione), propone il ripristino dell'ultima copia.
 */
object CopiaSicurezza {
    private const val PREF = "copia_sicurezza"
    private const val KEY_CARTELLA = "cartella"
    private const val KEY_VERSIONE = "versione"
    private const val PREFISSO = "ferromicon-backup-"
    const val RICHIESTA_CARTELLA = 16

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun cartella(ctx: Context): Uri? = prefs(ctx).getString(KEY_CARTELLA, null)?.let(Uri::parse)

    fun scegliCartella(act: Activity) {
        act.startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), RICHIESTA_CARTELLA)
    }

    /** Memorizza la cartella scelta e propone subito il ripristino, se c'è una copia. */
    fun dopoSceltaCartella(act: Activity, uri: Uri) {
        act.contentResolver.takePersistableUriPermission(uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        prefs(act).edit().putString(KEY_CARTELLA, uri.toString()).apply()
        Toast.makeText(act, "Cartella della copia di sicurezza impostata", Toast.LENGTH_SHORT).show()
        proponiRipristino(act)
    }

    /** File di copia nella cartella, dal più recente. */
    private fun copie(ctx: Context, tree: Uri): List<Pair<String, Uri>> {
        val radice = DocumentsContract.buildChildDocumentsUriUsingTree(tree,
            DocumentsContract.getTreeDocumentId(tree))
        val trovate = mutableListOf<Pair<String, Uri>>()
        ctx.contentResolver.query(radice,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val nome = c.getString(1) ?: continue
                if (!nome.startsWith(PREFISSO)) continue
                trovate += nome to DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0))
            }
        }
        return trovate.sortedByDescending { it.first }
    }

    /** Scrive la copia di oggi nella cartella, sovrascrivendo quella di oggi se esiste. */
    fun scrivi(ctx: Context) {
        val tree = cartella(ctx) ?: return
        runCatching {
            val nome = "$PREFISSO${LocalDate.now()}.json"
            val esistente = copie(ctx, tree).firstOrNull { it.first == nome }?.second
            val uri = esistente ?: DocumentsContract.createDocument(ctx.contentResolver,
                DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree)),
                "application/json", nome.removeSuffix(".json")) ?: return
            ctx.contentResolver.openOutputStream(uri, "wt")?.use {
                it.write(Archivio.aTesto(Archivio.caricaTutto(ctx)).toByteArray(Charsets.UTF_8))
            }
        }
    }

    /** Da chiamare all'avvio: gestisce l'aggiornamento e la reinstallazione. */
    fun alAvvio(act: Activity) {
        val versione = runCatching {
            act.packageManager.getPackageInfo(act.packageName, 0).versionCode
        }.getOrDefault(0)
        val p = prefs(act)
        val salvata = p.getInt(KEY_VERSIONE, -1)
        if (salvata == versione) return
        val dati = Archivio.caricaTutto(act)
        val vuoti = dati.servizi.isEmpty() && dati.assenze.isEmpty()
        if (salvata != -1) scrivi(act) // copia dei dati di prima dell'aggiornamento
        p.edit().putInt(KEY_VERSIONE, versione).apply()
        if (salvata != -1 && !vuoti) return // aggiornamento normale: i dati ci sono già
        proponiRipristino(act)
    }

    private fun proponiRipristino(act: Activity) {
        val tree = cartella(act)
        if (tree == null) {
            AlertDialog.Builder(act)
                .setTitle("Copia di sicurezza")
                .setMessage("Vuoi scegliere la cartella dove è salvata la copia dei dati?")
                .setPositiveButton("Scegli cartella") { _, _ -> scegliCartella(act) }
                .setNegativeButton("Non ora", null)
                .show()
            return
        }
        val ultima = runCatching { copie(act, tree).firstOrNull() }.getOrNull() ?: return
        val data = ultima.first.removePrefix(PREFISSO).removeSuffix(".json")
        AlertDialog.Builder(act)
            .setTitle("Ripristinare i dati?")
            .setMessage("Trovata la copia del $data. Ripristinando, i dati attuali vengono sostituiti da quelli della copia.")
            .setPositiveButton("Ripristina") { _, _ -> ripristina(act, ultima.second) }
            .setNegativeButton("Non ora", null)
            .show()
    }

    private fun ripristina(act: Activity, uri: Uri) {
        runCatching {
            val testo = act.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?: error("file non leggibile")
            Archivio.salvaTutto(act, Archivio.daTesto(testo))
        }.onSuccess {
            Toast.makeText(act, "Dati ripristinati", Toast.LENGTH_SHORT).show()
            act.recreate()
        }.onFailure {
            Toast.makeText(act, "Ripristino non riuscito: ${it.message}", Toast.LENGTH_LONG).show()
        }
    }
}
