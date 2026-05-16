import SwiftUI

/// Top-of-screen block. Optional eyebrow line above a serif title, with
/// optional `leading` (back arrow, etc.) and `trailing` (bell, share icon)
/// slots.
///
/// Slot type: we use `AnyView?` instead of `@ViewBuilder` generic
/// parameters because (a) the slots are tiny (a single SF Symbol button)
/// so the type-erasure cost is irrelevant, and (b) optionality at the API
/// surface is much cleaner this way — `nil` means "absent", no
/// `EmptyView()` boilerplate at call sites and no runtime checks against
/// the generic metatypes.
///
/// Layout rules (mirror Android's `ScreenHeader.kt`):
///  - When `leading` or `trailing` is provided, the top row holds
///    leading | eyebrow | spacer | trailing on one line, then the title
///    below it.
///  - When only `eyebrow` is provided, eyebrow stacks above the title.
///  - When both are nil and `eyebrow` is nil, only the title is rendered.
struct ScreenHeader: View {
    let title: String
    var eyebrow: String? = nil
    var leading: AnyView? = nil
    var trailing: AnyView? = nil

    var body: some View {
        VStack(alignment: .leading, spacing: FridgeySpacing.sm) {
            if leading != nil || trailing != nil {
                HStack(alignment: .center, spacing: FridgeySpacing.sm) {
                    if let leading = leading { leading }
                    if let eyebrow = eyebrow {
                        EyebrowLabel(text: eyebrow)
                    }
                    Spacer(minLength: 0)
                    if let trailing = trailing { trailing }
                }
            } else if let eyebrow = eyebrow {
                EyebrowLabel(text: eyebrow)
            }
            Text(title)
                .font(FridgeyFont.displayMedium)
                .foregroundStyle(Color.fridgeyInk)
        }
        .padding(.horizontal, FridgeySpacing.lg)
        .padding(.top, FridgeySpacing.xl)
        .padding(.bottom, FridgeySpacing.lg)
    }
}
