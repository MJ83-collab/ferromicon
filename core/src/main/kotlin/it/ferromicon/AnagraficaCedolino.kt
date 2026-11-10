package it.ferromicon

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Dati personali utili letti dal cedolino: bastano per impostare profilo, livello, CID, data di assunzione
 * e ferie residue senza digitarli. Codice fiscale, IBAN e simili non vengono letti.
 */
data class AnagraficaCedolino(
    val cid: String = "",
    val dataAssunzione: LocalDate? = null,
    val livello: Livello? = null,
    val livelloTesto: String = "",
    val profilo: Profilo? = null,
    val profiloTesto: String = "",
    val residenza: String = "",
    /** Numero degli aumenti periodici di anzianità. */
    val apa: Int? = null,
    val partTime: Double? = null,
    val adPersonam: Double? = null,
    val feriePrecedenti: Double? = null,
    val ferieCorrenti: Double? = null
) {
    val vuota get() = cid.isEmpty() && dataAssunzione == null && livello == null && profilo == null

    /** Impostazioni con i dati del cedolino al posto di quelli già presenti (solo ciò che è stato letto). */
    fun applica(imp: Impostazioni): Impostazioni = imp.copy(
        profilo = profilo ?: imp.profilo,
        cid = cid.ifEmpty { imp.cid },
        dataAssunzione = dataAssunzione ?: imp.dataAssunzione,
        livello = livello ?: imp.livello,
        adPersonam = adPersonam ?: imp.adPersonam,
        ferieResidueAnnoPrecedente = feriePrecedenti?.toInt() ?: imp.ferieResidueAnnoPrecedente
    )

    /** Righe leggibili da mostrare prima di applicare. */
    fun descrizione(): List<String> = buildList {
        profilo?.let { add("Profilo: ${it.etichetta}") }
        if (profilo == null && profiloTesto.isNotEmpty()) add("Profilo: $profiloTesto (non riconosciuto)")
        livello?.let { add("Livello: ${it.name}") }
        if (cid.isNotEmpty()) add("CID: $cid")
        dataAssunzione?.let { add("Assunzione: ${FORMATO.format(it)}") }
        if (residenza.isNotEmpty()) add("Residenza: $residenza")
        apa?.let { add("Aumenti periodici di anzianità: $it") }
        adPersonam?.let { if (it > 0) add("Assegno ad personam: %.2f €".format(java.util.Locale.ITALIAN, it)) }
        feriePrecedenti?.let { add("Ferie anno precedente residue: ${num(it)}") }
        ferieCorrenti?.let { add("Ferie anno corrente residue: ${num(it)}") }
    }

    private fun num(v: Double) = if (v == Math.floor(v)) v.toInt().toString() else "%.1f".format(java.util.Locale.ITALIAN, v)

    companion object {
        private val FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        private val DATA = DateTimeFormatter.ofPattern("dd.MM.yyyy")

        /** [testi] sono le righe del cedolino già ricostruite, [presenze] la tabella presenze/ferie, [componenti] i componenti della retribuzione. */
        fun leggi(testi: List<String>, presenze: Map<String, Double>, componenti: Map<String, Double>): AnagraficaCedolino {
            fun primo(re: Regex): String? = testi.firstNotNullOfOrNull { re.find(it)?.groupValues?.get(1) }
            val cid = primo(Regex("CID\\s*:\\s*(\\d+)"))?.trimStart('0').orEmpty()
            val assunzione = primo(Regex("Data Assunz\\.?\\s*:\\s*(\\d{2}\\.\\d{2}\\.\\d{4})"))
                ?.let { runCatching { LocalDate.parse(it, DATA) }.getOrNull() }
            val livelloRiga = primo(Regex("^Livello\\s*:\\s*(.+)$")).orEmpty()
            val livello = Livello.values().firstOrNull { it.name == livelloRiga.trim().split(Regex("\\s+")).firstOrNull() }
            val profiloRiga = primo(Regex("^Profilo\\s*:\\s*(.+?)(?:\\s+AREA:.*)?$")).orEmpty().trim()
            val u = profiloRiga.uppercase()
            val profilo = when {
                "MACCHINIST" in u || "CONDUZIONE" in u || "CONDUTTOR" in u -> Profilo.MACCHINISTA
                "CAPO TRENO" in u || "CAPOTRENO" in u || "SCORTA" in u -> Profilo.CAPOTRENO
                else -> null
            }
            val area = primo(Regex("AREA:\\s*\\S+\\s+(.+?)(?:\\s+CDC.*)?$")).orEmpty()
                .replace(Regex("^ST\\.?\\s*(di|DI)?\\s*"), "").trim()
            val apa = primo(Regex("Numero A\\.P\\.A\\s*:\\s*(\\d+)"))?.toIntOrNull()
            val partTime = primo(Regex("Perc\\.PartTime\\s*:\\s*([\\d.,]+)"))?.let { CedolinoParser.numero(it) }
            val adPers = componenti.entries.firstOrNull { it.key.uppercase().contains("AD PERSONAM") }?.value
            fun ferie(fine: String) = presenze.entries.firstOrNull { it.key.lowercase().replace(" ", "").contains("res.ferie$fine") }?.value
            return AnagraficaCedolino(cid, assunzione, livello, livelloRiga.trim(), profilo, profiloRiga, area, apa,
                partTime, adPers, ferie("ap"), ferie("ac"))
        }
    }
}
