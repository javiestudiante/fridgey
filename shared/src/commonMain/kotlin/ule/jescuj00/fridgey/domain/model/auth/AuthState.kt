package ule.jescuj00.fridgey.domain.model.auth

/**
 * Reactive auth state observed by the UI to decide between login screen
 * and the rest of the app.
 *
 * [Loading] is the *initial* state emitted before Firebase has hydrated
 * the persisted user from disk; the UI should hold a splash / progress
 * indicator instead of flashing the login screen.
 */
sealed class AuthState {
    object Loading : AuthState()
    object Unauthenticated : AuthState()
    data class Authenticated(val user: AuthUser) : AuthState()
    data class Error(val message: String) : AuthState()
}
