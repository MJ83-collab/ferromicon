package it.ferromicon

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun apriApp(ctx: Context, views: RemoteViews) {
    val pi = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    views.setOnClickPendingIntent(R.id.widget_radice, pi)
}

/** Aggiorna tutti i widget di Ferromicon (chiamato a ogni salvataggio dei dati). */
object Widget {
    fun aggiornaTutti(ctx: Context) {
        listOf(WidgetProssimo::class.java, WidgetMese::class.java, WidgetOggi::class.java, WidgetRiposo::class.java).forEach { classe ->
            val gestore = AppWidgetManager.getInstance(ctx)
            val ids = gestore.getAppWidgetIds(ComponentName(ctx, classe))
            if (ids.isNotEmpty()) {
                val intento = Intent(ctx, classe).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                ctx.sendBroadcast(intento)
            }
        }
    }
}

/** Widget "Prossimo servizio": giorno, orari, durata e quando si può riprendere dopo l'ultimo servizio. */
class WidgetProssimo : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, gestore: AppWidgetManager, ids: IntArray) {
        ids.forEach { gestore.updateAppWidget(it, costruisci(ctx)) }
    }

    private fun costruisci(ctx: Context): RemoteViews {
        val v = RemoteViews(ctx.packageName, R.layout.widget_prossimo)
        val dati = Archivio.caricaTutto(ctx)
        val adesso = LocalDateTime.now()
        val ordinati = dati.servizi.sortedBy { it.turno.inizio }
        val prossimo = ordinati.firstOrNull { it.turno.fine.isAfter(adesso) }
        val giorno = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALIAN)
        val ora = DateTimeFormatter.ofPattern("HH:mm")
        if (prossimo == null) {
            v.setTextViewText(R.id.w_etichetta, "Prossimo servizio")
            v.setTextViewText(R.id.w_titolo, "Nessun servizio")
            v.setTextViewText(R.id.w_dettaglio, "Non ci sono servizi in programma.")
            v.setTextViewText(R.id.w_nota, "")
        } else {
            val t = prossimo.turno
            val inCorso = !t.inizio.isAfter(adesso)
            val oggi = LocalDate.now()
            val quando = when (t.inizio.toLocalDate()) {
                oggi -> "Oggi"
                oggi.plusDays(1) -> "Domani"
                else -> giorno.format(t.inizio).replaceFirstChar { it.uppercase() }
            }
            v.setTextViewText(R.id.w_etichetta, if (inCorso) "Servizio in corso" else "Prossimo servizio · $quando")
            v.setTextViewText(R.id.w_titolo, "${ora.format(t.inizio)} – ${ora.format(t.fine)}")
            val minuti = java.time.Duration.between(t.inizio, t.fine).toMinutes()
            val treni = prossimo.righe.map { it.numero }.filter { it.isNotBlank() }.distinct().take(3)
            v.setTextViewText(
                R.id.w_dettaglio,
                "Durata ${minuti / 60} h ${"%02d".format(minuti % 60)}'" +
                    (if (treni.isEmpty()) "" else " · treni ${treni.joinToString(", ")}")
            )
            val ripresa = RegoleAltarail.ripresaMinima(t, prossimo.rfrDopo)
            v.setTextViewText(R.id.w_nota, "Dopo: riposo fino alle ${ora.format(ripresa)} del ${DateTimeFormatter.ofPattern("d/M").format(ripresa)}")
        }
        apriApp(ctx, v)
        return v
    }
}

/** Widget "Il mio mese": ore riconosciute sul monte ore, straordinario e festività in scadenza. */
class WidgetMese : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, gestore: AppWidgetManager, ids: IntArray) {
        ids.forEach { gestore.updateAppWidget(it, costruisci(ctx)) }
    }

    private fun costruisci(ctx: Context): RemoteViews {
        val v = RemoteViews(ctx.packageName, R.layout.widget_mese)
        val dati = Archivio.caricaTutto(ctx)
        val oggi = LocalDate.now()
        val mese = YearMonth.from(oggi)
        val r = Riepilogo.mese(dati, mese)
        val nomeMese = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ITALIAN).format(mese)
            .replaceFirstChar { it.uppercase() }
        v.setTextViewText(R.id.w_etichetta, nomeMese)
        v.setTextViewText(R.id.w_titolo, "${Riepilogo.ore(r.oreRiconosciuteMinuti)} / ${Riepilogo.ore(r.monteOreMinuti)}")
        v.setProgressBar(R.id.w_barra, 100, (r.progresso * 100).toInt().coerceIn(0, 100), false)
        v.setTextViewText(
            R.id.w_dettaglio,
            "Presenze ${r.presenzeComplessive} · straordinario ${Riepilogo.ore(r.straordinarioTotaleMinuti)}" +
                " · dormite ${r.dormite}"
        )
        val imp = dati.impostazioni
        val prossima = RecuperoFestivi.stati(oggi.year, oggi, imp.festiviForzati)
            .filter { it.recuperabile && !it.scaduto && !oggi.isBefore(it.data) }
            .minByOrNull { it.giorniAllaScadenza }
        v.setTextViewText(
            R.id.w_nota,
            if (prossima == null) "Nessun recupero festivo in scadenza"
            else "Recupero ${prossima.nome}: scade tra ${prossima.giorniAllaScadenza} gg"
        )
        apriApp(ctx, v)
        return v
    }
}
