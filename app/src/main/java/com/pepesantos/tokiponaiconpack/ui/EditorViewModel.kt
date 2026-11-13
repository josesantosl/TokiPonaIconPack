package com.pepesantos.tokiponaiconpack.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import com.pepesantos.tokiponaiconpack.R
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pepesantos.tokiponaiconpack.data.Assignment
import com.pepesantos.tokiponaiconpack.data.AssignmentsStore
import com.pepesantos.tokiponaiconpack.data.DeviceExport
import com.pepesantos.tokiponaiconpack.data.LaunchableApp
import com.pepesantos.tokiponaiconpack.data.PackIcons
import com.pepesantos.tokiponaiconpack.glyph.GlyphSuggestions
import com.pepesantos.tokiponaiconpack.data.SavedWordsStore
import com.pepesantos.tokiponaiconpack.data.Shortcuts
import com.pepesantos.tokiponaiconpack.data.StyleStore
import com.pepesantos.tokiponaiconpack.glyph.GlyphRenderer
import com.pepesantos.tokiponaiconpack.glyph.GlyphStyle
import com.pepesantos.tokiponaiconpack.glyph.SitelenExpression
import com.pepesantos.tokiponaiconpack.glyph.SvgExport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Colores disponibles para el glifo y el fondo (null = transparente). */
data class Swatch(val name: String, val argb: Long?) {
    val colorInt: Int? get() = argb?.toInt()
}

val ForegroundSwatches = listOf(
    Swatch("walo", 0xFFFFFFFF),
    Swatch("pimeja", 0xFF1B1B1F),
    Swatch("laso", 0xFF12129A),
    Swatch("jelo", 0xFFFFF27A),
    Swatch("loje", 0xFFD8473C),
)

val BackgroundSwatches = listOf(
    Swatch("ala", null),
    Swatch("pimeja", 0xFF1B1B1F),
    Swatch("walo", 0xFFF6F1E7),
    Swatch("jelo", 0xFFFFF27A),
    Swatch("laso", 0xFF12129A),
)

enum class ExportFormat(val mime: String, val extension: String) {
    SVG("image/svg+xml", "svg"),
    PNG("image/png", "png"),
}

class EditorViewModel(app: Application) : AndroidViewModel(app) {

    val renderer = GlyphRenderer(app)
    private val words = SavedWordsStore(app)
    private val styles = StyleStore(app)
    private val assignmentsStore = AssignmentsStore(app)
    val saved: StateFlow<List<String>> = words.words
    val assignments: StateFlow<List<Assignment>> = assignmentsStore.items

    init {
        // Mantiene al día el archivo que lee tools/sync_from_phone.sh.
        viewModelScope.launch {
            combine(assignmentsStore.items, words.words, ::Pair).collect { (a, s) ->
                withContext(Dispatchers.IO) { runCatching { DeviceExport.write(app, a, s) } }
            }
        }
    }

    var expr by mutableStateOf("toki pona")
        private set

    private val initial = styles.load()
    var foreground by mutableStateOf(
        ForegroundSwatches.firstOrNull { it.colorInt == initial.foreground } ?: ForegroundSwatches.first()
    )
        private set
    var background by mutableStateOf(
        BackgroundSwatches.firstOrNull { it.colorInt == initial.background } ?: BackgroundSwatches[1]
    )
        private set
    /** Unidades de fuente que se quitan por lado (0 = grosor original de la fuente). */
    var thin by mutableStateOf(initial.thin.coerceIn(0f, GlyphStyle.MAX_THIN))
        private set

    val style: GlyphStyle
        get() = GlyphStyle(foreground.colorInt!!, background.colorInt, thin)

    val isValid: Boolean get() = renderer.isValid(expr)

    /** Apps instaladas para el selector de accesos directos (null = sin cargar). */
    var apps by mutableStateOf<List<LaunchableApp>?>(null)
        private set

    /** Componentes con icono en el appfilter.xml de este APK. */
    private var themed: Set<String> = emptySet()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    fun onExprChange(value: String) {
        expr = value
    }

    fun selectForeground(s: Swatch) { foreground = s; persistStyle() }
    fun selectBackground(s: Swatch) { background = s; persistStyle() }
    fun changeThin(t: Float) { thin = t }
    fun commitThin() = persistStyle()

    private fun persistStyle() = styles.save(style)

    fun save() {
        if (!isValid) return
        words.add(expr)
        _messages.trySend(str(R.string.msg_saved, expr.trim()))
    }

    fun load(saved: String) {
        expr = saved
    }

    fun delete(saved: String) = words.remove(saved)

    fun suggestedFileName(format: ExportFormat) =
        "${SitelenExpression.fileName(expr)}.${format.extension}"

    fun export(uri: Uri, format: ExportFormat) {
        val e = expr
        val st = style
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openOutputStream(uri)!!.use { out ->
                        when (format) {
                            ExportFormat.SVG -> {
                                val svg = SvgExport.toSvg(renderer, e, st) ?: error("inválido")
                                out.write(svg.toByteArray())
                            }
                            ExportFormat.PNG -> {
                                val bmp = renderer.renderBitmap(e, PNG_SIZE, st) ?: error("inválido")
                                bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                            }
                        }
                    }
                }.isSuccess
            }
            _messages.send(
                if (ok) str(R.string.msg_exported, format.extension.uppercase())
                else str(R.string.msg_export_failed)
            )
        }
    }

    // ---- Accesos directos ---------------------------------------------------

    fun canPinShortcuts() = Shortcuts.isPinSupported(getApplication())

    fun loadApps() {
        if (apps != null) return
        viewModelScope.launch {
            val (loaded, filter) = withContext(Dispatchers.IO) {
                Shortcuts.launchableApps(getApplication()) to PackIcons.themedComponents(getApplication())
            }
            themed = filter
            apps = loaded
        }
    }

    // ---- Apps sin icono -----------------------------------------------------

    /** Apps instaladas que no tienen icono en el pack ni una asignación pendiente. */
    fun missingApps(apps: List<LaunchableApp>, assignments: List<Assignment>): List<LaunchableApp> {
        val assigned = assignments.mapTo(HashSet()) { it.component }
        return apps.filter {
            val c = it.component.flattenToString()
            c !in themed && c !in assigned
        }
    }

    fun suggestionFor(app: LaunchableApp): String =
        GlyphSuggestions.suggest(app.label, app.component.packageName, app.category)
            .takeIf { renderer.isValid(it) } ?: GlyphSuggestions.FALLBACK

    fun assign(app: LaunchableApp, glyph: String) {
        if (!renderer.isValid(glyph)) return
        assignmentsStore.assign(Assignment(app.component.flattenToString(), app.label, glyph.trim()))
    }

    fun assignAll(choices: List<Pair<LaunchableApp, String>>) {
        val valid = choices.filter { (_, g) -> renderer.isValid(g) }
        valid.forEach { (app, g) -> assign(app, g) }
        _messages.trySend(
            getApplication<Application>().resources
                .getQuantityString(R.plurals.msg_assigned_many, valid.size, valid.size)
        )
    }

    // ---- Asignaciones para el icon pack --------------------------------------

    fun assignToApp(app: LaunchableApp) {
        if (!isValid) return
        assignmentsStore.assign(Assignment(app.component.flattenToString(), app.label, expr.trim()))
        _messages.trySend(str(R.string.msg_assigned, app.label, expr.trim()))
    }

    fun removeAssignment(a: Assignment) = assignmentsStore.remove(a.component)

    fun pinShortcut(app: LaunchableApp) {
        val ok = Shortcuts.requestPin(getApplication(), renderer, expr, style, app)
        _messages.trySend(
            if (ok) str(R.string.msg_pin_confirm, app.label, expr.trim())
            else str(R.string.msg_pin_unsupported)
        )
    }

    // Al final de la clase a propósito: las propiedades se inicializan en orden de declaración,
    // y loadApps() usa `apps` y `themed`, que tienen que existir ya.
    init {
        loadApps()
    }

    /** Texto traducido según el idioma actual de la app. */
    private fun str(@StringRes id: Int, vararg args: Any): String =
        getApplication<Application>().getString(id, *args)

    private companion object {
        const val PNG_SIZE = 512
    }
}
