package ule.jescuj00.fridgey.ui.scanner

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import ule.jescuj00.fridgey.domain.scanner.DateScanResult
import ule.jescuj00.fridgey.domain.scanner.ImageData
import ule.jescuj00.fridgey.domain.usecase.ScanExpirationDateUseCase

/**
 * CameraX `ImageAnalysis.Analyzer` that throttles incoming frames and runs
 * each one through the shared [ScanExpirationDateUseCase].
 *
 * Design notes:
 *  - Throttling is **time-based** (`now - lastAnalysisTimestampMs`),
 *    not frame-count-based: the camera's framerate varies with lighting,
 *    so counting frames produces inconsistent throughput.
 *  - The `ImageProxy` lifecycle is the part that breaks if done wrong.
 *    There are exactly two paths that close it:
 *      1. Throttled or unusable frame → `imageProxy.close()` synchronously
 *         in `analyze`.
 *      2. Frame accepted → coroutine `try { … } finally { imageProxy.close() }`,
 *         so the proxy stays open until the suspend `scanUseCase` returns.
 *    Closing in `analyze()` after launching would race with the coroutine.
 *  - The coroutine is launched on the **injected** [coroutineScope] so the
 *    owner (the ViewModel) can cancel it cleanly when the screen leaves.
 *  - `nowMs` is a constructor seam so unit tests can drive a virtual clock
 *    (tied to `TestScope.currentTime`). Production uses wall-clock time.
 */
class DateScannerAnalyzer(
    private val scanUseCase: ScanExpirationDateUseCase,
    private val coroutineScope: CoroutineScope,
    private val minIntervalMs: Long = 500L,
    private val nowMs: () -> Long = System::currentTimeMillis,
) : FrameAnalyzer {

    /**
     * `replay = 1` so a value emitted before the VM subscribes (very early
     * frames during permission grant) still reaches the first collector.
     * `extraBufferCapacity = 1` + `DROP_OLDEST` keeps `tryEmit` non-suspending
     * even if the collector lags briefly. SharedFlow (rather than StateFlow)
     * is used because StateFlow deduplicates by `equals`, which would
     * collapse two consecutive identical [DateScanResult.Success] frames
     * into one and break stability counting in the VM.
     */
    private val _results = MutableSharedFlow<DateScanResult?>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val results: SharedFlow<DateScanResult?> = _results.asSharedFlow()

    /** `null` until the first analysis is launched, then the wall-clock time
     *  of that launch. Using `null` instead of `0L` so the very first frame
     *  always passes the throttle regardless of the clock's epoch. */
    private var lastAnalysisTimestampMs: Long? = null

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val now = nowMs()
        val last = lastAnalysisTimestampMs
        if (last != null && now - last < minIntervalMs) {
            // Throttled — close synchronously, do not launch a coroutine
            // and do not bump the timestamp.
            imageProxy.close()
            return
        }

        val media = imageProxy.image
        if (media == null) {
            // No usable frame; close without claiming the throttle slot.
            imageProxy.close()
            return
        }

        // Claim the slot only when we are actually launching analysis.
        lastAnalysisTimestampMs = now

        val inputImage = InputImage.fromMediaImage(media, imageProxy.imageInfo.rotationDegrees)
        val imageData = ImageData(inputImage)

        coroutineScope.launch {
            try {
                val result = try {
                    scanUseCase(imageData)
                } catch (e: Exception) {
                    // Don't let exceptions bubble up to CameraX — it would
                    // silently drop subsequent frames, which is hell to debug.
                    DateScanResult.Error(e.message ?: "Error scanning frame")
                }
                _results.tryEmit(result)
            } finally {
                imageProxy.close()
            }
        }
    }
}
