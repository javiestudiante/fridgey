package ule.jescuj00.fridgey.data.repository

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.FirebaseUser
import dev.gitlive.firebase.auth.GoogleAuthProvider
import dev.gitlive.firebase.auth.OAuthProvider
import dev.gitlive.firebase.auth.auth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import ule.jescuj00.fridgey.domain.model.Proveedor
import ule.jescuj00.fridgey.domain.model.Usuario
import ule.jescuj00.fridgey.domain.model.auth.AuthState
import ule.jescuj00.fridgey.domain.model.auth.AuthUser

/**
 * Single entry point for authentication. Wraps the gitlive Firebase Auth
 * SDK and keeps the local SQLDelight `Usuario` table in sync, so existing
 * repositories that key off `usuarioId` keep working unchanged after a
 * sign-in.
 *
 * Methods that can fail throw the underlying exception instead of
 * returning `kotlin.Result` — `Result` is a Kotlin inline value class and
 * is not exportable to Swift, so any class crossing the iOS framework
 * boundary must use throwing semantics.
 */
class AuthRepository(
    private val auth: FirebaseAuth = Firebase.auth,
    private val usuarioRepository: UsuarioRepository
) {

    /** Cold flow of [AuthState]. Emits [AuthState.Loading] up front while the
     *  Firebase SDK rehydrates the persisted user, then settles on
     *  [AuthState.Authenticated] / [AuthState.Unauthenticated]. */
    fun observeAuthState(): Flow<AuthState> =
        auth.authStateChanged
            .map<FirebaseUser?, AuthState> { user ->
                if (user == null) AuthState.Unauthenticated
                else AuthState.Authenticated(user.toAuthUser(defaultProvider = inferProvider(user)))
            }
            .onStart { emit(AuthState.Loading) }

    fun getCurrentUser(): AuthUser? =
        auth.currentUser?.toAuthUser(defaultProvider = inferProvider(auth.currentUser))

    suspend fun signInWithGoogleCredential(idToken: String): AuthUser {
        val credential = GoogleAuthProvider.credential(idToken = idToken, accessToken = null)
        val result = auth.signInWithCredential(credential)
        val firebaseUser = result.user ?: error("Firebase returned a null user after Google sign-in")
        val authUser = firebaseUser.toAuthUser(defaultProvider = Proveedor.GOOGLE)
        syncUserToLocalDb(authUser)
        return authUser
    }

    suspend fun signInWithAppleCredential(idToken: String, nonce: String): AuthUser {
        val credential = OAuthProvider.credential(
            providerId = "apple.com",
            idToken = idToken,
            rawNonce = nonce,
            accessToken = null
        )
        val result = auth.signInWithCredential(credential)
        val firebaseUser = result.user ?: error("Firebase returned a null user after Apple sign-in")
        val authUser = firebaseUser.toAuthUser(defaultProvider = Proveedor.APPLE)
        syncUserToLocalDb(authUser)
        return authUser
    }

    suspend fun signOut() {
        auth.signOut()
    }

    /** First-time sign-in: insert a row in `Usuario`. Subsequent sign-ins are
     *  upserts (the `Usuario.sq` insert uses INSERT OR REPLACE). */
    private suspend fun syncUserToLocalDb(authUser: AuthUser) {
        val existing = usuarioRepository.getUsuarioById(authUser.uid)
        if (existing == null) {
            usuarioRepository.insertUsuario(
                Usuario(
                    id = authUser.uid,
                    email = authUser.email ?: "",
                    nombre = authUser.displayName ?: authUser.email?.substringBefore("@") ?: "Usuario",
                    proveedor = authUser.provider,
                    fotoUrl = authUser.photoUrl
                )
            )
        }
    }

    private fun FirebaseUser.toAuthUser(defaultProvider: Proveedor): AuthUser =
        AuthUser(
            uid = uid,
            email = email,
            displayName = displayName,
            photoUrl = photoURL,
            provider = defaultProvider
        )

    private fun inferProvider(user: FirebaseUser?): Proveedor {
        if (user == null) return Proveedor.GOOGLE
        // providerData lists the linked providers; pick the first non-Firebase one.
        val ids = user.providerData.map { it.providerId }
        return when {
            ids.any { it == "apple.com" } -> Proveedor.APPLE
            ids.any { it == "google.com" } -> Proveedor.GOOGLE
            else -> Proveedor.GOOGLE
        }
    }
}
