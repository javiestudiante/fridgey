package ule.jescuj00.fridgey.domain.usecase

import ule.jescuj00.fridgey.data.remote.firestore.MarcadorExpulsion
import ule.jescuj00.fridgey.data.remote.firestore.NeveraRemoteRepository
import ule.jescuj00.fridgey.data.repository.NeveraRepository
import ule.jescuj00.fridgey.domain.model.ErrorCode
import ule.jescuj00.fridgey.domain.model.ModoNevera
import ule.jescuj00.fridgey.domain.model.OperationResult

/**
 * El PROPIETARIO expulsa a UN colaborador concreto: lo quita del array
 * `colaboradores` (y su entrada de `miembros`). La nevera sigue SYNCED y
 * compartida con el resto; el expulsado pierde el acceso en sus dispositivos
 * vía su propio SyncManager (PERMISSION_DENIED → eliminarNeveraLocal).
 *
 * Es la variante "quitar UN uid (ajeno)" del motor genérico
 * [NeveraRemoteRepository.actualizarMiembros]; en remoto la cubre el `allow
 * update` completo del dueño (no necesita regla nueva).
 *
 * Los arrays filtrados se construyen desde el doc del SERVIDOR, no desde la
 * copia local: el update del dueño reescribe los arrays completos y una copia
 * local desfasada podría expulsar por accidente a alguien que acaba de
 * unirse. No se pausa el sync (el doc no se borra y el dueño sigue
 * sincronizando — mismo criterio que DejarDeCompartir); el listener vivo
 * re-confirma el conjunto resultante.
 */
class ExpulsarColaboradorUseCase(
    private val neveraRepository: NeveraRepository,
    private val remoteRepository: NeveraRemoteRepository,
) {

    /**
     * Expulsa a [colaboradorId] de la nevera [neveraId]. Solo el propietario
     * ([requesterId]), y nunca sobre sí mismo. Idempotente: si el colaborador
     * ya no está, devuelve éxito tras alinear la copia local.
     */
    suspend operator fun invoke(
        neveraId: String,
        requesterId: String,
        colaboradorId: String,
    ): OperationResult<Unit> {
        val snapshot = neveraRepository.getSyncSnapshot(neveraId)
            ?: return OperationResult.Error(
                "La nevera no existe",
                ErrorCode.NOT_FOUND
            )

        if (snapshot.idPropietario != requesterId) {
            return OperationResult.Error(
                "Solo el propietario puede expulsar a un colaborador",
                ErrorCode.UNAUTHORIZED
            )
        }

        if (colaboradorId == requesterId) {
            return OperationResult.Error(
                "El propietario no se puede expulsar a sí mismo",
                ErrorCode.INVALID_INPUT
            )
        }

        return when (snapshot.modo) {
            // Sin nube no hay colaboración (invariante); por si quedara una
            // fila local huérfana, alinear y devolver éxito idempotente.
            ModoNevera.LOCAL -> {
                neveraRepository.removeColaborador(neveraId, colaboradorId)
                OperationResult.Success(Unit)
            }

            ModoNevera.SYNCED -> try {
                val doc = remoteRepository.getNevera(neveraId)
                if (doc != null && colaboradorId in doc.colaboradores) {
                    // AWAIT: la revocación de acceso debe constar en el
                    // servidor antes de darla por hecha.
                    remoteRepository.actualizarMiembros(
                        neveraId = neveraId,
                        colaboradores = doc.colaboradores - colaboradorId,
                        miembros = doc.miembros.filterNot { it.uid == colaboradorId },
                        // Marcador de expulsión (escritura del dueño): la Cloud
                        // Function avisará también al expulsado. actor = dueño.
                        expulsion = MarcadorExpulsion(
                            actorUid = requesterId,
                            objetivos = listOf(colaboradorId),
                        ),
                    )
                }
                // Consistencia local inmediata; el listener vivo re-confirma.
                neveraRepository.removeColaborador(neveraId, colaboradorId)
                OperationResult.Success(Unit)
            } catch (e: Exception) {
                OperationResult.Error(
                    "No se pudo expulsar a esa persona, vuelve a intentarlo",
                    ErrorCode.NETWORK_ERROR
                )
            }
        }
    }
}
