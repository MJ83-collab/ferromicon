package it.ferromicon

import java.util.Locale

/** Una figura professionale con il suo salario professionale mensile. */
data class Figura(val nome: String, val importoMensile: Double) {
    val etichetta: String get() = String.format(Locale.ITALY, "%s · %.2f €", nome, importoMensile)
}

/**
 * Tabella del salario professionale del CCNL Mobilità/Area Attività Ferroviarie del 22 maggio 2025
 * (Art. 72, pag. 120). Sono incluse le figure leggibili senza ambiguità nella tabella.
 * Le figure senza importo certo (es. le varianti per attività delle officine) non sono qui.
 */
object FigureProfessionali {

    val tutte: List<Figura> = listOf(
        Figura("Responsabile struttura operativa complessa", 364.02),
        Figura("Comandante", 364.02),
        Figura("Professional senior", 329.02),
        Figura("Coordinatore di Protezione Aziendale", 329.02),
        Figura("Direttore di macchina", 329.02),
        Figura("Responsabile di linea/struttura operativa-tecnica", 301.10),
        Figura("Professional", 279.10),
        Figura("Controllore/Coadiutore di Protezione Aziendale", 279.10),
        Figura("Professional sanitario", 279.10),
        Figura("Primo Ufficiale Navale", 279.10),
        Figura("Primo Ufficiale di Macchina", 279.10),
        Figura("Primo Ufficiale", 279.10),
        Figura("Impiegato Direttivo", 212.52),
        Figura("Capo Traffico Traslochi", 212.52),
        Figura("Capo Commessa commerciale", 212.52),
        Figura("Capo Tecnico Infrastrutture", 111.40),
        Figura("Capo Tecnico Rotabili", 175.94),
        Figura("Capo Stazione", 166.20),
        Figura("Macchinista", 208.96),
        Figura("Specialista Tecnico Amministrativo", 104.02),
        Figura("Capo Treno/Capo Servizi Treno", 142.54),
        Figura("Specialista Tecnico Commerciale", 137.22),
        Figura("Tecnico Specializzato di Protezione Aziendale", 104.02),
        Figura("Coadiutore medico", 104.02),
        Figura("Tecnico Sanitario Specializzato", 104.02),
        Figura("Ufficiale Navale", 167.85),
        Figura("Ufficiale di Macchina", 167.85),
        Figura("Ufficiale", 167.85),
        Figura("Nostromo", 144.61),
        Figura("Capo Motorista", 144.61),
        Figura("Capo Elettricista", 144.61),
    )

    /** Importo della figura con questo nome; null se il nome è vuoto o non è in tabella. */
    fun importo(nome: String): Double? =
        if (nome.isBlank()) null else tutte.firstOrNull { it.nome == nome }?.importoMensile
}
