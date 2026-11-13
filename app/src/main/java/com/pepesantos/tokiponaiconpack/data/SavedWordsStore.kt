package com.pepesantos.tokiponaiconpack.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Palabras/compuestos creados por el usuario, en orden (la más reciente primero). */
class SavedWordsStore(context: Context) {

    private val prefs = context.getSharedPreferences("nimi_sina", Context.MODE_PRIVATE)
    private val _words = MutableStateFlow(load())
    val words: StateFlow<List<String>> = _words.asStateFlow()

    fun add(expr: String) {
        val e = expr.trim()
        if (e.isEmpty()) return
        persist(listOf(e) + _words.value.filterNot { it.equals(e, ignoreCase = true) })
    }

    fun remove(expr: String) = persist(_words.value - expr)

    private fun load(): List<String> =
        prefs.getString(KEY, null)?.split('\n')?.filter { it.isNotBlank() } ?: emptyList()

    private fun persist(list: List<String>) {
        _words.value = list
        prefs.edit { putString(KEY, list.joinToString("\n")) }
    }

    private companion object {
        const val KEY = "words"
    }
}
