package com.veil.app.security

import java.io.IOException
import java.security.ProviderException
import java.util.concurrent.TimeUnit
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtectedStateStoreTest {
    @Test
    fun freshStateIsNotProvisioned() {
        val fixture = fixture()

        assertEquals(ProtectionStatus.NOT_PROVISIONED, fixture.store.currentStatus())
    }

    @Test
    fun successfulProvisioningTransitionsToReady() {
        val fixture = fixture()

        assertEquals(ProtectionStatus.READY, fixture.store.provision())
        assertTrue(fixture.file.exists())
    }

    @Test
    fun existingCiphertextWithMissingKeyIsUnavailableAndDoesNotCreateReplacement() {
        val fixture = fixture()
        assertEquals(ProtectionStatus.READY, fixture.store.provision())
        fixture.keys.makeMissing()

        assertEquals(ProtectionStatus.KEY_UNAVAILABLE, fixture.store.currentStatus())
        assertEquals(ProtectionStatus.KEY_UNAVAILABLE, fixture.store.provision())
        assertFalse(fixture.keys.provisioningRequested)
    }

    @Test
    fun malformedStateIsUnreadable() {
        val fixture = fixture()
        fixture.store.provision()
        fixture.file.contents = byteArrayOf(1, 2, 3)

        assertEquals(ProtectionStatus.CORRUPT_OR_UNREADABLE, fixture.store.currentStatus())
    }

    @Test
    fun unsupportedVersionFailsClosed() {
        val fixture = fixture()
        fixture.store.provision()
        fixture.file.contents = byteArrayOf('V'.code.toByte(), 'L'.code.toByte(), 'P'.code.toByte(), '1'.code.toByte(), 2)

        assertEquals(ProtectionStatus.CORRUPT_OR_UNREADABLE, fixture.store.currentStatus())
    }

    @Test
    fun oversizedCiphertextLengthIsRejectedWithoutAllocation() {
        val malformed = byteArrayOf(
            'V'.code.toByte(), 'L'.code.toByte(), 'P'.code.toByte(), '1'.code.toByte(),
            1, 12, 0x7f, 0xff.toByte(), 0xff.toByte(), 0xff.toByte(),
        )

        assertEquals(null, ProtectedStateFormat.decode(malformed))
    }

    @Test
    fun exactMaximumEnvelopeIsAccepted() {
        val ciphertext = ByteArray(ProtectedStateFormat.maxCiphertextLength) { 7 }
        val encoded = ProtectedStateFormat.encode(ProtectedBlob(ByteArray(ProtectedStateFormat.IV_LENGTH), ciphertext))

        assertEquals(ProtectedStateFormat.MAX_ENCODED_LENGTH, encoded.size)
        assertEquals(ciphertext.toList(), ProtectedStateFormat.decode(encoded)?.ciphertext?.toList())
    }

    @Test
    fun maximumPlusOnePhysicalBytesAreRejectedAfterBoundedRead() {
        val fixture = fixture()
        fixture.store.provision()
        fixture.file.contents = ByteArray(ProtectedStateFormat.MAX_ENCODED_LENGTH + 1)

        assertEquals(ProtectionStatus.CORRUPT_OR_UNREADABLE, fixture.store.currentStatus())
        assertEquals(ProtectedStateFormat.MAX_ENCODED_LENGTH, fixture.file.lastMaximumLength)
    }

    @Test
    fun providerFailureDuringKeyProvisioningFailsClosed() {
        val file = InMemoryProtectedStateFile()
        val failingKeys = object : LocalProtectionKeyStore by TestLocalProtectionKeyStore() {
            override fun provisioningKey(): ExistingKeyResult = throw ProviderException("provider unavailable")
        }

        assertEquals(ProtectionStatus.ERROR, ProtectedStateStore(failingKeys, file, AesGcmProtectedBlobCipher()).provision())
    }

    @Test
    fun providerFailureDuringExistingKeyLookupFailsClosed() {
        val file = InMemoryProtectedStateFile().apply { contents = byteArrayOf(1) }
        val failingKeys = object : LocalProtectionKeyStore by TestLocalProtectionKeyStore() {
            override fun existingKey(): ExistingKeyResult = throw ProviderException("provider unavailable")
        }

        assertEquals(ProtectionStatus.KEY_UNAVAILABLE, ProtectedStateStore(failingKeys, file, AesGcmProtectedBlobCipher()).currentStatus())
    }

    @Test
    fun providerFailureDuringEncryptionFailsClosed() {
        val fixture = fixture()
        val failingCipher = object : ProtectedBlobCipher {
            override fun encrypt(key: SecretKey, plaintext: ByteArray): ProtectedBlob = throw ProviderException("provider unavailable")
            override fun decrypt(key: SecretKey, blob: ProtectedBlob): ByteArray = throw ProviderException("provider unavailable")
        }

        assertEquals(ProtectionStatus.ERROR, ProtectedStateStore(fixture.keys, fixture.file, failingCipher).provision())
    }

    @Test
    fun unexpectedProviderIvLengthFailsWithoutCrashing() {
        val fixture = fixture()
        val wrongIvCipher = object : ProtectedBlobCipher {
            override fun encrypt(key: SecretKey, plaintext: ByteArray): ProtectedBlob = ProtectedBlob(ByteArray(8), ByteArray(16))
            override fun decrypt(key: SecretKey, blob: ProtectedBlob): ByteArray = ByteArray(0)
        }

        assertEquals(ProtectionStatus.ERROR, ProtectedStateStore(fixture.keys, fixture.file, wrongIvCipher).provision())
    }

    @Test
    fun duplicatePrepareDoesNotRunConcurrentProvisioning() {
        val fixture = fixture()
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        try {
            val controller = LocalProtectionController(
                fixture.store,
                executor.asCoroutineDispatcher(),
                CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            )
            controller.prepare()
            controller.prepare()
            executor.shutdown()
            assertTrue(executor.awaitTermination(3, TimeUnit.SECONDS))
            assertEquals(1, fixture.keys.provisioningCalls)
            assertEquals(ProtectionStatus.READY, controller.status.value)
            controller.cancel()
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun initialStatusIsScheduledWithoutSynchronousProtectedStateWork() {
        val fixture = fixture()
        fixture.store.provision()
        val readStarted = java.util.concurrent.CountDownLatch(1)
        val releaseRead = java.util.concurrent.CountDownLatch(1)
        fixture.file.onRead = {
            readStarted.countDown()
            assertTrue(releaseRead.await(3, TimeUnit.SECONDS))
        }
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        try {
            val controller = LocalProtectionController(
                fixture.store,
                executor.asCoroutineDispatcher(),
                CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            )

            assertEquals(ProtectionStatus.CHECKING, controller.status.value)
            assertTrue(readStarted.await(3, TimeUnit.SECONDS))
            releaseRead.countDown()
            executor.shutdown()
            assertTrue(executor.awaitTermination(3, TimeUnit.SECONDS))
            assertEquals(ProtectionStatus.READY, controller.status.value)
            controller.cancel()
        } finally {
            releaseRead.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun failedWriteDoesNotClaimReady() {
        val fixture = fixture()
        fixture.file.failWrites = true

        assertEquals(ProtectionStatus.ERROR, fixture.store.provision())
        assertEquals(ProtectionStatus.NOT_PROVISIONED, fixture.store.currentStatus())
    }

    @Test
    fun purgeRemovesLogicalKeyAndState() {
        val fixture = fixture()
        fixture.store.provision()

        val result = fixture.store.purge()

        assertTrue(result.complete)
        assertEquals(ProtectionStatus.PURGED, result.status)
        assertFalse(fixture.file.exists())
        assertTrue(fixture.keys.existingKey() is ExistingKeyResult.Missing)
    }

    @Test
    fun partialPurgeDoesNotClaimCompletion() {
        val fixture = fixture()
        fixture.store.provision()
        fixture.file.failDeletes = true

        val result = fixture.store.purge()

        assertFalse(result.complete)
        assertEquals(ProtectionStatus.KEY_UNAVAILABLE, result.status)
        assertTrue(fixture.file.exists())
    }

    @Test
    fun gcmUsesFreshRandomizedIvForEachEncryption() {
        val cipher = AesGcmProtectedBlobCipher()
        val key = generatedKey()

        val first = cipher.encrypt(key, "sentinel".encodeToByteArray())
        val second = cipher.encrypt(key, "sentinel".encodeToByteArray())

        assertNotEquals(first.iv.toList(), second.iv.toList())
        assertEquals("sentinel", cipher.decrypt(key, first).decodeToString())
    }

    private fun fixture(): Fixture {
        val keys = TestLocalProtectionKeyStore()
        val file = InMemoryProtectedStateFile()
        return Fixture(ProtectedStateStore(keys, file, AesGcmProtectedBlobCipher()), keys, file)
    }

    private fun generatedKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    private data class Fixture(
        val store: ProtectedStateStore,
        val keys: TestLocalProtectionKeyStore,
        val file: InMemoryProtectedStateFile,
    )
}

private class TestLocalProtectionKeyStore : LocalProtectionKeyStore {
    private var key: SecretKey? = null
    var provisioningRequested = false

    override fun existingKey(): ExistingKeyResult = key?.let { ExistingKeyResult.Available(it) } ?: ExistingKeyResult.Missing

    var provisioningCalls = 0

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

private class InMemoryProtectedStateFile : ProtectedStateFile {
    var contents: ByteArray? = null
    var failWrites = false
    var failDeletes = false

    override fun exists(): Boolean = contents != null
    var lastMaximumLength: Int? = null
    var onRead: (() -> Unit)? = null
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
