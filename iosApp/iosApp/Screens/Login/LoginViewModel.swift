import Foundation
import Shared

/// SwiftUI ObservableObject that drives [LoginView].
///
/// The shared use cases use *throwing* semantics (see GoogleSignInHelper.kt
/// for why). Kotlin/Native exposes `suspend fun invoke(): AuthUser` to
/// Swift as a completion-handler accepting `(AuthUser?, Error?)` — we
/// surface that shape directly here.
@MainActor
final class LoginViewModel: ObservableObject {

    @Published var isLoading: Bool = false
    @Published var errorMessage: String?

    private let signInWithGoogle = KoinIosKt.getSignInWithGoogleUseCase()
    private let signInWithApple = KoinIosKt.getSignInWithAppleUseCase()

    func onGoogleTapped() {
        guard !isLoading else { return }
        isLoading = true
        errorMessage = nil

        signInWithGoogle.invoke { [weak self] user, error in
            Task { @MainActor [weak self] in
                self?.handle(user: user, error: error)
            }
        }
    }

    func onAppleTapped() {
        guard !isLoading else { return }
        isLoading = true
        errorMessage = nil

        signInWithApple.invoke { [weak self] user, error in
            Task { @MainActor [weak self] in
                self?.handle(user: user, error: error)
            }
        }
    }

    /// On success we don't navigate from here — the auth-state flow that
    /// `ContentView` observes will rerender into the main app once Firebase
    /// emits the new authenticated user.
    private func handle(user: AuthUser?, error: Error?) {
        isLoading = false
        if let error = error {
            let message = error.localizedDescription
            // Suppress user-cancellations silently.
            if message.lowercased().contains("cancel") { return }
            errorMessage = message
        }
    }
}
