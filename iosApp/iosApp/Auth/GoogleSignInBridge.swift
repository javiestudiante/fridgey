import Foundation
import UIKit
import GoogleSignIn
import FirebaseCore
import Shared

/// Wraps the GoogleSignIn iOS SDK so the Kotlin shared module can request
/// an idToken without depending on any Apple framework.
///
/// `register()` installs the bridge into `GoogleSignInHelper.companion`;
/// once installed, calls to `GoogleSignInHelper.launchSignIn()` from
/// commonMain trigger `signIn(...)` here.
enum GoogleSignInBridge {

    static func register() {
        GoogleSignInHelper.companion.iosGoogleSignInBridge = { onSuccess, onError, onCancel in
            // Kotlin lambdas are exported with `KotlinUnit` return type.
            // Swift's implicit Void coercion does not apply when *passing*
            // such a closure as a value, only when invoking it. We wrap
            // each one in a literal closure that calls it (the return value
            // is then discarded in statement position).
            DispatchQueue.main.async {
                signIn(
                    onSuccess: { idToken, accessToken in onSuccess(idToken, accessToken) },
                    onError: { message in onError(message) },
                    onCancel: { onCancel() }
                )
            }
        }
    }

    private static func signIn(
        onSuccess: @escaping (String, String) -> Void,
        onError: @escaping (String) -> Void,
        onCancel: @escaping () -> Void
    ) {
        guard let clientID = FirebaseApp.app()?.options.clientID else {
            onError("FirebaseApp.clientID is nil — is GoogleService-Info.plist included in the target?")
            return
        }

        let config = GIDConfiguration(clientID: clientID)
        GIDSignIn.sharedInstance.configuration = config

        guard let presentingVC = topMostViewController() else {
            onError("Could not find a presenting view controller for Google Sign-In")
            return
        }

        GIDSignIn.sharedInstance.signIn(withPresenting: presentingVC) { result, error in
            if let error = error {
                // Cancellation is reported through a dedicated callback so
                // the UI doesn't have to inspect localized strings.
                let nsError = error as NSError
                if nsError.domain == kGIDSignInErrorDomain,
                   nsError.code == GIDSignInError.canceled.rawValue {
                    onCancel()
                    return
                }
                onError(error.localizedDescription)
                return
            }
            guard let idToken = result?.user.idToken?.tokenString else {
                onError("Google Sign-In did not return an idToken")
                return
            }
            // GIDGoogleUser.accessToken is non-optional on a successful sign-in.
            // gitlive's GoogleAuthProvider.credential on iOS requires a
            // non-null accessToken, so we surface it alongside the idToken.
            let accessToken = result!.user.accessToken.tokenString
            onSuccess(idToken, accessToken)
        }
    }

    /// The keyWindow's rootViewController, drilling through any presented modal
    /// hierarchies. SwiftUI uses a single keyed window, so this is good enough
    /// for the login flow.
    private static func topMostViewController() -> UIViewController? {
        let scene = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first { $0.activationState == .foregroundActive }
        let root = scene?.windows.first(where: { $0.isKeyWindow })?.rootViewController
        var current = root
        while let presented = current?.presentedViewController {
            current = presented
        }
        return current
    }
}
