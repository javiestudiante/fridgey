package ule.jescuj00.fridgey.data.auth

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * iOS Apple Sign-In is implemented in Swift via AuthorizationServices; the
 * Swift bridge generates a cryptographic nonce, hashes it with SHA-256,
 * hands the *hashed* value to Apple, and surfaces the resulting identity
 * token plus the *raw* nonce — Firebase needs the raw value to validate
 * the token.
 *
 * The bridge has three completion lambdas: success, error, and cancel.
 * Cancellation is reported separately so the UI can silence it without
 * resorting to fragile string-matching on the error message.
 */
actual class AppleSignInHelper {

    actual suspend fun launchSignIn(): AppleSignInResult =
        suspendCancellableCoroutine { cont ->
            val bridge = iosAppleSignInBridge ?: run {
                cont.resumeWithException(
                    IllegalStateException(
                        "iosAppleSignInBridge has not been set. The iOS app should " +
                            "wire it from iOSApp.swift before showing the login screen."
                    )
                )
                return@suspendCancellableCoroutine
            }
            bridge(
                { idToken, rawNonce ->
                    if (cont.isActive) cont.resume(AppleSignInResult(idToken, rawNonce))
                },
                { errorMessage ->
                    if (cont.isActive) cont.resumeWithException(Throwable(errorMessage))
                },
                {
                    if (cont.isActive) cont.resumeWithException(SignInCancelledException())
                }
            )
        }

    companion object {
        /**
         * Set from Swift at app startup (see AppleSignInBridge.swift).
         * Signature:
         *   (onSuccess: (idToken, rawNonce) -> Unit,
         *    onError:   (message) -> Unit,
         *    onCancel:  () -> Unit) -> Unit
         */
        var iosAppleSignInBridge: (
            (
                onSuccess: (String, String) -> Unit,
                onError: (String) -> Unit,
                onCancel: () -> Unit
            ) -> Unit
        )? = null
    }
}
