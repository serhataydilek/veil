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

internal sealed interface TimeRefreshResult {
    data class Advanced(val bound: ConservativeTimeBound) : TimeRefreshResult
    data object KeyUnavailable : TimeRefreshResult
    data object PersistFailed : TimeRefreshResult
}

internal sealed interface ConservativeNowResult {
    data class Available(val nowMs: Long) : ConservativeNowResult
    data object KeyUnavailable : ConservativeNowResult
    data object Unavailable : ConservativeNowResult
}

internal sealed interface LocalMessageListResult {
    data class Available(val records: List<LocalMessageRecord>) : LocalMessageListResult
    data object KeyUnavailable : LocalMessageListResult
    data object Unavailable : LocalMessageListResult
}

internal sealed interface LocalMessageLoadResult {
    data class Present(val record: LocalMessageRecord) : LocalMessageLoadResult
    data object Absent : LocalMessageLoadResult
    data object KeyUnavailable : LocalMessageLoadResult
    data object Unavailable : LocalMessageLoadResult
}

internal sealed interface LocalConversationListResult {
    data class Available(val shells: List<LocalConversationShell>) : LocalConversationListResult
    data object KeyUnavailable : LocalConversationListResult
}

internal sealed interface LocalPurgeResult {
    data class Removed(val count: Int) : LocalPurgeResult
    data object KeyUnavailable : LocalPurgeResult
}

internal class LocalStoreSession(
    private val key: SecretKey,
    private val cipher: LocalRecordCipher,
    private val store: LocalRecordStore,
    private val clock: RetentionClock,
    private val policy: RetentionPolicy,
) {
    val conversations = LocalConversationRepository(key, cipher, store)
    val messages = LocalMessageRepository(key, cipher, store, policy, ::conservativeNow)
    private val timeStore = ConservativeTimeStore(key, cipher, store)
    private var refreshingTime = false
    private var lastBound: ConservativeTimeBound? = null

    fun refreshTimeAndPurge(): TimeRefreshResult {
        if (refreshingTime) {
            val bound = lastBound ?: return TimeRefreshResult.PersistFailed
            return TimeRefreshResult.Advanced(bound)
        }
        refreshingTime = true
        return try {
            store.transact {
                val loaded = timeStore.load()
                val hasMessages = store.messageCount() > 0
                when (loaded) {
                    TimeBoundLoad.KeyUnavailable -> TimeRefreshResult.KeyUnavailable
                    TimeBoundLoad.Missing, TimeBoundLoad.Corrupt -> {
                        if (hasMessages) store.deleteAllMessages()
                        val initialized = ConservativeTime.initialize(clock)
                        when (timeStore.save(initialized)) {
                            TimeBoundSaveResult.Saved -> {
                                lastBound = initialized
                                TimeRefreshResult.Advanced(initialized)
                            }
                            TimeBoundSaveResult.KeyUnavailable -> TimeRefreshResult.KeyUnavailable
                            TimeBoundSaveResult.PersistFailed -> TimeRefreshResult.PersistFailed
                        }
                    }
                    is TimeBoundLoad.Present -> {
                        val advanced = ConservativeTime.advance(loaded.bound, clock)
                        when (timeStore.save(advanced.bound)) {
                            TimeBoundSaveResult.Saved -> {
                                lastBound = advanced.bound
                                if (advanced.expireAllMessages) {
                                    store.deleteAllMessages()
                                    TimeRefreshResult.Advanced(advanced.bound)
                                } else {
                                    when (val purged = messages.purgeExpired(advanced.bound.wallLowerBoundMs)) {
                                        is LocalPurgeResult.Removed -> TimeRefreshResult.Advanced(advanced.bound)
                                        LocalPurgeResult.KeyUnavailable -> TimeRefreshResult.KeyUnavailable
                                    }
                                }
                            }
                            TimeBoundSaveResult.KeyUnavailable -> TimeRefreshResult.KeyUnavailable
                            TimeBoundSaveResult.PersistFailed -> TimeRefreshResult.PersistFailed
                        }
                    }
                }
            }
        } finally {
            refreshingTime = false
        }
    }

    fun conservativeNow(): ConservativeNowResult = when (val result = refreshTimeAndPurge()) {
        is TimeRefreshResult.Advanced -> ConservativeNowResult.Available(result.bound.wallLowerBoundMs)
        TimeRefreshResult.KeyUnavailable -> ConservativeNowResult.KeyUnavailable
        TimeRefreshResult.PersistFailed -> ConservativeNowResult.Unavailable
    }

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
            } catch (error: GeneralSecurityException) {
                return when (LocalCryptoFailures.classify(error)) {
                    LocalCryptoFailureKind.KeyUnavailable -> LocalStoreOpenResult.KeyUnavailable
                    LocalCryptoFailureKind.AuthenticationFailed -> LocalStoreOpenResult.Unreadable
                }
            }
            val key = when (existing) {
                is ExistingKeyResult.Available -> existing.key
                ExistingKeyResult.Missing, ExistingKeyResult.Unavailable ->
                    return LocalStoreOpenResult.KeyUnavailable
            }
            return try {
                LocalStoreOpenResult.Ready(LocalStoreSession(key, cipher, store, clock, policy))
            } catch (_: ProviderException) {
                LocalStoreOpenResult.KeyUnavailable
            } catch (error: GeneralSecurityException) {
                when (LocalCryptoFailures.classify(error)) {
                    LocalCryptoFailureKind.KeyUnavailable -> LocalStoreOpenResult.KeyUnavailable
                    LocalCryptoFailureKind.AuthenticationFailed -> LocalStoreOpenResult.Unreadable
                }
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
        return when (val decrypted = decryptConversation(row)) {
            is LocalDecryptResult.Success -> decrypted.value
            LocalDecryptResult.KeyUnavailable, LocalDecryptResult.AuthenticationFailed, LocalDecryptResult.Unreadable -> null
        }
    }

    fun list(): LocalConversationListResult {
        val shells = ArrayList<LocalConversationShell>()
        for (row in store.listConversations()) {
            when (val decrypted = decryptConversation(row)) {
                is LocalDecryptResult.Success -> shells.add(decrypted.value)
                LocalDecryptResult.KeyUnavailable -> return LocalConversationListResult.KeyUnavailable
                LocalDecryptResult.AuthenticationFailed, LocalDecryptResult.Unreadable -> Unit
            }
        }
        return LocalConversationListResult.Available(shells)
    }

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
            val aad = LocalRecordAad.encode(LocalRecordType.CONVERSATION, shell.conversationId) ?: return null
            when (val encrypted = cipher.encryptLocal(key, plaintext, aad)) {
                is LocalEncryptResult.Success -> LocalRecordFormat.encode(encrypted.blob)
                LocalEncryptResult.KeyUnavailable, LocalEncryptResult.Failed -> null
            }
        } finally {
            plaintext.wipe()
        }
    }

    private fun decryptConversation(row: StoredConversationRow): LocalDecryptResult<LocalConversationShell> {
        val blob = LocalRecordFormat.decode(row.ciphertext) ?: return LocalDecryptResult.Unreadable
        val aad = LocalRecordAad.encode(LocalRecordType.CONVERSATION, row.conversationId)
            ?: return LocalDecryptResult.Unreadable
        val plaintext = when (val decrypted = cipher.decryptLocal(key, blob, aad)) {
            is LocalDecryptResult.Success -> decrypted.value
            LocalDecryptResult.AuthenticationFailed -> return LocalDecryptResult.AuthenticationFailed
            LocalDecryptResult.KeyUnavailable -> return LocalDecryptResult.KeyUnavailable
            LocalDecryptResult.Unreadable -> return LocalDecryptResult.Unreadable
        }
        return try {
            val parsed = LocalConversationPayloadCodec.parse(plaintext) ?: return LocalDecryptResult.Unreadable
            if (parsed.conversationId != row.conversationId) return LocalDecryptResult.Unreadable
            LocalDecryptResult.Success(parsed)
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
    private val conservativeNow: () -> ConservativeNowResult,
) {
    fun insert(record: LocalMessageRecord): Boolean {
        if (!validLocalId(record.messageId) || !validLocalId(record.conversationId)) return false
        val validation = RetentionRules.validate(record.envelope(), policy)
        if (validation !is RetentionValidation.Accepted) return false
        val now = when (val current = conservativeNow()) {
            is ConservativeNowResult.Available -> current.nowMs
            ConservativeNowResult.KeyUnavailable, ConservativeNowResult.Unavailable -> return false
        }
        if (RetentionRules.isExpired(validation.effectiveDeadlineWallMs, now)) return false
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

    fun listValidUnexpired(conversationId: String): LocalMessageListResult {
        val now = when (val current = conservativeNow()) {
            is ConservativeNowResult.Available -> current.nowMs
            ConservativeNowResult.KeyUnavailable -> return LocalMessageListResult.KeyUnavailable
            ConservativeNowResult.Unavailable -> return LocalMessageListResult.Unavailable
        }
        val records = ArrayList<LocalMessageRecord>()
        for (row in store.listMessagesForConversation(conversationId)) {
            when (val decoded = decodeValidUnexpired(row, now, deleteOnFailure = true)) {
                is LocalMessageLoadResult.Present -> records.add(decoded.record)
                LocalMessageLoadResult.Absent -> Unit
                LocalMessageLoadResult.KeyUnavailable -> return LocalMessageListResult.KeyUnavailable
                LocalMessageLoadResult.Unavailable -> return LocalMessageListResult.Unavailable
            }
        }
        return LocalMessageListResult.Available(records.sortedBy { it.createdAtWallMs })
    }

    fun loadValidUnexpired(messageId: String): LocalMessageLoadResult {
        val row = store.loadMessage(messageId) ?: return LocalMessageLoadResult.Absent
        val now = when (val current = conservativeNow()) {
            is ConservativeNowResult.Available -> current.nowMs
            ConservativeNowResult.KeyUnavailable -> return LocalMessageLoadResult.KeyUnavailable
            ConservativeNowResult.Unavailable -> return LocalMessageLoadResult.Unavailable
        }
        return decodeValidUnexpired(row, now, deleteOnFailure = true)
    }

    fun updateLifecycle(messageId: String, newState: LocalMessageState): Boolean {
        val row = store.loadMessage(messageId) ?: return false
        val now = when (val current = conservativeNow()) {
            is ConservativeNowResult.Available -> current.nowMs
            ConservativeNowResult.KeyUnavailable, ConservativeNowResult.Unavailable -> return false
        }
        val current = when (val decoded = decodeValidUnexpired(row, now, deleteOnFailure = true)) {
            is LocalMessageLoadResult.Present -> decoded.record
            LocalMessageLoadResult.Absent,
            LocalMessageLoadResult.KeyUnavailable,
            LocalMessageLoadResult.Unavailable,
            -> return false
        }
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

    fun purgeExpired(nowMs: Long): LocalPurgeResult {
        var removed = store.deleteMessagesWithExpiryHintAtOrBefore(nowMs)
        for (row in store.listAllMessages()) {
            when (val decoded = decodeValidUnexpired(row, nowMs, deleteOnFailure = true)) {
                is LocalMessageLoadResult.Present -> Unit
                LocalMessageLoadResult.Absent -> removed += 1
                LocalMessageLoadResult.KeyUnavailable -> return LocalPurgeResult.KeyUnavailable
                LocalMessageLoadResult.Unavailable -> return LocalPurgeResult.KeyUnavailable
            }
        }
        return LocalPurgeResult.Removed(removed)
    }

    fun purgeAllMessages() {
        store.deleteAllMessages()
    }

    private fun decodeValidUnexpired(
        row: StoredMessageRow,
        nowMs: Long,
        deleteOnFailure: Boolean,
    ): LocalMessageLoadResult {
        fun dropCorrupt(): LocalMessageLoadResult {
            if (deleteOnFailure) store.deleteMessage(row.messageId)
            return LocalMessageLoadResult.Absent
        }
        val blob = LocalRecordFormat.decode(row.ciphertext) ?: return dropCorrupt()
        val aad = LocalRecordAad.encode(LocalRecordType.MESSAGE, row.messageId) ?: return dropCorrupt()
        val plaintext = when (val decrypted = cipher.decryptLocal(key, blob, aad)) {
            is LocalDecryptResult.Success -> decrypted.value
            LocalDecryptResult.AuthenticationFailed, LocalDecryptResult.Unreadable -> return dropCorrupt()
            LocalDecryptResult.KeyUnavailable -> return LocalMessageLoadResult.KeyUnavailable
        }
        try {
            val parsed = LocalMessagePayloadCodec.parse(plaintext)
            if (parsed == null ||
                parsed.messageId != row.messageId ||
                parsed.conversationId != row.conversationId
            ) {
                return dropCorrupt()
            }
            val validation = RetentionRules.validate(parsed.envelope(), policy)
            if (validation !is RetentionValidation.Accepted) {
                return dropCorrupt()
            }
            if (row.expiryHintMs != validation.effectiveDeadlineWallMs) {
                return dropCorrupt()
            }
            if (RetentionRules.isExpired(validation.effectiveDeadlineWallMs, nowMs)) {
                return dropCorrupt()
            }
            if (parsed.state == LocalMessageState.EXPIRED) {
                return dropCorrupt()
            }
            return LocalMessageLoadResult.Present(parsed)
        } finally {
            plaintext.wipe()
        }
    }

    private fun encryptMessage(record: LocalMessageRecord): ByteArray? {
        val plaintext = LocalMessagePayloadCodec.encode(record) ?: return null
        return try {
            val aad = LocalRecordAad.encode(LocalRecordType.MESSAGE, record.messageId) ?: return null
            when (val encrypted = cipher.encryptLocal(key, plaintext, aad)) {
                is LocalEncryptResult.Success -> LocalRecordFormat.encode(encrypted.blob)
                LocalEncryptResult.KeyUnavailable, LocalEncryptResult.Failed -> null
            }
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
    data object KeyUnavailable : TimeBoundLoad
}

private sealed interface TimeBoundSaveResult {
    data object Saved : TimeBoundSaveResult
    data object KeyUnavailable : TimeBoundSaveResult
    data object PersistFailed : TimeBoundSaveResult
}

private class ConservativeTimeStore(
    private val key: SecretKey,
    private val cipher: LocalRecordCipher,
    private val store: LocalRecordStore,
) {
    fun load(): TimeBoundLoad {
        val encoded = store.loadMeta(META_KEY) ?: return TimeBoundLoad.Missing
        val blob = LocalRecordFormat.decode(encoded) ?: return TimeBoundLoad.Corrupt
        val aad = LocalRecordAad.encode(LocalRecordType.TIME_BOUND, TimeBoundPayloadCodec.RECORD_ID)
            ?: return TimeBoundLoad.Corrupt
        val plaintext = when (val decrypted = cipher.decryptLocal(key, blob, aad)) {
            is LocalDecryptResult.Success -> decrypted.value
            LocalDecryptResult.AuthenticationFailed, LocalDecryptResult.Unreadable -> return TimeBoundLoad.Corrupt
            LocalDecryptResult.KeyUnavailable -> return TimeBoundLoad.KeyUnavailable
        }
        return try {
            val parsed = TimeBoundPayloadCodec.parse(plaintext) ?: return TimeBoundLoad.Corrupt
            TimeBoundLoad.Present(parsed)
        } finally {
            plaintext.wipe()
        }
    }

    fun save(bound: ConservativeTimeBound): TimeBoundSaveResult {
        val plaintext = TimeBoundPayloadCodec.encode(bound) ?: return TimeBoundSaveResult.PersistFailed
        try {
            val aad = LocalRecordAad.encode(LocalRecordType.TIME_BOUND, TimeBoundPayloadCodec.RECORD_ID)
                ?: return TimeBoundSaveResult.PersistFailed
            val blob = when (val encrypted = cipher.encryptLocal(key, plaintext, aad)) {
                is LocalEncryptResult.Success -> encrypted.blob
                LocalEncryptResult.KeyUnavailable -> return TimeBoundSaveResult.KeyUnavailable
                LocalEncryptResult.Failed -> return TimeBoundSaveResult.PersistFailed
            }
            val encoded = LocalRecordFormat.encode(blob) ?: return TimeBoundSaveResult.PersistFailed
            return if (store.upsertMeta(META_KEY, encoded)) {
                TimeBoundSaveResult.Saved
            } else {
                TimeBoundSaveResult.PersistFailed
            }
        } finally {
            plaintext.wipe()
        }
    }

    private companion object {
        const val META_KEY = "time-bound"
    }
}
