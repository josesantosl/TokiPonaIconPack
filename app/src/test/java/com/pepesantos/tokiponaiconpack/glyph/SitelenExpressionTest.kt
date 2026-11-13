package com.pepesantos.tokiponaiconpack.glyph

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SitelenExpressionTest {

    private fun t(expr: String) = SitelenExpression.toFontText(expr)

    @Test fun singleWord() = assertEquals("linluwi", t("linluwi"))

    @Test fun variant() {
        assertEquals("sewi1", t("sewi:1"))
        assertEquals("sewi1", t("sewi1"))
    }

    @Test fun nestedHeadsAreAutomatic() {
        assertEquals("toki+pona", t("toki pona"))
        assertEquals("sitelen+tawa", t("sitelen tawa"))
        assertEquals("lipu+tenpo", t("Lipu  Tenpo"))
        assertEquals("tomo+tawa", t("tomo tawa"))
        assertEquals("jan+pona", t("jan pona"))
        assertEquals("poki+mani", t("poki mani"))
        assertEquals("sona+pona", t("sona pona"))
        assertEquals("lawa+pona", t("lawa pona"))
    }

    @Test fun wordsWithoutHollowStack() {
        assertEquals("pona-kalama", t("pona kalama"))
        assertEquals("esun-ilo", t("esun ilo"))
    }

    @Test fun otherCompoundsStack() = assertEquals("ilo-tenpo", t("ilo tenpo"))

    @Test fun explicitOperatorsWin() {
        assertEquals("toki-pona", t("toki^pona"))
        assertEquals("poki+sewi1", t("poki+sewi:1"))
    }

    @Test fun invalid() {
        assertNull(t(""))
        assertNull(t("a b c"))
        assertNull(t("toki!"))
    }

    @Test fun fileName() = assertEquals("poki_sewi_1", SitelenExpression.fileName("poki+sewi:1"))
}
