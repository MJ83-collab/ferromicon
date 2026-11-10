package it.ferromicon

import java.time.LocalDate
import java.time.Month

/**
 * Festività per il personale Altarail.
 * Altarail art. 19.1: il 7 dicembre (S. Ambrogio) sostituisce il 29 giugno.
 * Dal 2026 il 4 ottobre (S. Francesco) è di nuovo festa nazionale.
 */
object Festivi {

    fun dellAnno(anno: Int): Map<LocalDate, String> {
        val pasqua = pasqua(anno)
        val mappa = sortedMapOf(
            LocalDate.of(anno, Month.JANUARY, 1) to "Capodanno",
            LocalDate.of(anno, Month.JANUARY, 6) to "Epifania",
            pasqua to "Pasqua",
            pasqua.plusDays(1) to "Lunedì dell'Angelo",
            LocalDate.of(anno, Month.APRIL, 25) to "Liberazione",
            LocalDate.of(anno, Month.MAY, 1) to "Festa del lavoro",
            LocalDate.of(anno, Month.JUNE, 2) to "Festa della Repubblica",
            LocalDate.of(anno, Month.AUGUST, 15) to "Ferragosto",
            LocalDate.of(anno, Month.NOVEMBER, 1) to "Ognissanti",
            LocalDate.of(anno, Month.DECEMBER, 7) to "S. Ambrogio (al posto del 29 giugno)",
            LocalDate.of(anno, Month.DECEMBER, 8) to "Immacolata",
            LocalDate.of(anno, Month.DECEMBER, 25) to "Natale",
            LocalDate.of(anno, Month.DECEMBER, 26) to "S. Stefano"
        )
        if (anno >= 2026) mappa[LocalDate.of(anno, Month.OCTOBER, 4)] = "S. Francesco"
        return mappa
    }

    fun èFestivo(data: LocalDate): Boolean = dellAnno(data.year).containsKey(data)

    /**
     * Festività che danno diritto a indennità festiva e recupero (CCNL art. 29.2.1):
     * tutte tranne la domenica di Pasqua, che ha un'indennità propria (art. 76.2).
     */
    fun èFestivoContrattuale(data: LocalDate): Boolean = èFestivo(data) && data != pasqua(data.year)

    fun nome(data: LocalDate): String? = dellAnno(data.year)[data]

    /** Data della Pasqua (algoritmo gregoriano anonimo, Meeus/Jones/Butcher). */
    fun pasqua(anno: Int): LocalDate {
        val a = anno % 19
        val b = anno / 100
        val c = anno % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val mese = (h + l - 7 * m + 114) / 31
        val giorno = (h + l - 7 * m + 114) % 31 + 1
        return LocalDate.of(anno, mese, giorno)
    }
}
