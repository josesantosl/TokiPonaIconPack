package com.pepesantos.tokiponaiconpack.glyph

import android.graphics.Path
import android.graphics.PathIterator
import android.os.Build
import androidx.annotation.ColorInt
import androidx.annotation.RequiresApi
import java.util.Locale

object SvgExport {

    const val SIZE = 192

    fun toSvg(renderer: GlyphRenderer, expr: String, style: GlyphStyle): String? {
        val path = renderer.iconPath(expr, SIZE.toFloat()) ?: return null
        val fg = style.foreground
        val d = pathData(path)
        return buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine("<!-- ${expr.trim()} — sitelen pona, fuente nasin-nanpa de jan Itan (licencia MIT) -->")
            appendLine("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 $SIZE $SIZE" width="$SIZE" height="$SIZE">""")
            var mask = ""
            if (style.thin > 0f) {
                // Adelgazado: la máscara quita (negro) un trazo redondo sobre el contorno.
                val w = n(renderer.erodeWidth(SIZE.toFloat(), style.thin))
                appendLine("""  <defs><mask id="fino" maskUnits="userSpaceOnUse" x="0" y="0" width="$SIZE" height="$SIZE">""")
                appendLine("""    <path fill="#fff" d="$d"/>""")
                appendLine("""    <path fill="none" stroke="#000" stroke-width="$w" stroke-linejoin="round" stroke-linecap="round" d="$d"/>""")
                appendLine("""  </mask></defs>""")
                mask = """ mask="url(#fino)""""
            }
            style.background?.let {
                appendLine("""  <rect width="$SIZE" height="$SIZE" fill="${hex(it)}"${opacity(it, "fill-opacity")}/>""")
            }
            appendLine("""  <path fill="${hex(fg)}"${opacity(fg, "fill-opacity")}$mask d="$d"/>""")
            appendLine("</svg>")
        }
    }

    private fun pathData(path: Path): String =
        if (Build.VERSION.SDK_INT >= 34) exactPathData(path) else approximatePathData(path)

    /** Android 14+: curvas exactas. */
    @RequiresApi(34)
    private fun exactPathData(path: Path): String = buildString {
        val it = path.pathIterator
        val p = FloatArray(8)
        while (it.hasNext()) {
            when (it.next(p, 0)) {
                PathIterator.VERB_MOVE -> append("M${n(p[0])} ${n(p[1])}")
                PathIterator.VERB_LINE -> append("L${n(p[2])} ${n(p[3])}")
                PathIterator.VERB_QUAD, PathIterator.VERB_CONIC ->
                    append("Q${n(p[2])} ${n(p[3])} ${n(p[4])} ${n(p[5])}")
                PathIterator.VERB_CUBIC ->
                    append("C${n(p[2])} ${n(p[3])} ${n(p[4])} ${n(p[5])} ${n(p[6])} ${n(p[7])}")
                PathIterator.VERB_CLOSE -> append("Z")
                PathIterator.VERB_DONE -> break
            }
        }
    }

    /** Android 8–13: polilínea densa (error < 0.2 px en 192 px, invisible a simple vista). */
    private fun approximatePathData(path: Path): String = buildString {
        val a = path.approximate(0.2f)
        var prevFraction = -1f
        var i = 0
        while (i + 2 < a.size) {
            val f = a[i]
            // Dos puntos seguidos con la misma fracción = salto a un nuevo contorno.
            if (i == 0 || f == prevFraction) {
                if (i != 0) append("Z")
                append("M")
            } else {
                append("L")
            }
            append(n(a[i + 1])).append(' ').append(n(a[i + 2]))
            prevFraction = f
            i += 3
        }
        if (isNotEmpty()) append("Z")
    }

    private fun n(v: Float): String =
        String.format(Locale.US, "%.2f", v).trimEnd('0').trimEnd('.').let { if (it == "-0") "0" else it }

    private fun hex(@ColorInt c: Int) = String.format(Locale.US, "#%06X", c and 0xFFFFFF)

    private fun opacity(@ColorInt c: Int, attr: String): String {
        val a = (c ushr 24) and 0xFF
        return if (a == 0xFF) "" else """ $attr="${n(a / 255f)}""""
    }
}
