package com.veil.app.lock

/** Monotonic time only; wall-clock changes cannot lengthen an unlocked session. */
internal fun interface MonotonicClock {
    fun nowMillis(): Long
}

internal object SystemMonotonicClock : MonotonicClock {
    override fun nowMillis(): Long = System.nanoTime() / 1_000_000L
}

/**
 * A brief 30-second app switch is tolerated after a successful unlock. This is
 * deliberately not configuration or session state and is never persisted.
 */
internal class AppLockGracePolicy(
    private val graceMillis: Long = DEFAULT_GRACE_MILLIS,
) {
    init {
        require(graceMillis >= 0)
    }

    fun allowsResume(backgroundedAtMillis: Long, nowMillis: Long): Boolean {
        val elapsed = nowMillis - backgroundedAtMillis
        return elapsed >= 0 && elapsed <= graceMillis
    }

    internal companion object {
        const val DEFAULT_GRACE_MILLIS = 30_000L
    }
}
