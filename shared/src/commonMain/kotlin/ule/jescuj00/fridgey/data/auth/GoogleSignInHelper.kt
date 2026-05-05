package ule.jescuj00.fridgey.data.auth

/**
 * Platform-specific wrapper for the host needed to present the Google
 * Sign-In UI:
 *  - androidMain: typealias to `android.app.Activity` (Credential Manager
 *    requires an Activity context to render its bottom sheet).
 *  - iosMain: an empty class — the iOS flow is driven by a Swift bridge
 *    and the launcher is unused on that side.
 *
 * Modelled as an `expect class` (rather than a generic `Any`) so the
 * Android side gets compile-time safety and the iOS side stays type-clean.
 */
expect class GoogleSignInLauncher

/**
 * Tokens returned by a successful Google Sign-In flow.
 *
 * - [idToken] is the signed JWT used to authenticate the user with Firebase.
 * - [accessToken] is the OAuth 2.0 access token. It is `null` on Android
 *   (Credential Manager / GetSignInWithGoogleOption does not surface it)
 *   and non-null on iOS (the GoogleSignIn iOS SDK always returns one on a
 *   successful sign-in). The distinction matters because gitlive's
 *   `GoogleAuthProvider.credential` on iOS rejects a `null` accessToken
 *   even when an idToken is present, while the Android counterpart
 *   accepts it.
 */
data class GoogleSignInTokens(
    val idToken: String,
    val accessToken: String?
)

/**
 * Launches a platform-specific Google Sign-In flow.
 *
 * Returns a [GoogleSignInTokens] that the
 * [ule.jescuj00.fridgey.data.repository.AuthRepository] then exchanges
 * for a Firebase credential.
 *
 * Throwing semantics (instead of a `Result` wrapper) because Kotlin's
 * `Result` is an inline value class and is not exportable to Swift —
 * any class that returns `Result<T>` breaks the Kotlin/Native framework
 * build for the iOS targets.
 *
 * - Cancellation by the user is signalled with [SignInCancelledException].
 * - "No Google account on this device" is signalled with
 *   [NoGoogleAccountException].
 * - Other failures throw the underlying exception.
 *
 * Implementations:
 *  - androidMain: Credential Manager + `GetSignInWithGoogleOption`
 *  - iosMain:     delegates to a Swift bridge using the GoogleSignIn SDK
 */
expect class GoogleSignInHelper {
    suspend fun launchSignIn(launcher: GoogleSignInLauncher): GoogleSignInTokens
}

/** Marker exception so the UI layer can recognise a user-cancelled flow. */
class SignInCancelledException : Exception("User cancelled sign-in")

/** Thrown when the device has no Google account configured (or none that
 *  Credential Manager can surface). UI should prompt the user to add one. */
class NoGoogleAccountException : Exception(
    "No hay cuentas Google en este dispositivo. Añade una desde Ajustes."
)
