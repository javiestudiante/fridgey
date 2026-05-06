package ule.jescuj00.fridgey.domain.scanner

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.UIKit.NSTextAlignmentCenter
import platform.UIKit.UIColor
import platform.UIKit.UIFont
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetCurrentContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UILabel
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration test that exercises the iOS [TextRecognizer] against a real
 * Vision OCR pass on a programmatically generated [UIImage] with a known
 * date drawn on it.
 *
 * Designed to run as `:shared:iosSimulatorArm64Test`; it needs a working
 * iOS runtime for Vision to be present. Expect ~5–15 s per test in a
 * warm simulator (cold boot adds ~30 s on top).
 *
 * ## ⚠️ Currently blocked from running via Gradle
 *
 * The test code compiles cleanly (`:shared:compileTestKotlinIosSimulatorArm64`),
 * but `:shared:linkDebugTestIosSimulatorArm64` fails with
 * `ld: framework 'FirebaseCore' not found`.
 *
 * Cause: `iosMain` depends transitively on the gitlive Firebase Kotlin
 * SDK, whose iOS klibs declare `linkerOpts -framework FirebaseCore` in
 * their def files. The Gradle-driven test binary inherits that
 * requirement, but `FirebaseCore.framework` only exists once Xcode /
 * Cocoapods has built `iosApp` (it lives in DerivedData, no stable
 * path on disk that Gradle can add to its framework search path).
 *
 * To make this test runnable, one of these must happen in a future
 * sprint:
 *  1. Apply the Kotlin/Native Cocoapods plugin to `shared/build.gradle.kts`
 *     so the shared module understands its iOS Cocoapod dependencies
 *     (recommended path; canonical KMP+Cocoapods integration).
 *  2. Or: relocate these tests to an XCTest target inside `iosApp/` —
 *     Swift code calling into the K/N framework. Avoids the linker
 *     issue entirely because Xcode resolves Cocoapods natively.
 *  3. Or: configure `linkerOpts` on the test binary to point at the
 *     iosApp DerivedData path. Brittle, machine-specific, last resort.
 *
 * Until then this file serves as the spec of how the integration is
 * supposed to be exercised, and the asserts are the contract; the file
 * compiles to catch breakages in the Vision API surface even when the
 * full link-and-run cycle is unavailable.
 *
 * ## Note on flakiness (when it runs)
 * Vision's recognition of synthetic, programmatically-rendered text
 * (anti-aliased system fonts) is reliable but not bit-stable across iOS
 * versions. If this test starts failing intermittently after an Xcode /
 * iOS Simulator upgrade, that's the likely cause — not a regression in
 * [TextRecognizer]. Mitigations in order:
 *  1. Relax `assertContains` to a regex (`\\d{2}/\\d{2}/\\d{4}`) and
 *     rely on `extractsDateFromRecognizedText` for the precise check.
 *  2. Mark these as integration tests and exclude them from the fast CI
 *     lane via a build-script filter.
 *  3. Replace the synthetic image with a checked-in PNG fixture (more
 *     stable across iOS versions, but requires plumbing test resources).
 */
class TextRecognizerIosTest {

    private val testDateString = "31/12/2026"
    private val expectedLocalDate = LocalDate(2026, 12, 31)

    /**
     * Test 1: pure Vision wrapper. If this fails the problem is in
     * [TextRecognizer.recognizeText]'s call into Vision (configuration,
     * image conversion, observation extraction) — NOT in `DateParser`.
     */
    @Test
    fun recognizesPrintedDateAsRawText() = runBlocking {
        val image = makeImageWithText(testDateString)
        val recognizer = TextRecognizer()
        try {
            val result = recognizer.recognizeText(ImageData(image))
            assertContains(
                result.rawText, testDateString,
                message = "Vision did not recognise the date in the rendered " +
                    "image. rawText was: '${result.rawText}'",
            )
        } finally {
            recognizer.close()
        }
    }

    /**
     * Test 2: integration Vision → DateParser. If this fails but
     * [recognizesPrintedDateAsRawText] passes, the problem is in
     * `DateParser.extractDates` (or the chained call inside
     * [TextRecognizer.recognizeText]) — NOT in Vision.
     */
    @Test
    fun extractsDateFromRecognizedText() = runBlocking {
        val image = makeImageWithText(testDateString)
        val recognizer = TextRecognizer()
        try {
            val result = recognizer.recognizeText(ImageData(image))
            assertTrue(
                result.detectedDates.isNotEmpty(),
                "DateParser produced no dates from rawText '${result.rawText}'",
            )
            assertEquals(
                expectedLocalDate, result.detectedDates.first(),
                "Wrong date extracted from rawText '${result.rawText}'",
            )
        } finally {
            recognizer.close()
        }
    }

    /**
     * Generates a minimal-but-readable [UIImage] with [text] drawn on a
     * white background. No external resources, no test fixtures: deps are
     * only UIKit (always available on iOS simulator). 600 × 200 with
     * 72 pt bold black yields glyphs ~36 % of the canvas height — well
     * above any reasonable `minimumTextHeight` cutoff and large enough
     * for Vision `.accurate` to identify confidently.
     *
     * Implementation note: `NSString.drawAtPoint(_:withAttributes:)` —
     * the natural way to draw text in K/N — is part of the UIKit
     * `NSStringDrawing` category, and that category is not exposed to
     * Kotlin/Native cinterop. Instead we render a configured [UILabel]
     * via `CALayer.renderInContext(_:)`, which IS exposed and gives
     * equivalent output.
     */
    @OptIn(ExperimentalForeignApi::class)
    private fun makeImageWithText(
        text: String,
        widthPx: Double = 600.0,
        heightPx: Double = 200.0,
    ): UIImage {
        val label = UILabel(frame = CGRectMake(0.0, 0.0, widthPx, heightPx))
        label.text = text
        label.font = UIFont.boldSystemFontOfSize(72.0)
        label.textColor = UIColor.blackColor
        label.backgroundColor = UIColor.whiteColor
        label.textAlignment = NSTextAlignmentCenter

        UIGraphicsBeginImageContextWithOptions(
            size = CGSizeMake(widthPx, heightPx),
            opaque = true,
            scale = 1.0,
        )
        val context = requireNotNull(UIGraphicsGetCurrentContext()) {
            "UIGraphicsGetCurrentContext returned nil after begin — " +
                "context was not initialised correctly."
        }
        label.layer.renderInContext(context)

        val image = requireNotNull(UIGraphicsGetImageFromCurrentImageContext()) {
            "UIGraphicsGetImageFromCurrentImageContext returned nil — " +
                "rendered image could not be extracted from the context."
        }
        UIGraphicsEndImageContext()
        return image
    }
}
