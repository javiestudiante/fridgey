package ule.jescuj00.fridgey.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ule.jescuj00.fridgey.ui.theme.Hairline
import ule.jescuj00.fridgey.ui.theme.InkSoft
import ule.jescuj00.fridgey.ui.theme.MintDeep
import ule.jescuj00.fridgey.ui.theme.Paper
import ule.jescuj00.fridgey.ui.theme.PillShape
import ule.jescuj00.fridgey.ui.theme.SurfaceWhite

/**
 * Rounded category-filter chip. Selected: mint-deep fill / white text.
 * Unselected: paper fill / ink-2 text / 1-px hairline border (per design).
 * `count` is appended as ` · N` when not null (the active "Todo · 24").
 */
@Composable
fun FilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    count: Int? = null,
) {
    val bg = if (selected) MintDeep else Paper
    val fg = if (selected) SurfaceWhite else InkSoft
    val border = if (selected) Color.Transparent else Hairline
    val display = if (count != null) "$label · $count" else label

    Text(
        text = display,
        style = MaterialTheme.typography.bodySmall,  // sans 13
        color = fg,
        modifier = modifier
            .clip(PillShape)
            .background(bg)
            .border(1.dp, border, PillShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}
