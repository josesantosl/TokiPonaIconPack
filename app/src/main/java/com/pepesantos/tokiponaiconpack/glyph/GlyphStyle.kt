package com.pepesantos.tokiponaiconpack.glyph

import androidx.annotation.ColorInt

/**
 * Estilo con el que se dibuja un glifo.
 *
 * @param thin cuánto se adelgaza el trazo, en unidades de fuente quitadas por cada lado
 *             (0 = grosor original de nasin-nanpa, cuyo trazo mide ~100).
 */
data class GlyphStyle(
    @param:ColorInt val foreground: Int,
    @param:ColorInt val background: Int?,
    val thin: Float = DEFAULT_THIN,
) {
    companion object {
        /** Mismo valor que THIN en tools/generate_icons.py, para que coincida con el pack. */
        const val DEFAULT_THIN = 34f
        const val MAX_THIN = 40f
        val Default = GlyphStyle(foreground = 0xFFFFFFFF.toInt(), background = 0xFF1B1B1F.toInt())
    }
}
