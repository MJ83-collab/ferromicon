package it.ferromicon

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** Le date invernali sono in FerieInvernali (vedi FerieInvernaliTest); qui restano Pasqua e la sequenza estiva. */
class InvernaliTest {
    @Test fun pasquaCalcolata() {
        assertEquals(LocalDate.of(2026, 4, 5), PeriodiFerie.pasqua(2026))
        assertEquals(LocalDate.of(2027, 3, 28), PeriodiFerie.pasqua(2027))
        assertEquals(LocalDate.of(2028, 4, 16), PeriodiFerie.pasqua(2028))
    }

    @Test fun sequenzaScorreOgniAnno() {
        assertEquals("D", PeriodiFerie.letteraPerAnno("D", 2026, 2026))
        assertEquals("A", PeriodiFerie.letteraPerAnno("D", 2026, 2027))
        assertEquals("C", PeriodiFerie.letteraPerAnno("D", 2026, 2028))
        assertEquals("B", PeriodiFerie.letteraPerAnno("D", 2026, 2031))
        assertEquals("D", PeriodiFerie.letteraPerAnno("D", 2026, 2032))
    }
}
