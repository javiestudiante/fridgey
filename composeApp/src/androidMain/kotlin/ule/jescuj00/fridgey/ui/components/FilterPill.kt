package ule.jescuj00.fridgey.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import ule.jescuj00.fridgey.ui.theme.LocalFridgeySpacing
import ule.jescuj00.fridgey.ui.theme.MintDeep
import ule.jescuj00.fridgey.ui.theme.MintSoft
import ule.jescuj00.fridgey.ui.theme.PillShape
import ule.jescuj00.fridgey.ui.theme.SurfaceWhite

/**
 * Rounded pill used in the horizontal filter rail ("Todo · 24", "Lácteos").
 * Selected: mint-deep fill / white text. Unselected: mint-soft fill /
 * mint-deep text. `count` is appended as ` · N` when not null.
 */
@Composable
fun FilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    count: Int? = null,
) {
    val spacing = LocalFridgeySpacing.current
    val bg = if (selected) MintDeep else MintSoft
    val fg = if (selected) SurfaceWhite else MintDeep
    val display = if (count != null) "$label · $count" else label

    Text(
        text = display,
        style = MaterialTheme.typography.labelMedium,
        color = fg,
        modifier = modifier
            .clip(PillShape)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = spacing.md, vertical = spacing.sm),
    )
}
