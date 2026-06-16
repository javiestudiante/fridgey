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
 * Borrado definitivo de una nevera por su PROPIETARIO. Cubre los tres casos
 * del dueño (el cuarto, "colaborador sale", es [SalirDeNeveraUseCase]):
 *
 *  1. LOCAL → solo borrado local (productos + colaboradores + nevera en una
 *     transacción). No hay nube ni colaboradores que avisar.
 *  2. SYNCED sin colaboradores → borrado del doc remoto (y su subcolección de
 *     productos) + borrado local. La nevera desaparece de la cuenta y del
 *     resto de dispositivos del dueño.
 *  3. SYNCED con colaboradores → igual que (2); además los colaboradores
 *     pierden la nevera vía su propio SyncManager (el doc desaparece →
 *     Eliminada/PERMISSION_DENIED → eliminarNeveraLocal). Borra PARA TODOS.
 *
 * La distinción 2/3 solo afecta al AVISO de la UI (texto dinámico según
 * `tieneColaboradores`); la operación es la misma.
 *
 * AWAIT deliberado en el borrado remoto (mismo criterio que
 * [QuitarDeNubeUseCase]): es una acción explícita y destructiva del usuario y
 * debe constar en el servidor antes de darla por hecha — NO se reutiliza el
 * fire-and-forget de NeveraRepository.deleteNevera. La copia local se borra
 * DESPUÉS del ack para no perder datos si el borrado remoto falla.
 *
 * [SyncManager.pauseSync] antes del borrado remoto: sin la pausa, el listener
 * del dueño vería desaparecer el doc y aplicaría revertirANoCompartida en
 * mitad de la operación. Tras el borrado local la nevera sale del conjunto
 * SYNCED y la reconciliación no relanza nada; si el remoto falló, resumeSync
 * reengancha el listener (mismo patrón NonCancellable que QuitarDeNube).
 *
 * Limitación conocida (heredada de [NeveraRemoteRepository.deleteNevera]): el
 * batch de productos se ejecuta desde el cliente; si la app muere a mitad
 * pueden quedar docs de producto huérfanos en remoto. Sin impacto funcional
 * ni de seguridad — el doc padre se borra al final, así que el acceso de los
 * colaboradores se revoca igualmente.
 */
class BorrarNeveraUseCase(
    private val neveraRepository: NeveraRepository,
    private val remoteRepository: NeveraRemoteRepository,
    private val syncManager: SyncManager,
) {

    /**
     * Borra la nevera [neveraId]. Solo el propietario ([requesterId]); un
     * colaborador debe usar [SalirDeNeveraUseCase].
     */
    suspend operator fun invoke(neveraId: String, requesterId: String): OperationResult<Unit> {
        val snapshot = neveraRepository.getSyncSnapshot(neveraId)
            ?: return OperationResult.Error(
                "La nevera no existe",
                ErrorCode.NOT_FOUND
            )

        if (snapshot.idPropietario != requesterId) {
            return OperationResult.Error(
                "Solo el propietario puede borrar la nevera",
                ErrorCode.UNAUTHORIZED
            )
        }

        return when (snapshot.modo) {
            // Caso 1: nevera solo de este dispositivo — borrado local y listo.
            ModoNevera.LOCAL -> {
                neveraRepository.eliminarNeveraLocal(neveraId)
                OperationResult.Success(Unit)
            }

            // Casos 2 y 3: primero el servidor (AWAIT), después la copia local.
            ModoNevera.SYNCED -> {
                syncManager.pauseSync(neveraId)
                try {
                    remoteRepository.deleteNevera(neveraId)
                    neveraRepository.eliminarNeveraLocal(neveraId)
                    OperationResult.Success(Unit)
                } catch (e: Exception) {
                    OperationResult.Error(
                        "No se pudo borrar la nevera, vuelve a intentarlo",
                        ErrorCode.NETWORK_ERROR
                    )
                } finally {
                    withContext(NonCancellable) {
                        syncManager.resumeSync(neveraId)
                    }
                }
            }
        }
    }
}
