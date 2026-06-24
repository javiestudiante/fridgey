package ule.jescuj00.fridgey

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.qualifier.named
import ule.jescuj00.fridgey.data.repository.AuthRepository
import ule.jescuj00.fridgey.data.sync.SyncManager
import ule.jescuj00.fridgey.di.SYNC_SCOPE_QUALIFIER
import ule.jescuj00.fridgey.di.androidModule
import ule.jescuj00.fridgey.di.authBridgeModule
import ule.jescuj00.fridgey.di.notificationModule
import ule.jescuj00.fridgey.di.sharedModules
import ule.jescuj00.fridgey.di.viewModelModule
import ule.jescuj00.fridgey.domain.model.auth.AuthState
import ule.jescuj00.fridgey.domain.notification.NotificacionCaducidadScheduler
import ule.jescuj00.fridgey.domain.notification.RegistroTokenPush
import ule.jescuj00.fridgey.notificaciones.CanalCaducidad

class FridgeyApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // The google-services Gradle plugin adds a ContentProvider that
        // already initializes Firebase before onCreate runs; this call is
        // idempotent and gives us a deterministic log when something is
        // misconfigured.
        FirebaseApp.initializeApp(this)
        Log.d("Fridgey", "Firebase initialized: ${FirebaseApp.getInstance().name}")

        // Canal de notificación de avisos de caducidad (idempotente).
        CanalCaducidad.crear(this)

        val koinApp = startKoin {
            androidLogger()
            androidContext(this@FridgeyApplication)
            modules(sharedModules() + androidModule() + authBridgeModule() + viewModelModule() + notificationModule())
        }

        // El sync se engancha al ciclo de auth: login arranca el descubrimiento
        // en la nube + los listeners de neveras SYNCED, logout los para. iOS
        // hace su propio arranque equivalente desde bindSyncManagerToAuth.
        val koin = koinApp.koin
        val syncScope = koin.get<CoroutineScope>(named(SYNC_SCOPE_QUALIFIER))
        val syncManager = koin.get<SyncManager>()
        val authRepository = koin.get<AuthRepository>()
        val registroToken = koin.get<RegistroTokenPush>()
        syncScope.launch {
            authRepository.observeAuthState().collect { state ->
                when (state) {
                    is AuthState.Authenticated -> {
                        syncManager.start(syncScope, state.user.uid)
                        // Registra/refresca el token FCM de este dispositivo. En
                        // launch aparte: register() hace E/S de red y no debe
                        // bloquear el procesado de estados de auth. El BORRADO en
                        // logout NO va aquí: lo hace SignOutUseCase ANTES de cerrar
                        // sesión, mientras aún hay auth para autorizar el delete.
                        syncScope.launch { registroToken.register(state.user.uid) }
                    }
                    AuthState.Unauthenticated -> syncManager.stop()
                    AuthState.Loading -> Unit
                    is AuthState.Error -> syncManager.stop()
                }
            }
        }

        // Motor de avisos de caducidad (Fase 1, Android). Funciona sin la UI del
        // toggle (HITO 4): por defecto está ON. programarComprobacionDiaria deja
        // el periódico (KEEP) y comprobarAhora lanza un barrido inmediato para no
        // esperar al ciclo diario tras abrir la app o editar una fecha.
        val scheduler = koin.get<NotificacionCaducidadScheduler>()
        scheduler.programarComprobacionDiaria()
        scheduler.comprobarAhora()
    }
}
