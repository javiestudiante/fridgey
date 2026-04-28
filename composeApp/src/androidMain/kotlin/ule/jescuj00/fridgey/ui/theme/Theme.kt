package ule.jescuj00.fridgey.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = FreshGreen40,
    onPrimary = SurfaceLight,
    primaryContainer = FreshGreenContainerLight,
    onPrimaryContainer = FreshGreen40,
    secondary = WarnAmber40,
    onSecondary = SurfaceLight,
    secondaryContainer = WarnAmberContainerLight,
    onSecondaryContainer = WarnAmber40,
    error = ErrorRed40,
    onError = SurfaceLight,
    errorContainer = ErrorRedContainerLight,
    onErrorContainer = ErrorRed40,
    background = NeutralLight,
    onBackground = OnLight,
    surface = SurfaceLight,
    onSurface = OnLight
)

private val DarkColors = darkColorScheme(
    primary = FreshGreen80,
    onPrimary = FreshGreen40,
    primaryContainer = FreshGreenContainerDark,
    onPrimaryContainer = FreshGreen80,
    secondary = WarnAmber80,
    onSecondary = WarnAmber40,
    secondaryContainer = WarnAmberContainerDark,
    onSecondaryContainer = WarnAmber80,
    error = ErrorRed80,
    onError = ErrorRed40,
    errorContainer = ErrorRedContainerDark,
    onErrorContainer = ErrorRed80,
    background = NeutralDark,
    onBackground = OnDark,
    surface = SurfaceDark,
    onSurface = OnDark
)

@Composable
fun FridgeyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = FridgeyTypography,
        content = content
    )
}
