package ule.jescuj00.fridgey.ui.scanner

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * CameraX camera preview wired to the supplied [analyzer].
 *
 * Lifecycle:
 *  - `LaunchedEffect(analyzer)` resolves the [ProcessCameraProvider] (via the
 *    listener-based `getInstance` future, NOT `kotlinx-coroutines-guava`),
 *    builds [Preview] and [ImageAnalysis], unbinds any previous use cases,
 *    and binds the new ones to the current [LocalLifecycleOwner].
 *  - `DisposableEffect(analyzer)` cleanup calls `unbindAll()` on the held
 *    provider reference so leaving the screen releases the camera promptly
 *    (otherwise the OS holds the camera until the activity is destroyed).
 *  - The provider reference is hoisted into `mutableStateOf` so the
 *    cleanup block can see whichever instance was active when the
 *    keyed `LaunchedEffect` finished its setup.
 *
 * The analyzer runs on the **main executor** as requested in the spec.
 * The heavy OCR work runs off-thread inside ML Kit, so this is fine in
 * practice, but worth flagging if profiling later shows main-thread
 * contention: switching to `Executors.newSingleThreadExecutor()` is a
 * one-line change.
 */
@Composable
fun CameraPreview(
    analyzer: FrameAnalyzer,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Created once and reused across recompositions; AndroidView keeps the
    // same View instance because `factory` is invoked only on first composition.
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
        }
    }

    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    LaunchedEffect(analyzer) {
        val provider = suspendCancellableCoroutine<ProcessCameraProvider> { cont ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener(
                {
                    try {
                        cont.resume(future.get())
                    } catch (e: Exception) {
                        cont.resumeWithException(e)
                    }
                },
                ContextCompat.getMainExecutor(context),
            )
        }
        cameraProvider = provider

        val previewUseCase = Preview.Builder()
            .build()
            .also { it.surfaceProvider = previewView.surfaceProvider }

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(ContextCompat.getMainExecutor(context), analyzer)
            }

        provider.unbindAll()
        provider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            previewUseCase,
            imageAnalysis,
        )
    }

    DisposableEffect(analyzer) {
        onDispose {
            cameraProvider?.unbindAll()
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier,
    )
}
