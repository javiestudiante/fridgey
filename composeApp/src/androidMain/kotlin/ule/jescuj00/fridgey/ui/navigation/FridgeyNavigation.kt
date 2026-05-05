package ule.jescuj00.fridgey.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import ule.jescuj00.fridgey.domain.model.auth.AuthState
import ule.jescuj00.fridgey.domain.usecase.auth.ObserveAuthStateUseCase
import ule.jescuj00.fridgey.domain.usecase.auth.SignOutUseCase
import ule.jescuj00.fridgey.ui.screens.add_producto.AddProductoScreen
import ule.jescuj00.fridgey.ui.screens.create_nevera.CreateNeveraScreen
import ule.jescuj00.fridgey.ui.screens.login.LoginScreen
import ule.jescuj00.fridgey.ui.screens.nevera_detail.NeveraDetailScreen
import ule.jescuj00.fridgey.ui.screens.nevera_list.NeveraListScreen

@Composable
fun FridgeyNavigation() {
    val observeAuthStateUseCase: ObserveAuthStateUseCase = koinInject()
    val signOutUseCase: SignOutUseCase = koinInject()

    // Memoize the Flow so we don't rebuild it on every recomposition.
    // Without this, `collectAsState` re-subscribes on every tick and the
    // upstream emits a fresh initial value, causing a Loading↔Authenticated
    // recomposition loop (visible parpadeo).
    val authStateFlow = remember(observeAuthStateUseCase) { observeAuthStateUseCase() }
    val authState by authStateFlow.collectAsState(initial = AuthState.Loading)

    when (val state = authState) {
        AuthState.Loading -> SplashLoading()
        AuthState.Unauthenticated, is AuthState.Error -> {
            // Treat error states as unauthenticated — the LoginScreen will
            // surface a snackbar if the user retries and it fails again.
            UnauthenticatedGraph()
        }
        is AuthState.Authenticated -> {
            AuthenticatedGraph(
                currentUserId = state.user.uid,
                signOutUseCase = signOutUseCase
            )
        }
    }
}

@Composable
private fun SplashLoading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun UnauthenticatedGraph() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Screen.Login.route) {
        composable(Screen.Login.route) {
            // No explicit navigation on success — the auth state flow will
            // trigger a recomposition into AuthenticatedGraph automatically.
            LoginScreen(onSignedIn = { /* handled by auth state observer */ })
        }
    }
}

@Composable
private fun AuthenticatedGraph(
    currentUserId: String,
    signOutUseCase: SignOutUseCase
) {
    val navController = rememberNavController()
    val coroutineScope = rememberCoroutineScope()

    val onSignOut: () -> Unit = {
        coroutineScope.launch { signOutUseCase() }
    }

    NavHost(navController = navController, startDestination = Screen.NeveraList.route) {
        composable(Screen.NeveraList.route) {
            NeveraListScreen(
                currentUserId = currentUserId,
                onNavigateToCreate = { navController.navigate(Screen.CreateNevera.route) },
                onNavigateToNevera = { neveraId ->
                    navController.navigate(Screen.NeveraDetail.createRoute(neveraId))
                },
                onSignOut = onSignOut
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
