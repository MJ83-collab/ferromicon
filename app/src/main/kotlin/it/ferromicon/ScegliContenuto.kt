package it.ferromicon

import android.app.AlertDialog
import android.content.Context
import java.time.LocalDate

/** Finestra con cui si sceglie cosa mandare al calendario del telefono o a un file. */
object ScegliContenuto {
    fun mostra(ctx: Context, titolo: String, conferma: String, dopo: (OpzioniCalendario) -> Unit) {
        val nomi = arrayOf("Servizi (turni)", "Riposi", "Ferie e permessi", "Disponibilità e altre assenze",
            "Solo da oggi in poi")
        val scelte = booleanArrayOf(true, true, true, true, true)
        AlertDialog.Builder(ctx)
            .setTitle(titolo)
            .setMultiChoiceItems(nomi, scelte) { _, i, c -> scelte[i] = c }
            .setPositiveButton(conferma) { _, _ ->
                dopo(OpzioniCalendario(scelte[0], scelte[1], scelte[2], scelte[3],
                    da = if (scelte[4]) LocalDate.now() else null))
            }
            .setNegativeButton("Annulla", null)
            .show()
    }
}
