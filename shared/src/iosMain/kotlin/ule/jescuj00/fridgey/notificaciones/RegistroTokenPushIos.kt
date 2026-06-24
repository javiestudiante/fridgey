package ule.jescuj00.fridgey.notificaciones

import ule.jescuj00.fridgey.domain.notification.RegistroTokenPush

/**
 * Implementación iOS del puerto de registro de token push: NO-OP hasta el
 * HITO 5. Existe ya para que el grafo de Koin resuelva [RegistroTokenPush] en
 * iOS (lo necesita, p.ej., `SignOutUseCase`) y para que la lógica de ciclo de
 * vida compartida pueda invocarlo sin ramas por plataforma. En el HITO 5 se
 * implementará el puente APNs→FCM (obtener el token y escribir/borrar en
 * `usuarios/{uid}/tokens/{idInstalacion}`).
 */
class RegistroTokenPushIos : RegistroTokenPush {
    override suspend fun register(uid: String) {
        // no-op (HITO 5)
    }

    override suspend fun unregister(uid: String) {
        // no-op (HITO 5)
    }
}
