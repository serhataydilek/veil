package com.veil.app.local

internal const val MAX_LOCAL_ID_CHARS = LocalRecordAad.MAX_RECORD_ID_BYTES
internal const val MAX_LOCAL_ALIAS_BYTES = 256
internal const val MAX_LOCAL_MESSAGE_BODY_BYTES = 64 * 1024

internal enum class LocalConversationState {
    ESTABLISHING,
    ACTIVE,
    OFFLINE,
    IDENTITY_CHANGED,
    BLOCKED,
    RESET,
}

internal enum class LocalMessageState {
    LOCAL_CREATED,
    ENCRYPTED,
    QUEUED,
    RELAY_ACCEPTED,
    RECIPIENT_CLIENT_ACKED,
    FAILED,
    EXPIRED,
}

internal enum class LocalMessageDirection {
    OUTBOUND,
    INBOUND,
}

internal data class LocalConversationShell(
    val conversationId: String,
    val state: LocalConversationState,
    val localAlias: String?,
    val createdAtWallMs: Long,
    val updatedAtWallMs: Long,
)

internal data class LocalMessageRecord(
    val messageId: String,
    val conversationId: String,
    val direction: LocalMessageDirection,
    val state: LocalMessageState,
    val createdAtWallMs: Long,
    val authenticatedExpiryWallMs: Long,
    val relayDeadlineWallMs: Long?,
    val body: String,
)

internal fun validLocalId(id: String): Boolean {
    if (id.isEmpty()) return false
    val bytes = id.encodeToByteArray()
    if (bytes.size > LocalRecordAad.MAX_RECORD_ID_BYTES) return false
    return id.all { ch ->
        ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' || ch == '-' || ch == '_' || ch == '.'
    }
}
