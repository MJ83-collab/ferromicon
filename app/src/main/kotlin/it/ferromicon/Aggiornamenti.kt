package it.ferromicon

import android.app.Activity
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import org.json.JSONObject
import java.io.FileNotFoundException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Aggiornamento automatico: l'app guarda sulle release di GitHub se esiste una versione più recente,
 * la scarica e la fa installare dal sistema (Android chiede sempre una conferma).
 * Per funzionare la repo deve essere pubblica e la release deve contenere `versione.json` e `ferromicon.apk`.
 */
object Aggiornamenti {

    const val REPO = "MJ83-collab/ferromicon"
    private const val BASE = "https://github.com/$REPO/releases/latest/download/"
    private const val PREFS = "aggiornamenti"
    const val AZIONE = "it.ferromicon.INSTALLAZIONE"

    sealed class Esito {
        data class Nuova(val nome: String, val codice: Long) : Esito()
        data class Aggiornato(val nomeOnline: String) : Esito()
        data class Errore(val messaggio: String) : Esito()
    }

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun automatico(ctx: Context) = prefs(ctx).getBoolean("auto", true)

    fun impostaAutomatico(ctx: Context, attivo: Boolean) {
        prefs(ctx).edit().putBoolean("auto", attivo).apply()
    }

    /** Nome e numero della versione installata. */
    fun versioneAttuale(ctx: Context): Pair<String, Long> {
        val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        @Suppress("DEPRECATION")
        val codice = if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode.toLong()
        return (pi.versionName ?: "") to codice
    }

    /** Testo per la voce Strumenti: versione installata e, se già trovata, quella nuova da installare. */
    fun riga(ctx: Context): String {
        val (nome, codice) = versioneAttuale(ctx)
        val nuova = prefs(ctx).getString("nuova", null)
        return if (nuova != null) "Installata $nome · DISPONIBILE $nuova: tocca per installare"
        else "Installata $nome (codice $codice) · tocca per controllare"
    }

    private fun apri(indirizzo: String): HttpURLConnection {
        var url = URL(indirizzo)
        repeat(6) {
            val c = url.openConnection() as HttpURLConnection
            c.connectTimeout = 15000
            c.readTimeout = 30000
            c.instanceFollowRedirects = false
            val codice = c.responseCode
            if (codice in 300..399) {
                url = URL(url, c.getHeaderField("Location"))
                c.disconnect()
            } else {
                if (codice == 404) throw FileNotFoundException(indirizzo)
                if (codice !in 200..299) throw IOException("risposta $codice")
                return c
            }
        }
        throw IOException("troppi reindirizzamenti")
    }

    /** Cerca una versione nuova. Con [forza] false si ferma se l'ultimo controllo è di meno di 12 ore fa. */
    fun controlla(ctx: Context, forza: Boolean, risultato: (Esito) -> Unit) {
        val app = ctx.applicationContext
        val p = prefs(app)
        if (!forza && System.currentTimeMillis() - p.getLong("ultimo", 0L) < 12 * 3600_000L) return
        Thread {
            val esito: Esito = try {
                val conn = apri(BASE + "versione.json")
                val testo = conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
                conn.disconnect()
                val o = JSONObject(testo)
                val codice = o.getLong("versionCode")
                val nome = o.optString("versionName", codice.toString())
                p.edit().putLong("ultimo", System.currentTimeMillis()).apply()
                if (codice > versioneAttuale(app).second) {
                    p.edit().putString("nuova", nome).apply()
                    Esito.Nuova(nome, codice)
                } else {
                    p.edit().remove("nuova").apply()
                    Esito.Aggiornato(nome)
                }
            } catch (e: FileNotFoundException) {
                Esito.Errore("Non trovo la versione online. Per l'aggiornamento automatico la repo su GitHub " +
                    "deve essere pubblica e avere una release con versione.json.")
            } catch (e: Exception) {
                Esito.Errore("Controllo non riuscito: ${e.message ?: e.javaClass.simpleName}")
            }
            Handler(Looper.getMainLooper()).post { risultato(esito) }
        }.start()
    }

    /** Scarica l'APK e lo passa all'installatore di sistema. [fine] riceve null se tutto è andato bene, altrimenti il motivo. */
    fun scaricaEInstalla(ctx: Context, fine: (String?) -> Unit) {
        val app = ctx.applicationContext
        if (Build.VERSION.SDK_INT >= 26 && !app.packageManager.canRequestPackageInstalls()) {
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            fine("Consenti a Ferromicon di installare app (interruttore nella schermata che si è aperta), poi riprova.")
            return
        }
        Thread {
            var errore: String? = null
            try {
                val conn = apri(BASE + "ferromicon.apk")
                val lunghezza = conn.contentLengthLong
                val installer = app.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                val id = installer.createSession(params)
                installer.openSession(id).use { sessione ->
                    sessione.openWrite("ferromicon.apk", 0, if (lunghezza > 0) lunghezza else -1).use { out ->
                        conn.inputStream.use { it.copyTo(out) }
                        sessione.fsync(out)
                    }
                    conn.disconnect()
                    val intento = Intent(app, InstallazioneReceiver::class.java).setAction(AZIONE)
                    val flag = PendingIntent.FLAG_UPDATE_CURRENT or
                        (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                    sessione.commit(PendingIntent.getBroadcast(app, id, intento, flag).intentSender)
                }
            } catch (e: FileNotFoundException) {
                errore = "Non trovo l'APK online (la repo deve essere pubblica)."
            } catch (e: Exception) {
                errore = "Aggiornamento non riuscito: ${e.javaClass.simpleName} ${e.message ?: ""}".trim()
            }
            Handler(Looper.getMainLooper()).post { fine(errore) }
        }.start()
    }

    /** Finestra «nuova versione» con il pulsante per installare. */
    fun proponi(a: Activity, nuova: Esito.Nuova) {
        if (a.isFinishing) return
        AlertDialog.Builder(a)
            .setTitle("Nuova versione ${nuova.nome}")
            .setMessage("È disponibile un aggiornamento di Ferromicon. I tuoi dati restano nell'app. " +
                "Android ti chiederà di confermare l'installazione.")
            .setPositiveButton("Scarica e installa") { _, _ ->
                Toast.makeText(a, "Scarico l'aggiornamento…", Toast.LENGTH_SHORT).show()
                scaricaEInstalla(a) { errore -> if (errore != null) Toast.makeText(a, errore, Toast.LENGTH_LONG).show() }
            }
            .setNegativeButton("Più tardi", null)
            .show()
    }

    /** Controllo all'avvio, con al massimo una richiesta ogni 12 ore. */
    fun controllaInSilenzio(a: Activity) {
        if (!automatico(a)) return
        controlla(a, false) { if (it is Esito.Nuova) proponi(a, it) }
    }

    /** Voce «Aggiornamenti» degli Strumenti: controllo subito e risposta in ogni caso. */
    fun controllaSubito(a: Activity) {
        Toast.makeText(a, "Cerco aggiornamenti…", Toast.LENGTH_SHORT).show()
        controlla(a, true) { esito ->
            if (a.isFinishing) return@controlla
            when (esito) {
                is Esito.Nuova -> proponi(a, esito)
                is Esito.Aggiornato -> AlertDialog.Builder(a)
                    .setTitle("Sei aggiornato")
                    .setMessage("Versione installata: ${versioneAttuale(a).first} (codice ${versioneAttuale(a).second}).\n" +
                        "Ultima versione online: ${esito.nomeOnline}.")
                    .setPositiveButton("OK", null)
                    .setNeutralButton(if (automatico(a)) "Disattiva controllo automatico" else "Attiva controllo automatico") { _, _ ->
                        impostaAutomatico(a, !automatico(a))
                    }
                    .show()
                is Esito.Errore -> AlertDialog.Builder(a)
                    .setTitle("Aggiornamenti")
                    .setMessage(esito.messaggio)
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }
}

/** Riceve dal sistema l'esito dell'installazione e mostra la richiesta di conferma. */
class InstallazioneReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val stato = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)
        when (stato) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val conferma = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (conferma == null) {
                    Toast.makeText(ctx, "Installazione in attesa: il sistema non ha inviato la conferma.", Toast.LENGTH_LONG).show()
                    return
                }
                try {
                    conferma.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ctx.startActivity(conferma)
                } catch (e: Exception) {
                    Toast.makeText(ctx, "Conferma non aperta: ${e.javaClass.simpleName} ${e.message ?: ""}".trim(), Toast.LENGTH_LONG).show()
                }
            }
            PackageInstaller.STATUS_SUCCESS ->
                Toast.makeText(ctx, "Aggiornamento installato.", Toast.LENGTH_LONG).show()
            else -> Toast.makeText(ctx,
                "Installazione non riuscita (codice $stato): ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "errore"}",
                Toast.LENGTH_LONG).show()
        }
    }
}
