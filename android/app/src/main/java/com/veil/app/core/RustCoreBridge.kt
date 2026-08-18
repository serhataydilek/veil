package com.veil.app.core

class RustCoreBridge internal constructor(
    private val nativeApi: CoreNativeApi?,
    private val expectedContractVersion: UInt = EXPECTED_BRIDGE_CONTRACT_VERSION,
) {
    constructor() : this(ProductionCoreNativeApi.tryCreate())

    fun load(): CoreBridgeSnapshot {
        val api = nativeApi ?: return unavailable()
        return catchExpectedNativeFailure { readSnapshot(api) } ?: unavailable()
    }

    private fun readSnapshot(api: CoreNativeApi): CoreBridgeSnapshot {
        val version = api.bridgeContractVersion()
        if (version != expectedContractVersion) {
            return CoreBridgeSnapshot(
                status = CoreBridgeStatus.INCOMPATIBLE,
                contractVersion = version,
            )
        }
        val native = api.corePolicySnapshot()
        if (native.bridgeContractVersion != expectedContractVersion) {
            return CoreBridgeSnapshot(
                status = CoreBridgeStatus.INCOMPATIBLE,
                contractVersion = native.bridgeContractVersion,
            )
        }
        val rendezvous = native.rendezvousStatus.toCore() ?: return unavailable()
        val session = native.secureSessionStatus.toCore() ?: return unavailable()
        val maxAvailability = native.maxMessageAvailabilitySeconds.toAvailabilitySeconds()
            ?: return unavailable()
        return CoreBridgeSnapshot(
            status = CoreBridgeStatus.AVAILABLE,
            contractVersion = native.bridgeContractVersion,
            maxMessageAvailabilitySeconds = maxAvailability,
            rendezvousStatus = rendezvous,
            secureSessionStatus = session,
        )
    }

    private fun unavailable(): CoreBridgeSnapshot =
        CoreBridgeSnapshot(status = CoreBridgeStatus.UNAVAILABLE)
}

private fun NativeSecurityGateStatus.toCore(): CoreSecurityGateStatus? =
    when (this) {
        NativeSecurityGateStatus.REVIEW_REQUIRED -> CoreSecurityGateStatus.REVIEW_REQUIRED
    }

private fun ULong.toAvailabilitySeconds(): Long? {
    if (this > Long.MAX_VALUE.toULong()) return null
    return toLong()
}
