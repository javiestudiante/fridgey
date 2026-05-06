package ule.jescuj00.fridgey.ui.scanner

import android.media.Image
import androidx.camera.core.ImageInfo
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import ule.jescuj00.fridgey.domain.scanner.DateScanResult
import ule.jescuj00.fridgey.domain.scanner.ImageData
import ule.jescuj00.fridgey.domain.usecase.ScanExpirationDateUseCase
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DateScannerAnalyzerTest {

    private lateinit var useCase: ScanExpirationDateUseCase
    private lateinit var stubInputImage: InputImage

    @BeforeTest
    fun setUp() {
        useCase = mockk()
        stubInputImage = mockk(relaxed = true)
        // InputImage.fromMediaImage is the only Android-framework call inside
        // the analyzer; stub it so JVM unit tests don't need the real ML Kit
        // pipeline.
        mockkStatic(InputImage::class)
        every { InputImage.fromMediaImage(any(), any()) } returns stubInputImage
    }

    @AfterTest
    fun tearDown() {
        unmockkStatic(InputImage::class)
    }

    /** Builds a non-throttled, non-null-image proxy. `close()` is recorded. */
    private fun makeProxy(): ImageProxy = mockk(relaxed = true) {
        every { image } returns mockk<Image>(relaxed = true)
        every { imageInfo } returns mockk<ImageInfo>(relaxed = true) {
            every { rotationDegrees } returns 0
        }
    }

    @Test
    fun twoFrames100msApart_useCaseInvokedOnce() = runTest {
        coEvery { useCase.invoke(any<ImageData>()) } returns DateScanResult.NoDateFound("")
        val analyzer = DateScannerAnalyzer(
            scanUseCase = useCase,
            coroutineScope = this,
            minIntervalMs = 500L,
            nowMs = { testScheduler.currentTime },
        )

        analyzer.analyze(makeProxy())
        advanceUntilIdle()

        advanceTimeBy(100)
        analyzer.analyze(makeProxy())
        advanceUntilIdle()

        coVerify(exactly = 1) { useCase.invoke(any<ImageData>()) }
    }

    @Test
    fun twoFrames600msApart_useCaseInvokedTwice() = runTest {
        coEvery { useCase.invoke(any<ImageData>()) } returns DateScanResult.NoDateFound("")
        val analyzer = DateScannerAnalyzer(
            scanUseCase = useCase,
            coroutineScope = this,
            minIntervalMs = 500L,
            nowMs = { testScheduler.currentTime },
        )

        analyzer.analyze(makeProxy())
        advanceUntilIdle()

        advanceTimeBy(600)
        analyzer.analyze(makeProxy())
        advanceUntilIdle()

        coVerify(exactly = 2) { useCase.invoke(any<ImageData>()) }
    }

    @Test
    fun useCaseThrows_emitsErrorAndDoesNotPropagate() = runTest {
        coEvery { useCase.invoke(any<ImageData>()) } throws IllegalStateException("boom")
        val analyzer = DateScannerAnalyzer(
            scanUseCase = useCase,
            coroutineScope = this,
            minIntervalMs = 500L,
            nowMs = { testScheduler.currentTime },
        )

        val proxy = makeProxy()
        analyzer.analyze(proxy)   // must not throw
        advanceUntilIdle()

        // SharedFlow has no `.value`; replay=1 means the last emission lives
        // in `replayCache`, which is the test-side equivalent.
        val emitted = analyzer.results.replayCache.firstOrNull()
        assertTrue(emitted is DateScanResult.Error, "expected Error, got $emitted")
        // assertTrue smart-casts `emitted` to DateScanResult.Error here.
        assertEquals("boom", emitted.message)
        verify { proxy.close() }
    }

    @Test
    fun throttledFrame_closesSynchronously() = runTest {
        coEvery { useCase.invoke(any<ImageData>()) } returns DateScanResult.NoDateFound("")
        val analyzer = DateScannerAnalyzer(
            scanUseCase = useCase,
            coroutineScope = this,
            minIntervalMs = 500L,
            nowMs = { testScheduler.currentTime },
        )

        // First frame establishes the throttle baseline.
        analyzer.analyze(makeProxy())
        advanceUntilIdle()

        // Second frame, only 50 ms later — must be throttled.
        advanceTimeBy(50)
        val skipped = makeProxy()
        analyzer.analyze(skipped)

        // No `advanceUntilIdle` — the close must already have happened
        // synchronously inside `analyze()`, before any coroutine can run.
        verify(exactly = 1) { skipped.close() }
        coVerify(exactly = 1) { useCase.invoke(any<ImageData>()) }
    }
}
