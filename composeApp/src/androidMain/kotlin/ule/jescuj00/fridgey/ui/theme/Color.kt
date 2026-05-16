package ule.jescuj00.fridgey.ui.theme

import androidx.compose.ui.graphics.Color

// Brand palette — extracted from Claude Design's mobile-design HTML.
// Editorial-kitchen look: mint as brand, cream as canvas, ink for text,
// amber/rust for expiration states.

val Mint = Color(0xFF69A481)
val MintDeep = Color(0xFF3F6E55)
val MintSoft = Color(0xFFDCE8DF)

val Cream = Color(0xFFFBFAF6)       // App background — warm white, NOT pure #FFFFFF
val Smoke = Color(0xFFE7EDEB)       // Subtle backgrounds, dividers

val Ink = Color(0xFF1A1F1C)         // Primary text — near-black with green undertone
val InkSoft = Color(0xFF4A524C)     // Secondary text
val InkMuted = Color(0xFF8A938C)    // Tertiary text, uppercase labels, metadata

val Amber = Color(0xFFC8924A)       // "Caduca pronto" state
val Rust = Color(0xFFB5524A)        // "Caduca ya" / critical state

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
