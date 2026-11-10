package it.ferromicon

import android.app.Application
import android.content.Context
import android.os.Build
import java.io.File

class FerromiconApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Stile.inizializza(this)
        Errori.installa(this)
    }
}

/** Se l'app si chiude per un errore, ne salva i dettagli: alla riapertura si possono inviare. */
object Errori {
    private const val FILE = "ultimo-errore.txt"

    fun installa(ctx: Context) {
        val app = ctx.applicationContext
        val precedente = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { filo, errore ->
            try {
                File(app.filesDir, FILE).writeText(
                    "Ferromicon 0.0.4\n${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.SDK_INT}\n\n" +
                        errore.stackTraceToString()
                )
            } catch (x: Throwable) {
                // niente da fare: l'errore originale prosegue
            }
            precedente?.uncaughtException(filo, errore)
        }
    }

    fun leggi(ctx: Context): String? = File(ctx.filesDir, FILE).takeIf { it.exists() }?.readText()

    fun cancella(ctx: Context) { File(ctx.filesDir, FILE).delete() }
}
