import SwiftUI
import Shared

/// Editorial-kitchen login screen.
///
/// The original screen used solid-colour Material-style buttons; the new
/// design canvases the SSO CTAs on the cream background, with a serif
/// wordmark and an italic tagline above them. Functional plumbing
/// (`LoginViewModel`, the two SSO bridges, the navigation hand-off via
/// `AuthSession`) is unchanged — this is a visual rewrite only.
struct LoginView: View {

    @StateObject private var viewModel = LoginViewModel()

    var body: some View {
        ZStack {
            Color.fridgeyCream
                .ignoresSafeArea()

            VStack(spacing: 0) {
                Spacer()

                // --- Branding ---------------------------------------------
                VStack(spacing: FridgeySpacing.xs) {
                    Text("Fridgey.")
                        .font(FridgeyFont.displayLarge)
                        .foregroundStyle(Color.fridgeyInk)
                    Text("menos basura, más cena.")
                        .font(FridgeyFont.headlineMedium)
                        .italic()
                        .foregroundStyle(Color.fridgeyMintDeep)
                        .multilineTextAlignment(.center)
                }

                Spacer()

                // --- SSO --------------------------------------------------
                VStack(spacing: FridgeySpacing.md) {
                    googleButton
                    appleButton
                }

                Spacer()

                // --- Disclaimer -------------------------------------------
                Text("Al continuar aceptas nuestros términos.")
                    .font(FridgeyFont.bodySmall)
                    .foregroundStyle(Color.fridgeyInkMuted)
                    .multilineTextAlignment(.center)
                    .padding(.bottom, FridgeySpacing.xl)
            }
            .padding(.horizontal, FridgeySpacing.xl)

            if viewModel.isLoading {
                Color.black.opacity(0.15).ignoresSafeArea()
                ProgressView()
                    .tint(Color.fridgeyMint)
                    .scaleEffect(1.3)
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

    /// White CTA card with a subtle outline and a "Continuar con Google"
    /// label, preceded by the official Google "G" mark backed by the
    /// `google_logo` imageset in `Assets.xcassets` (SVG, vector-preserved).
    ///
    /// `.renderingMode(.original)` is load-bearing: without it SwiftUI
    /// would tint every shape in the SVG with the ambient `foregroundStyle`,
    /// collapsing the four-colour Google logo into a single tinted blob.
    private var googleButton: some View {
        Button(action: { viewModel.onGoogleTapped() }) {
            HStack(spacing: FridgeySpacing.md) {
                Image("google_logo")
                    .resizable()
                    .renderingMode(.original)
                    .aspectRatio(contentMode: .fit)
                    .frame(width: 20, height: 20)
                Text("Continuar con Google")
                    .font(FridgeyFont.labelLarge)
                    .foregroundStyle(Color.fridgeyInk)
            }
            .frame(maxWidth: .infinity)
            .frame(height: 56)
            .background(Color.fridgeySurfaceWhite)
            .clipShape(RoundedRectangle(cornerRadius: FridgeyRadius.sm))
            .overlay(
                RoundedRectangle(cornerRadius: FridgeyRadius.sm)
                    .stroke(Color.fridgeyInk.opacity(0.08), lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
        .disabled(viewModel.isLoading)
    }

    /// "Continue with Apple" CTA, styled to match Apple's HIG.
    ///
    /// We deliberately do NOT use SwiftUI's native `SignInWithAppleButton`.
    /// `AppleSignInBridge` already drives the full `ASAuthorizationController`
    /// flow internally — it generates the nonce, builds the request, presents
    /// the system sheet via its own delegate, extracts the identity token,
    /// and surfaces `(idToken, rawNonce)` to the shared Kotlin layer.
    ///
    /// Wiring `SignInWithAppleButton` AND the bridge would launch two
    /// competing Apple-Sign-In sheets (the SwiftUI button initiates its own
    /// `ASAuthorizationController`, and the bridge then initiates a second
    /// one). The only way to use `SignInWithAppleButton` cleanly would be
    /// to refactor `AppleSignInBridge` so it only handles the
    /// post-credential processing — both bridge and `LoginViewModel` are
    /// off-limits in this sprint.
    ///
    /// Instead this is a plain `Button` styled like Apple's official CTA:
    /// solid black background, SF Symbol `applelogo`, system-sized text.
    /// At this scale it is visually indistinguishable from
    /// `SignInWithAppleButton(.continue)`, and the system-rendered sheet
    /// that comes up after the tap is the canonical Apple-branded one.
    private var appleButton: some View {
        Button(action: { viewModel.onAppleTapped() }) {
            HStack(spacing: FridgeySpacing.sm) {
                Image(systemName: "applelogo")
                    .font(.system(size: 18, weight: .medium))
                Text("Continuar con Apple")
                    .font(FridgeyFont.labelLarge)
            }
            .foregroundStyle(Color.fridgeySurfaceWhite)
            .frame(maxWidth: .infinity)
            .frame(height: 56)
            .background(Color.black)
            .clipShape(RoundedRectangle(cornerRadius: FridgeyRadius.sm))
        }
        .buttonStyle(.plain)
        .disabled(viewModel.isLoading)
    }
}
