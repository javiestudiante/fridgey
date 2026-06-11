package ule.jescuj00.fridgey.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.context.startKoin
import org.koin.core.qualifier.named
import ule.jescuj00.fridgey.data.auth.AuthStateBinder
import ule.jescuj00.fridgey.data.binders.ExpiringTodayBinder
import ule.jescuj00.fridgey.data.binders.NeveraListBinder
import ule.jescuj00.fridgey.data.binders.ProductoListBinder
import ule.jescuj00.fridgey.data.repository.AuthRepository
import ule.jescuj00.fridgey.data.repository.NeveraRepository
import ule.jescuj00.fridgey.data.repository.ProductoRepository
import ule.jescuj00.fridgey.data.repository.UsuarioRepository
import ule.jescuj00.fridgey.data.sync.SyncManager
import ule.jescuj00.fridgey.domain.model.auth.AuthState
import ule.jescuj00.fridgey.domain.scanner.BarcodeScanner
import ule.jescuj00.fridgey.domain.usecase.AceptarInvitacionUseCase
import ule.jescuj00.fridgey.domain.usecase.AddColaboradorUseCase
import ule.jescuj00.fridgey.domain.usecase.CreateNeveraUseCase
import ule.jescuj00.fridgey.domain.usecase.DejarDeCompartirUseCase
import ule.jescuj00.fridgey.domain.usecase.GenerarInvitacionUseCase
import ule.jescuj00.fridgey.domain.usecase.LookupProductByBarcodeUseCase
import ule.jescuj00.fridgey.domain.usecase.QuitarDeNubeUseCase
import ule.jescuj00.fridgey.domain.usecase.RemoveColaboradorUseCase
import ule.jescuj00.fridgey.domain.usecase.ScanExpirationDateUseCase
import ule.jescuj00.fridgey.domain.usecase.SubirANubeUseCase
import ule.jescuj00.fridgey.domain.usecase.auth.ObserveAuthStateUseCase
import ule.jescuj00.fridgey.domain.usecase.auth.SignInWithAppleUseCase
import ule.jescuj00.fridgey.domain.usecase.auth.SignInWithGoogleUseCase
import ule.jescuj00.fridgey.domain.usecase.auth.SignOutUseCase

/**
 * Entry point called from Swift (`SharedKt.doInitKoin()`) at app startup.
 * Loads the shared modules plus the iOS-specific bindings.
 */
fun initKoin() {
    startKoin {
        modules(sharedModules() + iosModule())
    }
}

/**
 * Internal anchor used by the top-level accessor functions below.
 * Swift cannot call Koin's reified `get<T>()` directly, so we expose
 * one strongly-typed accessor per dependency.
 */
private object KoinAccessor : KoinComponent

// --- Repositories ---
fun getUsuarioRepository(): UsuarioRepository = KoinAccessor.get()
fun getNeveraRepository(): NeveraRepository = KoinAccessor.get()
fun getProductoRepository(): ProductoRepository = KoinAccessor.get()
fun getAuthRepository(): AuthRepository = KoinAccessor.get()

// --- Use cases ---
fun getCreateNeveraUseCase(): CreateNeveraUseCase = KoinAccessor.get()
fun getAddColaboradorUseCase(): AddColaboradorUseCase = KoinAccessor.get()
fun getRemoveColaboradorUseCase(): RemoveColaboradorUseCase = KoinAccessor.get()
fun getScanExpirationDateUseCase(): ScanExpirationDateUseCase = KoinAccessor.get()
fun getBarcodeScanner(): BarcodeScanner = KoinAccessor.get()
fun getLookupProductByBarcodeUseCase(): LookupProductByBarcodeUseCase = KoinAccessor.get()

// --- Neveras en la nube + colaboración ---
fun getSubirANubeUseCase(): SubirANubeUseCase = KoinAccessor.get()
fun getQuitarDeNubeUseCase(): QuitarDeNubeUseCase = KoinAccessor.get()
fun getDejarDeCompartirUseCase(): DejarDeCompartirUseCase = KoinAccessor.get()
fun getGenerarInvitacionUseCase(): GenerarInvitacionUseCase = KoinAccessor.get()
fun getAceptarInvitacionUseCase(): AceptarInvitacionUseCase = KoinAccessor.get()

// --- Auth use cases ---
fun getSignInWithGoogleUseCase(): SignInWithGoogleUseCase = KoinAccessor.get()
fun getSignInWithAppleUseCase(): SignInWithAppleUseCase = KoinAccessor.get()
fun getSignOutUseCase(): SignOutUseCase = KoinAccessor.get()
fun getObserveAuthStateUseCase(): ObserveAuthStateUseCase = KoinAccessor.get()

/** Fresh binder per call — each Swift subscriber owns its coroutine scope. */
fun getAuthStateBinder(): AuthStateBinder = KoinAccessor.get()

/** Fresh binder per call — same rationale as [getAuthStateBinder]. */
fun getProductoListBinder(): ProductoListBinder = KoinAccessor.get()

/** Fresh binder per call — same rationale as [getAuthStateBinder]. */
fun getNeveraListBinder(): NeveraListBinder = KoinAccessor.get()

/** Fresh binder per call — cross-fridge "caducan hoy" home banner. */
fun getExpiringTodayBinder(): ExpiringTodayBinder = KoinAccessor.get()

/**
 * Ties the [SyncManager] lifecycle to the auth cycle, mirroring EXACTLY what
 * `FridgeyApplication` does on Android: login starts the cloud discovery +
 * listeners for SYNCED fridges, logout stops them. Lives in Kotlin (not Swift)
 * so both
 * platforms share the same lifecycle semantics — Swift just calls this once
 * from `iOSApp.init`, right after [initKoin].
 */
fun bindSyncManagerToAuth() {
    val syncScope = KoinAccessor.get<CoroutineScope>(named(SYNC_SCOPE_QUALIFIER))
    val syncManager = KoinAccessor.get<SyncManager>()
    val authRepository = KoinAccessor.get<AuthRepository>()
    syncScope.launch {
        authRepository.observeAuthState().collect { state ->
            when (state) {
                is AuthState.Authenticated -> syncManager.start(syncScope, state.user.uid)
                AuthState.Unauthenticated -> syncManager.stop()
                AuthState.Loading -> Unit
                is AuthState.Error -> syncManager.stop()
            }
        }
    }
}
