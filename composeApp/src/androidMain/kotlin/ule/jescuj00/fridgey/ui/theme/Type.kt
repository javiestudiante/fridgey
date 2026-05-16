package ule.jescuj00.fridgey.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import ule.jescuj00.fridgey.R

/**
 * Instrument Serif is static (no variable font exists for this family):
 * declare each style explicitly with regular + italic.
 */
val InstrumentSerif = FontFamily(
    Font(R.font.instrumentserif_regular, FontWeight.Normal, FontStyle.Normal),
    Font(R.font.instrumentserif_italic,  FontWeight.Normal, FontStyle.Italic),
)

/**
 * Inter and JetBrains Mono are OpenType Variable Fonts: a single file
 * covers the entire weight axis. Compose interpolates the requested
 * `FontWeight` against the variable font at render time. Declaring
 * multiple `Font(...)` entries with different weights pointing to the
 * same variable file would be redundant and confusing.
 */
val Inter = FontFamily(
    Font(R.font.inter_variablefont_opszwght, FontWeight.Normal),
)

val JetBrainsMono = FontFamily(
    Font(R.font.jetbrainsmono_variablefont_wght, FontWeight.Normal),
)

/**
 * Material 3 Typography roles mapped to the Fridgey scale.
 * Weights are resolved against the variable font for Inter / Mono.
 */
val FridgeyTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = InstrumentSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 56.sp,
        lineHeight = 60.sp,
        letterSpacing = (-0.5).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = InstrumentSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 36.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.25).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = InstrumentSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 34.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = InstrumentSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = InstrumentSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 16.sp,
    ),
    // labelSmall — uppercase mono labels ("HOY · 28 ABRIL", "ESTA SEMANA")
    labelSmall = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.76.sp,
    ),
)

// --- Extra serif styles outside the M3 Typography role set ------------------
// Material 3's `Typography` only exposes the roles bundled with the design
// system; the editorial-kitchen palette needs a couple of Instrument Serif
// sizes that don't map onto any standard M3 role:
//
//  - `FridgeyNumericLarge` — big serif number used by `NeveraCard` metrics
//    and `ProductRow` days-remaining. M3's `displayMedium` is 36 (too big)
//    and `headlineLarge` is 28 (too small). 32 is the design spec value.
//  - `FridgeySectionCount` — small serif counter trailing a `SectionHeader`
//    title ("Tus neveras 02"). Same family as the headline but smaller so
//    it reads as a subordinate count, not a sibling title.

/** Big serif number used by NeveraCard metrics + ProductRow days. */
val FridgeyNumericLarge = TextStyle(
    fontFamily = InstrumentSerif,
    fontWeight = FontWeight.Normal,
    fontSize = 32.sp,
    lineHeight = 36.sp,
)

/** Small serif counter trailing a SectionHeader title ("Tus neveras 02"). */
val FridgeySectionCount = TextStyle(
    fontFamily = InstrumentSerif,
    fontWeight = FontWeight.Normal,
    fontSize = 14.sp,
    lineHeight = 18.sp,
)
