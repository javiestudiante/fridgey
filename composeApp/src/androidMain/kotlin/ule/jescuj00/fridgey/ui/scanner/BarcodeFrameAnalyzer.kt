package ule.jescuj00.fridgey.ui.scanner

import androidx.camera.core.ImageAnalysis
import kotlinx.coroutines.flow.SharedFlow
import ule.jescuj00.fridgey.domain.scanner.BarcodeResult

/**
 * Barcode counterpart of [FrameAnalyzer]. Same strict contract: emit EVERY
 * result including duplicates (the VM counts consecutive identical barcodes
 * for stability-based auto-confirm), so back this with a non-deduplicating
 * `SharedFlow(replay = 1, ...)` — never `StateFlow` / `distinctUntilChanged`.
 *
 * A `null` emission means "no barcode in this frame".
 */
interface BarcodeFrameAnalyzer : ImageAnalysis.Analyzer {
    val results: SharedFlow<BarcodeResult?>
}
