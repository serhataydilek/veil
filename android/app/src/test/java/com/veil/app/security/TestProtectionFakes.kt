package com.veil.app.security

import java.io.IOException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

internal class TestLocalProtectionKeyStore : LocalProtectionKeyStore {
    private var key: SecretKey? = null
    var provisioningRequested = false
    var provisioningCalls = 0

    override fun existingKey(): ExistingKeyResult = key?.let { ExistingKeyResult.Available(it) } ?: ExistingKeyResult.Missing

    override fun provisioningKey(): ExistingKeyResult {
        provisioningCalls += 1
        provisioningRequested = true
        val current = key ?: KeyGenerator.getInstance("AES").apply { init(256) }.generateKey().also { key = it }
        return ExistingKeyResult.Available(current)
    }

    override fun deleteKey(): Boolean {
        key = null
        return true
    }

    override fun securityLevel(): ProtectionSecurityLevel = ProtectionSecurityLevel.SOFTWARE

    fun makeMissing() {
        key = null
        provisioningRequested = false
    }
}

internal class InMemoryProtectedStateFile : ProtectedStateFile {
    var contents: ByteArray? = null
    var failWrites = false
    var failDeletes = false
    var lastMaximumLength: Int? = null
    var onRead: (() -> Unit)? = null

    override fun exists(): Boolean = contents != null

    override fun read(maximumLength: Int): ByteArray {
        lastMaximumLength = maximumLength
        onRead?.invoke()
        return contents ?: throw IOException("missing")
    }

    override fun write(bytes: ByteArray): Boolean {
        if (failWrites) return false
        contents = bytes.copyOf()
        return true
    }

    override fun delete(): Boolean {
        if (failDeletes) return false
        contents = null
        return true
    }
}

internal class FakeAppAuthenticator(
    var availabilityValue: AuthenticatorAvailability = AuthenticatorAvailability.AVAILABLE,
    var nextResult: AuthenticationResult = AuthenticationResult.SUCCESS,
    var completeImmediately: Boolean = true,
) : AppAuthenticator {
    var authenticateCalls = 0
    var cancelCalls = 0
    private var pending: ((AuthenticationResult) -> Unit)? = null

    override fun availability(): AuthenticatorAvailability = availabilityValue

    override fun authenticate(reason: AuthReason, onResult: (AuthenticationResult) -> Unit) {
        authenticateCalls += 1
        if (completeImmediately) {
            onResult(nextResult)
        } else {
            pending = onResult
        }
    }

    fun complete(result: AuthenticationResult = nextResult) {
        val callback = pending
        pending = null
        callback?.invoke(result)
    }

    override fun cancel() {
        cancelCalls += 1
        val callback = pending
        pending = null
        callback?.invoke(AuthenticationResult.CANCELLED)
    }
}

internal fun generatedAesKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

internal fun protectionFixture(): ProtectionFixture {
    val keys = TestLocalProtectionKeyStore()
    val file = InMemoryProtectedStateFile()
    return ProtectionFixture(ProtectedStateStore(keys, file, AesGcmProtectedBlobCipher()), keys, file)
}

internal data class ProtectionFixture(
    val store: ProtectedStateStore,
    val keys: TestLocalProtectionKeyStore,
    val file: InMemoryProtectedStateFile,
)
