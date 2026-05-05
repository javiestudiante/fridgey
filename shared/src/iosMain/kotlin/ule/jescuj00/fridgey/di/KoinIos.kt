package ule.jescuj00.fridgey.di

import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.context.startKoin
import ule.jescuj00.fridgey.data.auth.AuthStateBinder
import ule.jescuj00.fridgey.data.binders.NeveraListBinder
import ule.jescuj00.fridgey.data.binders.ProductoListBinder
import ule.jescuj00.fridgey.data.repository.AuthRepository
import ule.jescuj00.fridgey.data.repository.NeveraRepository
import ule.jescuj00.fridgey.data.repository.ProductoRepository
import ule.jescuj00.fridgey.data.repository.UsuarioRepository
import ule.jescuj00.fridgey.domain.usecase.AddColaboradorUseCase
import ule.jescuj00.fridgey.domain.usecase.CreateNeveraUseCase
import ule.jescuj00.fridgey.domain.usecase.RemoveColaboradorUseCase
import ule.jescuj00.fridgey.domain.usecase.ScanExpirationDateUseCase
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
