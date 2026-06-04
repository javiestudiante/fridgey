import SwiftUI

/// Cross-fridge "caducan hoy" banner (parity with Android `AlertCard`).
/// Rust-soft card (radius 16), solid 32×32 rust bullet (count in white),
/// title + optional subtitle, trailing chevron.
struct AlertCard: View {
    let bulletText: String
    let title: String
    let onClick: () -> Void
    var subtitle: String? = nil

    var body: some View {
        Button(action: onClick) {
            HStack(alignment: .center, spacing: 12) {
                Text(bulletText)
                    .font(.custom("Inter-Regular", size: 14).weight(.bold))
                    .foregroundStyle(Color.fridgeySurfaceWhite)
                    .frame(width: 32, height: 32)
                    .background(Color.fridgeyRust, in: Circle())

                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.custom("Inter-Regular", size: 13).weight(.semibold))
                        .foregroundStyle(Color.fridgeyMintDarker)
                        .multilineTextAlignment(.leading)
                    if let subtitle = subtitle {
                        Text(subtitle)
                            .font(.custom("Inter-Regular", size: 12))
                            .foregroundStyle(Color.fridgeyInkSoft)
                    }
                }

                Spacer()

                Image(systemName: "chevron.right")
                    .font(.system(size: 14))
                    .foregroundStyle(Color.fridgeyInkSoft)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
            .frame(maxWidth: .infinity)
            .background(Color.fridgeyRustSoft, in: RoundedRectangle(cornerRadius: FridgeyRadius.md))
        }
        .buttonStyle(.plain)
    }
}

#Preview {
    AlertCard(
        bulletText: "2",
        title: "2 productos caducan hoy en Casa",
        onClick: {},
        subtitle: "Yogur natural · Espinacas baby"
    )
    .padding()
    .background(Color.fridgeyCream)
}
