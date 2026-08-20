package com.veil.app.local

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal class InMemoryLocalRecordStore : LocalRecordStore {
    private val lock = ReentrantLock()
    private val meta = linkedMapOf<String, ByteArray>()
    private val conversations = linkedMapOf<String, StoredConversationRow>()
    private val messages = linkedMapOf<String, StoredMessageRow>()

    override fun <T> transact(block: () -> T): T = lock.withLock { block() }

    override fun insertConversation(row: StoredConversationRow) {
        lock.withLock {
            check(row.conversationId !in conversations)
            conversations[row.conversationId] = row.copy(ciphertext = row.ciphertext.copyOf())
        }
    }

    override fun updateConversation(row: StoredConversationRow) {
        lock.withLock {
            check(row.conversationId in conversations)
            conversations[row.conversationId] = row.copy(ciphertext = row.ciphertext.copyOf())
        }
    }

    override fun loadConversation(conversationId: String): StoredConversationRow? =
        lock.withLock { conversations[conversationId]?.copy(ciphertext = conversations.getValue(conversationId).ciphertext.copyOf()) }

    override fun listConversations(): List<StoredConversationRow> =
        lock.withLock { conversations.values.map { it.copy(ciphertext = it.ciphertext.copyOf()) } }

    override fun deleteConversation(conversationId: String) {
        lock.withLock {
            messages.entries.removeAll { it.value.conversationId == conversationId }
            conversations.remove(conversationId)
        }
    }

    override fun deleteMessagesForConversation(conversationId: String) {
        lock.withLock { messages.entries.removeAll { it.value.conversationId == conversationId } }
    }

    override fun insertMessage(row: StoredMessageRow) {
        lock.withLock {
            check(row.messageId !in messages)
            messages[row.messageId] = copyMessage(row)
        }
    }

    override fun updateMessage(row: StoredMessageRow) {
        lock.withLock {
            check(row.messageId in messages)
            messages[row.messageId] = copyMessage(row)
        }
    }

    override fun loadMessage(messageId: String): StoredMessageRow? =
        lock.withLock { messages[messageId]?.let(::copyMessage) }

    override fun listMessagesForConversation(conversationId: String): List<StoredMessageRow> =
        lock.withLock { messages.values.filter { it.conversationId == conversationId }.map(::copyMessage) }

    override fun listAllMessages(): List<StoredMessageRow> =
        lock.withLock { messages.values.map(::copyMessage) }

    override fun deleteMessage(messageId: String) {
        lock.withLock { messages.remove(messageId) }
    }

    override fun deleteMessagesWithExpiryHintAtOrBefore(hintMs: Long): Int =
        lock.withLock {
            val ids = messages.filterValues { it.expiryHintMs <= hintMs }.keys.toList()
            ids.forEach { messages.remove(it) }
            ids.size
        }

    override fun deleteAllMessages(): Int =
        lock.withLock {
            val count = messages.size
            messages.clear()
            count
        }

    override fun messageCount(): Int = lock.withLock { messages.size }

    override fun loadMeta(metaKey: String): ByteArray? =
        lock.withLock { meta[metaKey]?.copyOf() }

    override fun upsertMeta(metaKey: String, ciphertext: ByteArray) {
        lock.withLock { meta[metaKey] = ciphertext.copyOf() }
    }

    override fun deleteMeta(metaKey: String) {
        lock.withLock { meta.remove(metaKey) }
    }

    override fun close() = Unit

    fun tamperExpiryHint(messageId: String, expiryHintMs: Long) {
        lock.withLock {
            val current = messages.getValue(messageId)
            messages[messageId] = current.copy(expiryHintMs = expiryHintMs, ciphertext = current.ciphertext.copyOf())
        }
    }

    fun replaceMessageCiphertext(messageId: String, ciphertext: ByteArray) {
        lock.withLock {
            val current = messages.getValue(messageId)
            messages[messageId] = current.copy(ciphertext = ciphertext.copyOf())
        }
    }

    fun replaceMeta(metaKey: String, ciphertext: ByteArray) {
        upsertMeta(metaKey, ciphertext)
    }

    private fun copyMessage(row: StoredMessageRow) = StoredMessageRow(
        messageId = row.messageId,
        conversationId = row.conversationId,
        expiryHintMs = row.expiryHintMs,
        ciphertext = row.ciphertext.copyOf(),
    )
}
