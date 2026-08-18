package com.veil.app.core

internal fun compatibleNativeSnapshot(version: UInt = 1u) = NativeCorePolicySnapshot(
    bridgeContractVersion = version,
    maxMessageAvailabilitySeconds = 24uL * 60uL * 60uL,
    rendezvousStatus = NativeSecurityGateStatus.REVIEW_REQUIRED,
    secureSessionStatus = NativeSecurityGateStatus.REVIEW_REQUIRED,
)

internal class FakeCoreNativeApi(
    private val version: UInt = 1u,
    private val snapshot: NativeCorePolicySnapshot = compatibleNativeSnapshot(version),
) : CoreNativeApi {
    override fun bridgeContractVersion(): UInt = version
    override fun corePolicySnapshot(): NativeCorePolicySnapshot = snapshot
}

internal class ThrowingCoreNativeApi : CoreNativeApi {
    override fun bridgeContractVersion(): UInt = error("native binding failed")
    override fun corePolicySnapshot(): NativeCorePolicySnapshot = error("native binding failed")
}
