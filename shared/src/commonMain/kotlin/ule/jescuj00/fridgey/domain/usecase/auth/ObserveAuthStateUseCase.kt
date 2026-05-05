package ule.jescuj00.fridgey.domain.usecase.auth

import kotlinx.coroutines.flow.Flow
import ule.jescuj00.fridgey.data.repository.AuthRepository
import ule.jescuj00.fridgey.domain.model.auth.AuthState

class ObserveAuthStateUseCase(private val authRepository: AuthRepository) {
    operator fun invoke(): Flow<AuthState> = authRepository.observeAuthState()
}
