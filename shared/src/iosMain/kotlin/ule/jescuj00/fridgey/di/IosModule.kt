package ule.jescuj00.fridgey.di

import org.koin.core.module.Module
import org.koin.dsl.module
import ule.jescuj00.fridgey.data.db.DatabaseDriverFactory

/**
 * Supplies iOS-only bindings — the [DatabaseDriverFactory] for iOS
 * needs no constructor parameters (the SQLite native driver opens
 * a database file in the app sandbox).
 */
fun iosModule(): Module = module {
    single { DatabaseDriverFactory() }
}
