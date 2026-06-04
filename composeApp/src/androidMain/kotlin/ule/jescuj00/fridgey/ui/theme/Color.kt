package ule.jescuj00.fridgey.ui.theme

import androidx.compose.ui.graphics.Color

// Brand palette — extracted from Claude Design's mobile-design HTML.
// Editorial-kitchen look: mint as brand, cream as canvas, ink for text,
// amber/rust for expiration states.

val Mint = Color(0xFF69A481)
val MintDeep = Color(0xFF3F6E55)    // CTA, FAB, números "fresh"
val MintDarker = Color(0xFF2C4F3D)  // texto sobre mint-soft (badges)
val MintSoft = Color(0xFFDCE8DF)
val MintTint = Color(0xFFEFF4EE)    // fondo card destacada, fondo icono emoji

val Cream = Color(0xFFFBFAF6)       // Fondo home — warm white, NOT pure #FFFFFF
val Smoke = Color(0xFFE7EDEB)       // Fondo pantalla detalle, dividers
val Paper = Color(0xFFFFFFFF)       // Cards, baldas (alias de SurfaceWhite)

val Ink = Color(0xFF1A1F1C)         // Texto principal
val InkSoft = Color(0xFF4A524C)     // ink-2 — texto secundario
val InkMuted = Color(0xFF8A938C)    // ink-3 — labels, captions, metadata
val InkFaint = Color(0xFFC2C8C4)    // ink-4 — deshabilitado, bordes input

// Hairlines: ink con baja opacidad (rgba(26,31,28, …)).
val Hairline = Color(0x141A1F1C)        // 0.08
val HairlineStrong = Color(0x241A1F1C)  // 0.14

val Amber = Color(0xFFC8924A)       // "Caduca pronto" / warn
val Rust = Color(0xFFB5524A)        // "Caduca ya" / bad
val RustSoft = Color(0xFFF2D6CE)    // fondo notif alerta
val AmberSoft = Color(0xFFF4E7CF)

val SurfaceWhite = Color(0xFFFFFFFF)

// --- Compat shims ---------------------------------------------------------
// `NeveraDetailScreen` (and possibly other not-yet-migrated screens) imports
// these by name from the previous palette. Aliasing them to the new
// semantic tokens keeps the call sites compiling without touching them
// (Fase 4 will rewrite those screens against the proper Material 3
// scheme + `LocalFridgeySpacing`). Remove these once every screen has
// been migrated.
val FreshIndicator = Mint
val WarnIndicator = Amber
val ExpiredIndicator = Rust
