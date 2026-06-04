import SwiftUI
import Shared

/// Visual urgency bucket. The cuts live ONLY in `expirationState(_:)` — the
/// single source of truth reused by the detail section grouping, the day-number
/// colour, and the progress bar. (Mirror of Android `expirationStateOf`.)
enum ExpirationState {
    case bad, warn, fresh

    var color: Color {
        switch self {
        case .bad:   return .fridgeyRust
        case .warn:  return .fridgeyAmber
        case .fresh: return .fridgeyMintDeep
        }
    }
}

/// Design cuts: <=1 día → BAD, 2..7 → WARN, >7 → FRESH.
func expirationState(_ diasRestantes: Int) -> ExpirationState {
    if diasRestantes <= 1 { return .bad }
    if diasRestantes <= 7 { return .warn }
    return .fresh
}

/// Per-category tint for the emoji bubble (design palette); unlisted → smoke.
private func categoryIconBg(_ categoria: Categoria) -> Color {
    switch categoria {
    case .lacteos:  return Color(red: 0.933, green: 0.949, blue: 0.965)  // dairy  #EEF2F6
    case .carnes:   return Color(red: 0.961, green: 0.898, blue: 0.886)  // meat   #F5E5E2
    case .verduras: return Color(red: 0.898, green: 0.937, blue: 0.878)  // veg    #E5EFE0
    case .frutas:   return Color(red: 0.965, green: 0.922, blue: 0.863)  // fruit  #F6EBDC
    case .bebidas:  return Color(red: 0.910, green: 0.937, blue: 0.945)  // drink  #E8EFF1
    default:        return .fridgeySmoke
    }
}

/// One product inside a "balda" (parity with Android `ProductRow`):
/// 44 emoji bubble (tinted per category) · name + "Categoría · cantidad+unidad"
/// · big serif day count + label, with a 2-pt progress bar that widens as
/// expiry nears. Urgency is computed from `daysRemaining` via `expirationState`.
struct ProductRow: View {
    let categoria: Categoria
    let name: String
    let supporting: String
    let daysRemaining: Int

    private var state: ExpirationState { expirationState(daysRemaining) }
    private var accent: Color { state.color }
    private var label: String {
        if daysRemaining == 0 { return "HOY" }
        if daysRemaining == 1 || daysRemaining == -1 { return "DÍA" }
        return "DÍAS"
    }

    var body: some View {
        HStack(alignment: .center, spacing: 12) {
            Text(categoria.emoji)
                .font(.system(size: 22))
                .frame(width: 44, height: 44)
                .background(categoryIconBg(categoria),
                            in: RoundedRectangle(cornerRadius: FridgeyRadius.emojiIcon))

            VStack(alignment: .leading, spacing: 2) {
                Text(name)
                    .font(.custom("Inter-Regular", size: 15).weight(.medium))
                    .foregroundStyle(Color.fridgeyInk)
                Text(supporting)
                    .font(.custom("Inter-Regular", size: 12))
                    .foregroundStyle(Color.fridgeyInkMuted)
            }

            Spacer()

            VStack(alignment: .trailing, spacing: 0) {
                if daysRemaining != 0 {
                    Text(String(abs(daysRemaining)))
                        .font(.custom("InstrumentSerif-Regular", size: 26))
                        .foregroundStyle(accent)
                }
                Text(label)
                    .font(.custom("Inter-Regular", size: 10).weight(.medium))
                    .tracking(0.5)
                    .foregroundStyle(accent)
            }
            .frame(minWidth: 64, alignment: .trailing)
        }
        .padding(.horizontal, 18)
        .padding(.vertical, 14)
        .frame(maxWidth: .infinity)
        .overlay(alignment: .bottom) {
            GeometryReader { geo in
                Rectangle()
                    .fill(accent)
                    .frame(width: geo.size.width * progressFraction(daysRemaining), height: 2)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomLeading)
            }
        }
    }
}

/// Inverse of days-remaining → bar width fraction (closer to expiry ⇒ wider).
/// Linear over a 14-day horizon; floored at 0.06 so fresh items still show a hint.
private func progressFraction(_ dias: Int) -> CGFloat {
    let horizon: CGFloat = 14
    let clamped = CGFloat(max(0, min(dias, 14)))
    return max(0.06, min(1, 1 - clamped / horizon))
}
