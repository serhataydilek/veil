package com.veil.app.local

internal interface RetentionClock {
    fun wallClockMillis(): Long
    fun elapsedRealtimeMillis(): Long
    fun bootObservation(): BootObservation
}

internal data class BootObservation(
    val bootCount: Int?,
    val reliable: Boolean,
)

internal data class RetentionPolicy(
    val maxMessageAvailabilitySeconds: Long,
) {
    val maxAvailabilityMillis: Long = maxMessageAvailabilitySeconds * 1_000L
}

internal fun retentionPolicyFromSeconds(seconds: Long): RetentionPolicy? {
    if (seconds <= 0L) return null
    if (seconds > Long.MAX_VALUE / 1_000L) return null
    return RetentionPolicy(seconds)
}

internal data class RetentionEnvelope(
    val createdAtWallMs: Long,
    val authenticatedExpiryWallMs: Long,
    val relayDeadlineWallMs: Long?,
)

internal sealed interface RetentionValidation {
    data class Accepted(
        val envelope: RetentionEnvelope,
        val effectiveDeadlineWallMs: Long,
    ) : RetentionValidation

    data object Rejected : RetentionValidation
}

internal object RetentionRules {
    /**
     * Validates an authenticated retention envelope against policy and the
     * current conservative clock lower bound.
     *
     * Implausible future creation (`createdAt > conservativeNow`) is rejected
     * with zero skew until a reviewed allowance exists (ADR 004). This prevents
     * local records from extending visibility past the fail-closed bound.
     */
    fun validate(
        envelope: RetentionEnvelope,
        policy: RetentionPolicy,
        conservativeNowMs: Long,
    ): RetentionValidation {
        val created = envelope.createdAtWallMs
        val expiry = envelope.authenticatedExpiryWallMs
        if (created < 0L || expiry < 0L || conservativeNowMs < 0L) return RetentionValidation.Rejected
        if (created > conservativeNowMs) return RetentionValidation.Rejected
        if (expiry < created) return RetentionValidation.Rejected
        val maxMs = policy.maxAvailabilityMillis
        if (created > Long.MAX_VALUE - maxMs) return RetentionValidation.Rejected
        val maxExpiry = created + maxMs
        if (expiry > maxExpiry) return RetentionValidation.Rejected
        var effective = expiry
        val relay = envelope.relayDeadlineWallMs
        if (relay != null) {
            if (relay < 0L || relay < created) return RetentionValidation.Rejected
            if (relay < effective) effective = relay
        }
        if (effective > maxExpiry) effective = maxExpiry
        return RetentionValidation.Accepted(envelope, effective)
    }

    fun isExpired(effectiveDeadlineWallMs: Long, conservativeNowMs: Long): Boolean =
        effectiveDeadlineWallMs <= conservativeNowMs
}

internal data class ConservativeTimeBound(
    val wallLowerBoundMs: Long,
    val elapsedRealtimeAtObservationMs: Long,
    val bootObservation: BootObservation,
)

internal data class TimeBoundAdvance(
    val bound: ConservativeTimeBound,
    val expireAllMessages: Boolean,
)

internal object ConservativeTime {
    fun initialize(clock: RetentionClock): ConservativeTimeBound =
        ConservativeTimeBound(
            wallLowerBoundMs = clock.wallClockMillis(),
            elapsedRealtimeAtObservationMs = clock.elapsedRealtimeMillis(),
            bootObservation = clock.bootObservation(),
        )

    fun advance(previous: ConservativeTimeBound, clock: RetentionClock): TimeBoundAdvance {
        val wall = clock.wallClockMillis()
        val elapsed = clock.elapsedRealtimeMillis()
        val boot = clock.bootObservation()
        if (!sameReliableBoot(previous.bootObservation, boot)) {
            return TimeBoundAdvance(
                bound = ConservativeTimeBound(
                    wallLowerBoundMs = maxOf(previous.wallLowerBoundMs, wall),
                    elapsedRealtimeAtObservationMs = elapsed,
                    bootObservation = boot,
                ),
                expireAllMessages = true,
            )
        }
        val elapsedDelta = elapsed - previous.elapsedRealtimeAtObservationMs
        if (elapsedDelta < 0L) {
            return TimeBoundAdvance(
                bound = ConservativeTimeBound(
                    wallLowerBoundMs = maxOf(previous.wallLowerBoundMs, wall),
                    elapsedRealtimeAtObservationMs = elapsed,
                    bootObservation = boot,
                ),
                expireAllMessages = true,
            )
        }
        if (elapsedDelta > Long.MAX_VALUE - previous.wallLowerBoundMs) {
            return TimeBoundAdvance(
                bound = ConservativeTimeBound(
                    wallLowerBoundMs = Long.MAX_VALUE,
                    elapsedRealtimeAtObservationMs = elapsed,
                    bootObservation = boot,
                ),
                expireAllMessages = true,
            )
        }
        val elapsedImpliedWall = previous.wallLowerBoundMs + elapsedDelta
        val newLower = maxOf(previous.wallLowerBoundMs, wall, elapsedImpliedWall)
        return TimeBoundAdvance(
            bound = ConservativeTimeBound(
                wallLowerBoundMs = newLower,
                elapsedRealtimeAtObservationMs = elapsed,
                bootObservation = boot,
            ),
            expireAllMessages = false,
        )
    }

    private fun sameReliableBoot(previous: BootObservation, current: BootObservation): Boolean {
        if (!previous.reliable || !current.reliable) return false
        val previousCount = previous.bootCount ?: return false
        val currentCount = current.bootCount ?: return false
        return previousCount == currentCount
    }
}
