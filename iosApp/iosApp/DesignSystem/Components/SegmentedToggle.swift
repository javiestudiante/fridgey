import SwiftUI

/// One segment in a `SegmentedToggle`. `systemImage` is optional — when
/// set, an SF Symbol is rendered before the label inside the segment.
struct SegmentOption {
    let label: String
    var systemImage: String? = nil
}

/// Pill-shaped multi-segment toggle. Generalised over `options.count`,
/// though Fridgey only uses 2 segments today (Escanear / A mano).
///
/// The selected segment's background and content colours animate
/// to/from mint-deep over 200 ms; unselected segments stay transparent
/// on the outer mint-soft track.
struct SegmentedToggle: View {
    let options: [SegmentOption]
    let selectedIndex: Int
    let onSelect: (Int) -> Void

    var body: some View {
        HStack(spacing: FridgeySpacing.xs) {
            ForEach(options.indices, id: \.self) { index in
                segment(at: index)
            }
        }
        .padding(FridgeySpacing.xs)
        .frame(height: 44)
        .frame(maxWidth: .infinity)
        .background(Color.fridgeyMintSoft, in: Capsule())
        .animation(.easeOut(duration: 0.2), value: selectedIndex)
    }

    @ViewBuilder
    private func segment(at index: Int) -> some View {
        let option = options[index]
        let isSelected = index == selectedIndex
        let bg: Color = isSelected ? .fridgeyMintDeep : .clear
        let fg: Color = isSelected ? .fridgeySurfaceWhite : .fridgeyMintDeep

        Button(action: { onSelect(index) }) {
            HStack(spacing: FridgeySpacing.xs) {
                if let symbol = option.systemImage {
                    Image(systemName: symbol)
                }
                Text(option.label)
            }
            .font(FridgeyFont.labelMedium)
            .foregroundStyle(fg)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(bg, in: Capsule())
        }
        .buttonStyle(.plain)
    }
}
