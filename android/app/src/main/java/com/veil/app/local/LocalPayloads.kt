package com.veil.app.local

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

internal object LocalConversationPayloadCodec {
    private val magic = byteArrayOf('V'.code.toByte(), 'L'.code.toByte(), 'C'.code.toByte(), '1'.code.toByte())
    internal const val VERSION = 1

    fun encode(shell: LocalConversationShell): ByteArray? {
        if (!validLocalId(shell.conversationId)) return null
        val idBytes = shell.conversationId.encodeToByteArray()
        val aliasBytes = shell.localAlias?.encodeToByteArray()
        if (aliasBytes != null && (aliasBytes.isEmpty() || aliasBytes.size > MAX_LOCAL_ALIAS_BYTES)) return null
        val aliasFlag = if (aliasBytes == null) 0 else 1
        val aliasLen = aliasBytes?.size ?: 0
        val size = 4 + 1 + 2 + idBytes.size + 1 + 1 + (if (aliasBytes == null) 0 else 2 + aliasLen) + 8 + 8
        val buffer = ByteBuffer.allocate(size).order(ByteOrder.BIG_ENDIAN)
        buffer.put(magic)
        buffer.put(VERSION.toByte())
        buffer.putShort(idBytes.size.toShort())
        buffer.put(idBytes)
        buffer.put(encodeConversationState(shell.state))
        buffer.put(aliasFlag.toByte())
        if (aliasBytes != null) {
            buffer.putShort(aliasBytes.size.toShort())
            buffer.put(aliasBytes)
        }
        buffer.putLong(shell.createdAtWallMs)
        buffer.putLong(shell.updatedAtWallMs)
        return buffer.array()
    }

    fun parse(bytes: ByteArray): LocalConversationShell? {
        if (bytes.size < 4 + 1 + 2 + 1 + 1 + 1 + 8 + 8) return null
        val input = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        val actualMagic = ByteArray(4)
        input.get(actualMagic)
        if (!actualMagic.contentEquals(magic)) return null
        if ((input.get().toInt() and 0xff) != VERSION) return null
        val id = input.readBoundedUtf8(MAX_LOCAL_ID_CHARS) ?: return null
        if (!validLocalId(id)) return null
        val state = decodeConversationState(input.get()) ?: return null
        val alias = when (input.get().toInt() and 0xff) {
            0 -> null
            1 -> input.readBoundedUtf8(MAX_LOCAL_ALIAS_BYTES) ?: return null
            else -> return null
        }
        if (input.remaining() < 16) return null
        val created = input.long
        val updated = input.long
        if (input.hasRemaining()) return null
        return LocalConversationShell(id, state, alias, created, updated)
    }
}

internal object LocalMessagePayloadCodec {
    private val magic = byteArrayOf('V'.code.toByte(), 'L'.code.toByte(), 'M'.code.toByte(), '1'.code.toByte())
    internal const val VERSION = 1

    fun encode(record: LocalMessageRecord): ByteArray? {
        if (!validLocalId(record.messageId) || !validLocalId(record.conversationId)) return null
        val messageId = record.messageId.encodeToByteArray()
        val conversationId = record.conversationId.encodeToByteArray()
        val body = record.body.encodeToByteArray()
        if (body.size > MAX_LOCAL_MESSAGE_BODY_BYTES) return null
        val relayFlag = if (record.relayDeadlineWallMs == null) 0 else 1
        val relaySize = if (relayFlag == 1) 8 else 0
        val size = 4 + 1 + 2 + messageId.size + 2 + conversationId.size + 1 + 1 + 8 + 8 + 1 + relaySize + 4 + body.size
        val buffer = ByteBuffer.allocate(size).order(ByteOrder.BIG_ENDIAN)
        buffer.put(magic)
        buffer.put(VERSION.toByte())
        buffer.putShort(messageId.size.toShort())
        buffer.put(messageId)
        buffer.putShort(conversationId.size.toShort())
        buffer.put(conversationId)
        buffer.put(encodeDirection(record.direction))
        buffer.put(encodeMessageState(record.state))
        buffer.putLong(record.createdAtWallMs)
        buffer.putLong(record.authenticatedExpiryWallMs)
        buffer.put(relayFlag.toByte())
        if (record.relayDeadlineWallMs != null) buffer.putLong(record.relayDeadlineWallMs)
        buffer.putInt(body.size)
        buffer.put(body)
        return buffer.array()
    }

    fun parse(bytes: ByteArray): LocalMessageRecord? {
        if (bytes.size < 4 + 1 + 2 + 1 + 2 + 1 + 1 + 1 + 8 + 8 + 1 + 4) return null
        val input = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        val actualMagic = ByteArray(4)
        input.get(actualMagic)
        if (!actualMagic.contentEquals(magic)) return null
        if ((input.get().toInt() and 0xff) != VERSION) return null
        val messageId = input.readBoundedUtf8(MAX_LOCAL_ID_CHARS) ?: return null
        val conversationId = input.readBoundedUtf8(MAX_LOCAL_ID_CHARS) ?: return null
        if (!validLocalId(messageId) || !validLocalId(conversationId)) return null
        val direction = decodeDirection(input.get()) ?: return null
        val state = decodeMessageState(input.get()) ?: return null
        if (input.remaining() < 16) return null
        val created = input.long
        val expiry = input.long
        if (!input.hasRemaining()) return null
        val relay = when (input.get().toInt() and 0xff) {
            0 -> null
            1 -> {
                if (input.remaining() < 8) return null
                input.long
            }
            else -> return null
        }
        if (input.remaining() < 4) return null
        val bodyLength = input.int
        if (bodyLength < 0 || bodyLength > MAX_LOCAL_MESSAGE_BODY_BYTES) return null
        if (input.remaining() != bodyLength) return null
        val bodyBytes = ByteArray(bodyLength)
        input.get(bodyBytes)
        val body = String(bodyBytes, StandardCharsets.UTF_8)
        bodyBytes.wipe()
        return LocalMessageRecord(
            messageId = messageId,
            conversationId = conversationId,
            direction = direction,
            state = state,
            createdAtWallMs = created,
            authenticatedExpiryWallMs = expiry,
            relayDeadlineWallMs = relay,
            body = body,
        )
    }
}

internal object TimeBoundPayloadCodec {
    private val magic = byteArrayOf('V'.code.toByte(), 'L'.code.toByte(), 'T'.code.toByte(), '1'.code.toByte())
    internal const val VERSION = 1
    internal const val RECORD_ID = "time-bound"

    fun encode(bound: ConservativeTimeBound): ByteArray? {
        if (bound.wallLowerBoundMs < 0L || bound.elapsedRealtimeAtObservationMs < 0L) return null
        val bootCount = bound.bootObservation.bootCount
        val hasBoot = bootCount != null
        val size = 4 + 1 + 8 + 8 + 1 + 1 + if (hasBoot) 4 else 0
        val buffer = ByteBuffer.allocate(size).order(ByteOrder.BIG_ENDIAN)
        buffer.put(magic)
        buffer.put(VERSION.toByte())
        buffer.putLong(bound.wallLowerBoundMs)
        buffer.putLong(bound.elapsedRealtimeAtObservationMs)
        buffer.put(if (bound.bootObservation.reliable) 1 else 0)
        buffer.put(if (hasBoot) 1 else 0)
        if (bootCount != null) buffer.putInt(bootCount)
        return buffer.array()
    }

    fun parse(bytes: ByteArray): ConservativeTimeBound? {
        if (bytes.size < 4 + 1 + 8 + 8 + 1 + 1) return null
        val input = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        val actualMagic = ByteArray(4)
        input.get(actualMagic)
        if (!actualMagic.contentEquals(magic)) return null
        if ((input.get().toInt() and 0xff) != VERSION) return null
        val wall = input.long
        val elapsed = input.long
        if (wall < 0L || elapsed < 0L) return null
        val reliable = when (input.get().toInt() and 0xff) {
            0 -> false
            1 -> true
            else -> return null
        }
        val bootCount = when (input.get().toInt() and 0xff) {
            0 -> null
            1 -> {
                if (input.remaining() < 4) return null
                input.int
            }
            else -> return null
        }
        if (input.hasRemaining()) return null
        return ConservativeTimeBound(wall, elapsed, BootObservation(bootCount, reliable))
    }
}

private fun ByteBuffer.readBoundedUtf8(maxChars: Int): String? {
    if (remaining() < 2) return null
    val length = short.toInt() and 0xffff
    if (length == 0 || length > maxChars || remaining() < length) return null
    val bytes = ByteArray(length)
    get(bytes)
    val text = String(bytes, StandardCharsets.UTF_8)
    bytes.wipe()
    if (text.length > maxChars) return null
    return text
}

private fun encodeConversationState(state: LocalConversationState): Byte = when (state) {
    LocalConversationState.ESTABLISHING -> 1
    LocalConversationState.ACTIVE -> 2
    LocalConversationState.OFFLINE -> 3
    LocalConversationState.IDENTITY_CHANGED -> 4
    LocalConversationState.BLOCKED -> 5
    LocalConversationState.RESET -> 6
}.toByte()

private fun decodeConversationState(value: Byte): LocalConversationState? = when (value.toInt() and 0xff) {
    1 -> LocalConversationState.ESTABLISHING
    2 -> LocalConversationState.ACTIVE
    3 -> LocalConversationState.OFFLINE
    4 -> LocalConversationState.IDENTITY_CHANGED
    5 -> LocalConversationState.BLOCKED
    6 -> LocalConversationState.RESET
    else -> null
}

private fun encodeMessageState(state: LocalMessageState): Byte = when (state) {
    LocalMessageState.LOCAL_CREATED -> 1
    LocalMessageState.ENCRYPTED -> 2
    LocalMessageState.QUEUED -> 3
    LocalMessageState.RELAY_ACCEPTED -> 4
    LocalMessageState.RECIPIENT_CLIENT_ACKED -> 5
    LocalMessageState.FAILED -> 6
    LocalMessageState.EXPIRED -> 7
}.toByte()

private fun decodeMessageState(value: Byte): LocalMessageState? = when (value.toInt() and 0xff) {
    1 -> LocalMessageState.LOCAL_CREATED
    2 -> LocalMessageState.ENCRYPTED
    3 -> LocalMessageState.QUEUED
    4 -> LocalMessageState.RELAY_ACCEPTED
    5 -> LocalMessageState.RECIPIENT_CLIENT_ACKED
    6 -> LocalMessageState.FAILED
    7 -> LocalMessageState.EXPIRED
    else -> null
}

private fun encodeDirection(direction: LocalMessageDirection): Byte = when (direction) {
    LocalMessageDirection.OUTBOUND -> 1
    LocalMessageDirection.INBOUND -> 2
}.toByte()

private fun decodeDirection(value: Byte): LocalMessageDirection? = when (value.toInt() and 0xff) {
    1 -> LocalMessageDirection.OUTBOUND
    2 -> LocalMessageDirection.INBOUND
    else -> null
}
