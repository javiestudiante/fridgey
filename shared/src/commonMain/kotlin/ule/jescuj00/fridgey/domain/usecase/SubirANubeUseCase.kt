package ule.jescuj00.fridgey.domain.usecase

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import ule.jescuj00.fridgey.data.remote.firestore.MiembroDoc
import ule.jescuj00.fridgey.data.remote.firestore.NeveraDoc
import ule.jescuj00.fridgey.data.remote.firestore.NeveraRemoteRepository
import ule.jescuj00.fridgey.data.remote.firestore.toProductoDoc
import ule.jescuj00.fridgey.data.repository.NeveraRepository
import ule.jescuj00.fridgey.data.repository.ProductoRepository
import ule.jescuj00.fridgey.data.repository.UsuarioRepository
import ule.jescuj00.fridgey.domain.model.ErrorCode
import ule.jescuj00.fridgey.domain.model.ModoNevera
import ule.jescuj00.fridgey.domain.model.OperationResult

/**
 * Transición LOCAL → SYNCED: sube una nevera a la nube SIN invitar a nadie.
 * Tras subir, la nevera sigue al usuario en cualquier dispositivo donde inicie
 * sesión; sigue siendo solo suya (`colaboradores` vacío) hasta que,
 * opcionalmente, genere una invitación aparte (eje 2, derivado).
 *
 * ESTRICTA contra servidor: se espera el ack del upload — con timeout — y SOLO
 * entonces se voltea el modo a SYNCED. La variante optimista (encolar y voltear
 * ya) se descartó tras verificarla en dispositivo: el SyncManager engancha el
 * listener en cuanto el modo cambia y, con las reglas desplegadas (basadas en
 * `resource.data`), escuchar un doc que AÚN no existe en el servidor responde
 * PERMISSION_DENIED — que para el dueño significa "revocación" y revertía el
 * cambio en segundo plano. Con la espera, el listener solo se engancha a docs
 * ya existentes y PERMISSION_DENIED conserva su único significado real.
 *
 * Si el upload falla o expira, el modo se queda en LOCAL y se ENCOLA un borrado
 * de compensación: un timeout puede dejar escrituras en la cola interna de
 * Firestore que se comprometerían más tarde, y ese doc huérfano debe
 * desaparecer para que remoto y local converjan.
 *
 * Los productos se suben PRESERVANDO sus ids locales (decisión de diseño: no
 * duplicar identidades entre SQLite y Firestore).
 */
class SubirANubeUseCase(
    private val neveraRepository: NeveraRepository,
    private val productoRepository: ProductoRepository,
    private val usuarioRepository: UsuarioRepository,
    private val remoteRepository: NeveraRemoteRepository,
    private val syncScope: CoroutineScope,
) {

    /**
     * Sube la nevera [neveraId] a la cuenta del usuario. Solo el propietario
     * ([requesterId]) puede hacerlo. Si la nevera ya está SYNCED la operación
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
                "Solo el propietario puede guardar la nevera en su cuenta",
                ErrorCode.UNAUTHORIZED
            )
        }

        return when (snapshot.modo) {
            // Ya está en la nube: idempotente, no hay nada que subir.
            ModoNevera.SYNCED -> OperationResult.Success(Unit)

            ModoNevera.LOCAL -> {
                val owner = usuarioRepository.getUsuarioById(requesterId)
                val doc = NeveraDoc(
                    nombre = snapshot.nombre,
                    idPropietario = requesterId,
                    colaboradores = emptyList(),
                    miembros = listOf(
                        MiembroDoc(
                            uid = requesterId,
                            nombre = owner?.nombre ?: "",
                            fotoUrl = owner?.fotoUrl
                        )
                    ),
                    fechaCreacion = snapshot.fechaCreacion
                )

                // Preservamos los ids locales como ids de documento remoto.
                val productos = productoRepository.getProductosByNeveraOnce(neveraId)
                    .associate { it.id to it.toProductoDoc() }

                try {
                    withTimeout(UPLOAD_TIMEOUT_MS) {
                        remoteRepository.uploadNevera(neveraId, doc, productos)
                    }
                    neveraRepository.updateModo(neveraId, ModoNevera.SYNCED)
                    OperationResult.Success(Unit)
                } catch (e: TimeoutCancellationException) {
                    compensarUploadFallido(neveraId)
                    OperationResult.Error(
                        "Sin conexión con el servidor. Guardar la nevera en tu cuenta " +
                            "requiere conexión; inténtalo de nuevo.",
                        ErrorCode.NETWORK_ERROR
                    )
                } catch (e: CancellationException) {
                    // Cancelación externa (p.ej. el scope de la UI muere): la
                    // subida se da por no hecha — compensar y propagar.
                    compensarUploadFallido(neveraId)
                    throw e
                } catch (e: Exception) {
                    compensarUploadFallido(neveraId)
                    OperationResult.Error(
                        "No se pudo guardar la nevera en tu cuenta: ${e.message}",
                        ErrorCode.NETWORK_ERROR
                    )
                }
            }
        }
    }

    /**
     * El modo local sigue en LOCAL, pero parte del upload puede haber quedado
     * en la cola interna de Firestore (o incluso comprometido). Encola el
     * borrado remoto para que el estado remoto converja con el local — es un
     * no-op si nada llegó a escribirse.
     */
    private fun compensarUploadFallido(neveraId: String) {
        syncScope.launch {
            runCatching { remoteRepository.deleteNevera(neveraId) }
        }
    }

    private companion object {
        const val UPLOAD_TIMEOUT_MS = 15_000L
    }
}
