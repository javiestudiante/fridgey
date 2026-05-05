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
/// commonMain trigger `signIn(with:)` here.
enum GoogleSignInBridge {

    static func register() {
        GoogleSignInHelper.companion.iosGoogleSignInBridge = { onSuccess, onError in
            DispatchQueue.main.async {
                signIn(onSuccess: onSuccess, onError: onError)
            }
        }
    }

    private static func signIn(
        onSuccess: @escaping (String) -> Void,
        onError: @escaping (String) -> Void
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
                onError(error.localizedDescription)
                return
            }
            guard let idToken = result?.user.idToken?.tokenString else {
                onError("Google Sign-In did not return an idToken")
                return
            }
            onSuccess(idToken)
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
