package it.ferromicon

import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable

/**
 * Stile grafico: sfondo chiaro, un solo colore d'azione e tre colori di stato (verde, ambra, rosso).
 * I colori stanno in res/values (chiaro) e res/values-night (scuro): qui si leggono quelli in uso.
 */
object Stile {

    private lateinit var contesto: Context

    fun inizializza(ctx: Context) { contesto = ctx.applicationContext }

    private fun c(id: Int): Int = contesto.getColor(id)

    val primario get() = c(R.color.primario)
    val primarioTesto get() = c(R.color.primario_testo)
    val primarioChiaro get() = c(R.color.primario_chiaro)
    val testo get() = c(R.color.testo)
    val secondario get() = c(R.color.testo_secondario)
    val linea get() = c(R.color.linea)
    val superficie get() = c(R.color.superficie)

    val ok get() = c(R.color.ok)
    val okChiaro get() = c(R.color.ok_chiaro)
    val attenzione get() = c(R.color.attenzione)
    val attenzioneChiaro get() = c(R.color.attenzione_chiaro)
    val errore get() = c(R.color.errore)
    val erroreChiaro get() = c(R.color.errore_chiaro)
    val infoChiaro get() = c(R.color.info_chiaro)
    val neutroChiaro get() = c(R.color.neutro_chiaro)

    val scuro: Boolean
        get() = (contesto.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    private val densita get() = contesto.resources.displayMetrics.density

    /** Scheda bianca con bordo sottile e angoli a 14 dp. */
    fun scheda(raggioDp: Int = 14): GradientDrawable = GradientDrawable().apply {
        setColor(superficie)
        cornerRadius = raggioDp * densita
        setStroke(maxOf(1, densita.toInt()), linea)
    }

    /** Dà alle schermate secondarie lo stesso aspetto della principale: pulsanti con bordo verde. */
    fun rifinisci(v: android.view.View) {
        if (v is android.widget.Button) {
            v.background = contornoVerde(12)
            v.isAllCaps = false
            v.setTextColor(testo)
        }
        if (v is android.view.ViewGroup) for (i in 0 until v.childCount) rifinisci(v.getChildAt(i))
    }

    /** Pulsante grande con bordo verde di 2 dp. */
    fun contornoVerde(raggioDp: Int = 14): GradientDrawable = GradientDrawable().apply {
        setColor(superficie)
        cornerRadius = raggioDp * densita
        setStroke((2 * densita).toInt(), primario)
    }

    /** Rettangolo con angoli arrotondati, per celle e schede. */
    fun arrotondato(colore: Int, raggioDp: Int = 8): GradientDrawable = GradientDrawable().apply {
        setColor(colore)
        cornerRadius = raggioDp * contesto.resources.displayMetrics.density
    }
}
