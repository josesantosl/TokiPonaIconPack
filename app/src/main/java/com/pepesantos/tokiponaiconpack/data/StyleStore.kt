package com.pepesantos.tokiponaiconpack.data

import android.content.Context
import androidx.core.content.edit
import com.pepesantos.tokiponaiconpack.glyph.GlyphStyle

/**
 * Último estilo usado en el editor. El selector de iconos lo usa para dibujar
 * las palabras guardadas igual que las ve el usuario en el editor.
 */
class StyleStore(context: Context) {

    private val prefs = context.getSharedPreferences("estilo", Context.MODE_PRIVATE)

    fun load(): GlyphStyle {
        val d = GlyphStyle.Default
        return GlyphStyle(
            foreground = prefs.getInt(FG, d.foreground),
            background = if (prefs.getBoolean(HAS_BG, true)) prefs.getInt(BG, d.background!!) else null,
            thin = prefs.getFloat(THIN, d.thin),
        )
    }

    fun save(style: GlyphStyle) = prefs.edit {
        putInt(FG, style.foreground)
        putBoolean(HAS_BG, style.background != null)
        style.background?.let { putInt(BG, it) }
        putFloat(THIN, style.thin)
    }

    private companion object {
        const val FG = "fg"
        const val BG = "bg"
        const val HAS_BG = "has_bg"
        // Nueva clave al pasar a Fairfax HD: el valor antiguo estaba en unidades de nasin-nanpa.
        const val THIN = "thin_fairfax"
    }
}
