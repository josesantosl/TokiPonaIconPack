package com.pepesantos.tokiponaiconpack.glyph

import android.content.pm.ApplicationInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class GlyphSuggestionsTest {

    private fun s(label: String, pkg: String, cat: Int = ApplicationInfo.CATEGORY_UNDEFINED) =
        GlyphSuggestions.suggest(label, pkg, cat)

    @Test fun keywordsInLabel() {
        assertEquals("tomo mani", s("Banco Guayaquil", "com.bancodeguayaquil"))
        assertEquals("suno", s("OpenWeather", "uk.co.openweather"))
        assertEquals("lipu", s("Proton Mail", "ch.protonmail.android"))
        assertEquals("tomo ilo", s("Pro Launcher", "app.prolauncher"))
    }

    @Test fun keywordsInPackage() =
        assertEquals("ilo nimi", s("WM", "com.wasimaster.wmkeyboard"))

    @Test fun accentsAreIgnored() = assertEquals("ilo sitelen", s("Cámara", "x.y"))

    @Test fun shortKeywordsDoNotMatchInsideWords() =
        // "tv" no debe coincidir dentro de "atvantage"; "pay" no dentro de "playstack".
        assertEquals(GlyphSuggestions.FALLBACK, s("Atvantage", "com.playstack.thing"))

    @Test fun categoryFallback() {
        assertEquals("musi", s("Balatro", "com.playstack.balatro", ApplicationInfo.CATEGORY_GAME))
        assertEquals("kalama", s("Xyz", "q.r", ApplicationInfo.CATEGORY_AUDIO))
    }

    @Test fun defaultFallback() = assertEquals("ijo", s("Qwerty", "a.b"))
}
