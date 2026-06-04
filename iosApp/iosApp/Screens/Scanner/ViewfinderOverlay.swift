import SwiftUI

/// Full-screen darkened overlay with a transparent rounded-rectangle cutout
/// in the centre, plus guidance text positioned above the cutout.
///
/// Mirrors `ViewfinderOverlay.kt` (Compose Android). The Compose version
/// uses an offscreen layer + `BlendMode.Clear` to punch a hole through a
/// dark rectangle. The SwiftUI equivalent is `.compositingGroup()` plus
/// `.blendMode(.destinationOut)` — same idea: render the dark rect first,
/// then a "transparent" rectangle whose blend mode subtracts it.
struct ViewfinderOverlay: View {

    var guidanceText: String = "Coloca la fecha de caducidad dentro del recuadro"

    private let cutoutWidthFraction: CGFloat = 0.8
    private let cutoutHeightFraction: CGFloat = 0.15
    private let cutoutCornerRadius: CGFloat = 12

    var body: some View {
        GeometryReader { geo in
            let cutoutWidth = geo.size.width * cutoutWidthFraction
            let cutoutHeight = geo.size.height * cutoutHeightFraction
            // Cutout sits centred → its top edge is at (1 - h) / 2.
            let cutoutTopY = geo.size.height * (1 - cutoutHeightFraction) / 2

            ZStack {
                // Layer 1: dark backdrop with the cutout punched through.
                Rectangle()
                    .fill(Color.black.opacity(0.6))
                    .overlay(
                        RoundedRectangle(cornerRadius: cutoutCornerRadius)
                            .frame(width: cutoutWidth, height: cutoutHeight)
                            .blendMode(.destinationOut)
                    )
                    .compositingGroup()  // required for destinationOut to subtract

                // Layer 2: white stroke around the cutout for visibility.
                RoundedRectangle(cornerRadius: cutoutCornerRadius)
                    .stroke(Color.white, lineWidth: 2)
                    .frame(width: cutoutWidth, height: cutoutHeight)

                // Guidance text, positioned ~40 pt above the cutout top edge.
                Text(guidanceText)
                    .font(.body)
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)
                    .shadow(color: .black.opacity(0.7), radius: 4, x: 0, y: 2)
                    .padding(.horizontal, 32)
                    .frame(maxWidth: .infinity)
                    .position(
                        x: geo.size.width / 2,
                        y: max(cutoutTopY - 40, 60)
                    )
            }
        }
        .ignoresSafeArea()
    }
}
