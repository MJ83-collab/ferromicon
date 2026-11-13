package it.ferromicon

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject

/**
 * Elenco delle librerie di terze parti incluse nell'app, con la loro licenza.
 * I dati vengono da assets/licenze.json, generato in fase di build dal plugin
 * di controllo licenze (vedi app/build.gradle.kts): si aggiorna da solo.
 */
class LicenzeActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Licenze open source"

        val colonna = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        colonna.addView(TextView(this).apply {
            text = "Ferromicon usa solo componenti distribuiti con licenze libere. " +
                "Ecco l'elenco completo, dipendenze indirette incluse."
            textSize = 14f
        })

        val righe = leggiLicenze()
        if (righe.isEmpty()) {
            colonna.addView(TextView(this).apply {
                text = "Elenco non disponibile in questa versione."
                setPadding(0, dp(12), 0, 0)
            })
        }
        righe.forEach { r ->
            colonna.addView(TextView(this).apply {
                text = "${r.nome} ${r.versione}\n${r.licenza}"
                textSize = 13f
                setPadding(0, dp(10), 0, 0)
            })
        }

        setContentView(ScrollView(this).apply { addView(colonna) })
    }

    private data class Riga(val nome: String, val versione: String, val licenza: String)

    private fun leggiLicenze(): List<Riga> = try {
        val testo = assets.open("licenze.json").bufferedReader().use { it.readText() }
        val dipendenze = JSONObject(testo).getJSONArray("dipendenze")
        (0 until dipendenze.length()).map { i ->
            val d = dipendenze.getJSONObject(i)
            Riga(d.getString("nome"), d.getString("versione"), d.optString("licenza", "?"))
        }.sortedBy { it.nome }
    } catch (e: Exception) {
        emptyList()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
