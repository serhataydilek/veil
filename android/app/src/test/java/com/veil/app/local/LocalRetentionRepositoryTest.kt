package com.veil.app.local

import com.veil.app.core.CoreBridgeSnapshot
import com.veil.app.core.CoreBridgeStatus
import com.veil.app.security.ExistingKeyResult
import com.veil.app.security.TestLocalProtectionKeyStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalRetentionRepositoryTest {
    private val policy = retentionPolicyFromSeconds(24L * 60 * 60)!!
    private val created = 1_000_000L

    @Test
    fun validMessageRoundTripAndExpiredNeverReturned() {
        val clock = FakeRetentionClock(wallMs = created, elapsedMs = 0)
        val env = environment(clock)
        insertShell(env)
        val expiry = created + 5_000
        assertTrue(env.session.messages.insert(message(expiry = expiry)))
        assertEquals("body", listed(env).single().body)

        clock.wallMs = expiry
        clock.elapsedMs = 5_000
        assertTrue(listed(env).isEmpty())
        assertEquals(LocalMessageLoadResult.Absent, env.session.messages.loadValidUnexpired("msg-1"))
    }

    @Test
    fun wallRollbackOnSameBootNeverExtendsVisibility() {
        val clock = FakeRetentionClock(wallMs = created, elapsedMs = 0)
        val env = environment(clock)
        insertShell(env)
        val expiry = created + 2_000
        assertTrue(env.session.messages.insert(message(expiry = expiry)))
        clock.elapsedMs = 2_000
        clock.wallMs = created - 50_000
        assertTrue(listed(env).isEmpty())
    }

    @Test
    fun wallJumpForwardCanExpireEarly() {
        val clock = FakeRetentionClock(wallMs = created, elapsedMs = 0)
        val env = environment(clock)
        insertShell(env)
        assertTrue(env.session.messages.insert(message(expiry = created + 10_000)))
        clock.wallMs = created + 10_000
        assertTrue(listed(env).isEmpty())
    }

    @Test
    fun elapsedAdvancesWhileWallFrozenExpiresMessage() {
        val clock = FakeRetentionClock(wallMs = created, elapsedMs = 0)
        val env = environment(clock)
        insertShell(env)
        assertTrue(env.session.messages.insert(message(expiry = created + 3_000)))
        clock.elapsedMs = 3_000
        assertTrue(listed(env).isEmpty())
    }

    @Test
    fun rebootCausesConservativeEarlyPurgeAndKeepsShell() {
        val clock = FakeRetentionClock(wallMs = created, elapsedMs = 0)
        val env = environment(clock)
        insertShell(env)
        assertTrue(env.session.messages.insert(message(expiry = created + policy.maxAvailabilityMillis)))
        clock.boot = BootObservation(bootCount = 2, reliable = true)
        clock.elapsedMs = 1
        assertTrue(listed(env).isEmpty())
        assertEquals(CONV, env.session.conversations.load(CONV)?.conversationId)
        assertEquals(0, env.store.messageCount())
    }

    @Test
    fun ambiguousBootPurgesMessagesEarly() {
        val clock = FakeRetentionClock(wallMs = created, elapsedMs = 0)
        val env = environment(clock)
        insertShell(env)
        assertTrue(env.session.messages.insert(message(expiry = created + 8_000)))
        clock.boot = BootObservation(bootCount = null, reliable = false)
        assertTrue(listed(env).isEmpty())
        assertEquals(CONV, listedShells(env).single().conversationId)
    }

    @Test
    fun missingTimeStateWithMessagesPurgesThem() {
        val clock = FakeRetentionClock(wallMs = created, elapsedMs = 0)
        val env = environment(clock)
        insertShell(env)
        assertTrue(env.session.messages.insert(message(expiry = created + 8_000)))
        env.store.deleteMeta("time-bound")
        assertTrue(listed(env).isEmpty())
        assertEquals(0, env.store.messageCount())
        assertEquals(CONV, env.session.conversations.load(CONV)?.conversationId)
    }

    @Test
    fun corruptTimeStateWithMessagesPurgesThem() {
        val clock = FakeRetentionClock(wallMs = created, elapsedMs = 0)
        val env = environment(clock)
        insertShell(env)
        assertTrue(env.session.messages.insert(message(expiry = created + 8_000)))
        env.store.replaceMeta("time-bound", byteArrayOf(1, 2, 3, 4))
        assertTrue(listed(env).isEmpty())
        assertEquals(0, env.store.messageCount())
    }

    @Test
    fun earlierRelayDeadlineWinsAtRead() {
        val clock = FakeRetentionClock(wallMs = created, elapsedMs = 0)
        val env = environment(clock)
        insertShell(env)
        assertTrue(
            env.session.messages.insert(
                message(expiry = created + 9_000, relay = created + 1_000),
            ),
        )
        clock.elapsedMs = 1_000
        assertTrue(listed(env).isEmpty())
    }

    @Test
    fun laterRelayDeadlineCannotExtendVisibility() {
        val clock = FakeRetentionClock(wallMs = created, elapsedMs = 0)
        val env = environment(clock)
        insertShell(env)
        assertTrue(
            env.session.messages.insert(
                message(expiry = created + 1_000, relay = created + 20_000),
            ),
        )
        clock.elapsedMs = 1_000
        assertTrue(listed(env).isEmpty())
    }

    @Test
    fun lifecycleUpdateCannotExtendExpiry() {
        val clock = FakeRetentionClock(wallMs = created, elapsedMs = 0)
        val env = environment(clock)
        insertShell(env)
        assertTrue(env.session.messages.insert(message(expiry = created + 2_000)))
        assertTrue(env.session.messages.updateLifecycle("msg-1", LocalMessageState.QUEUED))
        val loaded = loadedMessage(env, "msg-1")
        assertEquals(LocalMessageState.QUEUED, loaded.state)
        assertEquals(created + 2_000, loaded.authenticatedExpiryWallMs)
        clock.elapsedMs = 2_000
        assertFalse(env.session.messages.updateLifecycle("msg-1", LocalMessageState.ENCRYPTED))
        assertTrue(listed(env).isEmpty())
    }

    @Test
    fun modifiedPlaintextExpiryHintCannotExtendVisibility() {
        val clock = FakeRetentionClock(wallMs = created, elapsedMs = 0)
        val env = environment(clock)
        insertShell(env)
        assertTrue(env.session.messages.insert(message(expiry = created + 1_000)))
        env.store.tamperExpiryHint("msg-1", created + policy.maxAvailabilityMillis)
        assertEquals(LocalMessageLoadResult.Absent, env.session.messages.loadValidUnexpired("msg-1"))
        assertTrue(listed(env).isEmpty())
        assertEquals(0, env.store.messageCount())
    }

    @Test
    fun overflowAndBeyondMaxAndBeforeCreationRejected() {
        val clock = FakeRetentionClock(wallMs = created, elapsedMs = 0)
        val env = environment(clock)
        insertShell(env)
        assertFalse(env.session.messages.insert(message(expiry = created - 1)))
        assertFalse(env.session.messages.insert(message(expiry = created + policy.maxAvailabilityMillis + 1)))
        assertFalse(
            env.session.messages.insert(
                message(createdAt = Long.MAX_VALUE - 10, expiry = Long.MAX_VALUE - 9),
            ),
        )
        assertTrue(listed(env).isEmpty())
    }

    @Test
    fun exactMaxBoundaryInsertsAndExpiresAtDeadline() {
        val clock = FakeRetentionClock(wallMs = created, elapsedMs = 0)
        val env = environment(clock)
        insertShell(env)
        val expiry = created + policy.maxAvailabilityMillis
        assertTrue(env.session.messages.insert(message(expiry = expiry)))
        assertEquals(1, listed(env).size)
        clock.elapsedMs = policy.maxAvailabilityMillis
        assertTrue(listed(env).isEmpty())
    }

    @Test
    fun missingKeyDoesNotProvisionReplacement() {
        val inner = TestLocalProtectionKeyStore().also { it.provisioningKey() }
        val keys = RecordingLocalProtectionKeyStore(inner)
        val store = InMemoryLocalRecordStore()
        val clock = FakeRetentionClock(wallMs = created, elapsedMs = 0)
        val session = openSession(keys, store, clock)
        insertShell(Env(session, store, keys))
        assertTrue(session.messages.insert(message(expiry = created + 1_000)))
        val before = keys.provisioningCalls
        inner.makeMissing()
        val reopened = LocalStoreSession.open(keys, store, AesGcmLocalRecordCipher(), clock, policy)
        assertTrue(reopened is LocalStoreOpenResult.KeyUnavailable)
        assertEquals(before, keys.provisioningCalls)
        assertEquals(1, store.messageCount())
    }

    @Test
    fun corruptMessageCiphertextNeverRenders() {
        val clock = FakeRetentionClock(wallMs = created, elapsedMs = 0)
        val env = environment(clock)
        insertShell(env)
        assertTrue(env.session.messages.insert(message(expiry = created + 8_000)))
        env.store.replaceMessageCiphertext("msg-1", ByteArray(64) { 7 })
        assertEquals(LocalMessageLoadResult.Absent, env.session.messages.loadValidUnexpired("msg-1"))
        assertTrue(listed(env).isEmpty())
        assertEquals(0, env.store.messageCount())
    }

    @Test
    fun destroyRemovesRelationshipAndMessages() {
        val clock = FakeRetentionClock(wallMs = created, elapsedMs = 0)
        val env = environment(clock)
        insertShell(env)
        assertTrue(env.session.messages.insert(message(expiry = created + 1_000)))
        env.session.destroyConversation(CONV)
        assertNull(env.session.conversations.load(CONV))
        assertEquals(0, env.store.messageCount())
    }

    @Test
    fun resetDeletesMessagesAndKeepsShell() {
        val clock = FakeRetentionClock(wallMs = created, elapsedMs = 0)
        val env = environment(clock)
        insertShell(env)
        assertTrue(env.session.messages.insert(message(expiry = created + 1_000)))
        assertTrue(env.session.resetConversationKeepingShell(CONV))
        assertEquals(LocalConversationState.RESET, env.session.conversations.load(CONV)?.state)
        assertEquals("alias", env.session.conversations.load(CONV)?.localAlias)
        assertEquals(0, env.store.messageCount())
    }

    @Test
    fun futureCreationTimestampIsRejectedOnInsert() {
        val clock = FakeRetentionClock(wallMs = created, elapsedMs = 0)
        val env = environment(clock)
        insertShell(env)
        assertFalse(
            env.session.messages.insert(
                message(expiry = created + 50_000, createdAt = created + 10_000),
            ),
        )
        assertEquals(0, env.store.messageCount())
    }

    @Test
    fun futureCreationCiphertextNeverRendersAndIsRemoved() {
        val clock = FakeRetentionClock(wallMs = created, elapsedMs = 0)
        val env = environment(clock)
        insertShell(env)
        val futureCreated = created + 60_000
        val record = LocalMessageRecord(
            messageId = "msg-future",
            conversationId = CONV,
            direction = LocalMessageDirection.OUTBOUND,
            state = LocalMessageState.LOCAL_CREATED,
            createdAtWallMs = futureCreated,
            authenticatedExpiryWallMs = futureCreated + 1_000,
            relayDeadlineWallMs = null,
            body = "future",
        )
        val plaintext = LocalMessagePayloadCodec.encode(record)!!
        val key = (env.keys.existingKey() as com.veil.app.security.ExistingKeyResult.Available).key
        val aad = LocalRecordAad.encode(LocalRecordType.MESSAGE, record.messageId)!!
        val blob = AesGcmLocalRecordCipher().encrypt(key, plaintext, aad)
        val encoded = LocalRecordFormat.encode(blob)!!
        env.store.insertMessage(
            StoredMessageRow(
                messageId = record.messageId,
                conversationId = CONV,
                expiryHintMs = futureCreated + 1_000,
                ciphertext = encoded,
            ),
        )
        assertEquals(LocalMessageLoadResult.Absent, env.session.messages.loadValidUnexpired("msg-future"))
        assertTrue(listed(env).isEmpty())
        assertEquals(0, env.store.messageCount())
    }

    @Test
    fun resetPersistFailureRollsBackMessageDeletion() {
        val cipher = FakeLocalRecordCipher()
        val keys = TestLocalProtectionKeyStore().also { it.provisioningKey() }
        val store = InMemoryLocalRecordStore()
        val clock = FakeRetentionClock(wallMs = created, elapsedMs = 0)
        val session = (
            LocalStoreSession.open(keys, store, cipher, clock, policy) as LocalStoreOpenResult.Ready
            ).session
        val env = Env(session, store, keys)
        insertShell(env)
        assertTrue(session.messages.insert(message(expiry = created + 8_000)))
        cipher.encryptFailure = { type ->
            if (type == LocalRecordType.CONVERSATION) {
                java.security.GeneralSecurityException("conversation encrypt failed")
            } else {
                null
            }
        }
        assertFalse(session.resetConversationKeepingShell(CONV))
        assertEquals(1, store.messageCount())
        assertEquals(LocalConversationState.ESTABLISHING, session.conversations.load(CONV)?.state)
        assertEquals("body", listed(env).single().body)
    }

    private fun environment(clock: FakeRetentionClock): Env {
        val keys = TestLocalProtectionKeyStore().also { it.provisioningKey() }
        val store = InMemoryLocalRecordStore()
        return Env(openSession(RecordingLocalProtectionKeyStore(keys), store, clock), store, keys)
    }

    private fun openSession(
        keys: com.veil.app.security.LocalProtectionKeyStore,
        store: LocalRecordStore,
        clock: RetentionClock,
    ): LocalStoreSession {
        val opened = LocalStoreSession.open(keys, store, AesGcmLocalRecordCipher(), clock, policy)
        return (opened as LocalStoreOpenResult.Ready).session
    }

    private fun listed(env: Env): List<LocalMessageRecord> {
        val result = env.session.messages.listValidUnexpired(CONV)
        assertTrue("expected available messages, got $result", result is LocalMessageListResult.Available)
        return (result as LocalMessageListResult.Available).records
    }

    private fun listedShells(env: Env): List<LocalConversationShell> {
        val result = env.session.conversations.list()
        assertTrue("expected available conversations, got $result", result is LocalConversationListResult.Available)
        return (result as LocalConversationListResult.Available).shells
    }

    private fun loadedMessage(env: Env, messageId: String): LocalMessageRecord {
        val result = env.session.messages.loadValidUnexpired(messageId)
        assertTrue("expected present message, got $result", result is LocalMessageLoadResult.Present)
        return (result as LocalMessageLoadResult.Present).record
    }

    private fun insertShell(env: Env) {
        assertTrue(
            env.session.conversations.upsert(
                LocalConversationShell(
                    conversationId = CONV,
                    state = LocalConversationState.ESTABLISHING,
                    localAlias = "alias",
                    createdAtWallMs = created,
                    updatedAtWallMs = created,
                ),
            ),
        )
    }

    private fun message(
        expiry: Long,
        relay: Long? = null,
        createdAt: Long = created,
    ) = LocalMessageRecord(
        messageId = "msg-1",
        conversationId = CONV,
        direction = LocalMessageDirection.OUTBOUND,
        state = LocalMessageState.LOCAL_CREATED,
        createdAtWallMs = createdAt,
        authenticatedExpiryWallMs = expiry,
        relayDeadlineWallMs = relay,
        body = "body",
    )

    private data class Env(
        val session: LocalStoreSession,
        val store: InMemoryLocalRecordStore,
        val keys: com.veil.app.security.LocalProtectionKeyStore,
    )

    private companion object {
        const val CONV = "conv-1"
    }
}
