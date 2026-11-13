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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pepesantos.tokiponaiconpack.IconPickerActivity
import com.pepesantos.tokiponaiconpack.data.PackIcons
import com.pepesantos.tokiponaiconpack.data.SavedWordsStore
import com.pepesantos.tokiponaiconpack.data.StyleStore
import com.pepesantos.tokiponaiconpack.glyph.GlyphRenderer

/** Fondo de las miniaturas del pack (los glifos del pack son blancos, sin fondo). */
private val PackTileBg = Color(0xFF3A4556)

@OptIn(ExperimentalMaterial3Api::class)
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
    val categories = remember { PackIcons.load(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Elige un icono") },
                actions = { TextButton(onClick = onCancel) { Text("Cancelar") } },
            )
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
            if (saved.isNotEmpty()) {
                header("nimi sina (guardadas en la app)", key = "saved")
                items(saved, key = { "saved:$it" }) { word ->
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
            // Claves por posición: los títulos de drawable.xml pueden repetirse.
            categories.forEachIndexed { i, cat ->
                header(cat.title, key = "cat:$i")
                itemsIndexed(cat.icons, key = { j, _ -> "cat:$i:$j" }) { _, icon ->
                    Tile(label = icon.name.replace("_en_", "+").replace('_', ' '), onClick = {
                        onPickResource(icon.resId)
                    }) {
                        Image(
                            painterResource(icon.resId),
                            contentDescription = icon.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(PackTileBg),
                        )
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.grid.LazyGridScope.header(title: String, key: String) {
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
