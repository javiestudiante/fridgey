package ule.jescuj00.fridgey.domain.usecase

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import ule.jescuj00.fridgey.data.remote.firestore.NeveraRemoteRepository
import ule.jescuj00.fridgey.data.repository.NeveraRepository
import ule.jescuj00.fridgey.data.sync.SyncManager
import ule.jescuj00.fridgey.domain.model.ErrorCode
import ule.jescuj00.fridgey.domain.model.ModoNevera
import ule.jescuj00.fridgey.domain.model.OperationResult

/**
 * Transición SYNCED → LOCAL: quita una nevera de la cuenta (la baja de la
 * nube) borrándola de Firestore y conservando la copia local del dueño.
 * Implica dejar de compartir: sin nube no hay colaboración.
 *
 * Se ESPERA el ack del servidor en [NeveraRemoteRepository.deleteNevera]: es
 * una revocación de acceso y debe constar como hecha antes de voltear el modo.
 * La UI la envuelve en spinner + timeout.
 *
 * [SyncManager.pauseSync] se invoca antes del borrado para que los ecos
 * REMOVED del borrado masivo de productos no vacíen la copia local del dueño,
 * que debe CONSERVAR sus datos al volver a LOCAL. Los colaboradores, en
 * cambio, pierden la nevera: lo hace su propio SyncManager al ver el doc
 * desaparecer (o al recibir PERMISSION_DENIED).
 */
class QuitarDeNubeUseCase(
    private val neveraRepository: NeveraRepository,
    private val remoteRepository: NeveraRemoteRepository,
    private val syncManager: SyncManager,
) {

    /**
     * Quita la nevera [neveraId] de la cuenta. Solo el propietario
     * ([requesterId]) puede hacerlo. Si la nevera ya es LOCAL la operación es
     * idempotente y devuelve éxito sin tocar nada.
     */
    suspend operator fun invoke(neveraId: String, requesterId: String): OperationResult<Unit> {
        val snapshot = neveraRepository.getSyncSnapshot(neveraId)
            ?: return OperationResult.Error(
                "La nevera no existe",
                ErrorCode.NOT_FOUND
            )

        if (snapshot.idPropietario != requesterId) {
            return OperationResult.Error(
                "Solo el propietario puede quitar la nevera de su cuenta",
                ErrorCode.UNAUTHORIZED
            )
        }

        return when (snapshot.modo) {
            // Ya es local: idempotente, no hay nada que borrar.
            ModoNevera.LOCAL -> OperationResult.Success(Unit)

            ModoNevera.SYNCED -> {
                syncManager.pauseSync(neveraId)
                try {
                    // AWAIT deliberado: la revocación debe constar en el
                    // servidor antes de dar la operación por hecha.
                    remoteRepository.deleteNevera(neveraId)
                    neveraRepository.revertirANoCompartida(neveraId)
                    OperationResult.Success(Unit)
                } catch (e: Exception) {
                    OperationResult.Error(
                        "No se pudo quitar la nevera de tu cuenta: ${e.message}",
                        ErrorCode.NETWORK_ERROR
                    )
                } finally {
                    // Si el borrado falló (modo sigue SYNCED) esto reengancha
                    // el listener; si se completó (modo LOCAL) no hace nada.
                    // NonCancellable: la UI envuelve esta operación en un
                    // timeout — si cancela, la reanudación debe ejecutarse
                    // igualmente o la nevera quedaría pausada para siempre.
                    withContext(NonCancellable) {
                        syncManager.resumeSync(neveraId)
                    }
                }
            }
        }
    }
}
