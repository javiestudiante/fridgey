package ule.jescuj00.fridgey.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.datetime.LocalDate
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import ule.jescuj00.fridgey.domain.model.ProductAutoFill
import ule.jescuj00.fridgey.domain.model.auth.AuthState
import ule.jescuj00.fridgey.domain.usecase.auth.ObserveAuthStateUseCase
import ule.jescuj00.fridgey.domain.usecase.auth.SignOutUseCase
import ule.jescuj00.fridgey.ui.scanner.DateScannerScreen
import ule.jescuj00.fridgey.ui.screens.add_producto.AddProductoScreen
import ule.jescuj00.fridgey.ui.screens.add_producto.AddProductoViewModel
import ule.jescuj00.fridgey.ui.screens.ajustes.AjustesScreen
import ule.jescuj00.fridgey.ui.screens.create_nevera.CreateNeveraScreen
import ule.jescuj00.fridgey.ui.screens.invitar.InvitarScreen
import ule.jescuj00.fridgey.ui.screens.login.LoginScreen
import ule.jescuj00.fridgey.ui.screens.nevera_detail.NeveraDetailScreen
import ule.jescuj00.fridgey.ui.screens.nevera_list.NeveraListScreen
import ule.jescuj00.fridgey.ui.screens.unirse.UnirseScreen

@Composable
fun FridgeyNavigation(
    deepLinkNeveraId: String? = null,
    onDeepLinkConsumed: () -> Unit = {},
) {
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
                signOutUseCase = signOutUseCase,
                // El deep-link sólo se resuelve autenticado: si el tap llega sin
                // sesión, queda pendiente hasta que se entra en este grafo.
                deepLinkNeveraId = deepLinkNeveraId,
                onDeepLinkConsumed = onDeepLinkConsumed,
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
    signOutUseCase: SignOutUseCase,
    deepLinkNeveraId: String? = null,
    onDeepLinkConsumed: () -> Unit = {},
) {
    val navController = rememberNavController()
    val coroutineScope = rememberCoroutineScope()

    val onSignOut: () -> Unit = {
        coroutineScope.launch { signOutUseCase() }
    }

    // Deep-link de notificación: navega UNA vez al detalle de la nevera y avisa
    // al Activity para que limpie el pendiente (rotar/recomponer no re-navega
    // porque la key vuelve a null tras consumirlo).
    LaunchedEffect(deepLinkNeveraId) {
        val neveraId = deepLinkNeveraId ?: return@LaunchedEffect
        navController.navigate(Screen.NeveraDetail.createRoute(neveraId))
        onDeepLinkConsumed()
    }

    NavHost(navController = navController, startDestination = Screen.NeveraList.route) {
        composable(Screen.NeveraList.route) {
            NeveraListScreen(
                currentUserId = currentUserId,
                onNavigateToCreate = { navController.navigate(Screen.CreateNevera.route) },
                onNavigateToNevera = { neveraId ->
                    navController.navigate(Screen.NeveraDetail.createRoute(neveraId))
                },
                onNavigateToUnirse = { navController.navigate(Screen.Unirse.route) },
                onNavigateToAjustes = { navController.navigate(Screen.Ajustes.route) },
                onSignOut = onSignOut
            )
        }

        composable(Screen.Ajustes.route) {
            AjustesScreen(
                onNavigateBack = { navController.popBackStack() },
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
                },
                onNavigateToScan = {
                    // "Escanear" del empty state: apila AddProducto y el
                    // escáner encima, de modo que el handoff del resultado vía
                    // previousBackStackEntry.savedStateHandle aterriza en
                    // AddProducto exactamente igual que en el flujo normal.
                    navController.navigate(Screen.AddProducto.createRoute(neveraId))
                    navController.navigate(Screen.DateScanner.route)
                },
                onNavigateToInvitar = {
                    navController.navigate(Screen.Invitar.createRoute(neveraId))
                }
            )
        }

        composable(
            route = Screen.Invitar.route,
            arguments = listOf(
                navArgument(Screen.Invitar.ARG_NEVERA_ID) { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val neveraId = requireNotNull(
                backStackEntry.arguments?.getString(Screen.Invitar.ARG_NEVERA_ID)
            )
            InvitarScreen(
                neveraId = neveraId,
                currentUserId = currentUserId,
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(Screen.Unirse.route) {
            UnirseScreen(
                currentUserId = currentUserId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToNevera = { neveraId ->
                    // Reemplaza el flujo de unión en el back stack: volver desde
                    // el detalle debe aterrizar en "Mis neveras", no en el form.
                    navController.navigate(Screen.NeveraDetail.createRoute(neveraId)) {
                        popUpTo(Screen.NeveraList.route)
                    }
                },
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

            // Resolve the VM at the NavHost level (still scoped per
            // NavBackStackEntry by Koin). This lets the LaunchedEffect below
            // call onScannedDateReceived directly, instead of plumbing the
            // date through the screen as another parameter.
            val viewModel: AddProductoViewModel = koinViewModel()

            // Receive ISO date string from the scanner via savedStateHandle.
            val savedStateHandle = backStackEntry.savedStateHandle
            val scannedDate by savedStateHandle
                .getStateFlow<String?>(SCANNED_DATE_KEY, null)
                .collectAsState()
            // Receive the Open Food Facts autofill (JSON) from the CÓDIGO phase.
            val scannedAutoFill by savedStateHandle
                .getStateFlow<String?>(SCANNED_AUTOFILL_KEY, null)
                .collectAsState()

            LaunchedEffect(scannedAutoFill) {
                val json = scannedAutoFill ?: return@LaunchedEffect
                ProductAutoFill.fromJsonOrNull(json)?.let { viewModel.onScannedProductReceived(it) }
                savedStateHandle[SCANNED_AUTOFILL_KEY] = null
            }

            LaunchedEffect(scannedDate) {
                val iso = scannedDate ?: return@LaunchedEffect
                viewModel.onScannedDateReceived(LocalDate.parse(iso))
                // Critical: clear the saved entry so re-entering this
                // composable (rotation, process death restoration) does
                // not re-trigger onScannedDateReceived with a stale value.
                savedStateHandle[SCANNED_DATE_KEY] = null
            }

            AddProductoScreen(
                neveraId = neveraId,
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onScanRequested = { navController.navigate(Screen.DateScanner.route) },
            )
        }

        composable(Screen.DateScanner.route) {
            DateScannerScreen(
                onDatePicked = { date: LocalDate, autoFill: ProductAutoFill? ->
                    // Hand the date (ISO yyyy-MM-dd) and, if the barcode phase
                    // resolved anything, the autofill (JSON) back to AddProducto.
                    val prev = navController.previousBackStackEntry?.savedStateHandle
                    prev?.set(SCANNED_DATE_KEY, date.toString())
                    if (autoFill != null) {
                        prev?.set(SCANNED_AUTOFILL_KEY, autoFill.toJson())
                    }
                    navController.popBackStack()
                },
                onManualEntry = { autoFill ->
                    // No date this route — but propagate the CÓDIGO-phase autofill
                    // (if any) so AddProducto lands pre-filled and the user only
                    // has to set the expiry date. Null → nothing to pre-fill
                    // (manual entry from scratch).
                    if (autoFill != null) {
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set(SCANNED_AUTOFILL_KEY, autoFill.toJson())
                    }
                    navController.popBackStack()
                },
                onCancel = { navController.popBackStack() },
            )
        }
    }
}
