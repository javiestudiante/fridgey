package ule.jescuj00.fridgey.domain.notification

/**
 * Puerto de registro del token de notificaciones push del dispositivo en
 * Firestore (`usuarios/{uid}/tokens/{idInstalacion}`). Interfaz plana en
 * commonMain resuelta por Koin (NO expect/actual: eso se reserva para piezas
 * como `DatabaseDriverFactory`); cada plataforma aporta su implementación:
 *  - Android (HITO 2): obtiene el token de FirebaseMessaging y lo escribe/borra.
 *  - iOS (HITO 5): hoy NO-OP; se completará con el puente APNs→FCM.
 *
 * Ambas operaciones deben ser a prueba de fallos: una excepción cruzando el
 * puente suspend de GitLive NO puede tumbar la app, así que la implementación
 * envuelve la E/S en try/catch y solo registra el error.
 */
interface RegistroTokenPush {
    /** Escribe/refresca el token del dispositivo para [uid] (idempotente). */
    suspend fun register(uid: String)

    /**
     * Borra el token del dispositivo de [uid]. DEBE invocarse mientras la sesión
     * sigue viva (la regla de `usuarios/{uid}/tokens` exige
     * `request.auth.uid == uid`): tras `signOut` ya no hay auth para autorizar el
     * borrado ni uid para construir la ruta.
     */
    suspend fun unregister(uid: String)
}
