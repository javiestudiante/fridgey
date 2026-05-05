package ule.jescuj00.fridgey.domain.usecase.auth

import ule.jescuj00.fridgey.data.auth.GoogleSignInHelper
import ule.jescuj00.fridgey.data.repository.AuthRepository
import ule.jescuj00.fridgey.domain.model.auth.AuthUser

/**
 * Two-step sign-in: launch the platform UI to obtain a Google ID token,
 * then exchange that token for a Firebase credential.
 *
 * Throws on failure (no `Result<T>` wrapper — see [GoogleSignInHelper]).
 */
class SignInWithGoogleUseCase(
    private val helper: GoogleSignInHelper,
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(): AuthUser {
        val idToken = helper.launchSignIn()
        return authRepository.signInWithGoogleCredential(idToken)
    }
}
