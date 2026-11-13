package com.pepesantos.tokiponaiconpack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import com.pepesantos.tokiponaiconpack.glyph.GlyphRenderer

/**
 * Dibuja la expresión con la fuente. [thin] adelgaza el trazo (unidades de fuente por lado);
 * [background] null = transparente.
 */
@Composable
fun GlyphPreview(
    renderer: GlyphRenderer,
    expr: String,
    foreground: Color,
    background: Color?,
    modifier: Modifier = Modifier,
    thin: Float = 0f,
) {
    Box(
        modifier
            .aspectRatio(1f)
            .then(if (background != null) Modifier.background(background) else Modifier)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                // Capa propia: el borrado (BlendMode.Clear) solo afecta al glifo.
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithCache {
                    val side = size.minDimension
                    val path = renderer.iconPath(expr, side)?.asComposePath()
                    val erode = if (thin > 0f) {
                        Stroke(
                            width = renderer.erodeWidth(side, thin),
                            join = StrokeJoin.Round,
                            cap = StrokeCap.Round,
                        )
                    } else null
                    onDrawBehind {
                        path?.let {
                            drawPath(it, foreground)
                            erode?.let { s -> drawPath(it, Color.Black, style = s, blendMode = BlendMode.Clear) }
                        }
                    }
                }
        )
    }
}
