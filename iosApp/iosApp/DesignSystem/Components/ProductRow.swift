import SwiftUI

/// Visual urgency bucket for a product's days-remaining value. The mapping
/// to colour is centralised in `stateColor` so callers don't reach for raw
/// tokens. Caller decides which bucket applies — the row does NOT compute
/// it from `daysRemaining` directly, because the bucket boundaries differ
/// across product categories / configurations.
enum ExpirationState {
    case expired, critical, warning, ok
}

/// One product in the "Detalle de nevera" list.
///
/// Layout: emoji bubble · name + supporting · big serif day count + label,
/// with a coloured 1-pt progress line beneath. The progress line is purely
/// cosmetic — it is a tinted divider, NOT a real progress bar.
///
/// Day display rule (caller-friendly): the number rendered is
/// `abs(daysRemaining)`. The state colour communicates expired-vs-fresh,
/// so a leading minus sign would be visual noise. When `daysRemaining == 0`
/// the number is omitted and only the "HOY" label is shown.
struct ProductRow: View {
    let emoji: String
    let name: String
    let supporting: String
    let daysRemaining: Int
    let state: ExpirationState
    var onClick: (() -> Void)? = nil

    private var stateColor: Color {
        switch state {
        case .expired, .critical: return .fridgeyRust
        case .warning:            return .fridgeyAmber
        case .ok:                 return .fridgeyInkMuted
        }
    }

    private var label: String {
        if daysRemaining == 0 { return "HOY" }
        if daysRemaining == 1 || daysRemaining == -1 { return "DÍA" }
        return "DÍAS"
    }

    var body: some View {
        Group {
            if let onClick = onClick {
                Button(action: onClick) { rowContent }
                    .buttonStyle(.plain)
            } else {
                rowContent
            }
        }
    }

    @ViewBuilder
    private var rowContent: some View {
        VStack(spacing: 0) {
            HStack(alignment: .center, spacing: 0) {
                // Emoji bubble
                Text(emoji)
                    .font(FridgeyFont.titleMedium)
                    .frame(width: 40, height: 40)
                    .background(Color.fridgeyMintSoft, in: Circle())

                // Name + supporting
                VStack(alignment: .leading, spacing: FridgeySpacing.xs) {
                    Text(name).font(FridgeyFont.titleMedium)
                        .foregroundStyle(Color.fridgeyInk)
                    Text(supporting)
                        .font(FridgeyFont.bodyMedium)
                        .foregroundStyle(Color.fridgeyInkSoft)
                }
                .padding(.horizontal, FridgeySpacing.md)

                Spacer()

                // Days + label
                VStack(alignment: .trailing, spacing: 0) {
                    if daysRemaining != 0 {
                        Text(String(abs(daysRemaining)))
                            .font(FridgeyFont.numericLarge)
                            .foregroundStyle(stateColor)
                    }
                    EyebrowLabel(text: label, color: stateColor)
                }
            }
            .padding(.horizontal, FridgeySpacing.lg)
            .padding(.vertical, FridgeySpacing.md)

            // Coloured progress line under the row
            Rectangle()
                .fill(stateColor.opacity(0.4))
                .frame(height: 1)
                .padding(.horizontal, FridgeySpacing.lg)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

#Preview {
    VStack(spacing: 0) {
        ProductRow(
            emoji: "🥬",
            name: "Espinacas baby",
            supporting: "Vegetales · 200 g",
            daysRemaining: 0,
            state: .critical,
            onClick: {}
        )
        ProductRow(
            emoji: "🥛",
            name: "Yogur natural",
            supporting: "Lácteos · 4 uds",
            daysRemaining: 3,
            state: .warning,
            onClick: {}
        )
        ProductRow(
            emoji: "🍎",
            name: "Manzana",
            supporting: "Frutas · 6 uds",
            daysRemaining: 12,
            state: .ok
        )
    }
    .background(Color.fridgeyCream)
}
