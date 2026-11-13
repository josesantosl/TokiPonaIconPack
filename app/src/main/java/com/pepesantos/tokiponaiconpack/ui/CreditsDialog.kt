package com.pepesantos.tokiponaiconpack.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import com.pepesantos.tokiponaiconpack.R
import androidx.compose.ui.unit.dp

private const val FONT_REPO = "https://github.com/ETBCOR/nasin-nanpa"

/** Créditos de la fuente y su licencia (texto literal de assets/licenses/nasin-nanpa.txt). */
@Composable
fun CreditsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val license = remember {
        runCatching {
            context.assets.open("licenses/nasin-nanpa.txt").bufferedReader().use { it.readText() }
        }.getOrDefault("")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.credits_title)) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.credits_body), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { uriHandler.openUri(FONT_REPO) }) {
                    Text("github.com/ETBCOR/nasin-nanpa")
                }
                // La licencia de jan Itan se muestra siempre literal, en inglés.
                Text(
                    stringResource(R.string.credits_license_note),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    license,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("pona") } },
    )
}
