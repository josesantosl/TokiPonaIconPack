package com.pepesantos.tokiponaiconpack.glyph

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import androidx.core.content.res.ResourcesCompat
import com.pepesantos.tokiponaiconpack.R
import kotlin.math.abs

/**
 * Dibuja palabras de sitelen pona con la fuente nasin-nanpa incluida en la app.
 *
 * El texto pasa por el motor de texto de Android (HarfBuzz), así que los compuestos se
 * componen con las mismas reglas GSUB/GPOS de la fuente que usa el script del pack.
 */
class GlyphRenderer(context: Context) {

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = ResourcesCompat.getFont(context, R.font.nasin_nanpa)
        textSize = EM
    }

    /** true si la fuente compone la expresión como un único glifo de sitelen pona. */
    fun isValid(expr: String): Boolean = fontPath(expr) != null

    /**
     * Trazado del glifo dentro de un cuadrado de [size]×[size], centrado y con el glifo
     * ocupando [safeRatio] del lado (misma escala para todos: el trazo tiene el mismo grosor).
     */
    fun iconPath(expr: String, size: Float, safeRatio: Float = DEFAULT_SAFE_RATIO): Path? {
        val path = fontPath(expr) ?: return null
        val bounds = RectF().also { path.computeBounds(it, true) }
        if (bounds.isEmpty) return null
        val s = scale(size, safeRatio)
        path.transform(Matrix().apply {
            setScale(s, s)
            // Horizontal: centrado por la tinta. Vertical: centrado por la caja em de la fuente.
            postTranslate(size / 2 - bounds.centerX() * s, size / 2 + EM_CENTER_Y * s)
        })
        return path
    }

    /**
     * Ancho (en px del lienzo) del trazo que se borra sobre el contorno para adelgazar el glifo
     * [thin] unidades de fuente por cada lado. El trazo va centrado en el contorno: el doble.
     */
    fun erodeWidth(size: Float, thin: Float, safeRatio: Float = DEFAULT_SAFE_RATIO): Float =
        2f * thin * scale(size, safeRatio)

    fun renderBitmap(
        expr: String,
        sizePx: Int,
        style: GlyphStyle,
        safeRatio: Float = DEFAULT_SAFE_RATIO,
    ): Bitmap? {
        val size = sizePx.toFloat()
        val path = iconPath(expr, size, safeRatio) ?: return null
        return Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).also { bmp ->
            val canvas = Canvas(bmp)
            style.background?.let { canvas.drawColor(it) }
            // Capa aparte: el borrado del contorno solo afecta al glifo, no al fondo.
            val layer = canvas.saveLayer(null, null)
            canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = style.foreground })
            if (style.thin > 0f) {
                canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.style = Paint.Style.STROKE
                    strokeWidth = erodeWidth(size, style.thin, safeRatio)
                    strokeJoin = Paint.Join.ROUND
                    strokeCap = Paint.Cap.ROUND
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                })
            }
            canvas.restoreToCount(layer)
        }
    }

    private fun scale(size: Float, safeRatio: Float) = size * safeRatio / EM

    private fun fontPath(expr: String): Path? {
        val text = SitelenExpression.toFontText(expr) ?: return null
        // Un glifo (simple o compuesto) avanza exactamente 1 em. Si la fuente no reconoce
        // la palabra, se dibujarían letras latinas con otro avance.
        if (abs(textPaint.measureText(text) - EM) > 1f) return null
        return Path().also { textPaint.getTextPath(text, 0, text.length, 0f, 0f, it) }
    }

    companion object {
        /** textSize = unitsPerEm de nasin-nanpa, así 1 px = 1 unidad de fuente. */
        private const val EM = 1000f
        /** Centro vertical de la caja em (y de -50 a 850 en la fuente). */
        private const val EM_CENTER_Y = 400f
        const val DEFAULT_SAFE_RATIO = 0.78f
        /** Para iconos adaptativos (108dp con zona segura de 66dp). */
        const val ADAPTIVE_SAFE_RATIO = 0.52f
    }
}
