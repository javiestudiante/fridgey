package ule.jescuj00.fridgey.domain.usecase

import dev.gitlive.firebase.firestore.FirebaseFirestoreException
import dev.gitlive.firebase.firestore.FirestoreExceptionCode
import kotlinx.datetime.Clock
import ule.jescuj00.fridgey.data.remote.firestore.MiembroDoc
import ule.jescuj00.fridgey.data.remote.firestore.NeveraDoc
import ule.jescuj00.fridgey.data.remote.firestore.NeveraRemoteRepository
import ule.jescuj00.fridgey.data.remote.firestore.epochSecondsOrNull
import ule.jescuj00.fridgey.data.repository.NeveraRepository
import ule.jescuj00.fridgey.data.repository.UsuarioRepository
import ule.jescuj00.fridgey.domain.model.ResultadoInvitacion

/**
 * UC-03b: el invitado acepta una invitación por código.
 *
 * Particularidad impuesta por las reglas de seguridad: un NO-miembro no puede
 * leer `neveras/{id}`, así que aquí no hay lectura previa de la nevera para
 * validar el límite — el join es "a ciegas" (arrayUnion en batch) y son las
 * REGLAS las que re-validan en el servidor: no-miembro, límite ≤ 3
 * colaboradores (4 usuarios con el dueño), invitación no expirada (reloj de
 * servidor) y un solo uso. Esa misma restricción de lectura se aprovecha como
 * sonda de pertenencia: si la nevera SÍ se puede leer, ya soy miembro.
 *
 * Idempotente por diseño: re-aceptar, doble-tap o aceptar una nevera de la
 * que ya formo parte termina en [ResultadoInvitacion.YaEresMiembro] tras
 * re-enganchar la copia local (no duplica ni falla).
 */
class AceptarInvitacionUseCase(
    private val neveraRepository: NeveraRepository,
    private val usuarioRepository: UsuarioRepository,
    private val remoteRepository: NeveraRemoteRepository,
) {

    suspend operator fun invoke(codigo: String, usuarioId: String): ResultadoInvitacion {
        // Same normalization the UI applies when displaying grouped codes
        // ("ABCD-EFGH" → "ABCDEFGH").
        val codigoNormalizado = codigo.trim().uppercase().replace("-", "").replace(" ", "")
        if (codigoNormalizado.isEmpty()) return ResultadoInvitacion.NoEncontrada

        return try {
            val invitacion = remoteRepository.getInvitacion(codigoNormalizado)
                ?: return ResultadoInvitacion.NoEncontrada

            // Membership probe: only members may read the fridge doc. Reading
            // it successfully means I'm already in → idempotent happy path
            // (also re-hydrates the local mirror after a reinstall).
            val yaMiembro = try {
                remoteRepository.getNevera(invitacion.neveraId)
            } catch (e: FirebaseFirestoreException) {
                if (e.code == FirestoreExceptionCode.PERMISSION_DENIED) null else throw e
            }
            if (yaMiembro != null) {
                engancharLocal(invitacion.neveraId, yaMiembro)
                return ResultadoInvitacion.YaEresMiembro(invitacion.neveraId, yaMiembro.nombre)
            }

            // Fail-fast pre-checks with the client clock — the rules
            // re-validate both against the SERVER clock at write time.
            if (invitacion.usada) return ResultadoInvitacion.YaUsada
            if (invitacion.expiraEn <= Clock.System.now().toEpochMilliseconds()) {
                return ResultadoInvitacion.Expirada
            }

            val perfil = usuarioRepository.getUsuarioById(usuarioId)
            val miembro = MiembroDoc(
                uid = usuarioId,
                nombre = perfil?.nombre ?: "",
                fotoUrl = perfil?.fotoUrl,
            )

            try {
                remoteRepository.aceptarInvitacion(
                    codigo = codigoNormalizado,
                    neveraId = invitacion.neveraId,
                    uid = usuarioId,
                    miembro = miembro,
                )
            } catch (e: FirebaseFirestoreException) {
                if (e.code != FirestoreExceptionCode.PERMISSION_DENIED) throw e
                // The rules rejected the join. Disambiguate by re-reading the
                // invitation (readable by any signed-in user).
                return diagnosticarRechazo(codigoNormalizado)
            }

            // Now a member → the fridge doc is readable; hook it into the
            // local mirror in SHARED mode (the SyncManager picks it up and
            // streams in the products).
            val nevera = remoteRepository.getNevera(invitacion.neveraId)
            if (nevera != null) {
                engancharLocal(invitacion.neveraId, nevera)
            }
            ResultadoInvitacion.Aceptada(
                neveraId = invitacion.neveraId,
                nombreNevera = nevera?.nombre ?: invitacion.nombreNevera,
            )
        } catch (e: Exception) {
            ResultadoInvitacion.Error(e.message ?: "No se pudo aceptar la invitación")
        }
    }

    private suspend fun engancharLocal(neveraId: String, doc: NeveraDoc) {
        neveraRepository.engancharNeveraRemota(
            neveraId = neveraId,
            doc = doc,
            updatedAtSeconds = doc.updatedAt.epochSecondsOrNull(),
        )
    }

    /**
     * The batch was rejected by the rules — figure out the most likely reason
     * from what we ARE allowed to read. Order matters: a used / expired
     * invitation explains the rejection on its own; otherwise the remaining
     * rule clause is the collaborator limit.
     */
    private suspend fun diagnosticarRechazo(codigo: String): ResultadoInvitacion {
        val invitacion = remoteRepository.getInvitacion(codigo)
            ?: return ResultadoInvitacion.NoEncontrada
        return when {
            invitacion.usada -> ResultadoInvitacion.YaUsada
            invitacion.expiraEn <= Clock.System.now().toEpochMilliseconds() ->
                ResultadoInvitacion.Expirada
            else -> ResultadoInvitacion.NeveraLlena
        }
    }
}
