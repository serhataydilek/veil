package com.veil.app.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RetentionRulesTest {
    private val policy = retentionPolicyFromSeconds(24L * 60 * 60)!!

    @Test
    fun validExpiryBeforeMaximumIsAccepted() {
        val created = 1_000L
        val expiry = created + policy.maxAvailabilityMillis - 1
        val result = RetentionRules.validate(RetentionEnvelope(created, expiry, null), policy)
        assertTrue(result is RetentionValidation.Accepted)
        assertEquals(expiry, (result as RetentionValidation.Accepted).effectiveDeadlineWallMs)
    }

    @Test
    fun exactMaximumBoundaryIsAccepted() {
        val created = 5_000L
        val expiry = created + policy.maxAvailabilityMillis
        val result = RetentionRules.validate(RetentionEnvelope(created, expiry, null), policy)
        assertEquals(expiry, (result as RetentionValidation.Accepted).effectiveDeadlineWallMs)
    }

    @Test
    fun expiryBeyondMaximumIsRejected() {
        val created = 5_000L
        val expiry = created + policy.maxAvailabilityMillis + 1
        assertEquals(
            RetentionValidation.Rejected,
            RetentionRules.validate(RetentionEnvelope(created, expiry, null), policy),
        )
    }

    @Test
    fun expiryBeforeCreationIsRejected() {
        assertEquals(
            RetentionValidation.Rejected,
            RetentionRules.validate(RetentionEnvelope(50, 49, null), policy),
        )
    }

    @Test
    fun integerOverflowIsRejected() {
        val created = Long.MAX_VALUE - 10
        assertEquals(
            RetentionValidation.Rejected,
            RetentionRules.validate(RetentionEnvelope(created, created + 1, null), policy),
        )
    }

    @Test
    fun expiredAtBoundary() {
        val deadline = 10_000L
        assertTrue(RetentionRules.isExpired(deadline, deadline))
        assertTrue(!RetentionRules.isExpired(deadline, deadline - 1))
    }

    @Test
    fun earlierRelayDeadlineWins() {
        val created = 100L
        val expiry = created + 5_000
        val relay = created + 1_000
        val result = RetentionRules.validate(RetentionEnvelope(created, expiry, relay), policy)
        assertEquals(relay, (result as RetentionValidation.Accepted).effectiveDeadlineWallMs)
    }

    @Test
    fun laterRelayDeadlineCannotExtendAuthenticatedExpiry() {
        val created = 100L
        val expiry = created + 1_000
        val relay = created + 8_000
        val result = RetentionRules.validate(RetentionEnvelope(created, expiry, relay), policy)
        assertEquals(expiry, (result as RetentionValidation.Accepted).effectiveDeadlineWallMs)
    }
}
