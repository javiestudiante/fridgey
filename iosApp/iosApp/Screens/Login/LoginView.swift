import SwiftUI
import Shared

struct LoginView: View {

    @StateObject private var viewModel = LoginViewModel()

    var body: some View {
        ZStack {
            VStack(spacing: 24) {
                Spacer()

                Text("Fridgey")
                    .font(.system(size: 56, weight: .bold))
                    .foregroundColor(.accentColor)

                Text("Inicia sesión para gestionar tus neveras")
                    .font(.body)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)

                Spacer()

                Button(action: { viewModel.onGoogleTapped() }) {
                    HStack {
                        Image(systemName: "person.crop.circle.fill")
                        Text("Continuar con Google")
                            .fontWeight(.semibold)
                    }
                    .frame(maxWidth: .infinity)
                    .padding()
                    .background(Color.accentColor)
                    .foregroundColor(.white)
                    .cornerRadius(10)
                }
                .disabled(viewModel.isLoading)

                Button(action: { viewModel.onAppleTapped() }) {
                    HStack {
                        Image(systemName: "applelogo")
                        Text("Continuar con Apple")
                            .fontWeight(.semibold)
                    }
                    .frame(maxWidth: .infinity)
                    .padding()
                    .background(Color.black)
                    .foregroundColor(.white)
                    .cornerRadius(10)
                }
                .disabled(viewModel.isLoading)

                Text("Al continuar aceptas el tratamiento de tu cuenta para la sincronización de neveras.")
                    .font(.caption)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
                    .padding(.top, 8)

                Spacer()
            }
            .padding(.horizontal, 32)

            if viewModel.isLoading {
                Color.black.opacity(0.15).ignoresSafeArea()
                ProgressView()
                    .progressViewStyle(.circular)
                    .scaleEffect(1.5)
            }
        }
        .alert(
            "Error",
            isPresented: Binding(
                get: { viewModel.errorMessage != nil },
                set: { if !$0 { viewModel.errorMessage = nil } }
            ),
            actions: { Button("OK") { viewModel.errorMessage = nil } },
            message: { Text(viewModel.errorMessage ?? "") }
        )
    }
}
