package com.pepesantos.tokiponaiconpack.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import com.pepesantos.tokiponaiconpack.glyph.GlyphRenderer
import com.pepesantos.tokiponaiconpack.glyph.GlyphStyle
import com.pepesantos.tokiponaiconpack.glyph.SitelenExpression

data class LaunchableApp(
    val label: String,
    val component: ComponentName,
    val icon: ImageBitmap,
    /** ApplicationInfo.CATEGORY_* que declara la app (o CATEGORY_UNDEFINED). */
    val category: Int,
)

object Shortcuts {

    /** Apps con icono en el cajón de apps (necesita <queries> en el manifiesto). Llamar fuera del hilo principal. */
    fun launchableApps(context: Context): List<LaunchableApp> {
        val pm = context.packageManager
        val iconPx = (48 * context.resources.displayMetrics.density).toInt()
        return pm.queryIntentActivities(launcherIntent(), 0)
            .filter { it.activityInfo.packageName != context.packageName }
            .map {
                LaunchableApp(
                    label = it.loadLabel(pm).toString(),
                    component = ComponentName(it.activityInfo.packageName, it.activityInfo.name),
                    icon = it.loadIcon(pm).toBitmap(iconPx, iconPx).asImageBitmap(),
                    category = it.activityInfo.applicationInfo.category,
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    fun isPinSupported(context: Context) = ShortcutManagerCompat.isRequestPinShortcutSupported(context)

    /**
     * Pide al launcher anclar un acceso directo que abre [app] con el glifo [expr].
     * Con fondo se usa un icono adaptativo (el launcher aplica su forma); sin fondo, el glifo suelto.
     */
    fun requestPin(
        context: Context,
        renderer: GlyphRenderer,
        expr: String,
        style: GlyphStyle,
        app: LaunchableApp,
    ): Boolean {
        val icon = if (style.background != null) {
            val bmp = renderer.renderBitmap(expr, ADAPTIVE_PX, style, GlyphRenderer.ADAPTIVE_SAFE_RATIO)
            bmp?.let { IconCompat.createWithAdaptiveBitmap(it) }
        } else {
            renderer.renderBitmap(expr, LEGACY_PX, style)?.let { IconCompat.createWithBitmap(it) }
        } ?: return false

        val launch = launcherIntent()
            .setComponent(app.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        val id = "sitelen_${SitelenExpression.fileName(expr)}_${app.component.flattenToShortString()}"
        val info = ShortcutInfoCompat.Builder(context, id)
            .setShortLabel(app.label)
            .setIcon(icon)
            .setIntent(launch)
            .build()
        return ShortcutManagerCompat.requestPinShortcut(context, info, null)
    }

    private fun launcherIntent() =
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

    private const val ADAPTIVE_PX = 432 // 108dp a xxxhdpi
    private const val LEGACY_PX = 192
}
