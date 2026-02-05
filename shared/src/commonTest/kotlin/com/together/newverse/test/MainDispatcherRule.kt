package com.together.newverse.test

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/**
 * A test helper for setting up the Main dispatcher in tests.
 * This is necessary because ViewModels use Dispatchers.Main which
 * is not available in unit tests.
 *
 * Usage:
 * ```
 * class MyViewModelTest {
 *     private val dispatcherRule = MainDispatcherRule()
 *
 *     @BeforeTest
 *     fun setup() {
 *         dispatcherRule.setup()
 *     }
 *
 *     @AfterTest
 *     fun tearDown() {
 *         dispatcherRule.tearDown()
 *     }
 * }
 * ```
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()
) {
    /**
     * Set up the test dispatcher as the main dispatcher.
     * Call this in @BeforeTest.
     */
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    /**
     * Reset the main dispatcher.
     * Call this in @AfterTest.
     */
    fun tearDown() {
        Dispatchers.resetMain()
    }
}
