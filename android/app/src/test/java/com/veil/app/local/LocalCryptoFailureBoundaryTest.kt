package com.veil.app.local

import com.veil.app.core.CoreBridgeSnapshot
import com.veil.app.core.CoreBridgeStatus
import com.veil.app.security.TestLocalProtectionKeyStore
import java.security.GeneralSecurityException
import java.security.InvalidKeyException
import java.security.ProviderException
import javax.crypto.AEADBadTagException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalCryptoFailureBoundaryTest {
    private val policy = retentionPolicyFromSeconds(24L * 60 * 60)!!
    private val created = 1_000_000L

    @Test
    fun validMessageDecryptIsReturned() {
        val env = environment()
        insertShell(env)
        assertTrue(env.session.messages.insert(message()))
        val listed = env.session.messages.listValidUnexpired(CONV)
        assertTrue(listed is LocalMessageListResult.Available)
        assertEquals("body", (listed as LocalMessageListResult.Available).records.single().body)
        assertEquals(1, env.store.messageCount())
    }

    @Test
    fun aeadAuthenticationFailureRemovesMessageAndNeverRenders() {
        val env = environment()
        insertShell(env)
        assertTrue(env.session.messages.insert(message()))
        val current = env.store.loadMessage("msg-1")!!
        val tampered = current.ciphertext.copyOf()
        tampered[tampered.lastIndex] = (tampered.last().toInt() xor 0x7f).toByte()
        env.store.replaceMessageCiphertext("msg-1", tampered)
        assertEquals(LocalMessageLoadResult.Absent, env.session.messages.loadValidUnexpired("msg-1"))
        val listed = env.session.messages.listValidUnexpired(CONV)
        assertTrue(listed is LocalMessageListResult.Available)
        assertTrue((listed as LocalMessageListResult.Available).records.isEmpty())
        assertEquals(0, env.store.messageCount())
    }

    @Test
    fun providerExceptionPreservesMessageAndReturnsUnavailable() {
        val cipher = FakeLocalRecordCipher()
        val env = environment(cipher)
        insertShell(env)
        assertTrue(env.session.messages.insert(message()))
        cipher.decryptFailure = { type ->
            if (type == LocalRecordType.MESSAGE) ProviderException("provider unavailable") else null
        }
        assertEquals(LocalMessageLoadResult.KeyUnavailable, env.session.messages.loadValidUnexpired("msg-1"))
        assertEquals(
            LocalMessageListResult.KeyUnavailable,
            env.session.messages.listValidUnexpired(CONV),
        )
        assertEquals(1, env.store.messageCount())
    }

    @Test
    fun keyUnavailablePreservesMessageAndReturnsUnavailable() {
        val cipher = FakeLocalRecordCipher()
        val env = environment(cipher)
        insertShell(env)
        assertTrue(env.session.messages.insert(message()))
        cipher.decryptFailure = { type ->
            if (type == LocalRecordType.MESSAGE) InvalidKeyException("keystore key unavailable") else null
        }
        assertEquals(LocalMessageLoadResult.KeyUnavailable, env.session.messages.loadValidUnexpired("msg-1"))
        assertEquals(
            LocalMessageListResult.KeyUnavailable,
            env.session.messages.listValidUnexpired(CONV),
        )
        assertEquals(1, env.store.messageCount())
    }

    @Test
    fun malformedVlrEnvelopeRemovesMessage() {
        val env = environment()
        insertShell(env)
        assertTrue(env.session.messages.insert(message()))
        env.store.replaceMessageCiphertext("msg-1", byteArrayOf(1, 2, 3, 4, 5))
        assertEquals(LocalMessageLoadResult.Absent, env.session.messages.loadValidUnexpired("msg-1"))
        assertEquals(0, env.store.messageCount())
    }

    @Test
    fun validTimeBoundLoads() {
        val env = environment()
        insertShell(env)
        assertTrue(env.session.messages.insert(message()))
        val refreshed = env.session.refreshTimeAndPurge()
        assertTrue(refreshed is TimeRefreshResult.Advanced)
        assertEquals(1, env.store.messageCount())
    }

    @Test
    fun malformedTimeBoundEnvelopeIsCorruptAndPurgesMessages() {
        val env = environment()
        insertShell(env)
        assertTrue(env.session.messages.insert(message()))
        env.store.replaceMeta("time-bound", byteArrayOf(1, 2, 3, 4))
        val refreshed = env.session.refreshTimeAndPurge()
        assertTrue(refreshed is TimeRefreshResult.Advanced)
        assertEquals(0, env.store.messageCount())
    }

    @Test
    fun timeBoundAeadFailureIsCorruptAndPurgesMessages() {
        val env = environment()
        insertShell(env)
        assertTrue(env.session.messages.insert(message()))
        val encoded = env.store.loadMeta("time-bound")!!
        val tampered = encoded.copyOf()
        tampered[tampered.lastIndex] = (tampered.last().toInt() xor 0x7f).toByte()
        env.store.replaceMeta("time-bound", tampered)
        val refreshed = env.session.refreshTimeAndPurge()
        assertTrue(refreshed is TimeRefreshResult.Advanced)
        assertEquals(0, env.store.messageCount())
    }

    @Test
    fun timeBoundProviderExceptionIsUnavailableAndDoesNotPurge() {
        val cipher = FakeLocalRecordCipher()
        val env = environment(cipher)
        insertShell(env)
        assertTrue(env.session.messages.insert(message()))
        cipher.decryptFailure = { type ->
            if (type == LocalRecordType.TIME_BOUND) ProviderException("provider unavailable") else null
        }
        val refreshed = env.session.refreshTimeAndPurge()
        assertEquals(TimeRefreshResult.KeyUnavailable, refreshed)
        assertEquals(1, env.store.messageCount())
        assertNotEquals(
            LocalMessageListResult.Available(emptyList()),
            env.session.messages.listValidUnexpired(CONV),
        )
        assertEquals(LocalMessageListResult.KeyUnavailable, env.session.messages.listValidUnexpired(CONV))
    }

    @Test
    fun timeBoundMissingKeyIsUnavailableAndDoesNotPurge() {
        val cipher = FakeLocalRecordCipher()
        val env = environment(cipher)
        insertShell(env)
        assertTrue(env.session.messages.insert(message()))
        cipher.decryptFailure = { type ->
            if (type == LocalRecordType.TIME_BOUND) InvalidKeyException("missing key") else null
        }
        assertEquals(TimeRefreshResult.KeyUnavailable, env.session.refreshTimeAndPurge())
        assertEquals(1, env.store.messageCount())
    }

    @Test
    fun aeadTagFailureOnTimeBoundViaThrownExceptionIsCorrupt() {
        val cipher = FakeLocalRecordCipher()
        val env = environment(cipher)
        insertShell(env)
        assertTrue(env.session.messages.insert(message()))
        cipher.decryptFailure = { type ->
            if (type == LocalRecordType.TIME_BOUND) AEADBadTagException("tag mismatch") else null
        }
        val refreshed = env.session.refreshTimeAndPurge()
        assertTrue(refreshed is TimeRefreshResult.Advanced)
        assertEquals(0, env.store.messageCount())
    }

    @Test
    fun timeBoundSaveFailureNeverLeavesSessionReady() {
        val cipher = FakeLocalRecordCipher()
        cipher.encryptBadIv = true
        val controller = controller(store = InMemoryLocalRecordStore(), cipher = cipher)
        controller.start()
        assertEquals(LocalDataStatus.ERROR, awaitSettled(controller))
        assertNull(controller.renderableMessages(CONV))
        controller.cancel()
    }

    @Test
    fun timeBoundEncryptProviderFailureNeverReadyAndPreservesMessages() {
        val store = InMemoryLocalRecordStore()
        val setup = environment(store = store)
        insertShell(setup)
        assertTrue(setup.session.messages.insert(message()))
        setup.session.close()
        val cipher = FakeLocalRecordCipher()
        cipher.encryptFailure = { type ->
            if (type == LocalRecordType.TIME_BOUND) ProviderException("encrypt unavailable") else null
        }
        val controller = controller(store = store, cipher = cipher, keys = setup.keys)
        controller.start()
        assertEquals(LocalDataStatus.KEY_UNAVAILABLE, awaitSettled(controller))
        assertEquals(1, store.messageCount())
        assertNull(controller.renderableMessages(CONV))
        controller.cancel()
    }

    @Test
    fun metadataSqliteWriteFailureNeverReady() {
        val store = InMemoryLocalRecordStore()
        val setup = environment(store = store)
        insertShell(setup)
        assertTrue(setup.session.messages.insert(message()))
        setup.session.close()
        store.failMetaWrites = true
        val controller = controller(store = store, keys = setup.keys)
        controller.start()
        assertEquals(LocalDataStatus.ERROR, awaitSettled(controller))
        assertEquals(1, store.messageCount())
        assertNull(controller.renderableMessages(CONV))
        controller.cancel()
    }

    @Test
    fun expectedCheckedCryptoFailureDoesNotRemainStuckInPurging() {
        val cipher = FakeLocalRecordCipher()
        cipher.encryptFailure = { type ->
            if (type == LocalRecordType.TIME_BOUND) GeneralSecurityException("encrypt failed") else null
        }
        val controller = controller(store = InMemoryLocalRecordStore(), cipher = cipher)
        controller.start()
        val status = awaitSettled(controller)
        assertEquals(LocalDataStatus.ERROR, status)
        assertNotEquals(LocalDataStatus.CHECKING, status)
        assertNotEquals(LocalDataStatus.PURGING, status)
        assertNotEquals(LocalDataStatus.READY, status)
        controller.cancel()
    }

    @Test
    fun unicodeAndOversizedLocalIdsFailCleanlyBeforeAad() {
        val unicode = "café"
        val cjk = "消息"
        val oversized = "a".repeat(LocalRecordAad.MAX_RECORD_ID_BYTES + 1)
        assertFalse(validLocalId(unicode))
        assertFalse(validLocalId(cjk))
        assertFalse(validLocalId(oversized))
        assertNull(LocalRecordAad.encode(LocalRecordType.MESSAGE, unicode))
        assertNull(LocalRecordAad.encode(LocalRecordType.MESSAGE, cjk))
        assertNull(LocalRecordAad.encode(LocalRecordType.CONVERSATION, oversized))
        val env = environment()
        assertFalse(
            env.session.conversations.upsert(
                LocalConversationShell(unicode, LocalConversationState.ESTABLISHING, null, created, created),
            ),
        )
        insertShell(env)
        assertFalse(env.session.messages.insert(message().copy(messageId = unicode)))
        assertFalse(env.session.messages.insert(message().copy(messageId = oversized)))
        assertEquals(0, env.store.messageCount())
    }

    @Test
    fun asciiLocalIdAtByteLimitIsAccepted() {
        val id = "A-z0._" + "b".repeat(LocalRecordAad.MAX_RECORD_ID_BYTES - 6)
        assertEquals(LocalRecordAad.MAX_RECORD_ID_BYTES, id.encodeToByteArray().size)
        assertTrue(validLocalId(id))
        assertTrue(LocalRecordAad.encode(LocalRecordType.MESSAGE, id) != null)
    }

    private fun environment(
        cipher: LocalRecordCipher = AesGcmLocalRecordCipher(),
        store: InMemoryLocalRecordStore = InMemoryLocalRecordStore(),
        keys: com.veil.app.security.LocalProtectionKeyStore = TestLocalProtectionKeyStore().also { it.provisioningKey() },
    ): Env {
        val session = (
            LocalStoreSession.open(keys, store, cipher, FakeRetentionClock(created, 0), policy)
                as LocalStoreOpenResult.Ready
            ).session
        return Env(session, store, keys)
    }

    private fun insertShell(env: Env) {
        assertTrue(
            env.session.conversations.upsert(
                LocalConversationShell(CONV, LocalConversationState.ESTABLISHING, "alias", created, created),
            ),
        )
    }

    private fun message() = LocalMessageRecord(
        messageId = "msg-1",
        conversationId = CONV,
        direction = LocalMessageDirection.OUTBOUND,
        state = LocalMessageState.LOCAL_CREATED,
        createdAtWallMs = created,
        authenticatedExpiryWallMs = created + 8_000,
        relayDeadlineWallMs = null,
        body = "body",
    )

    private fun controller(
        store: InMemoryLocalRecordStore,
        cipher: LocalRecordCipher = AesGcmLocalRecordCipher(),
        keys: com.veil.app.security.LocalProtectionKeyStore = TestLocalProtectionKeyStore().also { it.provisioningKey() },
    ) = LocalDataController(
        keyStore = keys,
        storeFactory = LocalRecordStoreFactory { LocalStoreFactoryResult.Opened(store) },
        cipher = cipher,
        clock = FakeRetentionClock(created, 0),
        policyLoader = RustRetentionPolicyLoader {
            CoreBridgeSnapshot(
                status = CoreBridgeStatus.AVAILABLE,
                maxMessageAvailabilitySeconds = 24L * 60 * 60,
            )
        },
        workerDispatcher = Dispatchers.Unconfined,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
    )

    private fun awaitSettled(controller: LocalDataController): LocalDataStatus = runBlocking {
        controller.status.first {
            it != LocalDataStatus.CHECKING &&
                it != LocalDataStatus.WAITING_FOR_PROTECTION &&
                it != LocalDataStatus.PURGING
        }
    }

    private data class Env(
        val session: LocalStoreSession,
        val store: InMemoryLocalRecordStore,
        val keys: com.veil.app.security.LocalProtectionKeyStore,
    )

    private companion object {
        const val CONV = "conv-1"
    }
}
