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
import androidx.compose.ui.unit.dp
import com.pepesantos.tokiponaiconpack.R

/** Una fuente usada por la app: texto, enlace y licencia (literal, de assets/licenses/). */
private data class FontCredit(val body: Int, val url: String, val urlLabel: String, val licenseAsset: String)

private val FONTS = listOf(
    // Todos los glifos: icon pack y editor (y los compuestos que se componen con ella).
    FontCredit(
        R.string.credits_body_fairfax,
        "https://www.kreativekorp.com/software/fonts/fairfaxhd/",
        "kreativekorp.com/software/fonts/fairfaxhd",
        "licenses/fairfax-hd.txt",
    ),
)

/** Créditos de las fuentes y sus licencias. */
@Composable
fun CreditsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val licenses = remember {
        FONTS.associateWith { f ->
            runCatching { context.assets.open(f.licenseAsset).bufferedReader().use { it.readText() } }
                .getOrDefault("")
        }
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
                FONTS.forEach { f ->
                    Text(stringResource(f.body), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { uriHandler.openUri(f.url) }) { Text(f.urlLabel) }
                }
                // Las licencias se muestran siempre literales, en inglés.
                Text(
                    stringResource(R.string.credits_license_note),
                    style = MaterialTheme.typography.bodySmall,
                )
                FONTS.forEach { f ->
                    Text(
                        licenses[f].orEmpty(),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("pona") } },
    )
}
