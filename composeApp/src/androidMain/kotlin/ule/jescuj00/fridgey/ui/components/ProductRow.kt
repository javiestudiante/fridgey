package ule.jescuj00.fridgey.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import ule.jescuj00.fridgey.ui.theme.Amber
import ule.jescuj00.fridgey.ui.theme.FridgeyNumericLarge
import ule.jescuj00.fridgey.ui.theme.FridgeyTheme
import ule.jescuj00.fridgey.ui.theme.InkMuted
import ule.jescuj00.fridgey.ui.theme.InkSoft
import ule.jescuj00.fridgey.ui.theme.LocalFridgeySpacing
import ule.jescuj00.fridgey.ui.theme.MintSoft
import ule.jescuj00.fridgey.ui.theme.Rust

/**
 * Visual urgency bucket for a product's days-remaining value. The mapping
 * to colour is centralised in `stateColor()` so callers don't reach for raw
 * tokens. Caller decides which bucket applies — the row does NOT compute
 * it from `daysRemaining` directly, because the bucket boundaries differ
 * across product categories / configurations.
 */
enum class ExpirationState { EXPIRED, CRITICAL, WARNING, OK }

/**
 * One product in the "Detalle de nevera" list.
 *
 * Layout: emoji bubble · name + supporting · big serif day count + label,
 * with a coloured 1-dp progress line beneath. The progress line is purely
 * cosmetic — it is a tinted divider, NOT a real progress bar.
 *
 * Day display rule (caller-friendly): the number rendered is
 * `abs(daysRemaining)`. The state colour communicates expired-vs-fresh,
 * so a leading minus sign would be visual noise.
 */
@Composable
fun ProductRow(
    emoji: String,
    name: String,
    supporting: String,
    daysRemaining: Int,
    state: ExpirationState,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val spacing = LocalFridgeySpacing.current
    val stateColor = stateColor(state)
    val label = when {
        daysRemaining == 0 -> "HOY"
        daysRemaining == 1 || daysRemaining == -1 -> "DÍA"
        else -> "DÍAS"
    }

    val rowModifier = if (onClick != null) {
        modifier.fillMaxWidth().clickable(onClick = onClick)
    } else {
        modifier.fillMaxWidth()
    }

    Column(modifier = rowModifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.lg, vertical = spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Emoji bubble
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MintSoft),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = emoji, style = MaterialTheme.typography.titleMedium)
            }

            // Name + supporting
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                Text(text = name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSoft,
                )
            }

            // Days + label
            Column(horizontalAlignment = Alignment.End) {
                if (daysRemaining != 0) {
                    Text(
                        text = abs(daysRemaining).toString(),
                        style = FridgeyNumericLarge,
                        color = stateColor,
                    )
                }
                EyebrowLabel(text = label, color = stateColor)
            }
        }

        // Coloured progress line under the row
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.lg)
                .height(1.dp)
                .background(stateColor.copy(alpha = 0.4f)),
        )
    }
}

private fun stateColor(state: ExpirationState): Color = when (state) {
    ExpirationState.EXPIRED, ExpirationState.CRITICAL -> Rust
    ExpirationState.WARNING -> Amber
    ExpirationState.OK -> InkMuted
}

@Preview(showBackground = true, backgroundColor = 0xFFFBFAF6)
@Composable
private fun ProductRowPreview() {
    FridgeyTheme {
        Column {
            ProductRow(
                emoji = "🥬",
                name = "Espinacas baby",
                supporting = "Vegetales · 200 g",
                daysRemaining = 0,
                state = ExpirationState.CRITICAL,
                onClick = {},
            )
            ProductRow(
                emoji = "🥛",
                name = "Yogur natural",
                supporting = "Lácteos · 4 uds",
                daysRemaining = 3,
                state = ExpirationState.WARNING,
                onClick = {},
            )
            ProductRow(
                emoji = "🍎",
                name = "Manzana",
                supporting = "Frutas · 6 uds",
                daysRemaining = 12,
                state = ExpirationState.OK,
                onClick = null,
            )
        }
    }
}
