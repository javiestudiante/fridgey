package ule.jescuj00.fridgey.di

import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.context.startKoin
import ule.jescuj00.fridgey.data.repository.NeveraRepository
import ule.jescuj00.fridgey.data.repository.ProductoRepository
import ule.jescuj00.fridgey.data.repository.UsuarioRepository
import ule.jescuj00.fridgey.domain.usecase.AddColaboradorUseCase
import ule.jescuj00.fridgey.domain.usecase.CreateNeveraUseCase
import ule.jescuj00.fridgey.domain.usecase.RemoveColaboradorUseCase
import ule.jescuj00.fridgey.domain.usecase.ScanExpirationDateUseCase

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

// --- Use cases ---
fun getCreateNeveraUseCase(): CreateNeveraUseCase = KoinAccessor.get()
fun getAddColaboradorUseCase(): AddColaboradorUseCase = KoinAccessor.get()
fun getRemoveColaboradorUseCase(): RemoveColaboradorUseCase = KoinAccessor.get()
fun getScanExpirationDateUseCase(): ScanExpirationDateUseCase = KoinAccessor.get()
