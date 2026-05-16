package ule.jescuj00.fridgey.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import ule.jescuj00.fridgey.ui.theme.LocalFridgeySpacing
import ule.jescuj00.fridgey.ui.theme.MintDeep
import ule.jescuj00.fridgey.ui.theme.MintSoft
import ule.jescuj00.fridgey.ui.theme.PillShape
import ule.jescuj00.fridgey.ui.theme.SurfaceWhite

/**
 * One segment in a [SegmentedToggle]. `iconRes` is optional — when set, an
 * Icon is rendered before the label inside the segment.
 */
data class SegmentOption(
    val label: String,
    @DrawableRes val iconRes: Int? = null,
)

/**
 * Pill-shaped multi-segment toggle. Generalised over `options.size`,
 * though Fridgey only uses 2 segments today (Escanear / A mano).
 *
 * The selected segment's background and content colours animate to/from
 * mint-deep over 200 ms; unselected segments stay transparent on the
 * outer mint-soft track.
 */
@Composable
fun SegmentedToggle(
    options: List<SegmentOption>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalFridgeySpacing.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(PillShape)
            .background(MintSoft)
            .padding(spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        options.forEachIndexed { index, option ->
            Segment(
                option = option,
                isSelected = index == selectedIndex,
                onClick = { onSelect(index) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun Segment(
    option: SegmentOption,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalFridgeySpacing.current
    val bg by animateColorAsState(
        targetValue = if (isSelected) MintDeep else Color.Transparent,
        animationSpec = tween(durationMillis = 200),
        label = "segment-bg",
    )
    val fg by animateColorAsState(
        targetValue = if (isSelected) SurfaceWhite else MintDeep,
        animationSpec = tween(durationMillis = 200),
        label = "segment-fg",
    )
    Row(
        modifier = modifier
            .clip(PillShape)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = spacing.md),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (option.iconRes != null) {
            Icon(
                painter = painterResource(option.iconRes),
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = option.label,
            style = MaterialTheme.typography.labelMedium,
            color = fg,
        )
    }
}
