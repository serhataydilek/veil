package com.veil.app.core

import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidRustCoreBridgeTest {
    @Test
    fun realRustBridge_reportsFailClosedCorePolicy() {
        repeat(8) {
            val snapshot = RustCoreBridge().load()
            assertEquals(CoreBridgeStatus.AVAILABLE, snapshot.status)
            assertEquals(1u, snapshot.contractVersion)
            assertEquals(24L * 60 * 60, snapshot.maxMessageAvailabilitySeconds)
            assertEquals(CoreSecurityGateStatus.REVIEW_REQUIRED, snapshot.rendezvousStatus)
            assertEquals(CoreSecurityGateStatus.REVIEW_REQUIRED, snapshot.secureSessionStatus)
        }
    }

    @Test
    fun realRustBridge_doesNotEnableSecurityFeatures() {
        val gate = SecurityFeatureGate(RustCoreBridge())
        assertEquals(
            SecurityFeatureAvailability.SecurityReviewRequired(SecurityFeature.RENDEZVOUS),
            gate.availability(SecurityFeature.RENDEZVOUS),
        )
        assertEquals(
            SecurityFeatureAvailability.SecurityReviewRequired(SecurityFeature.SECURE_SESSION),
            gate.availability(SecurityFeature.SECURE_SESSION),
        )
        assertEquals(
            SecurityFeatureAvailability.NotImplemented(SecurityFeature.CONTACT_ID_ISSUANCE),
            gate.availability(SecurityFeature.CONTACT_ID_ISSUANCE),
        )
    }
}
