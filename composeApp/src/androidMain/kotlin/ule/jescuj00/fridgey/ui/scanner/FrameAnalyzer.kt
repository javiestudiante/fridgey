package ule.jescuj00.fridgey.ui.scanner

import androidx.camera.core.ImageAnalysis
import kotlinx.coroutines.flow.SharedFlow
import ule.jescuj00.fridgey.domain.scanner.DateScanResult

/**
 * Minimal contract the [DateScannerViewModel] depends on. Production code
 * uses [DateScannerAnalyzer]; tests substitute a fake whose `results`
 * SharedFlow is mutable, so VM-level state transitions can be exercised
 * without going through CameraX `ImageProxy` / ML Kit `InputImage` mocks.
 *
 * # Implementation contract — DO NOT BREAK THIS
 *
 * **Implementations MUST propagate every emission including duplicates.**
 * The consumer ([DateScannerViewModel]) relies on duplicate emissions to
 * count *consecutive identical* detections for stability-based
 * auto-confirmation: three same-date frames in a row trigger an automatic
 * [ScannerEvent.DatePicked]. A deduplicating publisher would collapse those
 * three frames into one and the auto-confirm would never fire.
 *
 * Concretely this means:
 *  - **Do NOT use `StateFlow`** (or anything backed by `MutableStateFlow.value`):
 *    its setter compares with `equals` and swallows identical values.
 *  - **Do NOT use `Flow.distinctUntilChanged()`** anywhere upstream of the
 *    VM's collector.
 *  - The reference Android implementation is `MutableSharedFlow` configured
 *    with `replay = 1, extraBufferCapacity = 1, onBufferOverflow = DROP_OLDEST`.
 *    `replay = 1` preserves the "late subscriber sees the latest emission"
 *    semantics the VM depends on (it subscribes after the user grants the
 *    camera permission, and emissions can land in the small window between
 *    analyzer construction and collector activation). The buffer settings
 *    keep `tryEmit` non-suspending under the analyzer's 2 fps throttle.
 *
 * Future iOS implementation (AVFoundation-backed) must use the same
 * non-deduplicating, replay = 1 semantics; reaching for `StateFlow` because
 * "it's simpler" will silently break the auto-confirm path.
 */
interface FrameAnalyzer : ImageAnalysis.Analyzer {
    val results: SharedFlow<DateScanResult?>
}
