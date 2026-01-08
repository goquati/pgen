package de.quati.pgen.wal

import kotlinx.coroutines.delay
import kotlin.time.Duration


internal class Backoff(
    private val minDelay: Duration,
    private val maxDelay: Duration,
) {
    private var count: Int = 0

    fun reset() {
        count = 0
    }

    suspend fun incrementAndWait() {
        val factor = 1 shl count++
        val delayDuration = (minDelay * factor).coerceAtMost(maxDelay)
        delay(delayDuration)
    }
}
