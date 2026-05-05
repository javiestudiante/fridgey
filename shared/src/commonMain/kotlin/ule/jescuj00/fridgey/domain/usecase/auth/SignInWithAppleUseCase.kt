package ule.jescuj00.fridgey.domain.usecase.auth

import ule.jescuj00.fridgey.data.auth.AppleSignInHelper
import ule.jescuj00.fridgey.data.repository.AuthRepository
import ule.jescuj00.fridgey.domain.model.auth.AuthUser

/**
 * Two-step sign-in: launch the Swift Apple Sign-In flow to obtain
 * (idToken, rawNonce), then exchange the pair for a Firebase credential.
 */
class SignInWithAppleUseCase(
    private val helper: AppleSignInHelper,
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(): AuthUser {
        val signInResult = helper.launchSignIn()
        return authRepository.signInWithAppleCredential(
            idToken = signInResult.idToken,
            nonce = signInResult.nonce
        )
    }
}
