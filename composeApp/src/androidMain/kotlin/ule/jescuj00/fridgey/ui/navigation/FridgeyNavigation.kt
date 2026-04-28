package ule.jescuj00.fridgey.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ule.jescuj00.fridgey.ui.screens.add_producto.AddProductoScreen
import ule.jescuj00.fridgey.ui.screens.create_nevera.CreateNeveraScreen
import ule.jescuj00.fridgey.ui.screens.nevera_detail.NeveraDetailScreen
import ule.jescuj00.fridgey.ui.screens.nevera_list.NeveraListScreen

@Composable
fun FridgeyNavigation(currentUserId: String) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Screen.NeveraList.route) {
        composable(Screen.NeveraList.route) {
            NeveraListScreen(
                currentUserId = currentUserId,
                onNavigateToCreate = { navController.navigate(Screen.CreateNevera.route) },
                onNavigateToNevera = { neveraId ->
                    navController.navigate(Screen.NeveraDetail.createRoute(neveraId))
                }
            )
        }

        composable(Screen.CreateNevera.route) {
            CreateNeveraScreen(
                currentUserId = currentUserId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.NeveraDetail.route,
            arguments = listOf(
                navArgument(Screen.NeveraDetail.ARG_NEVERA_ID) { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val neveraId = requireNotNull(
                backStackEntry.arguments?.getString(Screen.NeveraDetail.ARG_NEVERA_ID)
            )
            NeveraDetailScreen(
                neveraId = neveraId,
                currentUserId = currentUserId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAddProducto = {
                    navController.navigate(Screen.AddProducto.createRoute(neveraId))
                }
            )
        }

        composable(
            route = Screen.AddProducto.route,
            arguments = listOf(
                navArgument(Screen.AddProducto.ARG_NEVERA_ID) { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val neveraId = requireNotNull(
                backStackEntry.arguments?.getString(Screen.AddProducto.ARG_NEVERA_ID)
            )
            AddProductoScreen(
                neveraId = neveraId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
