import SwiftUI

/// Inline section header: serif title (20) on the left, optional zero-padded
/// mono counter ("02", ink-3) on the right. `accentColor` tints the TITLE for
/// the urgency sections (rust / amber / ink); the counter stays ink-3.
/// Padding: 22 top / 22 horizontal / `bottomPadding` bottom (8 on the home
/// "Tus neveras" header, 10 on the detail urgency heads).
struct SectionHeader: View {
    let title: String
    var count: Int? = nil
    var accentColor: Color = .fridgeyInk
    var bottomPadding: CGFloat = 10

    var body: some View {
        HStack(alignment: .bottom) {
            Text(title)
                .font(.custom("InstrumentSerif-Regular", size: 20))
                .foregroundStyle(accentColor)
            Spacer()
            if let count = count {
                Text(String(format: "%02d", count))
                    .font(FridgeyFont.labelSmall)   // JetBrains Mono 11
                    .tracking(1.76)
                    .foregroundStyle(Color.fridgeyInkMuted)
            }
        }
        .padding(.horizontal, 22)
        .padding(.top, 22)
        .padding(.bottom, bottomPadding)
    }
}
