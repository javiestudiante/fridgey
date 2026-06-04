import SwiftUI

extension Color {
    static let fridgeyMint        = Color("Mint")
    static let fridgeyMintDeep    = Color("MintDeep")
    static let fridgeyMintSoft    = Color("MintSoft")
    static let fridgeyCream       = Color("Cream")
    static let fridgeySmoke       = Color("Smoke")
    static let fridgeyInk         = Color("Ink")
    static let fridgeyInkSoft     = Color("InkSoft")
    static let fridgeyInkMuted    = Color("InkMuted")
    static let fridgeyAmber       = Color("Amber")
    static let fridgeyRust        = Color("Rust")

    // --- Tokens añadidos para las pantallas de neveras (sRGB literales para
    // no tener que crear color-sets en el asset catalogue). ---
    static let fridgeyMintDarker  = Color(red: 0.173, green: 0.310, blue: 0.239)  // #2C4F3D
    static let fridgeyMintTint    = Color(red: 0.937, green: 0.957, blue: 0.933)  // #EFF4EE
    static let fridgeyInkFaint    = Color(red: 0.761, green: 0.784, blue: 0.769)  // #C2C8C4 (ink-4)
    static let fridgeyRustSoft    = Color(red: 0.949, green: 0.839, blue: 0.808)  // #F2D6CE
    static let fridgeyAmberSoft   = Color(red: 0.957, green: 0.906, blue: 0.812)  // #F4E7CF
    // Hairlines: ink con baja opacidad.
    static let fridgeyHairline       = Color(red: 0.102, green: 0.122, blue: 0.110).opacity(0.08)
    static let fridgeyHairlineStrong = Color(red: 0.102, green: 0.122, blue: 0.110).opacity(0.14)

    // --- Primitive aliases ------------------------------------------------
    // `SurfaceWhite` on Android is a token (#FFFFFF) used by cards / badges
    // that need to pop over the cream canvas. iOS doesn't carry it in the
    // asset catalogue — pure white is fine as a primitive — but exposing
    // it under the `fridgey*` prefix keeps the API symmetric with Android
    // so components can reference the design-system token instead of
    // reaching for `Color.white` directly.
    static let fridgeySurfaceWhite = Color.white
}
