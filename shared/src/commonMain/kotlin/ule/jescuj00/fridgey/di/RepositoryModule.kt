package ule.jescuj00.fridgey.di

import org.koin.core.module.Module
import org.koin.dsl.module
import ule.jescuj00.fridgey.data.repository.NeveraRepository
import ule.jescuj00.fridgey.data.repository.ProductoRepository
import ule.jescuj00.fridgey.data.repository.UsuarioRepository

/**
 * Provides the data-layer repositories as singletons.
 *
 * Each repository receives only the [Queries] objects it actually needs,
 * which keeps construction explicit and makes them easy to fake in tests.
 */
val repositoryModule: Module = module {
    single { UsuarioRepository(get()) }
    single { NeveraRepository(get(), get(), get()) }
    single { ProductoRepository(get()) }
}
