package ule.jescuj00.fridgey.di

import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module
import ule.jescuj00.fridgey.data.db.DatabaseDriverFactory

/**
 * Supplies Android-only bindings — currently just a [DatabaseDriverFactory]
 * built with the application [android.content.Context] registered via
 * `androidContext(...)` in [org.koin.core.context.startKoin].
 */
fun androidModule(): Module = module {
    single { DatabaseDriverFactory(androidContext()) }
}
