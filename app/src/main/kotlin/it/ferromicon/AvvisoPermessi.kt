package it.ferromicon

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import java.time.LocalDate
import java.time.YearMonth

/**
 * Permessi da usare entro fine anno (valgono per tutti i profili).
 * La home mostra il riepilogo; una notifica compare una volta al mese, alla prima apertura.
 */
object AvvisoPermessi {

    private const val CANALE = "permessi"
    private const val ID_NOTIFICA = 4402
    private const val PREFS = "avvisi"

    /** Giorni di permesso presi nell'anno e quelli che restano; null se il numero spettante non è impostato. */
    fun situazione(dati: Dati, anno: Int): Pair<Int, Int>? {
        val spettanti = dati.impostazioni.permessiSpettanti
        if (spettanti <= 0) return null
        val presi = dati.assenze.count {
            it.data.year == anno &&
                (it.tipo == TipoAssenza.PERMESSO || it.tipo == TipoAssenza.PERMESSO_EX_FESTIVITA)
        }
        return presi to (spettanti - presi).coerceAtLeast(0)
    }

    /** Testo del riepilogo per la home; null se non c'è nulla da mostrare. */
    fun testo(dati: Dati): String? {
        val anno = LocalDate.now().year
        val (presi, restano) = situazione(dati, anno) ?: return null
        val spettanti = dati.impostazioni.permessiSpettanti
        return "Permessi $anno: presi $presi su $spettanti, restano $restano. " +
            "Quelli non usati entro il 31 dicembre si perdono."
    }

    /** Notifica una volta al mese, se restano permessi da usare. */
    fun controllaMensile(ctx: Context) {
        val dati = Archivio.caricaTutto(ctx)
        val messaggio = testo(dati) ?: return
        val restano = situazione(dati, LocalDate.now().year)?.second ?: return
        if (restano <= 0) return
        val mese = YearMonth.now().toString()
        val p = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (p.getString("permessiMese", null) == mese) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CANALE, "Permessi", NotificationManager.IMPORTANCE_DEFAULT))
        val apri = PendingIntent.getActivity(ctx, 2,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notifica = Notification.Builder(ctx, CANALE)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Permessi da usare")
            .setContentText(messaggio)
            .setStyle(Notification.BigTextStyle().bigText(messaggio))
            .setContentIntent(apri)
            .setAutoCancel(true)
            .build()
        nm.notify(ID_NOTIFICA, notifica)
        p.edit().putString("permessiMese", mese).apply()
    }
}
