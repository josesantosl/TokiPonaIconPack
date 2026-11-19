package com.pepesantos.tokiponaiconpack.glyph

/**
 * Lo que escribe el usuario, con las mismas reglas que tools/generate_icons.py:
 *
 *  - `toki`          una palabra
 *  - `ilo tenpo`     automático: anidado si la primera palabra tiene hueco
 *                    ([SitelenLayout.nestHeads]), si no apilado (la primera abajo)
 *  - `ilo^tenpo`     fuerza apilado
 *  - `poki+sewi`     fuerza anidado (la segunda palabra pequeña dentro de la primera)
 */
object SitelenExpression {

    /** Expresión ya interpretada: una o dos palabras conocidas y si se pide anidar. */
    data class Parsed(val tokens: List<String>, val nested: Boolean)

    /** null si la expresión no es válida (vacía, más de dos palabras o palabras desconocidas). */
    fun parse(expr: String, layout: SitelenLayout): Parsed? {
        val e = expr.trim().lowercase()
        if (e.isEmpty()) return null
        val nested: Boolean
        val tokens: List<String>
        when {
            '+' in e -> { nested = true; tokens = e.split('+') }
            '^' in e -> { nested = false; tokens = e.split('^') }
            else -> {
                tokens = e.split(Regex("\\s+"))
                nested = tokens.size > 1 && tokens[0] in layout.nestHeads
            }
        }
        val clean = tokens.map { it.trim() }
        if (clean.size !in 1..2 || clean.any { it !in layout.words }) return null
        return Parsed(clean, nested)
    }

    /** Nombre de archivo seguro: "toki pona" -> "toki_pona". */
    fun fileName(expr: String): String =
        expr.trim().lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifEmpty { "sitelen" }
}
