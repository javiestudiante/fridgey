import SwiftUI
import Shared

struct ContentView: View {

    @StateObject private var session = AuthSession()

    var body: some View {
        switch session.state {
        case .loading:
            VStack(spacing: 12) {
                ProgressView()
                Text("Cargando…")
                    .foregroundColor(.secondary)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)

        case .unauthenticated:
            LoginView()

        case .authenticated(let user):
            MainAppView(currentUserId: user.uid, onSignOut: { session.signOut() })
        }
    }
}

/// Placeholder for the main authenticated experience on iOS. The
/// SwiftUI port of NeveraList lives in a later sprint; for now the
/// screen confirms the auth flow worked end-to-end and exposes a sign-out
/// button so the gating logic can be exercised.
struct MainAppView: View {
    let currentUserId: String
    let onSignOut: () -> Void

    var body: some View {
        VStack(spacing: 16) {
            Text("Sesión iniciada")
                .font(.title2)
                .fontWeight(.semibold)
            Text("UID: \(currentUserId)")
                .font(.caption)
                .foregroundColor(.secondary)
            Button("Cerrar sesión", action: onSignOut)
                .buttonStyle(.borderedProminent)
                .padding(.top, 16)
        }
        .padding()
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView()
    }
}
