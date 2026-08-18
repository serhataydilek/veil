package com.veil.app.core

internal class ProductionCoreNativeApi private constructor() : CoreNativeApi {
    override fun bridgeContractVersion(): UInt = uniffi.veil_ffi.bridgeContractVersion()

    override fun corePolicySnapshot(): NativeCorePolicySnapshot {
        val snapshot = uniffi.veil_ffi.corePolicySnapshot()
        return NativeCorePolicySnapshot(
            bridgeContractVersion = snapshot.bridgeContractVersion,
            maxMessageAvailabilitySeconds = snapshot.maxMessageAvailabilitySeconds,
            rendezvousStatus = snapshot.rendezvousStatus.toNative(),
            secureSessionStatus = snapshot.secureSessionStatus.toNative(),
        )
    }

    companion object {
        fun tryCreate(): CoreNativeApi? =
            try {
                uniffi.veil_ffi.uniffiEnsureInitialized()
                ProductionCoreNativeApi()
            } catch (_: Throwable) {
                null
            }
    }
}

private fun uniffi.veil_ffi.SecurityGateStatus.toNative(): NativeSecurityGateStatus =
    when (this) {
        uniffi.veil_ffi.SecurityGateStatus.REVIEW_REQUIRED -> NativeSecurityGateStatus.REVIEW_REQUIRED
    }
