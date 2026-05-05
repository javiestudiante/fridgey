package ule.jescuj00.fridgey.domain.model.auth

/**
 * Closed set of error categories the UI is expected to handle. Anything
 * not covered by the named cases falls into [Unknown] with the original
 * message preserved for logging.
 */
sealed class AuthError(val message: String) {
    object UserCancelled : AuthError("User cancelled the sign-in flow")
    object NetworkError : AuthError("Network error during authentication")
    object InvalidCredentials : AuthError("Invalid credentials")
    data class Unknown(val cause: String) : AuthError(cause)
}
