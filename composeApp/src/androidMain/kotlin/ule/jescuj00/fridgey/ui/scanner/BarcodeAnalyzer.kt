package ule.jescuj00.fridgey.ui.scanner

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import ule.jescuj00.fridgey.domain.scanner.BarcodeResult
import ule.jescuj00.fridgey.domain.scanner.BarcodeScanner
import ule.jescuj00.fridgey.domain.scanner.ImageData

/**
 * CameraX `ImageAnalysis.Analyzer` for the CÓDIGO phase. Structurally
 * identical to [DateScannerAnalyzer] (time-based 500 ms throttle, strict
 * `ImageProxy` close discipline, results on a non-deduplicating SharedFlow),
 * but runs the shared [BarcodeScanner] instead of the OCR use case.
 *
 * A frame with no barcode emits `null` (treated by the VM like a momentary
 * gap, so it does not reset stability progress).
 */
class BarcodeAnalyzer(
    private val barcodeScanner: BarcodeScanner,
    private val coroutineScope: CoroutineScope,
    private val minIntervalMs: Long = 500L,
    private val nowMs: () -> Long = System::currentTimeMillis,
) : BarcodeFrameAnalyzer {

    private val _results = MutableSharedFlow<BarcodeResult?>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val results: SharedFlow<BarcodeResult?> = _results.asSharedFlow()

    private var lastAnalysisTimestampMs: Long? = null

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val now = nowMs()
        val last = lastAnalysisTimestampMs
        if (last != null && now - last < minIntervalMs) {
            imageProxy.close()
            return
        }

        val media = imageProxy.image
        if (media == null) {
            imageProxy.close()
            return
        }

        lastAnalysisTimestampMs = now

        val inputImage = InputImage.fromMediaImage(media, imageProxy.imageInfo.rotationDegrees)
        val imageData = ImageData(inputImage)

        coroutineScope.launch {
            try {
                // A detection failure is "no barcode this frame" (null), not an
                // error state — keep the camera scanning.
                val result = try {
                    barcodeScanner.detect(imageData)
                } catch (e: Exception) {
                    null
                }
                _results.tryEmit(result)
            } finally {
                imageProxy.close()
            }
        }
    }
}
