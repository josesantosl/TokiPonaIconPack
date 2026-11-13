package com.pepesantos.tokiponaiconpack.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pepesantos.tokiponaiconpack.R
import com.pepesantos.tokiponaiconpack.data.LaunchableApp

/**
 * Lista de apps instaladas sin icono en el pack, con un glifo sugerido editable para cada una.
 */
@Composable
fun MissingAppsSheet(
    vm: EditorViewModel,
    missing: List<LaunchableApp>,
    foreground: Color,
    background: Color,
) {
    // Glifos que el usuario editó; las demás apps usan la sugerencia.
    val edited = remember { mutableStateMapOf<String, String>() }
    fun choiceFor(app: LaunchableApp) =
        edited[app.component.flattenToString()] ?: vm.suggestionFor(app)

    Column(
        Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.9f)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.missing_title), style = MaterialTheme.typography.titleMedium)
        if (missing.isEmpty()) {
            Text(
                stringResource(R.string.missing_none),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 24.dp),
            )
            return@Column
        }
        Text(
            stringResource(R.string.missing_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = { vm.assignAll(missing.map { it to choiceFor(it) }) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.assign_all, missing.size)) }

        LazyColumn {
            items(missing, key = { it.component.flattenToString() }) { app ->
                val key = app.component.flattenToString()
                val glyph = choiceFor(app)
                val valid = vm.renderer.isValid(glyph)
                Row(
                    Modifier.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Image(app.icon, contentDescription = null, modifier = Modifier.size(36.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            app.label,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        OutlinedTextField(
                            value = glyph,
                            onValueChange = { edited[key] = it },
                            singleLine = true,
                            isError = !valid,
                            textStyle = MaterialTheme.typography.bodyMedium,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.None,
                                autoCorrectEnabled = false,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    GlyphPreview(
                        renderer = vm.renderer,
                        expr = glyph,
                        foreground = foreground,
                        background = background,
                        thin = vm.thin,
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)),
                    )
                    TextButton(onClick = { vm.assign(app, glyph) }, enabled = valid) { Text("✓") }
                }
                HorizontalDivider()
            }
        }
    }
}
