package ule.jescuj00.fridgey.data.auth

/**
 * Launches a platform-specific Google Sign-In flow.
 *
 * Returns a Google **ID token** (a signed JWT) that the
 * [ule.jescuj00.fridgey.data.repository.AuthRepository] then exchanges
 * for a Firebase credential.
 *
 * Throwing semantics (instead of `Result<String>`) because Kotlin's
 * `Result` is an inline value class and is not exportable to Swift —
 * any class that returns `Result<T>` breaks the Kotlin/Native framework
 * build for the iOS targets.
 *
 * - Cancellation by the user is signalled with [SignInCancelledException].
 * - Other failures throw the underlying exception.
 *
 * Implementations:
 *  - androidMain: Credential Manager + Google Identity Services
 *  - iosMain:     delegates to a Swift bridge using the GoogleSignIn SDK
 */
expect class GoogleSignInHelper {
    suspend fun launchSignIn(): String
}

/** Marker exception so the UI layer can recognise a user-cancelled flow. */
class SignInCancelledException : Exception("User cancelled sign-in")
