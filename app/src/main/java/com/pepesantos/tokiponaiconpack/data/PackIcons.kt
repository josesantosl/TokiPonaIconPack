package com.pepesantos.tokiponaiconpack.data

import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.DrawableRes
import com.pepesantos.tokiponaiconpack.R
import org.xmlpull.v1.XmlPullParser

data class PackIcon(val name: String, @param:DrawableRes val resId: Int)
data class PackCategory(val title: String, val icons: List<PackIcon>)

/** Lee res/xml/drawable.xml (el mismo índice que usan los launchers). */
object PackIcons {

    @SuppressLint("DiscouragedApi") // los nombres vienen del XML generado; ver res/raw/keep.xml
    fun load(context: Context): List<PackCategory> {
        val res = context.resources
        val result = mutableListOf<PackCategory>()
        var title: String? = null
        var icons = mutableListOf<PackIcon>()
        fun flush() {
            if (title != null && icons.isNotEmpty()) result += PackCategory(title!!, icons)
            icons = mutableListOf()
        }
        res.getXml(R.xml.drawable).use { p ->
            while (p.next() != XmlPullParser.END_DOCUMENT) {
                if (p.eventType != XmlPullParser.START_TAG) continue
                when (p.name) {
                    "category" -> { flush(); title = localizedTitle(context, p.getAttributeValue(null, "title")) }
                    "item" -> {
                        val name = p.getAttributeValue(null, "drawable") ?: continue
                        val id = res.getIdentifier(name, "drawable", context.packageName)
                        if (id != 0) icons += PackIcon(name, id)
                    }
                }
            }
        }
        flush()
        return result
    }

    /**
     * Los títulos de drawable.xml van en inglés (los leen también los launchers); en la app se
     * traducen por su parte en toki pona. Los demás ("ilo …") son solo toki pona y se dejan.
     */
    private fun localizedTitle(context: Context, title: String?): String? {
        val res = when (title?.substringBefore(" (")?.trim()) {
            "nimi sina" -> R.string.category_saved
            "ilo" -> R.string.category_apps
            "nimi tu" -> R.string.category_compounds
            "nimi ale" -> R.string.category_all_words
            else -> return title
        }
        return context.getString(res)
    }

    /** Componentes ("paquete/Clase") que ya tienen icono en res/xml/appfilter.xml. */
    fun themedComponents(context: Context): Set<String> {
        val out = mutableSetOf<String>()
        context.resources.getXml(R.xml.appfilter).use { p ->
            while (p.next() != XmlPullParser.END_DOCUMENT) {
                if (p.eventType != XmlPullParser.START_TAG || p.name != "item") continue
                p.getAttributeValue(null, "component")
                    ?.removePrefix("ComponentInfo{")?.removeSuffix("}")
                    ?.let(out::add)
            }
        }
        return out
    }
}
