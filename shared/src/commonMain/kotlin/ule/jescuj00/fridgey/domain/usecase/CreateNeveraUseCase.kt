package ule.jescuj00.fridgey.domain.usecase

import ule.jescuj00.fridgey.data.repository.NeveraRepository
import ule.jescuj00.fridgey.domain.model.ErrorCode
import ule.jescuj00.fridgey.domain.model.OperationResult

class CreateNeveraUseCase(private val neveraRepository: NeveraRepository) {

    companion object {
        const val MAX_NEVERAS_PER_USER = 10
    }

    /**
     * Creates a new fridge if the name is valid and the user hasn't exceeded the limit.
     * Returns the new fridge ID on success.
     */
    suspend operator fun invoke(
        nombre: String,
        idPropietario: String
    ): OperationResult<String> {
        val trimmedName = nombre.trim()
        if (trimmedName.isEmpty()) {
            return OperationResult.Error(
                "El nombre de la nevera no puede estar vacío",
                ErrorCode.INVALID_INPUT
            )
        }

        val currentCount = neveraRepository.countNeverasByPropietario(idPropietario)
        if (currentCount >= MAX_NEVERAS_PER_USER) {
            return OperationResult.Error(
                "Has alcanzado el límite de $MAX_NEVERAS_PER_USER neveras",
                ErrorCode.INVALID_INPUT
            )
        }

        return try {
            val id = neveraRepository.createNevera(trimmedName, idPropietario)
            OperationResult.Success(id)
        } catch (e: Exception) {
            OperationResult.Error(
                "Error al crear la nevera: ${e.message}",
                ErrorCode.DATABASE_ERROR
            )
        }
    }
}
