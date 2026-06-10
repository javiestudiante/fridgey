package ule.jescuj00.fridgey.domain.usecase

import ule.jescuj00.fridgey.data.remote.firestore.NeveraRemoteRepository
import ule.jescuj00.fridgey.data.repository.NeveraRepository
import ule.jescuj00.fridgey.data.sync.SyncManager
import ule.jescuj00.fridgey.domain.model.ErrorCode
import ule.jescuj00.fridgey.domain.model.ModoNevera
import ule.jescuj00.fridgey.domain.model.OperationResult

/**
 * Transición SHARED → LOCAL: deja de compartir una nevera borrándola de
 * Firestore y conservando la copia local del dueño.
 *
 * A diferencia del share, aquí se ESPERA el ack del servidor en
 * [NeveraRemoteRepository.deleteNevera]: es una operación de revocación de
 * acceso y debe constar como hecha antes de voltear el modo. Offline, esa
 * llamada suspende hasta reconectar — la UI decidirá timeout/spinner en el
 * sprint B.
 *
 * [SyncManager.pauseSync] se invoca antes del borrado para que los ecos
 * REMOVED del borrado masivo de productos no vacíen la copia local del
 * dueño, que debe CONSERVAR sus datos al volver a LOCAL. Los colaboradores,
 * en cambio, pierden la nevera: lo hace su propio SyncManager al ver el doc
 * desaparecer (o al recibir PERMISSION_DENIED).
 */
class UnshareNeveraUseCase(
    private val neveraRepository: NeveraRepository,
    private val remoteRepository: NeveraRemoteRepository,
    private val syncManager: SyncManager,
) {

    /**
     * Deja de compartir la nevera [neveraId]. Solo el propietario
     * ([requesterId]) puede hacerlo. Si la nevera ya es LOCAL la operación
     * es idempotente y devuelve éxito sin tocar nada.
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
            // Ya es local: idempotente, no hay nada que borrar.
            ModoNevera.LOCAL -> OperationResult.Success(Unit)

            ModoNevera.SHARED -> {
                syncManager.pauseSync(neveraId)
                try {
                    // AWAIT deliberado: la revocación debe constar en el
                    // servidor antes de dar la operación por hecha.
                    remoteRepository.deleteNevera(neveraId)
                    neveraRepository.revertirANoCompartida(neveraId)
                    OperationResult.Success(Unit)
                } catch (e: Exception) {
                    OperationResult.Error(
                        "No se pudo dejar de compartir: ${e.message}",
                        ErrorCode.NETWORK_ERROR
                    )
                } finally {
                    // Si el borrado falló (modo sigue SHARED) esto reengancha
                    // el listener; si se completó (modo LOCAL) no hace nada.
                    syncManager.resumeSync(neveraId)
                }
            }
        }
    }
}
