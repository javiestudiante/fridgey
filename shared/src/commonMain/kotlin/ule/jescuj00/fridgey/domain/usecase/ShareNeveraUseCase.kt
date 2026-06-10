package ule.jescuj00.fridgey.domain.usecase

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
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
 * Transición LOCAL → SHARED: hace colaborativa una nevera subiéndola a
 * Firestore junto con sus productos.
 *
 * Decisión offline-first: el upload se ENCOLA en [syncScope] (la cola
 * interna de Firestore persiste las escrituras pendientes y sobrevive a
 * reinicios) y el modo local se voltea a SHARED inmediatamente — si el
 * dispositivo está offline, la nevera queda SHARED en local y el documento
 * llega al servidor al reconectar. Los ecos del propio upload los descarta
 * el listener (hasPendingWrites).
 *
 * Riesgo asumido y documentado: un rechazo de las reglas de seguridad
 * dejaría el modo local en SHARED sin doc remoto. No debería ocurrir: el
 * dueño cumple las reglas por construcción (escribe su propio doc con su
 * propio uid como propietario).
 *
 * Los productos se suben PRESERVANDO sus ids locales (decisión de diseño:
 * no duplicar identidades entre SQLite y Firestore).
 */
class ShareNeveraUseCase(
    private val neveraRepository: NeveraRepository,
    private val productoRepository: ProductoRepository,
    private val usuarioRepository: UsuarioRepository,
    private val remoteRepository: NeveraRemoteRepository,
    private val syncScope: CoroutineScope,
) {

    /**
     * Hace colaborativa la nevera [neveraId]. Solo el propietario
     * ([requesterId]) puede hacerlo. Si la nevera ya es SHARED la operación
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
                "Solo el propietario puede hacer colaborativa la nevera",
                ErrorCode.UNAUTHORIZED
            )
        }

        return when (snapshot.modo) {
            // Ya compartida: idempotente, no hay nada que subir.
            ModoNevera.SHARED -> OperationResult.Success(Unit)

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

                // Encolado fire-and-forget: Firestore persiste la escritura
                // pendiente y la entrega al reconectar.
                syncScope.launch {
                    runCatching { remoteRepository.uploadNevera(neveraId, doc, productos) }
                }

                neveraRepository.updateModo(neveraId, ModoNevera.SHARED)
                OperationResult.Success(Unit)
            }
        }
    }
}
