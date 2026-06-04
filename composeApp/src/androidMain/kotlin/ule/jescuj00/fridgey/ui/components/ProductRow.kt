package ule.jescuj00.fridgey.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import ule.jescuj00.fridgey.domain.model.Categoria
import ule.jescuj00.fridgey.ui.theme.Amber
import ule.jescuj00.fridgey.ui.theme.EmojiIconShape
import ule.jescuj00.fridgey.ui.theme.Ink
import ule.jescuj00.fridgey.ui.theme.InkMuted
import ule.jescuj00.fridgey.ui.theme.InstrumentSerif
import ule.jescuj00.fridgey.ui.theme.Inter
import ule.jescuj00.fridgey.ui.theme.MintDeep
import ule.jescuj00.fridgey.ui.theme.Rust
import ule.jescuj00.fridgey.ui.theme.Smoke

/**
 * Visual urgency bucket. **The cuts live ONLY in [expirationStateOf]** — the
 * single source of truth reused by (1) the detail section grouping, (2) the
 * ProductRow day-number colour, and (3) the progress bar. Do not re-derive
 * these thresholds anywhere else.
 */
enum class ExpirationState { BAD, WARN, FRESH }

/** Design cuts: <=1 día → BAD (rust), 2..7 → WARN (amber), >7 → FRESH (mint). */
fun expirationStateOf(diasRestantes: Int): ExpirationState = when {
    diasRestantes <= 1 -> ExpirationState.BAD
    diasRestantes <= 7 -> ExpirationState.WARN
    else -> ExpirationState.FRESH
}

/** Day-number / progress-bar colour for a bucket (fresh = mint-deep). */
fun ExpirationState.color(): Color = when (this) {
    ExpirationState.BAD -> Rust
    ExpirationState.WARN -> Amber
    ExpirationState.FRESH -> MintDeep
}

private val NameStyle = TextStyle(
    fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 15.sp, letterSpacing = (-0.1).sp,
)
private val SupportingStyle = TextStyle(
    fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp,
)
private val DaysStyle = TextStyle(
    fontFamily = InstrumentSerif, fontWeight = FontWeight.Normal, fontSize = 26.sp, letterSpacing = (-0.5).sp,
)
private val DaysLabelStyle = TextStyle(
    fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 0.5.sp,
)

/**
 * One product inside a "balda". Anatomy per Claude Design:
 *  - 44×44 emoji bubble (radius 14), background tinted per category.
 *  - name (sans 15) + supporting "Categoría · cantidad+unidad" (sans 12, ink-3).
 *  - big serif day count (26) + label (HOY / DÍA / DÍAS), coloured by urgency.
 *  - a 2-px progress bar pinned to the bottom whose width grows as expiry
 *    nears (see [progressFraction]).
 *
 * The urgency [state] is computed from [daysRemaining] via [expirationStateOf]
 * — callers don't pass it, keeping the cuts in one place.
 */
@Composable
fun ProductRow(
    categoria: Categoria,
    name: String,
    supporting: String,
    daysRemaining: Int,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val state = expirationStateOf(daysRemaining)
    val accent = state.color()
    val label = when {
        daysRemaining == 0 -> "HOY"
        daysRemaining == 1 || daysRemaining == -1 -> "DÍA"
        else -> "DÍAS"
    }

    val base = if (onClick != null) modifier.fillMaxWidth().clickable(onClick = onClick) else modifier.fillMaxWidth()

    Box(modifier = base) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Emoji bubble.
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(EmojiIconShape)
                    .background(categoryIconBg(categoria)),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = categoria.emoji, style = TextStyle(fontSize = 22.sp))
            }

            // Name + supporting.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(text = name, style = NameStyle, color = Ink)
                Spacer(Modifier.height(2.dp))
                Text(text = supporting, style = SupportingStyle, color = InkMuted)
            }

            // Days + label.
            Column(
                modifier = Modifier.widthIn(min = 64.dp),
                horizontalAlignment = Alignment.End,
            ) {
                if (daysRemaining != 0) {
                    Text(text = abs(daysRemaining).toString(), style = DaysStyle, color = accent)
                }
                Text(text = label, style = DaysLabelStyle, color = accent, textAlign = TextAlign.End)
            }
        }

        // Progress bar pinned to the bottom — wider as expiry nears.
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(progressFraction(daysRemaining))
                .height(2.dp)
                .background(accent),
        )
    }
}

/**
 * Per-category tint for the emoji bubble (design palette). Unlisted categories
 * fall back to smoke.
 */
private fun categoryIconBg(categoria: Categoria): Color = when (categoria) {
    Categoria.LACTEOS -> Color(0xFFEEF2F6)   // dairy
    Categoria.CARNES -> Color(0xFFF5E5E2)     // meat
    Categoria.VERDURAS -> Color(0xFFE5EFE0)   // veg
    Categoria.FRUTAS -> Color(0xFFF6EBDC)     // fruit
    Categoria.BEBIDAS -> Color(0xFFE8EFF1)    // drink
    else -> Smoke                              // other
}

/**
 * Inverse of days-remaining → bar width fraction. Closer to expiry ⇒ wider.
 * Linear over a 14-day horizon: día 0 (or expired) → full width; día 14 →
 * minimal sliver. Floored at 0.06 so even fresh items show a hint of bar.
 */
private fun progressFraction(diasRestantes: Int): Float {
    val horizon = 14f
    val clamped = diasRestantes.coerceIn(0, horizon.toInt())
    return (1f - clamped / horizon).coerceIn(0.06f, 1f)
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun ProductRowPreview() {
    Column {
        ProductRow(Categoria.VERDURAS, "Espinacas baby", "Verduras · 200 g", 0)
        ProductRow(Categoria.LACTEOS, "Yogur natural", "Lácteos · 4 uds", 3)
        ProductRow(Categoria.FRUTAS, "Manzanas", "Frutas · 6 uds", 12)
    }
}
