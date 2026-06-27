package ule.jescuj00.fridgey.notificaciones

import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Timestamp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import platform.Foundation.NSLog
import ule.jescuj00.fridgey.data.remote.firestore.COLECCION_TOKENS
import ule.jescuj00.fridgey.data.remote.firestore.COLECCION_USUARIOS
import ule.jescuj00.fridgey.data.remote.firestore.TokenDoc
import ule.jescuj00.fridgey.data.repository.PreferenciasRepository
import ule.jescuj00.fridgey.domain.notification.RegistroTokenPush

/**
 * Implementación iOS del puerto de token push. El token FCM NO lo da gitlive
 * (no cubre Messaging): nace en Swift (pod FirebaseMessaging, MessagingDelegate)
 * y se entrega aquí vía [onFcmTokenRecibido]. La ESCRITURA del TokenDoc vive en
 * Kotlin (gitlive), con el MISMO [TokenDoc] y ruta
 * `usuarios/{uid}/tokens/{idInstalacion}` que Android, con `platform="ios"`.
 *
 * Coordinación de dos disparadores asíncronos (análoga al register+onNewToken de
 * Android) protegida por [mutex]:
 *  - [register] (al autenticar): fija el uid; si ya hay token → escribe.
 *  - [onFcmTokenRecibido] (callback de Swift): fija el token; si ya hay uid →
 *    escribe. Gana el segundo en completarse: cubre "token aún no disponible al
 *    hacer login".
 *  - [unregister] (en SignOutUseCase, ANTES del signOut): borra el doc; no
 *    necesita el token (la ruta va por idInstalacion).
 *
 * Toda la E/S gitlive va en try/catch: una excepción cruzando el puente suspend
 * no debe tumbar la app.
 */
class RegistroTokenPushIos(
    private val firestore: FirebaseFirestore,
    private val preferencias: PreferenciasRepository,
    private val scope: CoroutineScope,
) : RegistroTokenPush {

    private val mutex = Mutex()
    private var uidActivo: String? = null
    private var tokenActual: String? = null

    override suspend fun register(uid: String) {
        val token = mutex.withLock {
            uidActivo = uid
            tokenActual
        }
        if (token != null) escribir(uid, token)
    }

    /**
     * Lo invoca Swift desde `messaging(_:didReceiveRegistrationToken:)`. No es
     * suspend (Swift no puede llamar suspend cómodamente): lanza en [scope].
     */
    fun onFcmTokenRecibido(token: String?) {
        if (token.isNullOrEmpty()) return
        scope.launch {
            val uid = mutex.withLock {
                tokenActual = token
                uidActivo
            }
            if (uid != null) escribir(uid, token)
        }
    }

    override suspend fun unregister(uid: String) {
        mutex.withLock { uidActivo = null }
        try {
            val idInstalacion = preferencias.idInstalacion()
            tokenRef(uid, idInstalacion).delete()
        } catch (e: Exception) {
            NSLog("RegistroTokenPushIos: no se pudo borrar el token de %@: %@", uid, e.toString())
        }
    }

    private suspend fun escribir(uid: String, token: String) {
        try {
            val idInstalacion = preferencias.idInstalacion()
            tokenRef(uid, idInstalacion).set(
                TokenDoc(
                    token = token,
                    platform = "ios",
                    updatedAt = Timestamp.ServerTimestamp,
                )
            )
        } catch (e: Exception) {
            NSLog("RegistroTokenPushIos: no se pudo registrar el token de %@: %@", uid, e.toString())
        }
    }

    private fun tokenRef(uid: String, idInstalacion: String) =
        firestore.collection(COLECCION_USUARIOS).document(uid)
            .collection(COLECCION_TOKENS).document(idInstalacion)
}
