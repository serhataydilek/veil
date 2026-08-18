package com.veil.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityFeatureGateTest {
    @Test
    fun rustBackedFeatures_mapReviewRequiredWhenBridgeIsAvailable() {
        val gate = SecurityFeatureGate(RustCoreBridge(FakeCoreNativeApi()))
        assertEquals(
            SecurityFeatureAvailability.SecurityReviewRequired(SecurityFeature.RENDEZVOUS),
            gate.availability(SecurityFeature.RENDEZVOUS),
        )
        assertEquals(
            SecurityFeatureAvailability.SecurityReviewRequired(SecurityFeature.SECURE_SESSION),
            gate.availability(SecurityFeature.SECURE_SESSION),
        )
    }

    @Test
    fun unavailableBridge_neverEnablesSecurityFeatures() {
        val gate = SecurityFeatureGate(RustCoreBridge(nativeApi = null))
        assertEquals(
            SecurityFeatureAvailability.Unavailable(SecurityFeature.RENDEZVOUS),
            gate.availability(SecurityFeature.RENDEZVOUS),
        )
        assertEquals(
            SecurityFeatureAvailability.Unavailable(SecurityFeature.SECURE_SESSION),
            gate.availability(SecurityFeature.SECURE_SESSION),
        )
        assertNoAvailableState(gate)
    }

    @Test
    fun incompatibleBridge_neverEnablesSecurityFeatures() {
        val gate = SecurityFeatureGate(
            RustCoreBridge(
                FakeCoreNativeApi(
                    version = 2u,
                    snapshot = NativeCorePolicySnapshot(
                        bridgeContractVersion = 2u,
                        maxMessageAvailabilitySeconds = 24uL * 60uL * 60uL,
                        rendezvousStatus = NativeSecurityGateStatus.REVIEW_REQUIRED,
                        secureSessionStatus = NativeSecurityGateStatus.REVIEW_REQUIRED,
                    ),
                ),
            ),
        )
        assertEquals(
            SecurityFeatureAvailability.Unavailable(SecurityFeature.RENDEZVOUS),
            gate.availability(SecurityFeature.RENDEZVOUS),
        )
        assertEquals(
            SecurityFeatureAvailability.Unavailable(SecurityFeature.SECURE_SESSION),
            gate.availability(SecurityFeature.SECURE_SESSION),
        )
        assertNoAvailableState(gate)
    }

    @Test
    fun contactIdIssuance_remainsNotImplemented() {
        val availableBridge = SecurityFeatureGate(RustCoreBridge(FakeCoreNativeApi()))
        val unavailableBridge = SecurityFeatureGate(RustCoreBridge(nativeApi = null))
        assertEquals(
            SecurityFeatureAvailability.NotImplemented(SecurityFeature.CONTACT_ID_ISSUANCE),
            availableBridge.availability(SecurityFeature.CONTACT_ID_ISSUANCE),
        )
        assertEquals(
            SecurityFeatureAvailability.NotImplemented(SecurityFeature.CONTACT_ID_ISSUANCE),
            unavailableBridge.availability(SecurityFeature.CONTACT_ID_ISSUANCE),
        )
    }

    @Test
    fun noFallbackSuccessExists() {
        assertNoAvailableState(SecurityFeatureGate(RustCoreBridge(FakeCoreNativeApi())))
        assertNoAvailableState(SecurityFeatureGate(RustCoreBridge(nativeApi = null)))
    }

    private fun assertNoAvailableState(gate: SecurityFeatureGate) {
        for (feature in SecurityFeature.entries) {
            when (val availability = gate.availability(feature)) {
                is SecurityFeatureAvailability.SecurityReviewRequired,
                is SecurityFeatureAvailability.Unavailable,
                is SecurityFeatureAvailability.NotImplemented,
                -> assertTrue(availability.toString().isNotEmpty())
            }
        }
    }
}
