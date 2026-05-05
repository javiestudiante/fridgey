import Foundation
import Combine
import Shared

/// Observes the Kotlin `Flow<AuthState>` from the shared module and
/// republishes the result as a Swift @Published value so SwiftUI views
/// can react to it via `@StateObject` / `@ObservedObject`.
///
/// Uses an [AuthStateBinder] from the shared layer because Kotlin/Native
/// doesn't expose `Flow` directly to Swift in a usable form.
@MainActor
final class AuthSession: ObservableObject {

    enum State {
        case loading
        case authenticated(AuthUser)
        case unauthenticated
    }

    @Published private(set) var state: State = .loading

    private let binder: AuthStateBinder = KoinIosKt.getAuthStateBinder()

    init() {
        binder.start(
            onValue: { [weak self] kotlinState in
                Task { @MainActor [weak self] in
                    self?.apply(kotlinState)
                }
            },
            onError: { [weak self] _ in
                Task { @MainActor [weak self] in
                    // Treat upstream flow errors as "logged out" — the
                    // login screen lets the user retry.
                    self?.state = .unauthenticated
                }
            }
        )
    }

    private func apply(_ kotlinState: AuthState) {
        // K/N exports the sealed-class subtypes as nested Swift types via
        // `swift_name("AuthState.Authenticated")` etc. — use the dotted
        // form here, NOT the flat ObjC name (`AuthStateAuthenticated`).
        if let authed = kotlinState as? AuthState.Authenticated {
            state = .authenticated(authed.user)
        } else if kotlinState is AuthState.Loading {
            state = .loading
        } else {
            // Unauthenticated and Error both lead to the login screen.
            state = .unauthenticated
        }
    }

    func signOut() {
        let useCase = KoinIosKt.getSignOutUseCase()
        // SignOutUseCase returns Unit, so the Swift completion handler
        // takes only `(Error?) -> Void` — one argument, not two.
        useCase.invoke { _ in /* the auth-state flow will pick up the change */ }
    }

    deinit {
        binder.dispose()
    }
}
