import XCTest
import UIKit
@testable import Shared

/// Runtime verification of the Apple Vision implementation of `TextRecognizer`
/// in `shared/src/iosMain`. The Kotlin test in `shared/src/iosTest/` acts as a
/// compile-time API specification (it compiles but cannot be executed in this
/// project due to gitlive Firebase linking constraints — see KDoc on that
/// test); this Swift test exercises the real Vision pipeline with a real
/// fixture image and verifies end-to-end behavior.
///
/// If `testRecognizesDateFromFixtureImage` fails with empty `rawText`, the
/// Vision bridge is broken. If it succeeds in `rawText` but fails on the
/// date assertion, the issue is in `DateParser`, not Vision.
final class TextRecognizerVisionTest: XCTestCase {

    func testRecognizesDateFromFixtureImage() async throws {
        // Load fixture from the test bundle.
        let bundle = Bundle(for: type(of: self))
        guard let url = bundle.url(forResource: "expiration_date_sample",
                                    withExtension: "jpg") else {
            XCTFail("""
                Fixture image not found in test bundle.
                Verify it is added to the fridgeyTests target's bundle resources
                (under iosApp/fridgeyTests/Fixtures/).
                Expected resource name: expiration_date_sample.jpg
                """)
            return
        }
        guard let image = UIImage(contentsOfFile: url.path) else {
            XCTFail("Failed to load fixture image as UIImage from \(url.path)")
            return
        }

        let imageData = ImageData(uiImage: image)
        let recognizer = TextRecognizer()

        // The Kotlin/Native bridge exposes `recognizeText` with a completion
        // handler in the Objective-C header, which Swift automatically
        // surfaces as `async throws` thanks to the swift_name annotation.
        let result = try await recognizer.recognizeText(imageData: imageData)

        // Always log what Vision actually read — invaluable for debugging
        // when the assertions below fail.
        print("================ Vision OCR result ================")
        print("rawText:    '\(result.rawText)'")
        print("confidence: \(result.confidence)")
        print("processingTimeMs: \(result.processingTimeMs)")
        print("detectedDates: \(result.detectedDates)")
        print("==================================================")

        // 1. Vision produced *something*.
        XCTAssertFalse(
            result.rawText.isEmpty,
            "Vision returned empty rawText — the bridge or the pipeline is broken. " +
            "Check that ImageData(uiImage:) is being constructed correctly and " +
            "that VNRecognizeTextRequest is firing inside the actual TextRecognizer."
        )

        // 2. Confidence is non-zero — defends against the dead-bridge case
        //    (the old stub always returned 0).
        XCTAssertGreaterThan(
            result.confidence, 0,
            "Confidence is zero — likely the dead `iosOcrBridge` stub is still " +
            "active or Vision returned no observations."
        )

        // 3. DateParser extracted at least one date.
        //    If this fails but rawText is non-empty, the issue is DateParser
        //    not handling the format Vision read, OR Vision misread the digits.
        XCTAssertFalse(
            result.detectedDates.isEmpty,
            "DateParser found no dates. Vision rawText was: '\(result.rawText)'. " +
            "If rawText looks correct, DateParser is the suspect."
        )

        // 4. The expected date (17 September 2027) is among the detected ones.
        //    Comparing component-by-component avoids relying on
        //    LocalDate equality across the K/N bridge.
        let containsExpectedDate = result.detectedDates.contains { date in
            date.year == 2027
                && date.monthNumber == 9
                && date.dayOfMonth == 17
        }
        XCTAssertTrue(
            containsExpectedDate,
            "Expected 2027-09-17 not in detectedDates. " +
            "Detected: \(result.detectedDates). " +
            "rawText: '\(result.rawText)'."
        )
    }

    /// Smoke test: a 1×1 white pixel must not crash and should produce empty
    /// output. Locks in the contract that empty input → empty output, never an
    /// exception or a populated `OcrResult`.
    func testHandlesEmptyImageGracefully() async throws {
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: 1, height: 1))
        let blank = renderer.image { ctx in
            UIColor.white.setFill()
            ctx.fill(CGRect(x: 0, y: 0, width: 1, height: 1))
        }

        let result = try await TextRecognizer().recognizeText(
            imageData: ImageData(uiImage: blank)
        )

        XCTAssertEqual(
            result.rawText, "",
            "Empty image should produce empty rawText, not crash or fabricate."
        )
        XCTAssertTrue(
            result.detectedDates.isEmpty,
            "Empty image should produce no dates."
        )
    }
}
