package ule.jescuj00.fridgey.data.auth

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * iOS Google Sign-In is performed in Swift (the GoogleSignIn SDK is a
 * CocoaPod and is consumed from Swift; the Kotlin/Native side never sees
 * its types). The Swift app wires a callback into [iosGoogleSignInBridge]
 * at startup; we suspend until Swift finishes and reports back through
 * one of the two completion lambdas it is given.
 */
actual class GoogleSignInHelper {

    actual suspend fun launchSignIn(): String = suspendCancellableCoroutine { cont ->
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
            { idToken -> if (cont.isActive) cont.resume(idToken) },
            { errorMessage ->
                if (cont.isActive) cont.resumeWithException(Throwable(errorMessage))
            }
        )
    }

    companion object {
        /**
         * Set from Swift at app startup (see GoogleSignInBridge.swift).
         * Signature: (onSuccess: (idToken) -> Unit, onError: (message) -> Unit) -> Unit.
         */
        var iosGoogleSignInBridge: ((onSuccess: (String) -> Unit, onError: (String) -> Unit) -> Unit)? = null
    }
}
