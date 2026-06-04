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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ule.jescuj00.fridgey.ui.theme.FridgeyShapes
import ule.jescuj00.fridgey.ui.theme.InkSoft
import ule.jescuj00.fridgey.ui.theme.Inter
import ule.jescuj00.fridgey.ui.theme.MintDarker
import ule.jescuj00.fridgey.ui.theme.Paper
import ule.jescuj00.fridgey.ui.theme.Rust
import ule.jescuj00.fridgey.ui.theme.RustSoft

private val TitleStyle = TextStyle(
    fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 17.sp,
)
private val SubtitleStyle = TextStyle(
    fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp,
)
private val BulletStyle = TextStyle(
    fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 14.sp,
)

/**
 * Cross-fridge "caducan hoy" banner. Rust-soft (#F2D6CE) card, radius 16,
 * with a solid 32×32 rust bullet (count in white), title + optional subtitle,
 * and a trailing chevron. Used at the top of "Mis neveras".
 */
@Composable
fun AlertCard(
    bulletText: String,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(FridgeyShapes.medium)
            .background(RustSoft)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(32.dp).clip(CircleShape).background(Rust),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = bulletText, style = BulletStyle, color = Paper)
        }
        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
        ) {
            Text(text = title, style = TitleStyle, color = MintDarker)
            if (subtitle != null) {
                Text(text = subtitle, style = SubtitleStyle, color = InkSoft)
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = InkSoft,
            modifier = Modifier.size(14.dp),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFBFAF6)
@Composable
private fun AlertCardPreview() {
    Box(modifier = Modifier.padding(16.dp)) {
        AlertCard(
            bulletText = "2",
            title = "2 productos caducan hoy en Casa",
            subtitle = "Yogur natural · Espinacas baby",
            onClick = {},
        )
    }
}
