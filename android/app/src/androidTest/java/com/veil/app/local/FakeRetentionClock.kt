package com.veil.app.local

internal class FakeRetentionClock(
    var wallMs: Long,
    var elapsedMs: Long,
    var boot: BootObservation = BootObservation(bootCount = 1, reliable = true),
) : RetentionClock {
    override fun wallClockMillis(): Long = wallMs
    override fun elapsedRealtimeMillis(): Long = elapsedMs
    override fun bootObservation(): BootObservation = boot
}
