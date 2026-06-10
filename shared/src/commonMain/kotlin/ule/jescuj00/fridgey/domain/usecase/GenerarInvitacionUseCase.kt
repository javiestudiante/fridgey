package ule.jescuj00.fridgey.domain.usecase

import kotlinx.datetime.Clock
import ule.jescuj00.fridgey.data.remote.firestore.InvitacionDoc
import ule.jescuj00.fridgey.data.remote.firestore.NeveraRemoteRepository
import ule.jescuj00.fridgey.data.repository.NeveraRepository
import ule.jescuj00.fridgey.domain.model.ErrorCode
import ule.jescuj00.fridgey.domain.model.InvitacionGenerada
import ule.jescuj00.fridgey.domain.model.ModoNevera
import ule.jescuj00.fridgey.domain.model.OperationResult
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * UC-03a: el propietario genera una invitación (código único + QR) para su
 * nevera colaborativa.
 *
 * El límite de 4 usuarios (propietario incluido) se comprueba ANTES de crear
 * la invitación, contra el estado del SERVIDOR — y las reglas lo vuelven a
 * imponer al aceptar, así que el límite está verificado en ambos extremos.
 *
 * Operación online por naturaleza: las reglas de `invitaciones` hacen get()
 * de la nevera y acotan `expiraEn` con el reloj del servidor, así que sin
 * conexión devuelve [ErrorCode.NETWORK_ERROR] en vez de encolar nada.
 */
class GenerarInvitacionUseCase(
    private val neveraRepository: NeveraRepository,
    private val remoteRepository: NeveraRemoteRepository,
) {

    companion object {
        /** Máximo de colaboradores en el array (el propietario va aparte). */
        const val MAX_COLABORADORES = 3

        const val VALIDEZ_HORAS = 24

        /** Sin I/O/0/1: evita ambigüedades al teclear el código a mano. */
        private const val ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        internal const val LONGITUD_CODIGO = 8

        /**
         * Código aleatorio de [LONGITUD_CODIGO] caracteres sobre [ALFABETO].
         * Se alimenta de [Uuid.random] (CSPRNG en Android); como 256 % 32 == 0,
         * el módulo por byte es uniforme. 32^8 ≈ 1,1e12 combinaciones para
         * códigos de un solo uso con 24h de vida: colisión y fuerza bruta
         * despreciables.
         */
        @OptIn(ExperimentalUuidApi::class)
        internal fun generarCodigo(): String {
            val bytes = Uuid.random().toByteArray()
            return buildString {
                repeat(LONGITUD_CODIGO) { i ->
                    append(ALFABETO[(bytes[i].toInt() and 0xFF) % ALFABETO.length])
                }
            }
        }
    }

    suspend operator fun invoke(
        neveraId: String,
        requesterId: String
    ): OperationResult<InvitacionGenerada> {
        val snapshot = neveraRepository.getSyncSnapshot(neveraId)
            ?: return OperationResult.Error("La nevera no existe", ErrorCode.NOT_FOUND)

        if (snapshot.idPropietario != requesterId) {
            return OperationResult.Error(
                "Solo el propietario puede invitar a la nevera",
                ErrorCode.UNAUTHORIZED
            )
        }

        when (snapshot.modo) {
            ModoNevera.LOCAL -> return OperationResult.Error(
                "Haz la nevera colaborativa antes de invitar",
                ErrorCode.INVALID_INPUT
            )
            ModoNevera.SHARED -> Unit
        }

        return try {
            // Estado del servidor: si el doc aún no existe allí, el upload de
            // la transición sigue en la cola offline y la invitación no puede
            // crearse todavía (las reglas hacen get() de la nevera).
            val neveraDoc = remoteRepository.getNevera(neveraId)
                ?: return OperationResult.Error(
                    "La nevera aún se está sincronizando. Comprueba tu conexión e inténtalo de nuevo",
                    ErrorCode.NETWORK_ERROR
                )

            if (neveraDoc.colaboradores.size >= MAX_COLABORADORES) {
                return OperationResult.Error(
                    "La nevera ha alcanzado el límite de 4 usuarios",
                    ErrorCode.MAX_COLABORADORES_REACHED
                )
            }

            val codigo = generarCodigo()
            val expiraEn = Clock.System.now().toEpochMilliseconds() + VALIDEZ_HORAS * 60L * 60L * 1000L
            remoteRepository.createInvitacion(
                codigo = codigo,
                invitacion = InvitacionDoc(
                    neveraId = neveraId,
                    creadaPor = requesterId,
                    expiraEn = expiraEn,
                    usada = false,
                    nombreNevera = snapshot.nombre,
                ),
            )
            OperationResult.Success(InvitacionGenerada(codigo = codigo, expiraEnMillis = expiraEn))
        } catch (e: Exception) {
            OperationResult.Error(
                "No se pudo crear la invitación: ${e.message}",
                ErrorCode.NETWORK_ERROR
            )
        }
    }
}
