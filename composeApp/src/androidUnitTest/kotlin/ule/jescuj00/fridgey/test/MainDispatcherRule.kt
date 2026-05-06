package ule.jescuj00.fridgey.test

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Swaps `Dispatchers.Main` for a [TestDispatcher] for the duration of a test.
 *
 * `viewModelScope` runs on `Dispatchers.Main.immediate` in production; without
 * this rule, any `viewModelScope.launch { … }` in tests throws
 * `IllegalStateException: Module with the Main dispatcher had failed to
 * initialize`.
 *
 * Default uses [StandardTestDispatcher] so tests can drive virtual time with
 * `advanceUntilIdle()` / `advanceTimeBy(...)`. Pass an `UnconfinedTestDispatcher`
 * via the constructor when eager execution is needed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = StandardTestDispatcher()
) : TestWatcher() {

    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
