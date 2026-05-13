package ule.jescuj00.fridgey.domain.scanner

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSDate
import platform.Foundation.NSError
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.UIImage
import platform.Vision.VNImageRequestHandler
import platform.Vision.VNRecognizeTextRequest
import platform.Vision.VNRecognizedText
import platform.Vision.VNRecognizedTextObservation
import platform.Vision.VNRequestTextRecognitionLevelAccurate
import ule.jescuj00.fridgey.domain.util.DateParser

/**
 * iOS-side `ImageData`: wraps a [UIImage] for Vision-based OCR.
 *
 * Hardened from the previous `Any` storage (a leftover from the
 * never-finished MLKit-via-Swift-bridge approach). [UIImage] is the
 * natural still-image input for [TextRecognizer.recognizeText].
 *
 * ## Future pipeline note (out of scope this sprint)
 *
 * When the live AVCapture pipeline arrives, the most efficient route is
 * `VNImageRequestHandler(cmSampleBuffer:orientation:options:)` — which
 * accepts a `CMSampleBuffer` directly without a `UIImage` round-trip.
 * Going through `UIImage` per frame is wasteful (UIImage wraps a
 * CGImage plus orientation metadata; we don't need the wrapper for
 * stream processing). When that work begins, do NOT just convert each
 * frame to `UIImage` to satisfy this constructor — rethink [ImageData]:
 *
 *  1. Add a secondary constructor for `CMSampleBuffer` (or `CGImage` +
 *     orientation), since `expect class ImageData` declares no
 *     constructor and each platform can pick its own.
 *  2. Or replace the `UIImage` storage with a sealed-like discriminator
 *     (UIImage / CMSampleBuffer / CGImage variants).
 *
 * Whichever shape you pick, the goal is: the AVCapture delegate hands
 * `CMSampleBuffer`s to the recognizer **directly**, with no UIImage in
 * between.
 */
actual class ImageData(val uiImage: UIImage)

/**
 * iOS text recognizer backed by [Apple Vision Framework][platform.Vision]
 * — the system framework, not Cocoapods. Direct cinterop, no Swift
 * wrapper, no bridge wiring needed at app startup.
 *
 * ## What's missing for production use (out of scope this sprint)
 *
 *  - **Live camera pipeline**: there is no `AVCaptureSession` or
 *    `AVCaptureVideoDataOutput` set up yet. The Android side has CameraX
 *    + a `DateScannerScreen` Compose UI; the iOS equivalents (SwiftUI
 *    scanner screen, `AVCaptureSession` delegate, scanner ViewModel)
 *    are pending.
 *  - **Throttling**: Vision does NOT drop frames automatically the way
 *    ML Kit did. The future `AVCaptureVideoDataOutputSampleBufferDelegate`
 *    must hold an `isProcessing` flag (or use a serial dispatch queue /
 *    `actor`) and discard incoming `CMSampleBuffer`s while a request is
 *    in flight. Without throttling, the capture queue fills up and
 *    frames pile up unbounded.
 *  - **Orientation**: `VNImageRequestHandler` is sensitive to
 *    `CGImagePropertyOrientation`. The future pipeline must map
 *    `AVCaptureConnection.videoOrientation` × device orientation →
 *    `CGImagePropertyOrientation` and pass it to the handler. Without
 *    this, OCR works in portrait but degrades in landscape.
 *  - **Per-frame handler instance**: `VNImageRequestHandler` is meant to
 *    be created fresh per frame and discarded — never reused. Don't
 *    "optimize" by caching the handler across frames; it's tied to the
 *    input image and Apple's lifecycle expectations.
 *  - **`recognitionLanguages` is hardcoded** to `["es-ES", "en-US"]`
 *    inside [recognizeText]. When the iOS scanner ViewModel exists,
 *    consider plumbing the language list through the API.
 *  - **`minimumTextHeight`** is not set. For live camera frames, fixing
 *    it to ~3% of frame height (e.g. `0.03f`) cuts CPU and reduces
 *    spurious detections of background text. Skipped here because still
 *    images may legitimately have small dates printed on packaging.
 *  - **`automaticallyDetectsLanguage = false`**: not set explicitly. The
 *    property exists since iOS 16, but the project's deployment target
 *    is iOS 15 (see `iosApp/Podfile`). Apple's documented default when
 *    `recognitionLanguages` is non-empty is already `false`, so we get
 *    the desired behaviour without the explicit setter; setting it on
 *    iOS 15 would crash with `unrecognized selector`. When the
 *    deployment target is bumped to iOS 16+, add the explicit setter
 *    here for defensive symmetry.
 *  - **`confidence` field**: hardcoded to `0.8f` if any text was
 *    recognised, mirroring the pre-existing Android path. Vision exposes
 *    `VNRecognizedText.confidence` per candidate; averaging would give
 *    a more honest signal. Left for follow-up.
 */
actual class TextRecognizer {

    /**
     * Cached, fully-configured [VNRecognizeTextRequest], reused across
     * every call to [recognizeText] for the lifetime of this recognizer.
     *
     * ## Why cache (vs. fresh per frame)
     *
     * The first `VNRecognizeTextRequest` that actually executes against
     * real text takes ~10 s while the `.accurate` recognition weights
     * page in. Constructing the request and assigning
     * `recognitionLanguages` is not free either: each language pulls its
     * tokenizer / candidate list. Doing this once per `TextRecognizer`
     * instance — instead of once per frame at 2 fps — means the cold
     * cost is paid exactly once. Combined with the warm-up dummy frame
     * fired by the iOS scanner View on entering `.scanning`, the first
     * REAL camera frame finds a hot request and returns in tens of ms.
     *
     * ## Why this is safe to share across calls
     *
     * Apple documents `VNRequest` as reusable as long as its
     * configuration properties are not mutated mid-execution. We set
     * them once in [createConfiguredRequest] and never touch them again.
     * What IS created fresh per frame is the [VNImageRequestHandler],
     * which retains the image and must not be reused.
     *
     * ## Result snapshotting
     *
     * After `performRequests` returns, `request.results` holds the
     * observations for the just-processed image. Those get OVERWRITTEN
     * on the next call. [runVisionOcr] reads them synchronously inside
     * the same function frame and projects to an immutable [String]
     * before returning, so no aliasing leaks across invocations.
     *
     * Lazy because the property must not run at field-init time —
     * `Dispatchers.Default` may not be available yet, and we want the
     * load deferred to the first warm-up call rather than to recognizer
     * construction.
     */
    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    private val request: VNRecognizeTextRequest by lazy { createConfiguredRequest() }

    actual suspend fun recognizeText(imageData: ImageData): OcrResult =
        withContext(Dispatchers.Default) {
            // Vision is synchronous on the calling thread; isolate it to
            // a background dispatcher so callers running on Main don't
            // see the UI thread blocked for the duration of the request.
            runVisionOcr(imageData)
        }

    /**
     * Builds the singleton [VNRecognizeTextRequest] with the exact same
     * configuration the previous per-call factory used:
     * `.accurate`, no language correction, Spanish + English. Called
     * exactly once per [TextRecognizer] via the [request] lazy delegate.
     */
    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    private fun createConfiguredRequest(): VNRecognizeTextRequest {
        val r = VNRecognizeTextRequest()
        r.recognitionLevel = VNRequestTextRecognitionLevelAccurate
        r.usesLanguageCorrection = false
        r.recognitionLanguages = listOf("es-ES", "en-US")
        return r
    }

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    private fun runVisionOcr(imageData: ImageData): OcrResult {
        val startTime = currentTimeMs()

        val cgImage = imageData.uiImage.CGImage
            ?: return OcrResult(
                rawText = "",
                detectedDates = emptyList(),
                confidence = 0f,
                processingTimeMs = currentTimeMs() - startTime,
            )

        // Fresh handler per frame — handler owns the image lifetime and
        // Apple is explicit that it must NOT be reused across images.
        val handler = VNImageRequestHandler(cGImage = cgImage, options = emptyMap<Any?, Any>())
        memScoped {
            val errorPtr = alloc<ObjCObjectVar<NSError?>>()
            handler.performRequests(listOf(request), errorPtr.ptr)
            // `performRequests` is synchronous; on return `request.results`
            // has been (re-)populated for this frame. If it errored,
            // results may be empty/nil and `errorPtr.value` is set — we
            // do not surface either as an exception: empty results
            // downstream become `DateScanResult.NoDateFound`, matching the
            // Android behaviour.
        }

        // Snapshot results into a local String NOW, before any
        // subsequent call can overwrite `request.results` (the request
        // is shared across calls). K/N cinterop loses the generic
        // argument from `NSArray<VNRecognizedTextObservation *>`; we get
        // back `List<*>` and have to recover the element type
        // explicitly.
        val observations = (request.results ?: emptyList<Any?>())
            .filterIsInstance<VNRecognizedTextObservation>()
        val rawText = observations
            .mapNotNull { obs ->
                (obs.topCandidates(1uL).firstOrNull() as? VNRecognizedText)?.string
            }
            .joinToString("\n")

        val elapsed = currentTimeMs() - startTime
        val dates = DateParser.extractDates(rawText)
        return OcrResult(
            rawText = rawText,
            detectedDates = dates,
            confidence = if (rawText.isNotEmpty()) 0.8f else 0f,
            processingTimeMs = elapsed,
        )
    }

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    actual fun close() {
        // Cancel any in-flight execution on the cached request. Safe to
        // call even if the request is idle (Apple: cancel on an
        // unstarted/finished request is a no-op). The fresh
        // VNImageRequestHandler created in `runVisionOcr` has no
        // long-lived state to release here.
        request.cancel()
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun currentTimeMs(): Long =
        (NSDate().timeIntervalSince1970 * 1000).toLong()
}
