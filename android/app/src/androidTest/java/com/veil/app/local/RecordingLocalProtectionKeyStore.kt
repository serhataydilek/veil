package com.veil.app.local

import com.veil.app.security.ExistingKeyResult
import com.veil.app.security.LocalProtectionKeyStore
import com.veil.app.security.ProtectionSecurityLevel

internal class RecordingLocalProtectionKeyStore(
    private val inner: LocalProtectionKeyStore,
) : LocalProtectionKeyStore {
    var existingCalls = 0
    var provisioningCalls = 0

    override fun existingKey(): ExistingKeyResult {
        existingCalls += 1
        return inner.existingKey()
    }

    override fun provisioningKey(): ExistingKeyResult {
        provisioningCalls += 1
        return inner.provisioningKey()
    }

    override fun deleteKey(): Boolean = inner.deleteKey()

    override fun securityLevel(): ProtectionSecurityLevel = inner.securityLevel()
}
