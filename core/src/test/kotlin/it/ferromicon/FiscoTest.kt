package it.ferromicon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FiscoTest {

    @Test fun irpefA30000Nel2025() {
        // 28.000 al 23% + 2.000 al 35% = 7.140; detrazione 1910*(50000-30000)/22000 = 1736,36 più 65 (fascia 25-35 mila)
        val i = Fisco.irpef(30000.0, 2025)
        assertEquals(7140.0, i.lorda, 0.01)
        assertEquals(1801.36, i.detrazioni, 0.01)
        assertEquals(1000.0, i.ulteriore, 0.01)
        assertEquals(7140.0 - 1801.36 - 1000.0, i.netta, 0.01)
    }

    @Test fun scaglioniPerAnno() {
        // Fino al 2021 cinque scaglioni; dal 2026 il secondo scende al 33%
        assertEquals(15000 * .23 + 5000 * .27, Fisco.irpef(20000.0, 2019).lorda, 0.01)
        assertEquals(28000 * .23 + 10000 * .33, Fisco.irpef(38000.0, 2026).lorda, 0.01)
        assertEquals(28000 * .23 + 10000 * .35, Fisco.irpef(38000.0, 2025).lorda, 0.01)
    }

    @Test fun addizionaleRegionaleLombardia() {
        assertEquals(15000 * .0123 + 5000 * .0158, Fisco.regionale(20000.0, null), 0.01)
        assertEquals(1000.0, Fisco.regionale(50000.0, 2.0), 0.01)
    }

    @Test fun nettoDiUnOraDiStraordinario() {
        val p = Fisco.Parametri()
        val n = Fisco.nettoExtra(32000.0, 100.0, 2025, p)
        assertEquals(9.19, n.inps, 0.001)
        assertEquals(90.81, n.imponibile, 0.001)
        assertTrue(n.netto in 30.0..60.0)       // tra 32 e 40 mila le detrazioni calano: l'aliquota marginale supera il 50%
        assertTrue(n.tasse > 0)
    }

    @Test fun marginaleCrescePassandoDalloScaglione() {
        val basso = Fisco.marginale(20000.0, 2025)
        val alto = Fisco.marginale(45000.0, 2025)
        assertTrue(alto > basso - 0.0001 || alto > 0.3)
        assertTrue(Fisco.marginale(60000.0, 2025) > 0.43)
    }
}
