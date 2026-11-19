package com.pepesantos.tokiponaiconpack.glyph

import androidx.annotation.ColorInt

/**
 * Estilo con el que se dibuja un glifo.
 *
 * @param thin cuánto se adelgaza el trazo, en unidades de fuente quitadas por cada lado
 *             (0 = grosor original de Fairfax HD, cuyo trazo mide [FONT_STROKE]).
 */
data class GlyphStyle(
    @param:ColorInt val foreground: Int,
    @param:ColorInt val background: Int?,
    val thin: Float = DEFAULT_THIN,
) {
    companion object {
        /** Grosor del trazo de Fairfax HD (unidades de fuente); FONT_STROKE en el script. */
        const val FONT_STROKE = 80f
        /** Mismo valor que THIN en tools/generate_icons.py, para que coincida con el pack. */
        const val DEFAULT_THIN = 25f
        /** Lo más fino: deja un trazo de 16 unidades (un 20 %). */
        const val MAX_THIN = 32f
        val Default = GlyphStyle(foreground = 0xFFFFFFFF.toInt(), background = 0xFF1B1B1F.toInt())
    }
}
