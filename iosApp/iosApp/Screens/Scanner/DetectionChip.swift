import SwiftUI
import Shared

/// Detected-date chip with a circular progress ring around it.
///
/// Mirrors the Android `DetectionChip` Composable. The ring fills in
/// proportion to `stabilityProgress` (0.0 → 1.0); when it reaches 1.0 the
/// VM has already auto-confirmed the date and the screen is about to
/// dismiss, so the user briefly sees the ring complete before navigation
/// kicks in (matches Android's UX).
struct DetectionChip: View {

    let date: Kotlinx_datetimeLocalDate
    let stabilityProgress: Float

    var body: some View {
        ZStack {
            // Background track — full circle in low-opacity white.
            Circle()
                .stroke(Color.white.opacity(0.3), lineWidth: 3)

            // Foreground arc — drawn from 12 o'clock, clockwise, animating.
            Circle()
                .trim(from: 0, to: CGFloat(stabilityProgress))
                .stroke(
                    Color.accentColor,
                    style: StrokeStyle(lineWidth: 3, lineCap: .round)
                )
                .rotationEffect(.degrees(-90))
                .animation(.easeOut(duration: 0.2), value: stabilityProgress)

            // Pill containing the formatted date.
            Capsule()
                .fill(Color.accentColor.opacity(0.15))
                .overlay(
                    Capsule()
                        .stroke(Color.accentColor.opacity(0.4), lineWidth: 1)
                )
                .overlay(
                    Text(date.formattedEs)
                        .font(.subheadline.weight(.semibold))
                        .foregroundColor(.accentColor)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 8)
                )
                .fixedSize()
        }
        .frame(width: 96, height: 96)
    }
}
