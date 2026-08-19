package com.veil.app.core

const val EXPECTED_BRIDGE_CONTRACT_VERSION: UInt = 1u

enum class CoreBridgeStatus {
    AVAILABLE,
    UNAVAILABLE,
    INCOMPATIBLE,
}

enum class CoreSecurityGateStatus {
    REVIEW_REQUIRED,
}

data class CoreBridgeSnapshot(
    val status: CoreBridgeStatus,
    val contractVersion: UInt? = null,
    val maxMessageAvailabilitySeconds: Long? = null,
    val rendezvousStatus: CoreSecurityGateStatus? = null,
    val secureSessionStatus: CoreSecurityGateStatus? = null,
)
