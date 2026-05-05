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
            NavigationStack {
                NeveraListView(
                    currentUserId: user.uid,
                    onSignOut: { session.signOut() }
                )
            }
        }
    }
}

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView()
    }
}
