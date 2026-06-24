package ule.jescuj00.fridgey.notificaciones

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.qualifier.named
import ule.jescuj00.fridgey.data.repository.AuthRepository
import ule.jescuj00.fridgey.di.SYNC_SCOPE_QUALIFIER
import ule.jescuj00.fridgey.domain.notification.RegistroTokenPush

/**
 * Servicio FCM declarado en el manifiesto. Por ahora (HITO 2) solo gestiona la
 * ROTACIÓN del token: [onNewToken]. La RECEPCIÓN de mensajes
 * ([onMessageReceived]) se implementa en el HITO 4.
 */
class FridgeyFirebaseMessagingService : FirebaseMessagingService(), KoinComponent {

    override fun onNewToken(token: String) {
        // FCM rotó el token. Si hay sesión, reescribimos el doc del token (register
        // re-consulta el token actual de FirebaseMessaging, así que el parámetro
        // `token` no se usa directamente). Si NO hay sesión, no hacemos nada: el
        // próximo `register` del ciclo de auth (al iniciar sesión) reconciliará.
        val uid = get<AuthRepository>().getCurrentUser()?.uid ?: return
        val registro = get<RegistroTokenPush>()
        // Scope de proceso (SupervisorJob): fire-and-forget, sobrevive al ciclo
        // corto del servicio. register() es a prueba de fallos internamente.
        get<CoroutineScope>(named(SYNC_SCOPE_QUALIFIER)).launch {
            registro.register(uid)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // HITO 4: canal + notificación local + deep-link con neveraId del payload.
        super.onMessageReceived(message)
    }
}
