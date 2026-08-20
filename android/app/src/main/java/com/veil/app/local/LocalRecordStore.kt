package com.veil.app.local

internal data class StoredConversationRow(
    val conversationId: String,
    val ciphertext: ByteArray,
)

internal data class StoredMessageRow(
    val messageId: String,
    val conversationId: String,
    val expiryHintMs: Long,
    val ciphertext: ByteArray,
)

internal interface LocalRecordStore {
    fun <T> transact(block: () -> T): T
    fun insertConversation(row: StoredConversationRow)
    fun updateConversation(row: StoredConversationRow)
    fun loadConversation(conversationId: String): StoredConversationRow?
    fun listConversations(): List<StoredConversationRow>
    fun deleteConversation(conversationId: String)
    fun deleteMessagesForConversation(conversationId: String)
    fun insertMessage(row: StoredMessageRow)
    fun updateMessage(row: StoredMessageRow)
    fun loadMessage(messageId: String): StoredMessageRow?
    fun listMessagesForConversation(conversationId: String): List<StoredMessageRow>
    fun listAllMessages(): List<StoredMessageRow>
    fun deleteMessage(messageId: String)
    fun deleteMessagesWithExpiryHintAtOrBefore(hintMs: Long): Int
    fun deleteAllMessages(): Int
    fun messageCount(): Int
    fun loadMeta(metaKey: String): ByteArray?
    fun upsertMeta(metaKey: String, ciphertext: ByteArray)
    fun deleteMeta(metaKey: String)
    fun close()
}

internal class IncompatibleLocalSchemaException : IllegalStateException("incompatible local schema")
