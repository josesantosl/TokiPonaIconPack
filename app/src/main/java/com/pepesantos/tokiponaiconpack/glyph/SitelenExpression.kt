package com.pepesantos.tokiponaiconpack.glyph

/**
 * Traduce lo que escribe el usuario al texto ASCII que nasin-nanpa compone con sus ligaduras.
 * Mismas reglas que tools/generate_icons.py:
 *
 *  - `toki`          una palabra
 *  - `sewi:1`/`sewi1` variante 1
 *  - `ilo tenpo`     automático: anidado si la primera palabra tiene hueco ([NEST_HEADS]),
 *                    si no apilado (la primera palabra abajo, la segunda arriba)
 *  - `ilo^tenpo`     fuerza apilado
 *  - `poki+sewi`     fuerza anidado (la segunda palabra pequeña dentro de la primera)
 */
object SitelenExpression {

    /**
     * Palabras con hueco en nasin-nanpa 4.0.2 (la fuente trae "<palabra>Tok_joinScaleTok").
     * El script las calcula de la fuente; si actualizas la fuente, revisa esta lista.
     */
    val NEST_HEADS = setOf(
        "akesi", "anpa", "ante", "awen", "epiku", "ijo", "jan", "jo", "kala", "kili", "kiwen",
        "ko", "kon", "kulupu", "lawa", "leko", "len", "lipu", "luka", "lupa", "mama", "mani",
        "meli", "mije", "monsi", "mu", "mun", "musi", "namako", "nanpa", "nasa", "nena", "nimi",
        "noka", "open", "pilin", "poka", "poki", "sama", "selo", "sike", "sin", "sinpin",
        "sitelen", "soko", "sona", "soweli", "suli", "suno", "telo", "toki", "tomo", "tonsi",
        "tu", "uta", "utala", "walo", "weka", "wile",
    )

    private val TOKEN = Regex("[a-z]+[0-9]?")

    /** Devuelve p. ej. "toki+pona" o "ilo-tenpo", o null si la expresión no es válida. */
    fun toFontText(expr: String): String? {
        val e = expr.trim().lowercase()
        if (e.isEmpty()) return null
        val nested: Boolean
        val tokens: List<String>
        when {
            '+' in e -> { nested = true; tokens = e.split('+') }
            '^' in e -> { nested = false; tokens = e.split('^') }
            else -> {
                tokens = e.split(Regex("\\s+"))
                nested = tokens.size > 1 && tokens[0].trimEnd(':', *DIGITS) in NEST_HEADS
            }
        }
        val clean = tokens.map { it.trim().replace(":", "") }
        if (clean.size !in 1..2 || clean.any { !TOKEN.matches(it) }) return null
        return clean.joinToString(if (nested) "+" else "-")
    }

    /** Nombre de archivo seguro: "toki pona" -> "toki_pona". */
    fun fileName(expr: String): String =
        expr.trim().lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifEmpty { "sitelen" }

    private val DIGITS = "0123456789".toCharArray()
}
