package ule.jescuj00.fridgey.domain.usecase

import ule.jescuj00.fridgey.data.repository.NeveraRepository
import ule.jescuj00.fridgey.data.repository.UsuarioRepository
import ule.jescuj00.fridgey.domain.model.ErrorCode
import ule.jescuj00.fridgey.domain.model.OperationResult

class AddColaboradorUseCase(
    private val neveraRepository: NeveraRepository,
    private val usuarioRepository: UsuarioRepository
) {

    companion object {
        /** Max users per fridge including owner. */
        const val MAX_USERS_PER_NEVERA = 4
    }

    /**
     * Adds [usuarioId] as a collaborator to [neveraId].
     * Only the owner ([requesterId]) can add collaborators.
     */
    suspend operator fun invoke(
        neveraId: String,
        usuarioId: String,
        requesterId: String
    ): OperationResult<Unit> {
        // Requester must be the owner
        if (!neveraRepository.isOwner(neveraId, requesterId)) {
            return OperationResult.Error(
                "Solo el propietario puede añadir colaboradores",
                ErrorCode.UNAUTHORIZED
            )
        }

        // Target user must exist
        if (usuarioRepository.getUsuarioById(usuarioId) == null) {
            return OperationResult.Error(
                "El usuario no existe",
                ErrorCode.NOT_FOUND
            )
        }

        // Cannot add the owner as collaborator
        if (usuarioId == requesterId) {
            return OperationResult.Error(
                "El propietario ya forma parte de la nevera",
                ErrorCode.INVALID_INPUT
            )
        }

        // Check existing collaborators for duplicates and limit
        val colaboradores = neveraRepository.getColaboradores(neveraId)
        if (colaboradores.any { it.id == usuarioId }) {
            return OperationResult.Error(
                "El usuario ya es colaborador de esta nevera",
                ErrorCode.INVALID_INPUT
            )
        }

        // +1 for the owner
        val totalUsers = colaboradores.size + 1
        if (totalUsers >= MAX_USERS_PER_NEVERA) {
            return OperationResult.Error(
                "La nevera ha alcanzado el límite de $MAX_USERS_PER_NEVERA usuarios",
                ErrorCode.MAX_COLABORADORES_REACHED
            )
        }

        return try {
            neveraRepository.addColaborador(neveraId, usuarioId)
            OperationResult.Success(Unit)
        } catch (e: Exception) {
            OperationResult.Error(
                "Error al añadir colaborador: ${e.message}",
                ErrorCode.DATABASE_ERROR
            )
        }
    }
}
