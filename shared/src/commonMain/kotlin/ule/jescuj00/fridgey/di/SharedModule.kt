package ule.jescuj00.fridgey.di

import org.koin.core.module.Module

/**
 * The platform-agnostic Koin modules.
 *
 * Each platform composes this with its own module that supplies
 * [ule.jescuj00.fridgey.data.db.DatabaseDriverFactory]:
 * - Android: `sharedModules() + androidModule()`
 * - iOS:    `sharedModules() + iosModule()`
 */
fun sharedModules(): List<Module> = listOf(
    databaseModule,
    networkModule,
    repositoryModule,
    useCaseModule
)
