package ule.jescuj00.fridgey

import androidx.compose.runtime.Composable
import ule.jescuj00.fridgey.ui.navigation.FridgeyNavigation
import ule.jescuj00.fridgey.ui.theme.FridgeyTheme

/** Hardcoded development user — replaced once real auth lands. */
const val DEV_USER_ID = "test_user_001"

@Composable
fun App() {
    FridgeyTheme {
        FridgeyNavigation(currentUserId = DEV_USER_ID)
    }
}
