package moto

import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/** Lets the browser repaint (status bar) during long GA / hybrid runs without changing results. */
class Yielder {
    private var last = TimeSource.Monotonic.markNow()
    suspend fun maybeYield() {
        if (last.elapsedNow() > 50.milliseconds) {
            delay(1)
            last = TimeSource.Monotonic.markNow()
        }
    }
}
