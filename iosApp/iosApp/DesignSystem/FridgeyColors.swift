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

    // --- Primitive aliases ------------------------------------------------
    // `SurfaceWhite` on Android is a token (#FFFFFF) used by cards / badges
    // that need to pop over the cream canvas. iOS doesn't carry it in the
    // asset catalogue — pure white is fine as a primitive — but exposing
    // it under the `fridgey*` prefix keeps the API symmetric with Android
    // so components can reference the design-system token instead of
    // reaching for `Color.white` directly.
    static let fridgeySurfaceWhite = Color.white
}
