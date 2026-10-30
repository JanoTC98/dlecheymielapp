package com.grupo4.dlecheymielapp.ui.common

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class QueuePollerTest {
    private val interval = 60_000L

    @Test
    fun whenEnabledRefreshesOncePerIntervalWhileActive() = runTest {
        var calls = 0
        val job = launch { QueuePoller(enabled = true, intervalMillis = interval) { calls++ }.run() }

        advanceTimeBy(interval - 1)
        runCurrent()
        assertEquals(0, calls)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, calls)

        advanceTimeBy(interval)
        runCurrent()
        assertEquals(2, calls)

        job.cancel()
    }

    @Test
    fun stopsRefreshingWhenItsCoroutineIsCancelled() = runTest {
        var calls = 0
        val job = launch { QueuePoller(enabled = true, intervalMillis = interval) { calls++ }.run() }

        advanceTimeBy(interval)
        runCurrent()
        assertEquals(1, calls)

        // Leaving the foreground cancels the lifecycle-bound coroutine.
        job.cancel()
        advanceTimeBy(interval * 3)
        runCurrent()
        assertEquals(1, calls)
    }

    @Test
    fun whenDisabledNeverRefreshes() = runTest {
        var calls = 0
        val job = launch { QueuePoller(enabled = false, intervalMillis = interval) { calls++ }.run() }

        advanceTimeBy(interval * 5)
        runCurrent()
        assertEquals(0, calls)

        job.cancel()
    }
}
