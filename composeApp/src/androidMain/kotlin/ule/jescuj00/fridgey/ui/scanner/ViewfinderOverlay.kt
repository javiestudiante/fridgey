package ule.jescuj00.fridgey.ui.scanner

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Full-screen darkened overlay with a transparent rounded-rectangle cutout
 * in the center, plus guidance text positioned above the cutout.
 *
 * The overlay is drawn into an offscreen layer
 * (`compositingStrategy = Offscreen`) so that `BlendMode.Clear` can punch
 * through it. Without the offscreen layer, `BlendMode.Clear` is a no-op
 * because the destination is the device's framebuffer (already opaque).
 */
@Composable
fun ViewfinderOverlay(
    modifier: Modifier = Modifier,
    guidanceText: String = "Coloca la fecha de caducidad dentro del recuadro",
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val cutoutWidthFraction = 0.8f
        val cutoutHeightFraction = 0.15f
        // Cutout is centered → its top edge sits at (1 - height) / 2 of the
        // available vertical space. Used both by the Canvas (to draw the
        // hole) and by the guidance Text (to anchor itself just above).
        val cutoutTopFraction = (1f - cutoutHeightFraction) / 2f
        val guidanceOffsetY = maxHeight * cutoutTopFraction - 40.dp

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        ) {
            val cutoutW = size.width * cutoutWidthFraction
            val cutoutH = size.height * cutoutHeightFraction
            val cutoutLeft = (size.width - cutoutW) / 2f
            val cutoutTop = (size.height - cutoutH) / 2f
            val corner = CornerRadius(12.dp.toPx())

            // 1. Dark fill across the whole screen.
            drawRect(color = Color.Black.copy(alpha = 0.6f))

            // 2. Punch the cutout hole. BlendMode.Clear writes (0,0,0,0)
            //    into the offscreen layer wherever the rect lies.
            drawRoundRect(
                color = Color.Transparent,
                topLeft = Offset(cutoutLeft, cutoutTop),
                size = Size(cutoutW, cutoutH),
                cornerRadius = corner,
                blendMode = BlendMode.Clear,
            )

            // 3. White stroke around the hole so the cutout is visually
            //    discoverable against any background.
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(cutoutLeft, cutoutTop),
                size = Size(cutoutW, cutoutH),
                cornerRadius = corner,
                style = Stroke(width = 2.dp.toPx()),
            )
        }

        Text(
            text = guidanceText,
            color = Color.White,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium.copy(
                shadow = Shadow(
                    color = Color.Black.copy(alpha = 0.7f),
                    offset = Offset(0f, 2f),
                    blurRadius = 4f,
                )
            ),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 32.dp)
                .fillMaxWidth()
                .padding(top = guidanceOffsetY),
        )
    }
}
