package ule.jescuj00.fridgey.data.auth

/**
 * Result of an Apple Sign-In flow.
 *
 * Firebase needs **both** the identity token and the *raw* (un-hashed)
 * nonce; the nonce passed to Apple is the SHA-256 of [nonce], so the iOS
 * bridge must keep the raw value around and surface it here.
 */
data class AppleSignInResult(val idToken: String, val nonce: String)

/**
 * Launches a platform-specific Apple Sign-In flow.
 *
 * Throwing semantics (no `Result<T>` wrapper) — see [GoogleSignInHelper]
 * for the rationale.
 *
 * Implementations:
 *  - androidMain: throws [NotImplementedError] — the Android login screen
 *    hides the Apple button on this platform.
 *  - iosMain: delegates to a Swift bridge using AuthorizationServices.
 */
expect class AppleSignInHelper {
    suspend fun launchSignIn(): AppleSignInResult
}
