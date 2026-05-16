import SwiftUI

/// Highlighted alert card with a solid coloured bullet on the left,
/// title (+ optional subtitle) in the middle, and a chevron on the right.
/// Used in "Mis neveras" for cards like "2 productos caducan hoy en Casa".
///
/// Background is rust-tinted (12% alpha) so the card pops against the
/// cream canvas without going as loud as the solid `Rust` colour.
struct AlertCard: View {
    let bulletText: String
    let title: String
    let onClick: () -> Void
    var subtitle: String? = nil

    var body: some View {
        Button(action: onClick) {
            HStack(alignment: .center, spacing: FridgeySpacing.md) {
                // Bullet
                Text(bulletText)
                    .font(FridgeyFont.titleMedium)
                    .foregroundStyle(Color.fridgeySurfaceWhite)
                    .frame(width: 40, height: 40)
                    .background(Color.fridgeyRust, in: Circle())

                // Middle column
                VStack(alignment: .leading, spacing: FridgeySpacing.xs) {
                    Text(title)
                        .font(FridgeyFont.titleMedium)
                        .foregroundStyle(Color.fridgeyInk)
                    if let subtitle = subtitle {
                        Text(subtitle)
                            .font(FridgeyFont.bodyMedium)
                            .foregroundStyle(Color.fridgeyInkSoft)
                    }
                }

                Spacer()

                // Chevron
                Image(systemName: "chevron.right")
                    .foregroundStyle(Color.fridgeyInkSoft)
                    .frame(width: 20, height: 20)
            }
            .padding(FridgeySpacing.lg)
            .frame(maxWidth: .infinity)
            .background(
                Color.fridgeyRust.opacity(0.12),
                in: RoundedRectangle(cornerRadius: FridgeyRadius.md)
            )
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
