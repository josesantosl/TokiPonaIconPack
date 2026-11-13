package com.pepesantos.tokiponaiconpack.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Glifo que el usuario quiere para una app en el icon pack. [component] = "paquete/Clase completa". */
data class Assignment(val component: String, val label: String, val expr: String)

/** Asignaciones hechas en la app, pendientes de incluirse en el pack con tools/sync_from_phone.sh. */
class AssignmentsStore(context: Context) {

    private val prefs = context.getSharedPreferences("asignaciones", Context.MODE_PRIVATE)
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<Assignment>> = _items.asStateFlow()

    /** Una app solo puede tener un glifo: reemplaza la asignación anterior. */
    fun assign(a: Assignment) =
        persist(listOf(a) + _items.value.filterNot { it.component == a.component })

    fun remove(component: String) = persist(_items.value.filterNot { it.component == component })

    private fun load(): List<Assignment> {
        val arr = JSONArray(prefs.getString(KEY, "[]"))
        return (0 until arr.length()).map { i ->
            arr.getJSONObject(i).run {
                Assignment(getString("component"), getString("label"), getString("words"))
            }
        }
    }

    private fun persist(list: List<Assignment>) {
        _items.value = list
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("component", it.component).put("label", it.label).put("words", it.expr))
        }
        prefs.edit { putString(KEY, arr.toString()) }
    }

    private companion object {
        const val KEY = "list"
    }
}

/**
 * Archivo que lee `tools/generate_icons.py --from-device` (mismo formato que tools/apps.json,
 * más la lista de palabras guardadas). Se escribe en dos sitios:
 *  - filesDir: accesible con `adb shell run-as` en builds debug.
 *  - getExternalFilesDir: alternativa para builds release (Android/data/<paquete>/files).
 */
object DeviceExport {
    const val FILE_NAME = "tokipona_export.json"

    fun write(context: Context, assignments: List<Assignment>, saved: List<String>) {
        val icons = JSONArray()
        assignments.forEach {
            icons.put(
                JSONObject()
                    .put("label", it.label)
                    .put("words", it.expr)
                    .put("components", JSONArray().put(it.component))
            )
        }
        val json = JSONObject()
            .put("icons", icons)
            .put("saved", JSONArray(saved))
            .toString(2)
        File(context.filesDir, FILE_NAME).writeText(json)
        context.getExternalFilesDir(null)?.let { runCatching { File(it, FILE_NAME).writeText(json) } }
    }
}
