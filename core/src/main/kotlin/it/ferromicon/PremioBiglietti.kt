package it.ferromicon

/**
 * Premio biglietti del capotreno (voci Trenord 1A28, 1A86, 1A87, 1A99).
 * Stima indicativa: gli importi del mese vanno inseriti a mano, l'app non li legge dai sistemi aziendali.
 */
object PremioBiglietti {

    /** Quantità e importi del mese, inseriti dall'utente. */
    data class Dati(
        /** Regolarizzazioni a bordo con penalità (voce 1A28: 2 € ciascuna). */
        val regolarizzazioni: Int = 0,
        /** Biglietti emessi a bordo senza esazione: importo (voce 1A86: 25%). */
        val bigliettiSenzaEsazione: Double = 0.0,
        /** Sovrattassa riscossa (voce 1A87: 25%). */
        val sovrattassaRiscossa: Double = 0.0,
        /** Verbali pagati a posteriori (voce 1A99: 2 € ciascuno). */
        val verbaliPagati: Int = 0,
        /** Lunga percorrenza: aggiunge il 7% sulla regolarizzazione (vademecum 2.0). */
        val lungaPercorrenza: Boolean = false
    )

    data class Voce(val codice: String, val descrizione: String, val importo: Double)

    const val INDENNITA_REGOLARIZZAZIONE = 2.0
    const val PERCENTUALE_BIGLIETTO = 0.25
    const val PERCENTUALE_SOVRATTASSA = 0.25
    const val PERCENTUALE_LUNGA_PERCORRENZA = 0.07

    fun calcola(d: Dati): List<Voce> {
        val voci = mutableListOf<Voce>()
        val regolarizzazione = d.regolarizzazioni * INDENNITA_REGOLARIZZAZIONE
        if (regolarizzazione > 0) voci += Voce("1A28",
            "${d.regolarizzazioni} regolarizzazioni a bordo × 2 €", regolarizzazione)
        if (d.lungaPercorrenza && regolarizzazione > 0) voci += Voce("LP",
            "Lunga percorrenza: 7% sulla regolarizzazione", regolarizzazione * PERCENTUALE_LUNGA_PERCORRENZA)
        if (d.bigliettiSenzaEsazione > 0) voci += Voce("1A86",
            "25% dei biglietti emessi senza esazione", d.bigliettiSenzaEsazione * PERCENTUALE_BIGLIETTO)
        if (d.sovrattassaRiscossa > 0) voci += Voce("1A87",
            "25% della sovrattassa riscossa", d.sovrattassaRiscossa * PERCENTUALE_SOVRATTASSA)
        if (d.verbaliPagati > 0) voci += Voce("1A99",
            "${d.verbaliPagati} verbali pagati × 2 €", d.verbaliPagati * INDENNITA_REGOLARIZZAZIONE)
        return voci
    }

    fun totale(d: Dati): Double = calcola(d).sumOf { it.importo }
}
