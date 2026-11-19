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
import kotlin.math.min

/**
 * Dibuja palabras de sitelen pona con la fuente Fairfax HD incluida en la app, componiendo
 * igual que tools/generate_icons.py:
 *
 *  - si la fuente trae el compuesto (palabra + U+F1996 escalar / U+F1995 apilar + palabra),
 *    se usa tal cual;
 *  - si no, la segunda palabra va en la caja que la fuente usa en ese contenedor (anidado) o
 *    las dos en las mitades de un apilado, escaladas sin deformar y con el trazo devuelto al
 *    grosor de la fuente ([SitelenLayout]).
 *
 * Las coordenadas internas son unidades de fuente (textSize = em) con la y hacia abajo.
 */
class GlyphRenderer(context: Context) {

    val layout = SitelenLayout.load(context)
    private val em = layout.em

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = ResourcesCompat.getFont(context, R.font.fairfax_pona_hd)
        textSize = em
    }

    /** true si la expresión es una o dos palabras que la fuente conoce. */
    fun isValid(expr: String): Boolean = SitelenExpression.parse(expr, layout) != null

    /**
     * Trazado del glifo dentro de un cuadrado de [size]×[size], centrado y con la caja em
     * ocupando [safeRatio] del lado (misma escala para todos: el trazo tiene el mismo grosor).
     */
    fun iconPath(expr: String, size: Float, safeRatio: Float = DEFAULT_SAFE_RATIO): Path? {
        val path = fontPath(expr) ?: return null
        val bounds = RectF().also { path.computeBounds(it, true) }
        if (bounds.isEmpty) return null
        val s = scale(size, safeRatio)
        path.transform(Matrix().apply {
            setScale(s, s)
            // Horizontal: centrado por la tinta. Vertical: centrado por la caja de los glifos.
            postTranslate(size / 2 - bounds.centerX() * s, size / 2 + layout.centerY * s)
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

    private fun scale(size: Float, safeRatio: Float) = size * safeRatio / em

    // -- composición ---------------------------------------------------------------------

    private fun fontPath(expr: String): Path? {
        val parsed = SitelenExpression.parse(expr, layout) ?: return null
        val chars = parsed.tokens.map { String(Character.toChars(layout.words.getValue(it))) }
        if (chars.size == 1) return textPath(chars[0])

        val (a, _) = parsed.tokens
        // ¿La fuente trae el compuesto? Entonces es un solo glifo, con el avance de uno.
        val seq = chars[0] + (if (parsed.nested) SCALE else STACK) + chars[1]
        if (textPaint.measureText(seq) < textPaint.measureText(chars[0]) * 1.5f) return textPath(seq)

        // Pedir anidar en una palabra sin hueco: se apila (como en el pack).
        val box = if (parsed.nested) layout.nest[a] else null
        return if (box != null) {
            textPath(chars[0]).apply { addPath(piece(chars[1], box)) }
        } else {
            piece(chars[0], layout.stackBottom).apply { addPath(piece(chars[1], layout.stackTop)) }
        }
    }

    /** Glifo(s) de [text] en la posición de la fuente (origen en la línea base, y hacia abajo). */
    private fun textPath(text: String) = Path().also { textPaint.getTextPath(text, 0, text.length, 0f, 0f, it) }

    /**
     * Glifo suelto escalado sin deformar dentro de [box] (unidades de fuente, y hacia arriba),
     * con el trazo devuelto al grosor de la fuente, como hace Fairfax en sus compuestos.
     */
    private fun piece(char: String, box: RectF): Path {
        val p = textPath(char)
        val g = RectF().also { p.computeBounds(it, true) }
        // La caja, con la y hacia abajo como el trazado del texto.
        val b = RectF(box.left, -box.bottom, box.right, -box.top)
        val k = min(b.width() / g.width(), b.height() / g.height())
        p.transform(Matrix().apply {
            setScale(k, k)
            postTranslate(b.centerX() - g.centerX() * k, b.centerY() - g.centerY() * k)
        })
        val grow = layout.stroke * (1 - k) / 2       // el trazo escalado mide stroke·k
        if (grow > 0f) {
            val edge = Path()
            Paint().apply {
                style = Paint.Style.STROKE
                strokeWidth = 2 * grow
                strokeJoin = Paint.Join.MITER
                strokeMiter = 4f
                strokeCap = Paint.Cap.BUTT
            }.getFillPath(p, edge)
            p.op(edge, Path.Op.UNION)
        }
        return p
    }

    companion object {
        /** Controles UCSUR para pedir a la fuente un compuesto apilado o escalado (anidado). */
        private const val STACK = "󱦕"   // U+F1995
        private const val SCALE = "󱦖"   // U+F1996
        /** Igual que SAFE / VIEWPORT en tools/generate_icons.py. */
        const val DEFAULT_SAFE_RATIO = 0.78f
        /** Para iconos adaptativos (108dp con zona segura de 66dp). */
        const val ADAPTIVE_SAFE_RATIO = 0.52f
    }
}
