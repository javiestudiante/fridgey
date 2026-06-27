package ule.jescuj00.fridgey.di

import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import ule.jescuj00.fridgey.data.auth.AppleSignInHelper
import ule.jescuj00.fridgey.data.auth.AuthStateBinder
import ule.jescuj00.fridgey.data.auth.GoogleSignInHelper
import ule.jescuj00.fridgey.data.binders.ExpiringTodayBinder
import ule.jescuj00.fridgey.data.binders.NeveraListBinder
import ule.jescuj00.fridgey.data.binders.ProductoListBinder
import ule.jescuj00.fridgey.data.db.DatabaseDriverFactory
import ule.jescuj00.fridgey.domain.notification.RegistroTokenPush
import ule.jescuj00.fridgey.notificaciones.RegistroTokenPushIos

/**
 * Supplies iOS-only bindings — the [DatabaseDriverFactory] for iOS
 * needs no constructor parameters (the SQLite native driver opens
 * a database file in the app sandbox).
 *
 * The sign-in helpers on iOS delegate to Swift bridges set from
 * `iOSApp.swift`; their Kotlin classes have no constructor args.
 */
fun iosModule(): Module = module {
    single { DatabaseDriverFactory() }
    single { GoogleSignInHelper() }
    single { AppleSignInHelper() }
    factory { AuthStateBinder(get()) }       // factory: each Swift consumer gets its own scope
    factory { ProductoListBinder(get()) }    // idem
    factory { NeveraListBinder(get()) }      // idem
    factory { ExpiringTodayBinder(get()) }   // idem
    // Puerto de token push (HITO 5): la impl concreta (con onFcmTokenRecibido que
    // llama Swift) + alias a la interfaz para SignOutUseCase / bindSyncManagerToAuth.
    single { RegistroTokenPushIos(get(), get(), get(named(SYNC_SCOPE_QUALIFIER))) }
    single<RegistroTokenPush> { get<RegistroTokenPushIos>() }
}
