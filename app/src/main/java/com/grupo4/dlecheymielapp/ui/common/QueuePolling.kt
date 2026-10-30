package com.grupo4.dlecheymielapp.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.grupo4.dlecheymielapp.BuildConfig
import kotlinx.coroutines.delay

/**
 * Calls [refresh] every [intervalMillis] for as long as [run] is active. Cancelling the coroutine stops it.
 * When [enabled] is false, [run] returns at once and [refresh] is never called.
 */
class QueuePoller(private val enabled: Boolean, private val intervalMillis: Long, private val refresh: () -> Unit) {
    suspend fun run() {
        if (!enabled) return
        while (true) {
            delay(intervalMillis)
            refresh()
        }
    }
}

/**
 * Polls [refresh] while the screen is RESUMED (visible, app in the foreground) and stops in the background or when the
 * composable leaves the composition. Off unless the build sets QUEUE_POLLING_ENABLED; detail screens do not use it.
 */
@Composable
fun QueuePollingEffect(refresh: () -> Unit) {
    val currentRefresh by rememberUpdatedState(refresh)
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            QueuePoller(
                enabled = BuildConfig.QUEUE_POLLING_ENABLED,
                intervalMillis = BuildConfig.QUEUE_POLLING_SECONDS * 1000L,
            ) { currentRefresh() }.run()
        }
    }
}
