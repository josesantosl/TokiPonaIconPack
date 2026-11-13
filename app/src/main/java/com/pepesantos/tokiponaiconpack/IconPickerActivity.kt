package com.pepesantos.tokiponaiconpack

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.pepesantos.tokiponaiconpack.ui.IconPickerScreen
import com.pepesantos.tokiponaiconpack.ui.theme.TokiPonaTheme

/**
 * Selector de iconos para launchers (protocolo ADW `org.adw.launcher.icons.ACTION_PICK_ICON`,
 * que usan Nova, ADW y otros al cambiar el icono de una app "desde otra app").
 * Devuelve el icono elegido como Bitmap en el extra "icon" y, si es del pack, también como
 * recurso para que el launcher lo pueda volver a cargar a mejor resolución.
 */
class IconPickerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            TokiPonaTheme {
                IconPickerScreen(
                    onPickBitmap = ::returnBitmap,
                    onPickResource = ::returnResource,
                    onCancel = ::finish,
                )
            }
        }
    }

    private fun returnBitmap(bitmap: Bitmap) {
        setResult(RESULT_OK, Intent().putExtra(EXTRA_ICON, bitmap))
        finish()
    }

    @Suppress("DEPRECATION") // ShortcutIconResource es lo que esperan los launchers
    private fun returnResource(@DrawableRes resId: Int) {
        val bitmap = ContextCompat.getDrawable(this, resId)!!.toBitmap(RESULT_PX, RESULT_PX)
        val data = Intent()
            .putExtra(EXTRA_ICON, bitmap)
            .putExtra(
                Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                Intent.ShortcutIconResource.fromContext(this, resId),
            )
        setResult(RESULT_OK, data)
        finish()
    }

    companion object {
        private const val EXTRA_ICON = "icon"
        /** Suficiente para un icono y por debajo del límite de tamaño de un Intent. */
        const val RESULT_PX = 192
    }
}
