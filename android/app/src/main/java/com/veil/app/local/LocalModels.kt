package com.veil.app.local

internal const val MAX_LOCAL_ID_CHARS = 64
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
    if (id.isEmpty() || id.length > MAX_LOCAL_ID_CHARS) return false
    return id.all { ch -> ch.isLetterOrDigit() || ch == '-' || ch == '_' || ch == '.' }
}
