import SwiftUI

/// Inline section header used inside a screen.
/// Serif title on the left, optional zero-padded counter ("02") on the
/// right. `accentColor` lets callers tint both title and counter for
/// status sections (rust for "Caduca ya", amber for "Esta semana", etc).
struct SectionHeader: View {
    let title: String
    var count: Int? = nil
    var accentColor: Color = .fridgeyInk

    var body: some View {
        HStack(alignment: .bottom) {
            Text(title)
                .font(FridgeyFont.headlineMedium)
                .foregroundStyle(accentColor)
            Spacer()
            if let count = count {
                Text(String(format: "%02d", count))
                    .font(FridgeyFont.sectionCount)
                    .foregroundStyle(accentColor)
            }
        }
        .padding(.horizontal, FridgeySpacing.lg)
        .padding(.top, FridgeySpacing.xl)
        .padding(.bottom, FridgeySpacing.sm)
    }
}
