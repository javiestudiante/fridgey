package ule.jescuj00.fridgey

import androidx.compose.runtime.Composable
import ule.jescuj00.fridgey.ui.navigation.FridgeyNavigation
import ule.jescuj00.fridgey.ui.theme.FridgeyTheme

@Composable
fun App() {
    FridgeyTheme {
        FridgeyNavigation()
    }
}
