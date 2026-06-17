package ule.jescuj00.fridgey

import androidx.compose.runtime.Composable
import ule.jescuj00.fridgey.ui.navigation.FridgeyNavigation
import ule.jescuj00.fridgey.ui.theme.FridgeyTheme

@Composable
fun App(
    // neveraId a abrir tras tocar una notificación de caducidad (null = ninguno).
    deepLinkNeveraId: String? = null,
    // Se invoca cuando la navegación ha consumido el deep-link (una sola vez).
    onDeepLinkConsumed: () -> Unit = {},
) {
    FridgeyTheme {
        FridgeyNavigation(
            deepLinkNeveraId = deepLinkNeveraId,
            onDeepLinkConsumed = onDeepLinkConsumed,
        )
    }
}
