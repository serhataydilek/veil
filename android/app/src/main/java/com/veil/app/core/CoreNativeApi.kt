package com.veil.app.core

internal interface CoreNativeApi {
    fun bridgeContractVersion(): UInt
    fun corePolicySnapshot(): NativeCorePolicySnapshot
}

internal data class NativeCorePolicySnapshot(
    val bridgeContractVersion: UInt,
    val maxMessageAvailabilitySeconds: ULong,
    val rendezvousStatus: NativeSecurityGateStatus,
    val secureSessionStatus: NativeSecurityGateStatus,
)

internal enum class NativeSecurityGateStatus {
    REVIEW_REQUIRED,
}
