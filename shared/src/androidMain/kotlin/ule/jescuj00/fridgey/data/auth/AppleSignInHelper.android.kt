package ule.jescuj00.fridgey.data.auth

/**
 * Android does not support native Apple Sign-In. The login screen hides the
 * Apple button when the platform is Android, so [launchSignIn] should never
 * actually be invoked here — it throws if it ever is.
 */
actual class AppleSignInHelper {
    actual suspend fun launchSignIn(): AppleSignInResult =
        throw NotImplementedError("Apple Sign-In is not supported on Android")
}
