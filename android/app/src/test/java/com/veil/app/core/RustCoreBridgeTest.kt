package com.veil.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RustCoreBridgeTest {
    @Test
    fun compatibleContract_isAvailableWithRustPolicy() {
        val bridge = RustCoreBridge(
            FakeCoreNativeApi(version = 1u, snapshot = compatibleNativeSnapshot()),
        )
        val loaded = bridge.load()
        assertEquals(CoreBridgeStatus.AVAILABLE, loaded.status)
        assertEquals(1u, loaded.contractVersion)
        assertEquals(24L * 60 * 60, loaded.maxMessageAvailabilitySeconds)
        assertEquals(CoreSecurityGateStatus.REVIEW_REQUIRED, loaded.rendezvousStatus)
        assertEquals(CoreSecurityGateStatus.REVIEW_REQUIRED, loaded.secureSessionStatus)
    }

    @Test
    fun wrongContractVersion_isIncompatibleWithoutPolicySnapshot() {
        val bridge = RustCoreBridge(
            FakeCoreNativeApi(version = 2u, snapshot = compatibleNativeSnapshot(version = 2u)),
        )
        val loaded = bridge.load()
        assertEquals(CoreBridgeStatus.INCOMPATIBLE, loaded.status)
        assertEquals(2u, loaded.contractVersion)
        assertNull(loaded.maxMessageAvailabilitySeconds)
        assertNull(loaded.rendezvousStatus)
        assertNull(loaded.secureSessionStatus)
    }

    @Test
    fun nativeFailure_isUnavailableWithoutPolicySnapshot() {
        val bridge = RustCoreBridge(nativeApi = null)
        val loaded = bridge.load()
        assertEquals(CoreBridgeStatus.UNAVAILABLE, loaded.status)
        assertNull(loaded.contractVersion)
        assertNull(loaded.maxMessageAvailabilitySeconds)
        assertNull(loaded.rendezvousStatus)
        assertNull(loaded.secureSessionStatus)
    }

    @Test
    fun bindingException_isUnavailableWithoutPolicySnapshot() {
        val bridge = RustCoreBridge(ThrowingCoreNativeApi())
        val loaded = bridge.load()
        assertEquals(CoreBridgeStatus.UNAVAILABLE, loaded.status)
        assertNull(loaded.contractVersion)
        assertNull(loaded.maxMessageAvailabilitySeconds)
        assertNull(loaded.rendezvousStatus)
        assertNull(loaded.secureSessionStatus)
    }

    @Test
    fun linkageError_isUnavailableWithoutPolicySnapshot() {
        val bridge = RustCoreBridge(LinkageFailingCoreNativeApi())
        val loaded = bridge.load()
        assertEquals(CoreBridgeStatus.UNAVAILABLE, loaded.status)
        assertNull(loaded.contractVersion)
        assertNull(loaded.maxMessageAvailabilitySeconds)
        assertNull(loaded.rendezvousStatus)
        assertNull(loaded.secureSessionStatus)
    }

    @Test
    fun snapshotVersionMismatch_isIncompatible() {
        val bridge = RustCoreBridge(
            FakeCoreNativeApi(
                version = 1u,
                snapshot = compatibleNativeSnapshot(version = 99u),
            ),
        )
        val loaded = bridge.load()
        assertEquals(CoreBridgeStatus.INCOMPATIBLE, loaded.status)
        assertEquals(99u, loaded.contractVersion)
        assertNull(loaded.rendezvousStatus)
    }
}
