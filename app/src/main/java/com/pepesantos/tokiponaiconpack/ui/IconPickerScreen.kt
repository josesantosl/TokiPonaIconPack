package com.pepesantos.tokiponaiconpack.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pepesantos.tokiponaiconpack.IconPickerActivity
import com.pepesantos.tokiponaiconpack.R
import com.pepesantos.tokiponaiconpack.data.PackCategory
import com.pepesantos.tokiponaiconpack.data.PackIcon
import com.pepesantos.tokiponaiconpack.data.PackIcons
import com.pepesantos.tokiponaiconpack.data.SavedWordsStore
import com.pepesantos.tokiponaiconpack.data.StyleStore
import com.pepesantos.tokiponaiconpack.glyph.GlyphRenderer

/** Fondo de las miniaturas del pack (los glifos del pack son blancos, sin fondo). */
private val PackTileBg = Color(0xFF3A4556)

/** Selector para launchers: devuelve el icono elegido (del pack o una palabra guardada). */
@Composable
fun IconPickerScreen(
    onPickBitmap: (Bitmap) -> Unit,
    onPickResource: (Int) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val renderer = remember { GlyphRenderer(context) }
    val style = remember { StyleStore(context).load() }
    val wordsStore = remember { SavedWordsStore(context) }
    val saved by wordsStore.words.collectAsStateWithLifecycle()
    val savedHeader = stringResource(R.string.saved_in_app_header)

    PackIconGrid(
        title = stringResource(R.string.icon_picker_title),
        closeLabel = stringResource(R.string.cancel),
        onClose = onCancel,
        onIconClick = { onPickResource(it.resId) },
    ) { query, _ ->
        val shown = saved.filter { matches(it, query) }
        if (shown.isNotEmpty()) {
            header(savedHeader, key = "saved")
            items(shown, key = { "saved:$it" }) { word ->
                Tile(label = word, onClick = {
                    renderer.renderBitmap(word, IconPickerActivity.RESULT_PX, style)?.let(onPickBitmap)
                }) {
                    GlyphPreview(
                        renderer = renderer,
                        expr = word,
                        foreground = Color(style.foreground),
                        background = style.background?.let { Color(it) } ?: PackTileBg,
                        thin = style.thin,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)),
                    )
                }
            }
        }
    }
}

/** Galería de solo lectura con todos los iconos del pack; tocar uno lo muestra en grande. */
@Composable
fun IconGalleryScreen(onClose: () -> Unit) {
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val categories = remember { PackIcons.load(context) }
    val byName = remember(categories) { categories.flatMap { it.icons }.associateBy { it.name } }

    PackIconGrid(
        title = stringResource(R.string.gallery_title),
        closeLabel = stringResource(R.string.close),
        onClose = onClose,
        onIconClick = { selected = it.name },
        categories = categories,
    ) { _, found ->
        item(span = { GridItemSpan(maxLineSpan) }, key = "count") {
            Text(
                if (found == 0) stringResource(R.string.no_results)
                else pluralStringResource(R.plurals.gallery_count, found, found),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    selected?.let { byName[it] }?.let { icon ->
        AlertDialog(
            onDismissRequest = { selected = null },
            confirmButton = { TextButton(onClick = { selected = null }) { Text(stringResource(R.string.close)) } },
            title = { Text(icon.label) },
            text = {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    PackIconImage(icon, Modifier.widthIn(max = 200.dp).fillMaxWidth(), corner = 28)
                }
            },
        )
    }
}

/** Búsqueda por subcadena, sin distinguir mayúsculas; vacía = todo. */
private fun matches(text: String, query: String): Boolean =
    query.isBlank() || text.contains(query.trim(), ignoreCase = true)

private val VariantPart = Regex("v\\d+")

/** Cuántas palabras forman el icono (`toki` = 1, `ilo_en_toki` = 2); `_v1` es variante, no palabra. */
private val PackIcon.parts: Int
    get() = label.split(' ', '+').count { it.isNotEmpty() && !VariantPart.matches(it) }

/**
 * Resultados de búsqueda sin categorías ni repetidos (un icono puede estar en varias):
 * primero los simples y luego las combinaciones; a igual tamaño, coincidencia exacta,
 * luego los que empiezan por la búsqueda y luego el resto, en el orden del pack.
 */
private fun search(all: List<PackCategory>, query: String): List<PackIcon> {
    val q = query.trim().lowercase()
    fun closeness(icon: PackIcon) = when {
        icon.label == q || icon.name == q -> 0
        icon.label.startsWith(q) || icon.name.startsWith(q) -> 1
        else -> 2
    }
    return all.asSequence()
        .flatMap { it.icons }
        .filter { matches(it.label, q) || matches(it.name, q) }
        .distinctBy { it.name }
        .sortedWith(compareBy<PackIcon> { it.parts }.thenBy { closeness(it) })
        .toList()
}

/**
 * Rejilla común con barra de búsqueda: cabecera opcional (`leading`, recibe la búsqueda y
 * cuántos iconos del pack coinciden) y luego los iconos del pack por categoría, o los
 * resultados ordenados si se está buscando.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PackIconGrid(
    title: String,
    closeLabel: String,
    onClose: () -> Unit,
    onIconClick: (PackIcon) -> Unit,
    categories: List<PackCategory>? = null,
    leading: LazyGridScope.(query: String, found: Int) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val all = categories ?: remember { PackIcons.load(context) }
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(all, query) { if (query.isBlank()) null else search(all, query) }
    val total = remember(all) { all.sumOf { it.icons.size } }
    val found = results?.size ?: total

    Scaffold(
        topBar = {
            // Fondo opaco: si no, la rejilla se ve a través del campo de búsqueda al desplazarla.
            Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
                TopAppBar(
                    title = { Text(title) },
                    actions = { TextButton(onClick = onClose) { Text(closeLabel) } },
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.search)) },
                    singleLine = true,
                    trailingIcon = if (query.isNotEmpty()) {
                        { TextButton(onClick = { query = "" }) { Text("×") } }
                    } else null,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Search,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 4.dp),
                )
            }
        },
    ) { padding ->
        val dir = LocalLayoutDirection.current
        LazyVerticalGrid(
            columns = GridCells.Adaptive(72.dp),
            contentPadding = PaddingValues(
                start = padding.calculateStartPadding(dir) + 12.dp,
                end = padding.calculateEndPadding(dir) + 12.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            leading(query, found)
            if (results != null) {
                items(results, key = { "search:${it.name}" }) { icon ->
                    Tile(label = icon.label, onClick = { onIconClick(icon) }) {
                        PackIconImage(icon, Modifier.fillMaxWidth())
                    }
                }
            } else {
                // Claves por posición: los títulos de drawable.xml pueden repetirse.
                all.forEachIndexed { i, cat ->
                    header(cat.title, key = "cat:$i")
                    itemsIndexed(cat.icons, key = { j, _ -> "cat:$i:$j" }) { _, icon ->
                        Tile(label = icon.label, onClick = { onIconClick(icon) }) {
                            PackIconImage(icon, Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}

/** Nombre legible a partir del drawable (`ilo_en_toki` → "ilo+toki"). */
private val PackIcon.label: String get() = name.replace("_en_", "+").replace('_', ' ')

@Composable
private fun PackIconImage(icon: PackIcon, modifier: Modifier, corner: Int = 14) {
    Image(
        painterResource(icon.resId),
        contentDescription = icon.label,
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(corner.dp))
            .background(PackTileBg),
    )
}

private fun LazyGridScope.header(title: String, key: String) {
    item(span = { GridItemSpan(maxLineSpan) }, key = "header:$key") {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        )
    }
}

@Composable
private fun Tile(label: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        content()
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
