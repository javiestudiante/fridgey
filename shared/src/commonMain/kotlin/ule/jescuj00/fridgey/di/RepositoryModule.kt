package ule.jescuj00.fridgey.di

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import ule.jescuj00.fridgey.data.remote.firestore.NeveraRemoteRepository
import ule.jescuj00.fridgey.data.repository.AuthRepository
import ule.jescuj00.fridgey.data.repository.NeveraRepository
import ule.jescuj00.fridgey.data.repository.ProductoRepository
import ule.jescuj00.fridgey.data.repository.UsuarioRepository
import ule.jescuj00.fridgey.data.sync.SyncManager

/** Koin qualifier for the process-wide [CoroutineScope] used by Firestore sync. */
const val SYNC_SCOPE_QUALIFIER = "syncScope"

/**
 * Provides the data-layer repositories as singletons.
 *
 * Each repository receives only the [Queries] objects it actually needs,
 * which keeps construction explicit and makes them easy to fake in tests.
 */
val repositoryModule: Module = module {
    // Process-wide scope for Firestore pushes and sync listeners.
    // SupervisorJob so a failed push doesn't kill the rest of the scope.
    single(named(SYNC_SCOPE_QUALIFIER)) {
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    single { Firebase.firestore }
    single { NeveraRemoteRepository(get()) }

    single { UsuarioRepository(get()) }
    // The remote repository is handed over lazily: resolving it eagerly would
    // construct Firebase.firestore at repo creation, which LOCAL-only flows
    // (and their unit tests) never need.
    single {
        NeveraRepository(
            get(), get(), get(), get(),
            lazy { get<NeveraRemoteRepository>() },
            get(named(SYNC_SCOPE_QUALIFIER)),
        )
    }
    single {
        ProductoRepository(
            get(), get(),
            lazy { get<NeveraRemoteRepository>() },
            get(named(SYNC_SCOPE_QUALIFIER)),
        )
    }
    single { AuthRepository(auth = Firebase.auth, usuarioRepository = get()) }

    single { SyncManager(get(), get(), get()) }
}

