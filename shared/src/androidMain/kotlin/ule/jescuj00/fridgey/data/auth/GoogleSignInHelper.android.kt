package ule.jescuj00.fridgey.data.auth

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/**
 * Android Google Sign-In via the Credential Manager API.
 *
 * `serverClientId` is the **Web** OAuth Client ID (client_type: 3) coming
 * from the project's google-services.json — the same value Firebase Auth
 * uses internally to validate the returned ID token. It must not be the
 * Android client_id.
 *
 * The Activity is supplied per-call (not stored in the helper) so the
 * helper itself can be a singleton without leaking Activity references.
 * Credential Manager requires an Activity context to render its bottom
 * sheet UI; passing the Application context produces opaque "no
 * credentials" failures on some devices.
 */
actual typealias GoogleSignInLauncher = Activity

actual class GoogleSignInHelper(
    private val serverClientId: String
) {
    actual suspend fun launchSignIn(launcher: GoogleSignInLauncher): GoogleSignInTokens {
        val credentialManager = CredentialManager.create(launcher)

        // GetSignInWithGoogleOption is the primitive Google recommends for
        // explicit "Sign in with Google" buttons (post-2024 Credential
        // Manager guidance). GetGoogleIdOption is meant for *passive*
        // sign-in on app launch and frequently throws NoCredentialException
        // for first-time users even with setFilterByAuthorizedAccounts(false).
        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId).build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        val response = try {
            credentialManager.getCredential(launcher, request)
        } catch (e: GetCredentialCancellationException) {
            throw SignInCancelledException()
        } catch (e: NoCredentialException) {
            throw NoGoogleAccountException()
        }

        val credential = response.credential
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            // Credential Manager does not expose an OAuth access token; the
            // Firebase Android SDK accepts a null accessToken when an idToken
            // is provided, so this is fine on this platform.
            val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
            return GoogleSignInTokens(idToken = idToken, accessToken = null)
        }
        throw IllegalStateException("Unexpected credential type: ${credential.type}")
    }
}
