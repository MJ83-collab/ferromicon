package it.ferromicon

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.CalendarContract
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Eventi dell'agenda scritti nel calendario del telefono (e quindi in Google Calendar, se l'account è sincronizzato). */
object CalendarioTelefono {

    class Voce(val id: Long, val nome: String, val conto: String)

    fun permessi(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
            ctx.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Calendari su cui si può scrivere. */
    fun calendari(ctx: Context): List<Voce> {
        val lista = ArrayList<Voce>()
        val proiezione = arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME)
        ctx.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, proiezione,
            "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ?",
            arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString()), null)?.use { c ->
            while (c.moveToNext()) lista += Voce(c.getLong(0), c.getString(1) ?: "Calendario", c.getString(2) ?: "")
        }
        return lista
    }

    private fun uriApp(id: Long) = "ferromicon://evento/$id"

    /** Toglie gli eventi creati da Ferromicon. */
    fun rimuovi(ctx: Context): Int = ctx.contentResolver.delete(
        CalendarContract.Events.CONTENT_URI, "${CalendarContract.Events.CUSTOM_APP_PACKAGE} = ?", arrayOf(ctx.packageName))

    private const val PREFS = "calendario"

    /**
     * Dopo un aggiornamento dell'app rifa la sincronizzazione con il calendario già scelto,
     * così gli eventi tornano allineati ai dati salvati. Gira in background e non tocca i dati.
     */
    fun riallineaDopoAggiornamento(ctx: Context) {
        val app = ctx.applicationContext
        val p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val versione = runCatching { app.packageManager.getPackageInfo(app.packageName, 0).longVersionCode }
            .getOrDefault(0L)
        if (p.getLong("versioneVista", -1L) == versione) return
        val calendario = p.getLong("calendarioId", -1L)
        if (calendario < 0 || !permessi(app)) {
            p.edit().putLong("versioneVista", versione).apply()
            return
        }
        Thread {
            val ok = runCatching { sincronizza(app, calendario, Archivio.caricaTutto(app), OpzioniCalendario()) }.isSuccess
            if (ok) p.edit().putLong("versioneVista", versione).apply()
        }.start()
    }

    /** Sostituisce gli eventi di Ferromicon con servizi e assenze dell'agenda da [da] in poi. */
    fun sincronizza(ctx: Context, calendario: Long, completi: Dati, opzioni: OpzioniCalendario): Int {
        rimuovi(ctx)
        val dati = opzioni.applica(completi)
        val roma = ZoneId.of("Europe/Rome")
        val ora = DateTimeFormatter.ofPattern("HH:mm")
        val valori = ArrayList<ContentValues>()
        fun base(id: Long, titolo: String, descrizione: String) = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendario)
            put(CalendarContract.Events.TITLE, titolo)
            put(CalendarContract.Events.DESCRIPTION, descrizione)
            put(CalendarContract.Events.CUSTOM_APP_PACKAGE, ctx.packageName)
            put(CalendarContract.Events.CUSTOM_APP_URI, uriApp(id))
        }
        dati.servizi.forEach { s ->
            val v = base(s.id, (if (s.origineTurno.isNotEmpty()) s.origineTurno + " " else "Servizio ") +
                "${ora.format(s.turno.inizio)}–${ora.format(s.turno.fine)}", s.note)
            v.put(CalendarContract.Events.DTSTART, s.turno.inizio.atZone(roma).toInstant().toEpochMilli())
            v.put(CalendarContract.Events.DTEND, s.turno.fine.atZone(roma).toInstant().toEpochMilli())
            v.put(CalendarContract.Events.EVENT_TIMEZONE, roma.id)
            valori += v
        }
        dati.assenze.forEach { a ->
            val v = base(a.id, a.tipo.simbolo + " " + a.tipo.etichetta, a.note)
            v.put(CalendarContract.Events.ALL_DAY, 1)
            v.put(CalendarContract.Events.DTSTART, a.data.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
            v.put(CalendarContract.Events.DTEND, a.data.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
            v.put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
            valori += v
        }
        var inseriti = 0
        valori.forEach { if (ctx.contentResolver.insert(CalendarContract.Events.CONTENT_URI, it) != null) inseriti++ }
        return inseriti
    }
}

/** Sincronizzazione dell'agenda col calendario del telefono. */
class CalendarioTelefonoActivity : Activity() {

    private lateinit var contenuto: LinearLayout
    private var voci: List<CalendarioTelefono.Voce> = emptyList()
    private var scelta: Spinner? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Calendario del telefono"
        contenuto = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
        }
        setContentView(ScrollView(this).apply { addView(contenuto) })
        mostra()
    }

    override fun onResume() {
        super.onResume()
        window.decorView.post { Stile.rifinisci(window.decorView) }
    }

    private fun testo(t: String, size: Float = 14f) = TextView(this).apply {
        text = t; textSize = size; setTextColor(Stile.testo); setPadding(0, dp(6), 0, dp(6))
    }

    private fun mostra() {
        contenuto.removeAllViews()
        contenuto.addView(testo("Scrive servizi, riposi e ferie dell'agenda (a tua scelta) nel calendario che scegli. " +
            "Se è un calendario Google, compare anche in Google Calendar e sugli altri tuoi dispositivi. " +
            "Gli eventi creati da Ferromicon si riconoscono e si aggiornano a ogni sincronizzazione senza toccare gli altri."))
        if (!CalendarioTelefono.permessi(this)) {
            contenuto.addView(Button(this).apply {
                text = "Consenti l'accesso al calendario"
                setOnClickListener {
                    requestPermissions(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR), 51)
                }
            })
            Stile.rifinisci(contenuto)
            return
        }
        voci = CalendarioTelefono.calendari(this)
        if (voci.isEmpty()) {
            contenuto.addView(testo("Non trovo calendari su cui scrivere. Aggiungi un account Google al telefono o apri prima l'app Calendario."))
            return
        }
        contenuto.addView(testo("Calendario di destinazione", 16f))
        scelta = Spinner(this).apply {
            adapter = ArrayAdapter(this@CalendarioTelefonoActivity, android.R.layout.simple_spinner_dropdown_item,
                voci.map { if (it.conto.isNotEmpty() && it.conto != it.nome) "${it.nome} (${it.conto})" else it.nome })
        }
        contenuto.addView(scelta)
        contenuto.addView(Button(this).apply {
            text = "Sincronizza ora"
            setOnClickListener { sincronizza() }
        })
        contenuto.addView(Button(this).apply {
            text = "Rimuovi gli eventi di Ferromicon"
            setOnClickListener {
                AlertDialog.Builder(this@CalendarioTelefonoActivity)
                    .setMessage("Tolgo dal calendario del telefono tutti gli eventi creati da Ferromicon? L'agenda dell'app non cambia.")
                    .setPositiveButton("Rimuovi") { _, _ ->
                        val n = runCatching { CalendarioTelefono.rimuovi(this@CalendarioTelefonoActivity) }.getOrDefault(0)
                        Toast.makeText(this@CalendarioTelefonoActivity, "$n eventi rimossi", Toast.LENGTH_SHORT).show()
                    }.setNegativeButton("Annulla", null).show()
            }
        })
        Stile.rifinisci(contenuto)
    }

    private fun sincronizza() {
        val voce = voci.getOrNull(scelta?.selectedItemPosition ?: 0) ?: return
        ScegliContenuto.mostra(this, "Cosa sincronizzare", "Sincronizza") { opzioni ->
            val dati = Archivio.caricaTutto(this)
            val n = runCatching { CalendarioTelefono.sincronizza(this, voce.id, dati, opzioni) }
            n.onSuccess {
                getSharedPreferences("calendario", MODE_PRIVATE).edit().putLong("calendarioId", voce.id).apply()
            }
            Toast.makeText(this, n.fold({ "$it eventi scritti in ${voce.nome}" }, { "Sincronizzazione non riuscita: ${it.message}" }),
                Toast.LENGTH_LONG).show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 51) mostra()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
