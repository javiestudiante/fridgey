package ule.jescuj00.fridgey.data.auth

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Empty launcher — the iOS Sign-In flow is driven entirely by the Swift
 * bridge (UIWindowScene + GoogleSignIn SDK), so there is nothing to
 * carry across the boundary. Defined as a class (not `object`) so Swift
 * callers can instantiate it with `GoogleSignInLauncher()`.
 */
actual class GoogleSignInLauncher

/**
 * iOS Google Sign-In is performed in Swift (the GoogleSignIn SDK is a
 * CocoaPod and is consumed from Swift; the Kotlin/Native side never sees
 * its types). The Swift app wires a callback into [iosGoogleSignInBridge]
 * at startup; we suspend until Swift finishes and reports back through
 * one of the three completion lambdas it is given (success, error, cancel).
 *
 * The success lambda surfaces **two** strings — the Google ID token and
 * the OAuth access token — because gitlive's `GoogleAuthProvider.credential`
 * on iOS rejects a null accessToken (unlike the Android variant).
 */
actual class GoogleSignInHelper {

    actual suspend fun launchSignIn(launcher: GoogleSignInLauncher): GoogleSignInTokens =
        suspendCancellableCoroutine { cont ->
            val bridge = iosGoogleSignInBridge ?: run {
                cont.resumeWithException(
                    IllegalStateException(
                        "iosGoogleSignInBridge has not been set. The iOS app should " +
                            "wire it from iOSApp.swift before showing the login screen."
                    )
                )
                return@suspendCancellableCoroutine
            }
            bridge(
                { idToken, accessToken ->
                    if (cont.isActive) {
                        cont.resume(GoogleSignInTokens(idToken = idToken, accessToken = accessToken))
                    }
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
         * Set from Swift at app startup (see GoogleSignInBridge.swift).
         * Signature:
         *   (onSuccess: (idToken, accessToken) -> Unit,
         *    onError:   (message) -> Unit,
         *    onCancel:  () -> Unit) -> Unit
         */
        var iosGoogleSignInBridge: (
            (
                onSuccess: (String, String) -> Unit,
                onError: (String) -> Unit,
                onCancel: () -> Unit
            ) -> Unit
        )? = null
    }
}
