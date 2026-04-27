package ule.jescuj00.fridgey.di

import org.koin.core.module.Module
import org.koin.dsl.module
import ule.jescuj00.fridgey.data.db.DatabaseDriverFactory
import ule.jescuj00.fridgey.database.FoodSaverDatabase

/**
 * Wires the SQLDelight database and its generated [Queries] objects into Koin.
 *
 * Expects [DatabaseDriverFactory] to be provided by a platform module
 * (see [androidModule] / [iosModule]).
 */
val databaseModule: Module = module {
    single { FoodSaverDatabase(get<DatabaseDriverFactory>().createDriver()) }

    single { get<FoodSaverDatabase>().usuarioQueries }
    single { get<FoodSaverDatabase>().neveraQueries }
    single { get<FoodSaverDatabase>().neveraColaboradorQueries }
    single { get<FoodSaverDatabase>().productoQueries }
    single { get<FoodSaverDatabase>().productoCacheQueries }
}
