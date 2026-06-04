package ule.jescuj00.fridgey.di

import org.koin.core.module.Module
import org.koin.dsl.module
import ule.jescuj00.fridgey.domain.scanner.BarcodeScanner
import ule.jescuj00.fridgey.domain.scanner.TextRecognizer
import ule.jescuj00.fridgey.domain.usecase.AddColaboradorUseCase
import ule.jescuj00.fridgey.domain.usecase.CreateNeveraUseCase
import ule.jescuj00.fridgey.domain.usecase.LookupProductByBarcodeUseCase
import ule.jescuj00.fridgey.domain.usecase.ParseQuantityUseCase
import ule.jescuj00.fridgey.domain.usecase.RemoveColaboradorUseCase
import ule.jescuj00.fridgey.domain.usecase.ScanExpirationDateUseCase
import ule.jescuj00.fridgey.domain.usecase.auth.ObserveAuthStateUseCase
import ule.jescuj00.fridgey.domain.usecase.auth.SignInWithAppleUseCase
import ule.jescuj00.fridgey.domain.usecase.auth.SignInWithGoogleUseCase
import ule.jescuj00.fridgey.domain.usecase.auth.SignOutUseCase

/**
 * Provides domain use cases as factories — they are stateless and short-lived,
 * so a fresh instance per resolution costs nothing and avoids accidental shared state.
 *
 * [TextRecognizer] is provided here too (as a single) since it owns native resources
 * that should be reused across calls.
 */
val useCaseModule: Module = module {
    // Both recognizers own native resources (ML Kit / Vision clients) that
    // should be reused across calls → singles, like TextRecognizer.
    single { TextRecognizer() }
    single { BarcodeScanner() }

    factory { CreateNeveraUseCase(get()) }
    factory { AddColaboradorUseCase(get(), get()) }
    factory { RemoveColaboradorUseCase(get()) }
    factory { ScanExpirationDateUseCase(get()) }
    factory { ParseQuantityUseCase() }
    factory { LookupProductByBarcodeUseCase(get()) }

    factory { SignInWithGoogleUseCase(get(), get()) }
    factory { SignInWithAppleUseCase(get(), get()) }
    factory { SignOutUseCase(get()) }
    factory { ObserveAuthStateUseCase(get()) }
}
