package com.veil.app.security

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AndroidProtectedPreferenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var keys: AndroidLocalProtectionKeyStore
    private lateinit var file: AndroidAtomicProtectedStateFile
    private lateinit var store: ProtectedStateStore

    @Before
    fun setUp() {
        keys = AndroidLocalProtectionKeyStore(TEST_ALIAS)
        file = AndroidAtomicProtectedStateFile(context, TEST_FILE_NAME)
        store = ProtectedStateStore(keys, file, AesGcmProtectedBlobCipher())
        assertTrue(store.purge().complete)
    }

    @After
    fun tearDown() {
        assertTrue(store.purge().complete)
    }

    @Test
    fun appLockPreferenceRoundTripsInsideProtectedState() {
        assertEquals(ProtectionStatus.READY, store.provision())
        assertEquals(false, store.load().payload?.appLockEnabled)
        assertTrue(store.writeAppLockEnabled(true))
        assertEquals(true, store.load().payload?.appLockEnabled)
        assertEquals(false, store.load().payload?.fromLegacy)
        assertTrue(store.writeAppLockEnabled(false))
        assertEquals(false, store.load().payload?.appLockEnabled)
    }

    @Test
    fun phase1bSentinelMigratesToPhase1cPayload() {
        val key = (keys.provisioningKey() as ExistingKeyResult.Available).key
        val blob = AesGcmProtectedBlobCipher().encrypt(key, ProtectedLocalPayloadCodec.LEGACY_SENTINEL)
        assertTrue(file.write(ProtectedStateFormat.encode(blob)))

        assertTrue(store.migrateLegacyIfPresent())
        val loaded = store.load()
        assertEquals(ProtectionStatus.READY, loaded.status)
        assertEquals(false, loaded.payload?.fromLegacy)
        assertEquals(false, loaded.payload?.appLockEnabled)
    }

    private companion object {
        const val TEST_ALIAS = "veil.test.app-lock-preference.v1"
        const val TEST_FILE_NAME = "veil-test-app-lock-preference.v1"
    }
}
