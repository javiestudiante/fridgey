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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ule.jescuj00.fridgey.ui.theme.Ink
import ule.jescuj00.fridgey.ui.theme.InkMuted
import ule.jescuj00.fridgey.ui.theme.InstrumentSerif

private val SectionTitleStyle = TextStyle(
    fontFamily = InstrumentSerif, fontWeight = FontWeight.Normal,
    fontSize = 20.sp, lineHeight = 24.sp, letterSpacing = (-0.2).sp,
)

/**
 * Inline section header: serif title (20) on the left, optional zero-padded
 * mono counter ("02", ink-3) on the right. [accentColor] tints the TITLE for
 * the urgency sections of the detail screen (rust "Caduca ya", amber "Esta
 * semana", ink "Más adelante"); the counter stays ink-3.
 *
 * Padding: 22 top / 22 horizontal / [bottomPadding] bottom (8 on the home
 * "Tus neveras" header, 10 on the detail urgency heads — per the design).
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
    accentColor: Color = Ink,
    bottomPadding: Dp = 10.dp,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = bottomPadding),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(text = title, style = SectionTitleStyle, color = accentColor)
        if (count != null) {
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "%02d".format(count),
                style = MaterialTheme.typography.labelSmall,  // JetBrains Mono 11
                color = InkMuted,
            )
        }
    }
}
