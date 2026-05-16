package ule.jescuj00.fridgey.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import ule.jescuj00.fridgey.ui.theme.FridgeySectionCount
import ule.jescuj00.fridgey.ui.theme.Ink
import ule.jescuj00.fridgey.ui.theme.LocalFridgeySpacing

/**
 * Inline section header used inside a screen.
 * Serif title on the left, optional zero-padded counter ("02") on the right.
 * `accentColor` lets callers tint both title and counter for status
 * sections (rust for "Caduca ya", amber for "Esta semana", etc).
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
    accentColor: Color = Ink,
) {
    val spacing = LocalFridgeySpacing.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = spacing.lg,
                end = spacing.lg,
                top = spacing.xl,
                bottom = spacing.sm,
            ),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = accentColor,
        )
        if (count != null) {
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "%02d".format(count),
                style = FridgeySectionCount,
                color = accentColor,
            )
        }
    }
}
