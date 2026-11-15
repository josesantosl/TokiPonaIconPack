package com.pepesantos.tokiponaiconpack.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pepesantos.tokiponaiconpack.R
import com.pepesantos.tokiponaiconpack.data.LaunchableApp
import com.pepesantos.tokiponaiconpack.glyph.GlyphStyle
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(vm: EditorViewModel = viewModel()) {
    val saved by vm.saved.collectAsStateWithLifecycle()
    val assignments by vm.assignments.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    var pickerMode by rememberSaveable { mutableStateOf<PickerMode?>(null) }
    var showMissing by rememberSaveable { mutableStateOf(false) }
    var showCredits by rememberSaveable { mutableStateOf(false) }
    // En la versión de la tienda no hay asignaciones: no deben ocultar apps de la lista.
    val missing = vm.apps?.let { vm.missingApps(it, if (vm.devTools) assignments else emptyList()) }

    val exportSvg = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ExportFormat.SVG.mime)
    ) { uri -> uri?.let { vm.export(it, ExportFormat.SVG) } }
    val exportPng = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ExportFormat.PNG.mime)
    ) { uri -> uri?.let { vm.export(it, ExportFormat.PNG) } }

    val fg = vm.foreground.color()!!
    val bg = vm.background.color()
    val previewBg = bg ?: MaterialTheme.colorScheme.surfaceVariant

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("sitelen pona") })
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val dir = LocalLayoutDirection.current
        LazyVerticalGrid(
            columns = GridCells.Adaptive(96.dp),
            contentPadding = PaddingValues(
                start = padding.calculateStartPadding(dir) + 16.dp,
                end = padding.calculateEndPadding(dir) + 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        GlyphPreview(
                            renderer = vm.renderer,
                            expr = vm.expr,
                            foreground = fg,
                            background = previewBg,
                            thin = vm.thin,
                            modifier = Modifier
                                .widthIn(max = 240.dp)
                                .fillMaxWidth(0.7f)
                                .clip(RoundedCornerShape(24.dp)),
                        )
                    }

                    OutlinedTextField(
                        value = vm.expr,
                        onValueChange = vm::onExprChange,
                        label = { Text("nimi") },
                        singleLine = true,
                        isError = vm.expr.isNotBlank() && !vm.isValid,
                        supportingText = {
                            Text(
                                if (vm.expr.isNotBlank() && !vm.isValid) {
                                    stringResource(R.string.expr_invalid)
                                } else {
                                    "toki · ilo tenpo · poki+sewi1 · ilo^toki"
                                }
                            )
                        },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                            imeAction = ImeAction.Done,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    SwatchRow(stringResource(R.string.label_foreground), ForegroundSwatches, vm.foreground, vm::selectForeground)
                    SwatchRow(stringResource(R.string.label_background), BackgroundSwatches, vm.background, vm::selectBackground)

                    Column {
                        // El trazo de nasin-nanpa mide ~100 unidades; se quitan `thin` por cada lado.
                        val percent = (100 - 2 * vm.thin).roundToInt()
                        Text(
                            stringResource(
                                R.string.thickness,
                                if (vm.thin < 0.5f) stringResource(R.string.thickness_original) else "$percent%",
                            ),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        // Izquierda = fino, derecha = grosor original de la fuente.
                        Slider(
                            value = GlyphStyle.MAX_THIN - vm.thin,
                            onValueChange = { vm.changeThin(GlyphStyle.MAX_THIN - it) },
                            onValueChangeFinished = vm::commitThin,
                            valueRange = 0f..GlyphStyle.MAX_THIN,
                        )
                        Row {
                            Text(stringResource(R.string.thickness_thin), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                            Text(stringResource(R.string.thickness_original), style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(onClick = vm::save, enabled = vm.isValid) { Text(stringResource(R.string.save)) }
                        OutlinedButton(
                            onClick = { exportSvg.launch(vm.suggestedFileName(ExportFormat.SVG)) },
                            enabled = vm.isValid,
                        ) { Text(stringResource(R.string.export_svg)) }
                        OutlinedButton(
                            onClick = { exportPng.launch(vm.suggestedFileName(ExportFormat.PNG)) },
                            enabled = vm.isValid,
                        ) { Text(stringResource(R.string.export_png)) }
                        OutlinedButton(
                            onClick = { vm.loadApps(); pickerMode = PickerMode.SHORTCUT },
                            enabled = vm.isValid && vm.canPinShortcuts(),
                        ) { Text(stringResource(R.string.shortcut)) }
                        if (vm.devTools) {
                            OutlinedButton(
                                onClick = { vm.loadApps(); pickerMode = PickerMode.ASSIGN },
                                enabled = vm.isValid,
                            ) { Text(stringResource(R.string.assign_to_app)) }
                        }
                    }

                    if (missing != null && missing.isNotEmpty()) {
                        Card(
                            Modifier
                                .fillMaxWidth()
                                .clickable { showMissing = true },
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    pluralStringResource(R.plurals.missing_count, missing.size, missing.size),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    stringResource(R.string.missing_tap),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    Text(
                        if (saved.isEmpty()) stringResource(R.string.saved_empty) else "nimi sina",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }

            items(saved, key = { it }) { word ->
                SavedTile(vm, word, fg, previewBg)
            }

            if (vm.devTools && assignments.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(Modifier.padding(top = 8.dp)) {
                        Text(stringResource(R.string.assignments_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.assignments_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(assignments, key = { "a:" + it.component }, span = { GridItemSpan(maxLineSpan) }) { a ->
                    ListItem(
                        leadingContent = {
                            GlyphPreview(
                                renderer = vm.renderer,
                                expr = a.expr,
                                foreground = fg,
                                background = previewBg,
                                thin = vm.thin,
                                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)),
                            )
                        },
                        headlineContent = { Text(a.label) },
                        supportingContent = { Text(a.expr) },
                        trailingContent = {
                            TextButton(onClick = { vm.removeAssignment(a) }) { Text("×") }
                        },
                        modifier = Modifier.clickable { vm.load(a.expr) },
                    )
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.padding(top = 16.dp)) {
                    Text(
                        stringResource(R.string.how_to_apply),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.credits_link),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .clickable { showCredits = true },
                    )
                }
            }
        }
    }

    if (showCredits) {
        CreditsDialog(onDismiss = { showCredits = false })
    }

    if (showMissing && missing != null) {
        ModalBottomSheet(onDismissRequest = { showMissing = false }) {
            MissingAppsSheet(vm, missing, fg, previewBg, devTools = vm.devTools)
        }
    }

    pickerMode?.let { mode ->
        ModalBottomSheet(onDismissRequest = { pickerMode = null }) {
            AppPicker(
                apps = vm.apps,
                header = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        GlyphPreview(
                            renderer = vm.renderer,
                            expr = vm.expr,
                            foreground = fg,
                            background = previewBg,
                            thin = vm.thin,
                            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)),
                        )
                        Text(
                            when (mode) {
                                PickerMode.SHORTCUT -> stringResource(R.string.picker_question_shortcut, vm.expr.trim())
                                PickerMode.ASSIGN -> stringResource(R.string.picker_question_assign, vm.expr.trim())
                            },
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                },
                onPick = { app ->
                    pickerMode = null
                    when (mode) {
                        PickerMode.SHORTCUT -> vm.pinShortcut(app)
                        PickerMode.ASSIGN -> vm.assignToApp(app)
                    }
                },
            )
        }
    }
}

private enum class PickerMode { SHORTCUT, ASSIGN }

@Composable
private fun AppPicker(
    apps: List<LaunchableApp>?,
    header: @Composable () -> Unit,
    onPick: (LaunchableApp) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    Column(
        Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.85f)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        header()
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(R.string.search)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (apps == null) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val filtered = apps.filter { it.label.contains(query.trim(), ignoreCase = true) }
            LazyColumn {
                items(filtered, key = { it.component.flattenToString() }) { app ->
                    ListItem(
                        headlineContent = { Text(app.label) },
                        leadingContent = {
                            Image(app.icon, contentDescription = null, modifier = Modifier.size(40.dp))
                        },
                        modifier = Modifier.clickable { onPick(app) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SavedTile(vm: EditorViewModel, word: String, fg: Color, bg: Color) {
    Card(Modifier.clickable { vm.load(word) }) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            GlyphPreview(
                renderer = vm.renderer,
                expr = word,
                foreground = fg,
                background = bg,
                thin = vm.thin,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    word,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 8.dp).weight(1f),
                )
                TextButton(onClick = { vm.delete(word) }) { Text("×") }
            }
        }
    }
}

@Composable
private fun SwatchRow(
    title: String,
    swatches: List<Swatch>,
    selected: Swatch,
    onSelect: (Swatch) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            swatches.forEach { sw ->
                val isSelected = sw == selected
                val c = sw.color()
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(c ?: Color.Transparent)
                        .border(
                            BorderStroke(
                                if (isSelected) 3.dp else 1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline,
                            ),
                            CircleShape,
                        )
                        .clickable { onSelect(sw) },
                    contentAlignment = Alignment.Center,
                ) {
                    val mark = when {
                        c == null -> MaterialTheme.colorScheme.onSurfaceVariant
                        c.luminance() > 0.5f -> Color.Black
                        else -> Color.White
                    }
                    when {
                        isSelected -> Text("✓", color = mark, style = MaterialTheme.typography.titleMedium)
                        c == null -> Text("∅", color = mark)
                    }
                }
            }
        }
    }
}

internal fun Swatch.color(): Color? = argb?.let { Color(it) }
