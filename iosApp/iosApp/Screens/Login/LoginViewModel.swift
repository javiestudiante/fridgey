import Foundation
import Shared

/// SwiftUI ObservableObject that drives [LoginView].
///
/// The shared use cases use *throwing* semantics (see GoogleSignInHelper.kt
/// for why). Kotlin/Native exposes `suspend fun invoke(): AuthUser` to
/// Swift as a completion-handler accepting `(AuthUser?, Error?)` — we
/// surface that shape directly here.
///
/// Cancellation: the Swift bridges (Google, Apple) report user-cancellations
/// through a dedicated `onCancel` callback that the Kotlin side translates
/// into [SignInCancelledException]. The use case rethrows it; we recognise
/// it by class type in `handle(...)` and silence it without showing an
/// alert.
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

        // The launcher is a no-op on iOS (the Swift bridge drives the UI),
        // but the shared `invoke` signature requires an instance.
        signInWithGoogle.invoke(launcher: GoogleSignInLauncher()) { [weak self] user, error in
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
        guard let error = error else { return }

        // Kotlin throws `SignInCancelledException` on user cancellation;
        // the K/N exporter surfaces it in Swift as a class conforming to
        // `Error`, so a plain `is` check is enough.
        if error is SignInCancelledException { return }

        errorMessage = error.localizedDescription
    }
}
