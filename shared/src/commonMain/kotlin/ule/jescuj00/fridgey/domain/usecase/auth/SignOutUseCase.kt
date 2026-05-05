package ule.jescuj00.fridgey.domain.usecase.auth

import ule.jescuj00.fridgey.data.repository.AuthRepository

class SignOutUseCase(private val authRepository: AuthRepository) {
    suspend operator fun invoke() = authRepository.signOut()
}
