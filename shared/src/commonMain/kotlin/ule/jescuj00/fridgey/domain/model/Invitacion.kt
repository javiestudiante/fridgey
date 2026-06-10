package ule.jescuj00.fridgey.domain.model

/**
 * Resultado de [ule.jescuj00.fridgey.domain.usecase.GenerarInvitacionUseCase]:
 * el código que la UI muestra (y codifica como QR) y su caducidad.
 */
data class InvitacionGenerada(
    val codigo: String,
    /** Epoch millis del instante de expiración (ahora + 24h). */
    val expiraEnMillis: Long,
)

/**
 * Estados posibles al aceptar una invitación (UC-03b). Tipo sellado a
 * propósito: la UI debe cubrirlos TODOS con un `when` exhaustivo y dar un
 * mensaje claro por cada uno.
 *
 * [Aceptada] y [YaEresMiembro] son los dos finales "felices" — el segundo
 * existe porque aceptar es idempotente: re-aceptar o aceptar una nevera de
 * la que ya formo parte no duplica ni falla, simplemente re-engancha la
 * copia local (útil tras una reinstalación).
 */
sealed interface ResultadoInvitacion {
    data class Aceptada(val neveraId: String, val nombreNevera: String) : ResultadoInvitacion
    data class YaEresMiembro(val neveraId: String, val nombreNevera: String) : ResultadoInvitacion

    /** El código no corresponde a ninguna invitación. */
    data object NoEncontrada : ResultadoInvitacion
    data object Expirada : ResultadoInvitacion
    data object YaUsada : ResultadoInvitacion

    /** La nevera ya tiene 4 usuarios (incluyendo al propietario). */
    data object NeveraLlena : ResultadoInvitacion
    data class Error(val mensaje: String) : ResultadoInvitacion
}
