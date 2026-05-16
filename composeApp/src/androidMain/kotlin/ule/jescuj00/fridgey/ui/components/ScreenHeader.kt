package ule.jescuj00.fridgey.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import ule.jescuj00.fridgey.ui.theme.Ink
import ule.jescuj00.fridgey.ui.theme.LocalFridgeySpacing

/**
 * Top-of-screen block. Optional eyebrow line above a serif title.
 * Slots `leading` and `trailing` are nullable composables for back arrows,
 * notification bells, share icons, etc.
 *
 * Layout rules:
 *  - When `leading` or `trailing` is provided, the top row holds
 *    leading | eyebrow | spacer | trailing on one line, then the title
 *    below it.
 *  - When only `eyebrow` is provided, eyebrow stacks above the title.
 *  - When `eyebrow` is null and there is no leading/trailing, only the
 *    title is rendered.
 */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val spacing = LocalFridgeySpacing.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = spacing.lg,
                end = spacing.lg,
                top = spacing.xl,
                bottom = spacing.lg,
            ),
    ) {
        if (leading != null || trailing != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (leading != null) {
                    leading()
                    Spacer(modifier = Modifier.width(spacing.sm))
                }
                if (eyebrow != null) {
                    EyebrowLabel(text = eyebrow)
                }
                Spacer(modifier = Modifier.weight(1f))
                if (trailing != null) {
                    trailing()
                }
            }
            Spacer(modifier = Modifier.height(spacing.sm))
        } else if (eyebrow != null) {
            EyebrowLabel(text = eyebrow)
            Spacer(modifier = Modifier.height(spacing.sm))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.displayMedium,
            color = Ink,
        )
    }
}
