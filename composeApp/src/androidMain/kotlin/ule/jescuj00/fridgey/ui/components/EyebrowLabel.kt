package ule.jescuj00.fridgey.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import ule.jescuj00.fridgey.ui.theme.InkMuted

/**
 * Uppercase mono label used for screen eyebrows ("HOY · 28 ABRIL"),
 * section eyebrows ("ESTA SEMANA"), category labels, and metric captions.
 *
 * Renders `text` verbatim — the caller decides on casing; the tracking and
 * mono family come from `MaterialTheme.typography.labelSmall` (defined in
 * `Type.kt`).
 */
@Composable
fun EyebrowLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = InkMuted,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = modifier,
    )
}
