import SwiftUI

/// Rounded pill used in the horizontal filter rail ("Todo · 24", "Lácteos").
/// Selected: mint-deep fill / white text. Unselected: mint-soft fill /
/// mint-deep text. `count` is appended as ` · N` when not nil.
struct FilterPill: View {
    let label: String
    let selected: Bool
    let onClick: () -> Void
    var count: Int? = nil

    private var displayText: String {
        if let count = count { return "\(label) · \(count)" }
        return label
    }

    var body: some View {
        Button(action: onClick) {
            Text(displayText)
                .font(FridgeyFont.labelMedium)
                .foregroundStyle(selected ? Color.fridgeySurfaceWhite : Color.fridgeyMintDeep)
                .padding(.horizontal, FridgeySpacing.md)
                .padding(.vertical, FridgeySpacing.sm)
                .background(
                    selected ? Color.fridgeyMintDeep : Color.fridgeyMintSoft,
                    in: Capsule()
                )
        }
        .buttonStyle(.plain)
    }
}
