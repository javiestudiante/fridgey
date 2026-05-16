package ule.jescuj00.fridgey.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val FridgeyLightColors = lightColorScheme(
    primary = Mint,
    onPrimary = SurfaceWhite,
    primaryContainer = MintSoft,
    onPrimaryContainer = MintDeep,

    secondary = MintDeep,
    onSecondary = SurfaceWhite,
    secondaryContainer = MintSoft,
    onSecondaryContainer = MintDeep,

    tertiary = Amber,
    onTertiary = SurfaceWhite,

    error = Rust,
    onError = SurfaceWhite,
    errorContainer = Color(0xFFFAE5E2),
    onErrorContainer = Rust,

    background = Cream,
    onBackground = Ink,

    surface = Cream,
    onSurface = Ink,
    surfaceVariant = Smoke,
    onSurfaceVariant = InkSoft,

    outline = Color(0x14000000),
    outlineVariant = Color(0x0A000000),
)

/**
 * Single-light-scheme editorial-kitchen theme. Dark mode was removed
 * intentionally in this sprint: the design language is anchored to the
 * cream canvas + ink ink combination, and the dark palette has not
 * been authored yet. Dark support can be reintroduced in a later
 * sprint once dark equivalents are designed; until then we render
 * light regardless of the system setting.
 *
 * Spacing tokens live in `LocalFridgeySpacing` as a CompositionLocal so
 * that callers read them via `LocalFridgeySpacing.current.lg` rather
 * than importing a global, matching the pattern used by Material 3's
 * `LocalContentColor`.
 */
@Composable
fun FridgeyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FridgeyLightColors,
        typography = FridgeyTypography,
        shapes = FridgeyShapes,
        content = {
            CompositionLocalProvider(
                LocalFridgeySpacing provides FridgeySpacing(),
                content = content,
            )
        }
    )
}
