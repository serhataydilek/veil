package com.veil.app.security

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AndroidProtectedStateStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var keys: AndroidLocalProtectionKeyStore
    private lateinit var file: AndroidAtomicProtectedStateFile
    private lateinit var store: ProtectedStateStore

    @Before
    fun setUp() {
        keys = AndroidLocalProtectionKeyStore(TEST_ALIAS)
        file = AndroidAtomicProtectedStateFile(context, TEST_FILE_NAME)
        store = ProtectedStateStore(keys, file, AesGcmProtectedBlobCipher())
        assertTrue("test setup must remove only its isolated alias and file", store.purge().complete)
    }

    @After
    fun tearDown() {
        assertTrue("test teardown must remove only its isolated alias and file", store.purge().complete)
    }

    @Test
    fun createsKeyAndRoundTripsProtectedState() {
        assertEquals(ProtectionStatus.READY, store.provision())
        assertTrue(keys.existingKey() is ExistingKeyResult.Available)
        assertEquals(ProtectionStatus.READY, store.currentStatus())
    }

    @Test
    fun repeatedEncryptionUsesDifferentCiphertext() {
        val key = (keys.provisioningKey() as ExistingKeyResult.Available).key
        val cipher = AesGcmProtectedBlobCipher()
        val first = cipher.encrypt(key, "state".encodeToByteArray())
        val second = cipher.encrypt(key, "state".encodeToByteArray())

        assertNotEquals(first.iv.toList(), second.iv.toList())
        assertEquals("state", cipher.decrypt(key, first).decodeToString())
    }

    @Test
    fun deletedKeyWithExistingStateIsUnavailableWithoutRegeneration() {
        assertEquals(ProtectionStatus.READY, store.provision())
        assertTrue(keys.deleteKey())

        assertEquals(ProtectionStatus.KEY_UNAVAILABLE, store.currentStatus())
        assertEquals(ProtectionStatus.KEY_UNAVAILABLE, store.provision())
        assertTrue(keys.existingKey() is ExistingKeyResult.Missing)
    }

    @Test
    fun purgeRemovesTestKeyAndProtectedState() {
        store.provision()

        val result = store.purge()

        assertTrue(result.complete)
        assertFalse(file.exists())
        assertTrue(keys.existingKey() is ExistingKeyResult.Missing)
    }

    @Test
    fun oversizedPhysicalFileFailsClosed() {
        assertTrue(file.write(ByteArray(ProtectedStateFormat.MAX_ENCODED_LENGTH + 1)))
        keys.provisioningKey()

        assertEquals(ProtectionStatus.CORRUPT_OR_UNREADABLE, store.currentStatus())
    }

    private companion object {
        const val TEST_ALIAS = "veil.test.local-protection.v1"
        const val TEST_FILE_NAME = "veil-test-local-state.v1"
    }
}
