package ule.jescuj00.fridgey.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ule.jescuj00.fridgey.ui.theme.FridgeyShapes
import ule.jescuj00.fridgey.ui.theme.FridgeyTheme
import ule.jescuj00.fridgey.ui.theme.Ink
import ule.jescuj00.fridgey.ui.theme.InkSoft
import ule.jescuj00.fridgey.ui.theme.LocalFridgeySpacing
import ule.jescuj00.fridgey.ui.theme.Rust
import ule.jescuj00.fridgey.ui.theme.SurfaceWhite

/**
 * Highlighted alert card with a solid coloured bullet on the left,
 * title (+ optional subtitle) in the middle, and a chevron on the right.
 * Used in "Mis neveras" for cards like "2 productos caducan hoy en Casa".
 *
 * Background is rust-tinted (12% alpha) so the card pops against the cream
 * canvas without going as loud as the solid `Rust` colour.
 */
@Composable
fun AlertCard(
    bulletText: String,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    val spacing = LocalFridgeySpacing.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(FridgeyShapes.medium)
            .background(Rust.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Bullet
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Rust),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = bulletText,
                style = MaterialTheme.typography.titleMedium,
                color = SurfaceWhite,
            )
        }
        // Middle column
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = spacing.md),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Ink,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSoft,
                )
            }
        }
        // Chevron
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = InkSoft,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFBFAF6)
@Composable
private fun AlertCardPreview() {
    FridgeyTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            AlertCard(
                bulletText = "2",
                title = "2 productos caducan hoy en Casa",
                subtitle = "Yogur natural · Espinacas baby",
                onClick = {},
            )
        }
    }
}
