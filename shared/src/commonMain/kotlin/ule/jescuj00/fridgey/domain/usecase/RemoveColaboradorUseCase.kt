package ule.jescuj00.fridgey.domain.usecase

import ule.jescuj00.fridgey.data.repository.NeveraRepository
import ule.jescuj00.fridgey.domain.model.ErrorCode
import ule.jescuj00.fridgey.domain.model.OperationResult

class RemoveColaboradorUseCase(private val neveraRepository: NeveraRepository) {

    /**
     * Removes [usuarioId] from [neveraId].
     * - Owner can remove any collaborator.
     * - Collaborators can only remove themselves.
     * - The owner cannot be removed.
     */
    suspend operator fun invoke(
        neveraId: String,
        usuarioId: String,
        requesterId: String
    ): OperationResult<Unit> {
        val isRequesterOwner = neveraRepository.isOwner(neveraId, requesterId)

        // Cannot remove the owner
        if (neveraRepository.isOwner(neveraId, usuarioId)) {
            return OperationResult.Error(
                "No se puede eliminar al propietario de la nevera",
                ErrorCode.UNAUTHORIZED
            )
        }

        // Non-owner collaborators can only remove themselves
        if (!isRequesterOwner && requesterId != usuarioId) {
            return OperationResult.Error(
                "Solo el propietario puede eliminar a otros colaboradores",
                ErrorCode.UNAUTHORIZED
            )
        }

        return try {
            neveraRepository.removeColaborador(neveraId, usuarioId)
            OperationResult.Success(Unit)
        } catch (e: Exception) {
            OperationResult.Error(
                "Error al eliminar colaborador: ${e.message}",
                ErrorCode.DATABASE_ERROR
            )
        }
    }
}
