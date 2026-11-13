package com.pepesantos.tokiponaiconpack.glyph

import android.content.pm.ApplicationInfo

/**
 * Propone un glifo para una app sin icono en el pack.
 *  1. Palabras clave en el nombre de la app o en su paquete (español, inglés, italiano…).
 *  2. Si no hay coincidencia, la categoría que declara la app en Android.
 *  3. Si tampoco, *ijo* («cosa»).
 */
object GlyphSuggestions {

    const val FALLBACK = "ijo"

    /** Reglas en orden de prioridad: la primera que coincide gana. */
    private val RULES: List<Pair<List<String>, String>> = listOf(
        listOf("launcher") to "tomo ilo",
        listOf("keyboard", "teclado", "tastiera") to "ilo nimi",
        listOf("camera", "camara", "cam", "fotocamera") to "ilo sitelen",
        listOf("gallery", "galeria", "galleria", "photos", "photo", "foto", "fotos") to "sitelen",
        listOf("podcast", "podcasts", "antennapod") to "kute",
        listOf("music", "musica", "radio", "spotify", "player") to "kalama musi",
        listOf("video", "tv", "movie", "movies", "stream", "streaming", "twitch", "film") to "sitelen tawa",
        listOf("mail", "email", "correo", "posta", "gmail", "inbox") to "lipu",
        listOf("messenger", "message", "messages", "messaging", "chat", "sms", "mensajes") to "toki",
        listOf("dialer", "phone", "telefono", "call", "calls") to "ilo toki",
        listOf("contacts", "contactos", "contatti", "people") to "jan",
        listOf("bank", "banco", "banca", "banking", "bancaria") to "tomo mani",
        listOf("wallet", "pay", "payment", "payments", "money", "finance", "crypto", "cartera") to "mani",
        listOf("shop", "shopping", "store", "tienda", "market", "mercado", "negozio", "buy") to "esun",
        listOf("maps", "map", "mapa", "mappe", "gps", "navigation", "navi") to "ma",
        listOf("weather", "clima", "meteo", "forecast") to "suno",
        listOf("clock", "reloj", "orologio", "alarm", "alarma", "timer") to "ilo tenpo",
        listOf("calendar", "calendario", "agenda") to "lipu tenpo",
        listOf("todo", "tasks", "tareas", "checklist") to "lipu pali",
        listOf("notes", "note", "notas", "keep", "memo", "notebook") to "lipu lili",
        listOf("calculator", "calculadora", "calcolatrice", "calc") to "nanpa",
        listOf("browser", "navegador", "web", "internet") to "linluwi",
        listOf("drive", "cloud", "nube") to "poki sewi1",
        listOf("files", "file", "archivos", "explorer", "filemanager") to "poki",
        listOf("translate", "translator", "traductor", "traduttore") to "toki ante",
        listOf("dictionary", "diccionario", "dizionario") to "lipu nimi",
        listOf("learn", "learning", "school", "university", "edu", "course", "academy") to "kama sona",
        listOf("book", "books", "reader", "libro", "libros", "ebook", "kindle") to "lipu sona",
        listOf("news", "noticias", "notizie") to "lipu sin",
        listOf("health", "fit", "fitness", "salud", "salute", "sport", "workout", "run") to "sijelo",
        listOf("food", "recipe", "recipes", "comida", "delivery", "restaurant", "cibo") to "moku",
        listOf("travel", "train", "trains", "bus", "transit", "flight", "flights", "viaje", "taxi") to "tomo tawa",
        listOf("password", "passwords", "pass", "authenticator", "auth", "vpn", "2fa", "otp") to "nimi len",
        listOf("security", "safety", "secure", "antivirus") to "awen",
        listOf("settings", "ajustes", "impostazioni", "config") to "ilo",
        listOf("terminal", "ssh", "shell", "termux", "console") to "ilo nanpa",
        listOf("github", "gitlab", "git", "code", "dev") to "pali kulupu",
        listOf("social", "community", "forum") to "kulupu",
        listOf("game", "games", "juego", "juegos", "gioco") to "musi",
        listOf("smarthome", "smart", "home", "house", "casa") to "tomo",
        listOf("print", "printer", "impresora") to "ilo lipu",
        listOf("scan", "scanner", "escaner") to "ilo lukin",
        listOf("tuner", "guitar", "afinador", "metronome") to "ilo kalama",
        listOf("pets", "dog", "mascota", "zooplus") to "soweli",
        listOf("energy", "electricity", "power", "energia") to "wawa",
    )

    fun suggest(label: String, packageName: String, category: Int): String {
        val tokens = tokenize(label) + tokenize(packageName)
        RULES.firstOrNull { (keywords, _) -> keywords.any { kw -> tokens.any { matches(it, kw) } } }
            ?.let { return it.second }
        return when (category) {
            ApplicationInfo.CATEGORY_GAME -> "musi"
            ApplicationInfo.CATEGORY_AUDIO -> "kalama"
            ApplicationInfo.CATEGORY_VIDEO -> "sitelen tawa"
            ApplicationInfo.CATEGORY_IMAGE -> "sitelen"
            ApplicationInfo.CATEGORY_SOCIAL -> "kulupu"
            ApplicationInfo.CATEGORY_NEWS -> "lipu sin"
            ApplicationInfo.CATEGORY_MAPS -> "ma"
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> "pali"
            ApplicationInfo.CATEGORY_ACCESSIBILITY -> "ilo"
            else -> FALLBACK
        }
    }

    // Partes del paquete que no dicen nada de la app.
    private val NOISE = setOf("com", "org", "net", "io", "app", "apps", "android", "mobile", "de", "it", "fr", "uk", "co", "me", "www")

    private fun tokenize(s: String): List<String> =
        s.lowercase()
            .replace(Regex("[áàä]"), "a").replace(Regex("[éèë]"), "e")
            .replace(Regex("[íìï]"), "i").replace(Regex("[óòö]"), "o").replace(Regex("[úùü]"), "u")
            .split(Regex("[^a-z0-9]+"))
            .filter { it.isNotEmpty() && it !in NOISE }

    /** Palabras clave cortas deben coincidir al inicio del token (evita "tv" dentro de otra palabra). */
    private fun matches(token: String, keyword: String): Boolean =
        if (keyword.length >= 5) token.contains(keyword) else token == keyword || token.startsWith(keyword)
}
