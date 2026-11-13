package it.ferromicon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ElencoCidTest {
    private val csv = """
        CID;Cognome e nome;Impianto
        1000001;PERSONA UNO;
        1000002;PERSONA DUE;Varese
        1000003;PERSONA TRE;Milano
    """.trimIndent()

    @Test
    fun leggeLeRigheValide() {
        val e = ElencoCid.leggi(csv)
        assertEquals(3, e.size)
        assertEquals(VoceCid(1000002L, "PERSONA DUE", "Varese"), e[1])
    }

    @Test
    fun scartaRigheNonValide() {
        val e = ElencoCid.leggi("CID;Nome;Impianto\nabc;X;Y\n;;\n1000004;RIGA VALIDA;")
        assertEquals(1, e.size)
        assertEquals(1000004L, e[0].cid)
    }

    @Test
    fun cercaPerCidOPerNome() {
        val e = ElencoCid.leggi(csv)
        assertEquals("PERSONA UNO", ElencoCid.cerca(e, "1000001")[0].nominativo)
        assertEquals("PERSONA DUE", ElencoCid.cerca(e, "due")[0].nominativo)
        assertTrue(ElencoCid.cerca(e, "   ").isEmpty())
    }
}
