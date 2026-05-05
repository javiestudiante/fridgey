import Foundation
import UIKit
import AuthenticationServices
import CryptoKit
import Shared

/// Drives the native Sign in with Apple flow and surfaces (idToken, rawNonce)
/// to the Kotlin shared module. Firebase requires the **raw** nonce to
/// validate the identity token, so we keep both around — the SHA-256 hash
/// is what gets sent to Apple, the raw value is what gets sent to Firebase.
final class AppleSignInBridge: NSObject {

    /// Singleton — Sign in with Apple needs a long-lived delegate target,
    /// otherwise the controller is deallocated mid-flow and the OS reports
    /// "presentation context not found".
    static let shared = AppleSignInBridge()

    private var rawNonce: String?
    private var onSuccess: ((String, String) -> Void)?
    private var onError: ((String) -> Void)?
    private var onCancel: (() -> Void)?

    static func register() {
        AppleSignInHelper.companion.iosAppleSignInBridge = { onSuccess, onError, onCancel in
            // Kotlin lambdas are exported with `KotlinUnit` return type.
            // Swift's implicit Void coercion does not apply when *passing*
            // such a closure as a value, only when invoking it. We wrap
            // each one in a literal closure that calls it (the return value
            // is then discarded in statement position).
            DispatchQueue.main.async {
                AppleSignInBridge.shared.start(
                    onSuccess: { idToken, rawNonce in onSuccess(idToken, rawNonce) },
                    onError: { message in onError(message) },
                    onCancel: { onCancel() }
                )
            }
        }
    }

    private func start(
        onSuccess: @escaping (String, String) -> Void,
        onError: @escaping (String) -> Void,
        onCancel: @escaping () -> Void
    ) {
        self.onSuccess = onSuccess
        self.onError = onError
        self.onCancel = onCancel

        let nonce = Self.randomNonceString()
        self.rawNonce = nonce

        let request = ASAuthorizationAppleIDProvider().createRequest()
        request.requestedScopes = [.fullName, .email]
        request.nonce = Self.sha256(nonce)

        let controller = ASAuthorizationController(authorizationRequests: [request])
        controller.delegate = self
        controller.presentationContextProvider = self
        controller.performRequests()
    }

    private static func randomNonceString(length: Int = 32) -> String {
        precondition(length > 0)
        let charset: [Character] =
            Array("0123456789ABCDEFGHIJKLMNOPQRSTUVXYZabcdefghijklmnopqrstuvwxyz-._")
        var result = ""
        var remainingLength = length
        while remainingLength > 0 {
            let randoms: [UInt8] = (0 ..< 16).map { _ in
                var random: UInt8 = 0
                _ = SecRandomCopyBytes(kSecRandomDefault, 1, &random)
                return random
            }
            for random in randoms {
                if remainingLength == 0 { break }
                if random < charset.count {
                    result.append(charset[Int(random)])
                    remainingLength -= 1
                }
            }
        }
        return result
    }

    private static func sha256(_ input: String) -> String {
        let inputData = Data(input.utf8)
        let hashed = SHA256.hash(data: inputData)
        return hashed.map { String(format: "%02x", $0) }.joined()
    }

    private func clearCallbacks() {
        self.onSuccess = nil
        self.onError = nil
        self.onCancel = nil
        self.rawNonce = nil
    }
}

extension AppleSignInBridge: ASAuthorizationControllerDelegate {
    func authorizationController(
        controller: ASAuthorizationController,
        didCompleteWithAuthorization authorization: ASAuthorization
    ) {
        defer { clearCallbacks() }
        guard let credential = authorization.credential as? ASAuthorizationAppleIDCredential else {
            onError?("Unexpected Apple credential type")
            return
        }
        guard let tokenData = credential.identityToken,
              let idToken = String(data: tokenData, encoding: .utf8) else {
            onError?("Apple did not return an identity token")
            return
        }
        guard let rawNonce = self.rawNonce else {
            onError?("Missing raw nonce after Apple Sign-In")
            return
        }
        onSuccess?(idToken, rawNonce)
    }

    func authorizationController(
        controller: ASAuthorizationController,
        didCompleteWithError error: Error
    ) {
        defer { clearCallbacks() }
        // Cancellation is reported through a dedicated callback so the UI
        // can swallow it without inspecting localized error strings.
        if let asError = error as? ASAuthorizationError, asError.code == .canceled {
            onCancel?()
            return
        }
        onError?(error.localizedDescription)
    }
}

extension AppleSignInBridge: ASAuthorizationControllerPresentationContextProviding {
    func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        let scene = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first { $0.activationState == .foregroundActive }
        return scene?.windows.first(where: { $0.isKeyWindow }) ?? UIWindow()
    }
}
