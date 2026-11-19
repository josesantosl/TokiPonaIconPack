package com.pepesantos.tokiponaiconpack.glyph

import android.content.Context
import android.graphics.RectF
import org.json.JSONArray
import org.json.JSONObject

/**
 * Lo que tools/generate_icons.py aprende de Fairfax HD y deja en assets/sitelen_layout.json,
 * para que el editor componga exactamente como el icon pack.
 *
 * Las cajas están en unidades de fuente con la y hacia arriba, como en la fuente.
 */
class SitelenLayout(
    /** Unidades por em de la fuente. */
    val em: Float,
    /** Centro vertical de la caja de los glifos. */
    val centerY: Float,
    /** Grosor del trazo de la fuente. */
    val stroke: Float,
    /** Palabra -> código UCSUR. */
    val words: Map<String, Int>,
    /** Contenedor -> caja donde va la segunda palabra al anidar. */
    val nest: Map<String, RectF>,
    /** Palabras con hueco que solo anidan si se pide con '+'. */
    val neverNest: Set<String>,
    /** Cajas de un apilado: abajo la primera palabra, arriba la segunda. */
    val stackBottom: RectF,
    val stackTop: RectF,
) {
    /** Palabras que anidan automáticamente (las que tienen hueco). */
    val nestHeads: Set<String> get() = nest.keys - neverNest

    companion object {
        private const val ASSET = "sitelen_layout.json"

        fun load(context: Context): SitelenLayout {
            val json = JSONObject(context.assets.open(ASSET).bufferedReader().use { it.readText() })
            val words = json.getJSONObject("words").let { o ->
                o.keys().asSequence().associateWith { o.getString(it).toInt(16) }
            }
            val nest = json.getJSONObject("nest").let { o ->
                o.keys().asSequence().associateWith { box(o.getJSONArray(it)) }
            }
            val neverNest = json.optJSONArray("neverNest")
                ?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
                .orEmpty()
            val stack = json.getJSONArray("stack")
            return SitelenLayout(
                em = json.getDouble("em").toFloat(),
                centerY = json.getDouble("centerY").toFloat(),
                stroke = json.getDouble("stroke").toFloat(),
                words = words,
                nest = nest,
                neverNest = neverNest,
                stackBottom = box(stack.getJSONArray(0)),
                stackTop = box(stack.getJSONArray(1)),
            )
        }

        /** [x0, y0, x1, y1] (y hacia arriba) -> RectF(left, top, right, bottom) con la misma y. */
        private fun box(a: JSONArray) = RectF(
            a.getDouble(0).toFloat(), a.getDouble(1).toFloat(),
            a.getDouble(2).toFloat(), a.getDouble(3).toFloat(),
        )
    }
}
