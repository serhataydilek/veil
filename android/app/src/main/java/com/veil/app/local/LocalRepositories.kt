package com.veil.app.local

import com.veil.app.security.ExistingKeyResult
import com.veil.app.security.LocalProtectionKeyStore
import java.security.GeneralSecurityException
import java.security.ProviderException
import javax.crypto.SecretKey

internal sealed interface LocalStoreOpenResult {
    data class Ready(val session: LocalStoreSession) : LocalStoreOpenResult
    data object KeyUnavailable : LocalStoreOpenResult
    data object Unreadable : LocalStoreOpenResult
}

internal class LocalStoreSession(
    private val key: SecretKey,
    private val cipher: LocalRecordCipher,
    private val store: LocalRecordStore,
    private val clock: RetentionClock,
    private val policy: RetentionPolicy,
) {
    val conversations = LocalConversationRepository(key, cipher, store)
    val messages = LocalMessageRepository(key, cipher, store, policy) { conservativeNowMs() }
    private val timeStore = ConservativeTimeStore(key, cipher, store)
    private var refreshingTime = false
    private var lastBound: ConservativeTimeBound? = null

    fun refreshTimeAndPurge(): ConservativeTimeBound {
        if (refreshingTime) {
            return lastBound ?: ConservativeTime.initialize(clock)
        }
        refreshingTime = true
        return try {
            store.transact {
                val loaded = timeStore.load()
                val hasMessages = store.messageCount() > 0
                val bound = when (loaded) {
                    TimeBoundLoad.Missing, TimeBoundLoad.Corrupt -> {
                        if (hasMessages) store.deleteAllMessages()
                        val initialized = ConservativeTime.initialize(clock)
                        timeStore.save(initialized)
                        initialized
                    }
                    is TimeBoundLoad.Present -> {
                        val advanced = ConservativeTime.advance(loaded.bound, clock)
                        timeStore.save(advanced.bound)
                        lastBound = advanced.bound
                        if (advanced.expireAllMessages) {
                            store.deleteAllMessages()
                        } else {
                            messages.purgeExpired(advanced.bound.wallLowerBoundMs)
                        }
                        advanced.bound
                    }
                }
                lastBound = bound
                bound
            }
        } finally {
            refreshingTime = false
        }
    }

    fun conservativeNowMs(): Long = refreshTimeAndPurge().wallLowerBoundMs

    fun destroyConversation(conversationId: String) {
        store.transact { conversations.destroy(conversationId) }
    }

    fun resetConversationKeepingShell(conversationId: String): Boolean =
        store.transact {
            val shell = conversations.load(conversationId) ?: return@transact false
            messages.deleteAllForConversation(conversationId)
            conversations.upsert(
                shell.copy(
                    state = LocalConversationState.RESET,
                    updatedAtWallMs = clock.wallClockMillis(),
                ),
            )
            true
        }

    fun close() {
        store.close()
    }

    companion object {
        fun open(
            keyStore: LocalProtectionKeyStore,
            store: LocalRecordStore,
            cipher: LocalRecordCipher,
            clock: RetentionClock,
            policy: RetentionPolicy,
        ): LocalStoreOpenResult {
            val existing = try {
                keyStore.existingKey()
            } catch (_: ProviderException) {
                return LocalStoreOpenResult.KeyUnavailable
            }
            val key = when (existing) {
                is ExistingKeyResult.Available -> existing.key
                ExistingKeyResult.Missing, ExistingKeyResult.Unavailable ->
                    return LocalStoreOpenResult.KeyUnavailable
            }
            return try {
                LocalStoreOpenResult.Ready(LocalStoreSession(key, cipher, store, clock, policy))
            } catch (_: GeneralSecurityException) {
                LocalStoreOpenResult.Unreadable
            } catch (_: ProviderException) {
                LocalStoreOpenResult.Unreadable
            }
        }
    }
}

internal class LocalConversationRepository(
    private val key: SecretKey,
    private val cipher: LocalRecordCipher,
    private val store: LocalRecordStore,
) {
    fun upsert(shell: LocalConversationShell): Boolean {
        if (!validLocalId(shell.conversationId)) return false
        val normalized = shell.copy(localAlias = shell.localAlias?.takeIf { it.isNotEmpty() })
        val encoded = encryptConversation(normalized) ?: return false
        return try {
            store.transact {
                val existing = store.loadConversation(shell.conversationId)
                val row = StoredConversationRow(shell.conversationId, encoded)
                if (existing == null) store.insertConversation(row) else store.updateConversation(row)
            }
            true
        } catch (_: RuntimeException) {
            false
        }
    }

    fun load(conversationId: String): LocalConversationShell? {
        val row = store.loadConversation(conversationId) ?: return null
        return decryptConversation(row)
    }

    fun list(): List<LocalConversationShell> =
        store.listConversations().mapNotNull { decryptConversation(it) }

    fun delete(conversationId: String) {
        store.deleteConversation(conversationId)
    }

    fun deleteMessagesRetainingShell(conversationId: String) {
        store.deleteMessagesForConversation(conversationId)
    }

    fun destroy(conversationId: String) {
        store.deleteConversation(conversationId)
    }

    private fun encryptConversation(shell: LocalConversationShell): ByteArray? {
        val plaintext = LocalConversationPayloadCodec.encode(shell) ?: return null
        return try {
            val aad = LocalRecordAad.encode(LocalRecordType.CONVERSATION, shell.conversationId)
            val blob = cipher.encrypt(key, plaintext, aad)
            LocalRecordFormat.encode(blob)
        } catch (_: GeneralSecurityException) {
            null
        } catch (_: ProviderException) {
            null
        } finally {
            plaintext.wipe()
        }
    }

    private fun decryptConversation(row: StoredConversationRow): LocalConversationShell? {
        val blob = LocalRecordFormat.decode(row.ciphertext) ?: return null
        val plaintext = try {
            val aad = LocalRecordAad.encode(LocalRecordType.CONVERSATION, row.conversationId)
            cipher.decrypt(key, blob, aad)
        } catch (_: GeneralSecurityException) {
            return null
        } catch (_: ProviderException) {
            return null
        }
        return try {
            val parsed = LocalConversationPayloadCodec.parse(plaintext) ?: return null
            if (parsed.conversationId != row.conversationId) return null
            parsed
        } finally {
            plaintext.wipe()
        }
    }
}

internal class LocalMessageRepository(
    private val key: SecretKey,
    private val cipher: LocalRecordCipher,
    private val store: LocalRecordStore,
    private val policy: RetentionPolicy,
    private val conservativeNow: () -> Long,
) {
    fun insert(record: LocalMessageRecord): Boolean {
        if (!validLocalId(record.messageId) || !validLocalId(record.conversationId)) return false
        val validation = RetentionRules.validate(record.envelope(), policy)
        if (validation !is RetentionValidation.Accepted) return false
        if (RetentionRules.isExpired(validation.effectiveDeadlineWallMs, conservativeNow())) return false
        if (store.loadConversation(record.conversationId) == null) return false
        val ciphertext = encryptMessage(record) ?: return false
        return try {
            store.insertMessage(
                StoredMessageRow(
                    messageId = record.messageId,
                    conversationId = record.conversationId,
                    expiryHintMs = validation.effectiveDeadlineWallMs,
                    ciphertext = ciphertext,
                ),
            )
            true
        } catch (_: RuntimeException) {
            false
        }
    }

    fun listValidUnexpired(conversationId: String): List<LocalMessageRecord> {
        val now = conservativeNow()
        return store.listMessagesForConversation(conversationId).mapNotNull { row ->
            decodeValidUnexpired(row, now, deleteOnFailure = true)
        }.sortedBy { it.createdAtWallMs }
    }

    fun loadValidUnexpired(messageId: String): LocalMessageRecord? {
        val row = store.loadMessage(messageId) ?: return null
        return decodeValidUnexpired(row, conservativeNow(), deleteOnFailure = true)
    }

    fun updateLifecycle(messageId: String, newState: LocalMessageState): Boolean {
        val row = store.loadMessage(messageId) ?: return false
        val now = conservativeNow()
        val current = decodeValidUnexpired(row, now, deleteOnFailure = true) ?: return false
        val updated = current.copy(state = newState)
        val validation = RetentionRules.validate(updated.envelope(), policy)
        if (validation !is RetentionValidation.Accepted) return false
        val previous = RetentionRules.validate(current.envelope(), policy)
        if (previous !is RetentionValidation.Accepted) return false
        if (validation.effectiveDeadlineWallMs > previous.effectiveDeadlineWallMs) return false
        if (updated.authenticatedExpiryWallMs != current.authenticatedExpiryWallMs) return false
        if (updated.relayDeadlineWallMs != current.relayDeadlineWallMs) return false
        if (updated.createdAtWallMs != current.createdAtWallMs) return false
        val ciphertext = encryptMessage(updated) ?: return false
        return try {
            store.updateMessage(
                row.copy(
                    expiryHintMs = previous.effectiveDeadlineWallMs,
                    ciphertext = ciphertext,
                ),
            )
            true
        } catch (_: RuntimeException) {
            false
        }
    }

    fun delete(messageId: String) {
        store.deleteMessage(messageId)
    }

    fun deleteAllForConversation(conversationId: String) {
        store.deleteMessagesForConversation(conversationId)
    }

    fun purgeExpired(nowMs: Long): Int {
        var removed = store.deleteMessagesWithExpiryHintAtOrBefore(nowMs)
        store.listAllMessages().forEach { row ->
            if (decodeValidUnexpired(row, nowMs, deleteOnFailure = true) == null) {
                removed += 1
            }
        }
        return removed
    }

    fun purgeAllMessages() {
        store.deleteAllMessages()
    }

    private fun decodeValidUnexpired(
        row: StoredMessageRow,
        nowMs: Long,
        deleteOnFailure: Boolean,
    ): LocalMessageRecord? {
        val blob = LocalRecordFormat.decode(row.ciphertext)
        if (blob == null) {
            if (deleteOnFailure) store.deleteMessage(row.messageId)
            return null
        }
        val plaintext = try {
            val aad = LocalRecordAad.encode(LocalRecordType.MESSAGE, row.messageId)
            cipher.decrypt(key, blob, aad)
        } catch (_: GeneralSecurityException) {
            if (deleteOnFailure) store.deleteMessage(row.messageId)
            return null
        } catch (_: ProviderException) {
            if (deleteOnFailure) store.deleteMessage(row.messageId)
            return null
        }
        try {
            val parsed = LocalMessagePayloadCodec.parse(plaintext)
            if (parsed == null ||
                parsed.messageId != row.messageId ||
                parsed.conversationId != row.conversationId
            ) {
                if (deleteOnFailure) store.deleteMessage(row.messageId)
                return null
            }
            val validation = RetentionRules.validate(parsed.envelope(), policy)
            if (validation !is RetentionValidation.Accepted) {
                if (deleteOnFailure) store.deleteMessage(row.messageId)
                return null
            }
            if (row.expiryHintMs != validation.effectiveDeadlineWallMs) {
                if (deleteOnFailure) store.deleteMessage(row.messageId)
                return null
            }
            if (RetentionRules.isExpired(validation.effectiveDeadlineWallMs, nowMs)) {
                if (deleteOnFailure) store.deleteMessage(row.messageId)
                return null
            }
            if (parsed.state == LocalMessageState.EXPIRED) {
                if (deleteOnFailure) store.deleteMessage(row.messageId)
                return null
            }
            return parsed
        } finally {
            plaintext.wipe()
        }
    }

    private fun encryptMessage(record: LocalMessageRecord): ByteArray? {
        val plaintext = LocalMessagePayloadCodec.encode(record) ?: return null
        return try {
            val aad = LocalRecordAad.encode(LocalRecordType.MESSAGE, record.messageId)
            val blob = cipher.encrypt(key, plaintext, aad)
            LocalRecordFormat.encode(blob)
        } catch (_: GeneralSecurityException) {
            null
        } catch (_: ProviderException) {
            null
        } finally {
            plaintext.wipe()
        }
    }
}

internal fun LocalMessageRecord.envelope(): RetentionEnvelope =
    RetentionEnvelope(createdAtWallMs, authenticatedExpiryWallMs, relayDeadlineWallMs)

private sealed interface TimeBoundLoad {
    data class Present(val bound: ConservativeTimeBound) : TimeBoundLoad
    data object Missing : TimeBoundLoad
    data object Corrupt : TimeBoundLoad
}

private class ConservativeTimeStore(
    private val key: SecretKey,
    private val cipher: LocalRecordCipher,
    private val store: LocalRecordStore,
) {
    fun load(): TimeBoundLoad {
        val encoded = store.loadMeta(META_KEY) ?: return TimeBoundLoad.Missing
        val blob = LocalRecordFormat.decode(encoded) ?: return TimeBoundLoad.Corrupt
        val plaintext = try {
            val aad = LocalRecordAad.encode(LocalRecordType.TIME_BOUND, TimeBoundPayloadCodec.RECORD_ID)
            cipher.decrypt(key, blob, aad)
        } catch (_: GeneralSecurityException) {
            return TimeBoundLoad.Corrupt
        } catch (_: ProviderException) {
            return TimeBoundLoad.Corrupt
        }
        return try {
            val parsed = TimeBoundPayloadCodec.parse(plaintext) ?: return TimeBoundLoad.Corrupt
            TimeBoundLoad.Present(parsed)
        } finally {
            plaintext.wipe()
        }
    }

    fun save(bound: ConservativeTimeBound) {
        val plaintext = TimeBoundPayloadCodec.encode(bound) ?: return
        try {
            val aad = LocalRecordAad.encode(LocalRecordType.TIME_BOUND, TimeBoundPayloadCodec.RECORD_ID)
            val blob = cipher.encrypt(key, plaintext, aad)
            val encoded = LocalRecordFormat.encode(blob) ?: return
            store.upsertMeta(META_KEY, encoded)
        } finally {
            plaintext.wipe()
        }
    }

    private companion object {
        const val META_KEY = "time-bound"
    }
}
