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
import java.time.LocalDate
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

    const val ACTION_RECUPERO = "it.ferromicon.RECUPERO_FESTIVI"
    private const val CANALE_RECUPERO = "recupero_festivi"
    private const val ID_NOTIFICA_RECUPERO = 1001
    private const val PREF_RECUPERO = "promemoria"
    private const val KEY_ULTIMO_RECUPERO = "recupero_ultimo_avviso"

    private fun intentRecupero(ctx: Context): PendingIntent {
        val intent = Intent(ctx, PromemoriaReceiver::class.java).setAction(ACTION_RECUPERO)
        return PendingIntent.getBroadcast(ctx, 2, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /**
     * Avviso per il recupero festivi: [giorniAvvisoRecupero] giorni prima della scadenza più vicina, alle 9.
     * Finestra aperta: un avviso al giorno alle 9, finché ci sono festività da recuperare.
     * Disattivabile dalle impostazioni.
     */
    fun programmaRecupero(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(intentRecupero(ctx))
        val dati = Archivio.caricaTutto(ctx)
        val imp = dati.impostazioni
        if (!imp.avvisoRecuperoAttivo) return
        val oggi = LocalDate.now()
        val prossima = RecuperoFestivi.daRecuperare(oggi.year, oggi, imp.festiviForzati, dati.servizi)
            .filter { it.scadenza >= oggi }
            .minByOrNull { it.scadenza } ?: return
        val finestra = prossima.scadenza.minusDays(imp.giorniAvvisoRecupero.toLong())
        val adesso = LocalDateTime.now()
        val ultimo = ctx.getSharedPreferences(PREF_RECUPERO, Context.MODE_PRIVATE)
            .getString(KEY_ULTIMO_RECUPERO, null)
        val oggiAlle9 = oggi.atTime(9, 0)
        val quando: LocalDateTime = when {
            oggi.isBefore(finestra) -> finestra.atTime(9, 0)
            ultimo == oggi.toString() -> oggi.plusDays(1).atTime(9, 0)
            adesso.isBefore(oggiAlle9) -> oggiAlle9
            else -> adesso.plusMinutes(1)
        }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
            quando.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), intentRecupero(ctx))
    }

    fun mostraRecupero(ctx: Context) {
        val dati = Archivio.caricaTutto(ctx)
        val imp = dati.impostazioni
        if (!imp.avvisoRecuperoAttivo) return
        val oggi = LocalDate.now()
        val stati = RecuperoFestivi.daRecuperare(oggi.year, oggi, imp.festiviForzati, dati.servizi)
            .filter { it.scadenza >= oggi }
        val prossima = stati.minByOrNull { it.scadenza } ?: return
        if (oggi.isBefore(prossima.scadenza.minusDays(imp.giorniAvvisoRecupero.toLong()))) return
        ctx.getSharedPreferences(PREF_RECUPERO, Context.MODE_PRIVATE)
            .edit().putString(KEY_ULTIMO_RECUPERO, oggi.toString()).apply()

        val fmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ITALIAN)
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CANALE_RECUPERO, "Recupero festività",
            NotificationManager.IMPORTANCE_DEFAULT))
        val apri = PendingIntent.getActivity(ctx, 3, Intent(ctx, RecuperoFestiviActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notifica = Notification.Builder(ctx, CANALE_RECUPERO)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Festività da recuperare")
            .setContentText("${stati.size} da recuperare, la prima scade il ${fmt.format(prossima.scadenza)}")
            .setContentIntent(apri)
            .setAutoCancel(true)
            .build()
        nm.notify(ID_NOTIFICA_RECUPERO, notifica)
    }

    /** Cancella e riprogramma l'allarme per il prossimo servizio. */
    fun programma(ctx: Context) {
        programmaRecupero(ctx)
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
        if (intent.action == Promemoria.ACTION_RECUPERO) {
            Promemoria.mostraRecupero(context)
            Promemoria.programmaRecupero(context)
            return
        }
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) {
            val id = Promemoria.estraiId(intent)
            if (id >= 0) Promemoria.mostra(context, id)
        }
        Promemoria.programma(context)
    }
}
