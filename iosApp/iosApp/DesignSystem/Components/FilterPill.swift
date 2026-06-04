import SwiftUI

/// Category-filter chip. Selected: mint-deep fill / white text. Unselected:
/// paper fill / ink-2 text / 1-pt hairline border (per design). `count` is
/// appended as ` · N` (the active "Todo · 24").
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
                .font(.custom("Inter-Regular", size: 13))
                .foregroundStyle(selected ? Color.fridgeySurfaceWhite : Color.fridgeyInkSoft)
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                .background(
                    selected ? Color.fridgeyMintDeep : Color.fridgeySurfaceWhite,
                    in: Capsule()
                )
                .overlay(
                    Capsule().stroke(selected ? Color.clear : Color.fridgeyHairline, lineWidth: 1)
                )
        }
        .buttonStyle(.plain)
    }
}
