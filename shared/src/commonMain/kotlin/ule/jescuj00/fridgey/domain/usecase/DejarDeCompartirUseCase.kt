package ule.jescuj00.fridgey.domain.usecase

import ule.jescuj00.fridgey.data.remote.firestore.MiembroDoc
import ule.jescuj00.fridgey.data.remote.firestore.NeveraRemoteRepository
import ule.jescuj00.fridgey.data.repository.NeveraRepository
import ule.jescuj00.fridgey.data.repository.UsuarioRepository
import ule.jescuj00.fridgey.domain.model.ErrorCode
import ule.jescuj00.fridgey.domain.model.ModoNevera
import ule.jescuj00.fridgey.domain.model.OperationResult

/**
 * Deja de compartir: expulsa a TODOS los colaboradores pero la nevera
 * PERMANECE en la nube (sigue SYNCED) y en los dispositivos del dueño. Solo se
 * vacía `colaboradores` (y `miembros` queda con el dueño). NO baja de la nube,
 * NO borra el doc remoto y — a diferencia de QuitarDeNube — NO pausa el sync:
 * el listener debe seguir vivo porque el dueño sigue sincronizando.
 *
 * Eje 2 del modelo: "compartida" es derivado (colaboradores no vacío), así que
 * dejar de compartir es simplemente vaciar ese array; el eje de persistencia
 * (SYNCED) no se toca.
 *
 * Se espera el ack del update remoto (revocación de acceso, debe constar) y se
 * vacía también en local para consistencia inmediata; el listener vivo
 * re-confirma el mismo conjunto vacío después (idempotente). Los colaboradores
 * expulsados pierden el acceso vía su propio SyncManager (PERMISSION_DENIED al
 * salir de `colaboradores`).
 */
class DejarDeCompartirUseCase(
    private val neveraRepository: NeveraRepository,
    private val remoteRepository: NeveraRemoteRepository,
    private val usuarioRepository: UsuarioRepository,
) {

    /**
     * Quita todos los colaboradores de la nevera [neveraId]. Solo el
     * propietario ([requesterId]). Idempotente: si ya no hay colaboradores (o
     * la nevera no está en la nube) devuelve éxito sin tocar nada.
     */
    suspend operator fun invoke(neveraId: String, requesterId: String): OperationResult<Unit> {
        val snapshot = neveraRepository.getSyncSnapshot(neveraId)
            ?: return OperationResult.Error(
                "La nevera no existe",
                ErrorCode.NOT_FOUND
            )

        if (snapshot.idPropietario != requesterId) {
            return OperationResult.Error(
                "Solo el propietario puede dejar de compartir la nevera",
                ErrorCode.UNAUTHORIZED
            )
        }

        return when (snapshot.modo) {
            // Sin nube no hay colaboración que retirar: idempotente.
            ModoNevera.LOCAL -> OperationResult.Success(Unit)

            ModoNevera.SYNCED -> {
                val owner = usuarioRepository.getUsuarioById(requesterId)
                val ownerMiembro = MiembroDoc(
                    uid = requesterId,
                    nombre = owner?.nombre ?: "",
                    fotoUrl = owner?.fotoUrl,
                )
                try {
                    // AWAIT: la expulsión debe constar en el servidor. El doc
                    // NO se borra — solo se vacía `colaboradores`/`miembros`.
                    remoteRepository.quitarColaboradores(neveraId, ownerMiembro)
                    // Consistencia local inmediata; el listener vivo re-confirma.
                    neveraRepository.vaciarColaboradoresLocal(neveraId)
                    OperationResult.Success(Unit)
                } catch (e: Exception) {
                    OperationResult.Error(
                        "No se pudo dejar de compartir: ${e.message}",
                        ErrorCode.NETWORK_ERROR
                    )
                }
            }
        }
    }
}
