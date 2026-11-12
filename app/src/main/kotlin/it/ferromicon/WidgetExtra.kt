package it.ferromicon

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ORA_FMT = DateTimeFormatter.ofPattern("HH:mm")
private val GIORNO_FMT = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ITALIAN)

/** Widget "Oggi": il servizio o l'assenza di oggi, e il giorno dopo. */
class WidgetOggi : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, gestore: AppWidgetManager, ids: IntArray) {
        ids.forEach { gestore.updateAppWidget(it, costruisci(ctx)) }
    }

    private fun costruisci(ctx: Context): RemoteViews {
        val v = RemoteViews(ctx.packageName, R.layout.widget_mese)
        v.setViewVisibility(R.id.w_barra, View.GONE)
        val dati = Archivio.caricaTutto(ctx)
        val oggi = LocalDate.now()
        val domani = oggi.plusDays(1)

        fun riga(giorno: LocalDate): String {
            val servizi = dati.servizi.filter { it.turno.inizio.toLocalDate() == giorno }.sortedBy { it.turno.inizio }
            if (servizi.isNotEmpty()) {
                val prima = servizi.first().turno.inizio
                val ultima = servizi.last().turno.fine
                return "Servizio ${ORA_FMT.format(prima)} – ${ORA_FMT.format(ultima)}"
            }
            val assenza = dati.assenze.firstOrNull { it.data == giorno }
            return assenza?.let { "${it.tipo.simbolo} ${it.tipo.etichetta}" } ?: "Nessun dato in agenda"
        }

        v.setTextViewText(R.id.w_etichetta, "Oggi · " + GIORNO_FMT.format(oggi).replaceFirstChar { it.uppercase() })
        v.setTextViewText(R.id.w_titolo, riga(oggi))
        v.setTextViewText(R.id.w_dettaglio, "Domani: " + riga(domani))
        val prossimoDopo = dati.servizi.filter { it.turno.inizio.toLocalDate().isAfter(oggi) }
            .minByOrNull { it.turno.inizio }
        v.setTextViewText(R.id.w_nota, prossimoDopo?.let {
            "Prossimo servizio: ${GIORNO_FMT.format(it.turno.inizio.toLocalDate())}"
        } ?: "")
        apriApp(ctx, v)
        return v
    }
}

/** Widget "Prossimo riposo": quanti giorni mancano al prossimo riposo o ferie, e quanti ne restano nel mese. */
class WidgetRiposo : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, gestore: AppWidgetManager, ids: IntArray) {
        ids.forEach { gestore.updateAppWidget(it, costruisci(ctx)) }
    }

    private fun costruisci(ctx: Context): RemoteViews {
        val v = RemoteViews(ctx.packageName, R.layout.widget_mese)
        v.setViewVisibility(R.id.w_barra, View.GONE)
        val dati = Archivio.caricaTutto(ctx)
        val oggi = LocalDate.now()
        val prossimo = dati.assenze.filter { it.data >= oggi && it.tipo.riposo }.minByOrNull { it.data }
        val ferie = dati.assenze.filter { it.data >= oggi && it.tipo == TipoAssenza.FERIE }.minByOrNull { it.data }
        v.setTextViewText(R.id.w_etichetta, "Prossimo riposo · ${dati.impostazioni.profiloAttivo.etichetta}")
        if (prossimo == null) {
            v.setTextViewText(R.id.w_titolo, "Nessun riposo")
            v.setTextViewText(R.id.w_dettaglio, "Non ci sono riposi in agenda da oggi in poi.")
        } else {
            val giorni = java.time.temporal.ChronoUnit.DAYS.between(oggi, prossimo.data)
            v.setTextViewText(R.id.w_titolo, when (giorni) {
                0L -> "Riposo oggi"
                1L -> "Riposo domani"
                else -> "Tra $giorni giorni"
            })
            v.setTextViewText(R.id.w_dettaglio, "${GIORNO_FMT.format(prossimo.data)} · ${prossimo.tipo.etichetta}")
        }
        v.setTextViewText(R.id.w_nota, ferie?.let {
            "Prossime ferie: ${GIORNO_FMT.format(it.data)}"
        } ?: "Nessuna ferie in agenda")
        apriApp(ctx, v)
        return v
    }
}

/** Widget "Permessi da usare": quanti giorni di permesso restano nell'anno, per il profilo scelto. */
class WidgetPermessi : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, gestore: AppWidgetManager, ids: IntArray) {
        ids.forEach { gestore.updateAppWidget(it, costruisci(ctx)) }
    }

    private fun costruisci(ctx: Context): RemoteViews {
        val v = RemoteViews(ctx.packageName, R.layout.widget_mese)
        v.setViewVisibility(R.id.w_barra, View.GONE)
        val dati = Archivio.caricaTutto(ctx)
        val anno = LocalDate.now().year
        v.setTextViewText(R.id.w_etichetta, "Permessi $anno · ${dati.impostazioni.profiloAttivo.etichetta}")
        val situazione = AvvisoPermessi.situazione(dati, anno)
        if (situazione == null) {
            v.setTextViewText(R.id.w_titolo, "Non impostato")
            v.setTextViewText(R.id.w_dettaglio, "Imposta i giorni spettanti nelle Impostazioni.")
            v.setTextViewText(R.id.w_nota, "")
        } else {
            val (presi, restano) = situazione
            v.setTextViewText(R.id.w_titolo, "Restano $restano")
            v.setTextViewText(R.id.w_dettaglio, "Presi $presi su ${dati.impostazioni.permessiSpettanti}")
            v.setTextViewText(R.id.w_nota, "Quelli non usati entro il 31 dicembre si perdono.")
        }
        apriApp(ctx, v)
        return v
    }
}
