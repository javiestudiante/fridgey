package ule.jescuj00.fridgey.data.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/**
 * Android Google Sign-In via the Credential Manager API.
 *
 * `serverClientId` is the **Web** OAuth Client ID (client_type: 3) coming
 * from the project's google-services.json — the same value Firebase Auth
 * uses internally to validate the returned ID token. It must not be the
 * Android client_id.
 *
 * The activity [Context] is mandatory because Credential Manager needs to
 * present a system-managed bottom sheet UI; pass the application context
 * — the SDK will hop to the foreground activity for presentation.
 */
actual class GoogleSignInHelper(
    private val context: Context,
    private val serverClientId: String
) {
    actual suspend fun launchSignIn(): String {
        val credentialManager = CredentialManager.create(context)

        val googleIdOption = GetGoogleIdOption.Builder()
            // Don't filter by previously authorized accounts — the user may
            // be signing in for the first time on this device.
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(serverClientId)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val response = try {
            credentialManager.getCredential(context, request)
        } catch (e: GetCredentialCancellationException) {
            throw SignInCancelledException()
        }

        val credential = response.credential
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            return GoogleIdTokenCredential.createFrom(credential.data).idToken
        }
        throw IllegalStateException("Unexpected credential type: ${credential.type}")
    }
}
