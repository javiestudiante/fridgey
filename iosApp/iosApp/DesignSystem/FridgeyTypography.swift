import SwiftUI

/// Typography tokens. Family resolution uses PostScript names (NOT file names).
/// Inter and JetBrains Mono are variable fonts: weight is applied via
/// `.weight(...)` modifier; SwiftUI interpolates against the variable axis.
enum FridgeyFont {
    static let displayLarge   = Font.custom("InstrumentSerif-Regular", size: 56)
    static let displayMedium  = Font.custom("InstrumentSerif-Regular", size: 36)
    static let displayItalic  = Font.custom("InstrumentSerif-Italic",  size: 28)
    static let headlineLarge  = Font.custom("InstrumentSerif-Regular", size: 28)
    static let headlineMedium = Font.custom("InstrumentSerif-Regular", size: 22)
    static let titleMedium    = Font.custom("Inter-Regular",           size: 16).weight(.medium)
    static let bodyLarge      = Font.custom("Inter-Regular",           size: 16)
    static let bodyMedium     = Font.custom("Inter-Regular",           size: 14)
    static let bodySmall      = Font.custom("Inter-Regular",           size: 13)
    static let labelLarge     = Font.custom("Inter-Regular",           size: 15).weight(.medium)
    static let labelMedium    = Font.custom("Inter-Regular",           size: 13).weight(.medium)
    static let labelSmall     = Font.custom("JetBrainsMono-Regular",   size: 11)

    // --- Extra serif sizes outside the M3-aligned scale -------------------
    // `numericLarge` and `sectionCount` are not part of the canonical role
    // set; they exist to mirror the same Instrument Serif sizes Android
    // declares as `FridgeyNumericLarge` / `FridgeySectionCount` for the
    // NeveraCard metric numbers, the ProductRow days-remaining, and the
    // small serif counter trailing a SectionHeader title.

    /// Big serif number used by NeveraCard metrics + ProductRow days.
    static let numericLarge   = Font.custom("InstrumentSerif-Regular", size: 32)
    /// Small serif counter trailing a SectionHeader title ("Tus neveras 02").
    static let sectionCount   = Font.custom("InstrumentSerif-Regular", size: 14)
}
