package ule.jescuj00.fridgey.notificaciones

import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.firebase.messaging.FirebaseMessaging
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Timestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ule.jescuj00.fridgey.data.remote.firestore.COLECCION_TOKENS
import ule.jescuj00.fridgey.data.remote.firestore.COLECCION_USUARIOS
import ule.jescuj00.fridgey.data.remote.firestore.TokenDoc
import ule.jescuj00.fridgey.data.repository.PreferenciasRepository
import ule.jescuj00.fridgey.domain.notification.RegistroTokenPush

/**
 * Implementación Android del puerto de token push: obtiene el token de
 * FirebaseMessaging y escribe/borra `usuarios/{uid}/tokens/{idInstalacion}` vía
 * el SDK GitLive (mismo Firestore que el resto de la app; `serverTimestamp` con
 * `Timestamp.ServerTimestamp`, igual que `uploadNevera`).
 *
 * Toda la E/S va envuelta en try/catch: una excepción cruzando el puente suspend
 * de GitLive (o un `Tasks.await` fallido) NO debe tumbar la app — solo se
 * registra. Obtener y guardar el token NO requiere el permiso POST_NOTIFICATIONS
 * (ese permiso solo afecta a MOSTRAR notificaciones, no a registrar el token).
 */
class RegistroTokenPushAndroid(
    private val firestore: FirebaseFirestore,
    private val preferencias: PreferenciasRepository,
) : RegistroTokenPush {

    private fun tokenRef(uid: String, idInstalacion: String) =
        firestore.collection(COLECCION_USUARIOS).document(uid)
            .collection(COLECCION_TOKENS).document(idInstalacion)

    override suspend fun register(uid: String) {
        try {
            // FirebaseMessaging.token devuelve un Task; lo esperamos en IO (no hay
            // kotlinx-coroutines-play-services en el proyecto, Tasks.await basta).
            val token = withContext(Dispatchers.IO) {
                Tasks.await(FirebaseMessaging.getInstance().token)
            }
            val idInstalacion = preferencias.idInstalacion()
            tokenRef(uid, idInstalacion).set(
                TokenDoc(
                    token = token,
                    platform = "android",
                    updatedAt = Timestamp.ServerTimestamp,
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo registrar el token FCM para $uid", e)
        }
    }

    override suspend fun unregister(uid: String) {
        try {
            val idInstalacion = preferencias.idInstalacion()
            tokenRef(uid, idInstalacion).delete()
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo borrar el token FCM de $uid", e)
        }
    }

    private companion object {
        const val TAG = "RegistroTokenPush"
    }
}
