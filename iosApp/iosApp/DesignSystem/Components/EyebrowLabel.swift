import SwiftUI

/// Uppercase mono label used for screen eyebrows ("HOY · 28 ABRIL"),
/// section eyebrows ("ESTA SEMANA"), category labels, and metric captions.
///
/// Renders `text` verbatim — the caller decides casing. The 1.76 tracking
/// is applied here because `Font.custom(...)` cannot carry letter-spacing
/// on iOS; the Android counterpart bakes it into `labelSmall`.
struct EyebrowLabel: View {
    let text: String
    var color: Color = .fridgeyInkMuted

    var body: some View {
        Text(text)
            .font(FridgeyFont.labelSmall)
            .tracking(1.76)
            .foregroundStyle(color)
    }
}
