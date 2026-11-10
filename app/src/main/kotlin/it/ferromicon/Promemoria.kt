package it.ferromicon

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Promemoria prima del servizio. Si programma un solo allarme, per il prossimo
 * servizio; quando scatta, mostra la notifica e programma il successivo.
 * Nella notifica compare anche l'esito del riposo e dei limiti.
 */
object Promemoria {

    private const val CANALE = "promemoria_servizio"
    private const val EXTRA_ID = "id_servizio"

    private fun intentAllarme(ctx: Context, id: Long?): PendingIntent {
        val intent = Intent(ctx, PromemoriaReceiver::class.java)
        if (id != null) intent.putExtra(EXTRA_ID, id)
        return PendingIntent.getBroadcast(ctx, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** Cancella e riprogramma l'allarme per il prossimo servizio. */
    fun programma(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(intentAllarme(ctx, null))

        val dati = Archivio.caricaTutto(ctx)
        val minuti = dati.impostazioni.promemoriaMinuti
        if (minuti <= 0) return
        val adesso = LocalDateTime.now()
        val prossimo = dati.servizi
            .filter { it.turno.inizio.minusMinutes(minuti.toLong()).isAfter(adesso) }
            .minByOrNull { it.turno.inizio } ?: return

        val quando = prossimo.turno.inizio.minusMinutes(minuti.toLong())
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // Allarme non esatto: non serve un permesso speciale, può tardare di qualche minuto.
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, quando, intentAllarme(ctx, prossimo.id))
    }

    fun mostra(ctx: Context, id: Long) {
        val dati = Archivio.caricaTutto(ctx)
        val ordinati = dati.servizi.sortedBy { it.turno.inizio }
        val indice = ordinati.indexOfFirst { it.id == id }
        if (indice < 0) return
        val t = ordinati[indice].turno

        val fmt = DateTimeFormatter.ofPattern("EEE d MMM HH:mm", Locale.ITALIAN)
        val durata = Duration.between(t.inizio, t.fine).toMinutes()
        val problemi = Analisi.verificaServizio(ordinati, indice, dati.assenze, dati.impostazioni.limitiOrario)
            .filter { it.gravita == Gravita.VIOLAZIONE || it.gravita == Gravita.ATTENZIONE }
        val testo = if (problemi.isEmpty()) "Riposo e limiti in regola."
        else problemi.joinToString("\n") { MainActivity.formatta(it) }

        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CANALE, "Promemoria servizio",
            NotificationManager.IMPORTANCE_DEFAULT))
        val apri = PendingIntent.getActivity(ctx, 1, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notifica = Notification.Builder(ctx, CANALE)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Servizio ${fmt.format(t.inizio)} (${durata / 60} h ${durata % 60}')")
            .setContentText(problemi.firstOrNull()?.messaggio ?: testo)
            .setStyle(Notification.BigTextStyle().bigText(testo))
            .setContentIntent(apri)
            .setAutoCancel(true)
            .build()
        nm.notify(id.hashCode(), notifica)
    }

    fun estraiId(intent: Intent): Long = intent.getLongExtra(EXTRA_ID, -1L)
}

/** Riceve l'allarme del promemoria e il riavvio del telefono. */
class PromemoriaReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) {
            val id = Promemoria.estraiId(intent)
            if (id >= 0) Promemoria.mostra(context, id)
        }
        Promemoria.programma(context)
    }
}
