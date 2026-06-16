package ule.jescuj00.fridgey.domain.usecase

import dev.gitlive.firebase.firestore.FirebaseFirestoreException
import dev.gitlive.firebase.firestore.FirestoreExceptionCode
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import ule.jescuj00.fridgey.data.remote.firestore.NeveraRemoteRepository
import ule.jescuj00.fridgey.data.repository.NeveraRepository
import ule.jescuj00.fridgey.data.sync.SyncManager
import ule.jescuj00.fridgey.domain.model.ErrorCode
import ule.jescuj00.fridgey.domain.model.ModoNevera
import ule.jescuj00.fridgey.domain.model.OperationResult

/**
 * Un COLABORADOR sale de una nevera compartida: se quita a sí mismo del array
 * `colaboradores` (y su entrada de `miembros`) y pierde la copia local. La
 * nevera NO se borra para nadie más — sigue viva para el dueño y el resto.
 *
 * Es la variante "quitar UN uid (el propio)" del motor genérico
 * [NeveraRemoteRepository.actualizarMiembros]; en remoto la permite la regla
 * `esAutoSalidaValida` (un no-propietario solo puede quitarse a SÍ MISMO).
 *
 * Los arrays filtrados se construyen desde el doc del SERVIDOR (no desde la
 * copia local, que puede estar obsoleta): la regla valida el diff exacto y un
 * array desfasado quitaría/añadiría a terceros y sería rechazado. Aun así
 * queda una ventana mínima entre el get y el update; si el update es
 * rechazado se refresca y reintenta UNA sola vez, y si vuelve a fallar se
 * devuelve un error recuperable legible (nada de retry agresivo ni fallo
 * silencioso).
 *
 * AWAIT deliberado: la salida debe constar en el servidor antes de borrar la
 * copia local (si fallara después de borrar en local, el doc remoto seguiría
 * listándonos y el descubrimiento re-engancharía la nevera).
 *
 * [SyncManager.pauseSync] antes del update: al salir de `colaboradores`
 * nuestro propio listener recibiría PERMISSION_DENIED y resolvería la
 * limpieza por su cuenta en mitad de la operación; con la pausa el borrado
 * local es determinista aquí. Tras [NeveraRepository.eliminarNeveraLocal] la
 * nevera sale del conjunto SYNCED y resumeSync no reengancha nada; si el
 * update falló, reengancha el listener (mismo patrón que QuitarDeNube).
 */
class SalirDeNeveraUseCase(
    private val neveraRepository: NeveraRepository,
    private val remoteRepository: NeveraRemoteRepository,
    private val syncManager: SyncManager,
) {

    /**
     * Saca a [requesterId] (colaborador, NO propietario) de la nevera
     * [neveraId]. Idempotente: si el servidor ya no nos lista (expulsados en
     * paralelo, nevera borrada…) la salida se considera completada y solo se
     * limpia la copia local.
     */
    suspend operator fun invoke(neveraId: String, requesterId: String): OperationResult<Unit> {
        val snapshot = neveraRepository.getSyncSnapshot(neveraId)
            ?: return OperationResult.Error(
                "La nevera no existe",
                ErrorCode.NOT_FOUND
            )

        if (snapshot.idPropietario == requesterId) {
            return OperationResult.Error(
                "El propietario no puede salir de su propia nevera",
                ErrorCode.UNAUTHORIZED
            )
        }

        return when (snapshot.modo) {
            // Estado anómalo (una nevera de colaborador siempre es SYNCED por
            // construcción): no hay nada que quitar en remoto; limpiar la
            // copia local ya es "salir".
            ModoNevera.LOCAL -> {
                neveraRepository.eliminarNeveraLocal(neveraId)
                OperationResult.Success(Unit)
            }

            ModoNevera.SYNCED -> {
                syncManager.pauseSync(neveraId)
                try {
                    if (quitarmeEnRemoto(neveraId, requesterId)) {
                        neveraRepository.eliminarNeveraLocal(neveraId)
                        OperationResult.Success(Unit)
                    } else {
                        OperationResult.Error(
                            "No se pudo salir de la nevera, vuelve a intentarlo",
                            ErrorCode.NETWORK_ERROR
                        )
                    }
                } finally {
                    withContext(NonCancellable) {
                        syncManager.resumeSync(neveraId)
                    }
                }
            }
        }
    }

    /**
     * Quita [uid] del doc remoto. `true` si la salida consta en el servidor
     * (incluido el caso "ya estábamos fuera"); `false` si no se pudo tras el
     * único reintento.
     */
    private suspend fun quitarmeEnRemoto(neveraId: String, uid: String): Boolean {
        // 2 pasadas como máximo: el intento y UN reintento tras refrescar.
        repeat(2) { intento ->
            val doc = try {
                remoteRepository.getNevera(neveraId)
            } catch (e: Exception) {
                // Un no-miembro no puede ni leer el doc: si el get es denegado
                // es que ya no estamos en `colaboradores` — salida ya hecha.
                if (e.esPermisoDenegado()) return true
                return false
            } ?: return true // El doc ya no existe: la nevera fue borrada.

            if (uid !in doc.colaboradores) return true // Ya fuera: idempotente.

            try {
                remoteRepository.actualizarMiembros(
                    neveraId = neveraId,
                    colaboradores = doc.colaboradores - uid,
                    miembros = doc.miembros.filterNot { it.uid == uid },
                )
                return true
            } catch (e: Exception) {
                // Update rechazado o caído. Si era el primer intento, el
                // repeat refresca el doc y prueba una única vez más (cubre la
                // ventana get→update si la membresía cambió en medio).
                if (intento == 1) return false
            }
        }
        return false
    }

    private fun Throwable.esPermisoDenegado(): Boolean =
        this is FirebaseFirestoreException && code == FirestoreExceptionCode.PERMISSION_DENIED
}
