package com.veil.app.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LocalPayloadCodecTest {
    @Test
    fun conversationRoundTripRejectsTrailingBytesAndUnknownVersion() {
        val shell = LocalConversationShell(
            conversationId = "conv-1",
            state = LocalConversationState.ESTABLISHING,
            localAlias = "desk",
            createdAtWallMs = 10,
            updatedAtWallMs = 11,
        )
        val encoded = LocalConversationPayloadCodec.encode(shell)
        assertNotNull(encoded)
        assertEquals(shell, LocalConversationPayloadCodec.parse(encoded!!))
        assertNull(LocalConversationPayloadCodec.parse(encoded + byteArrayOf(0)))
        encoded[4] = 99
        assertNull(LocalConversationPayloadCodec.parse(encoded))
    }

    @Test
    fun messageRoundTripRejectsTrailingBytesAndUnknownState() {
        val record = LocalMessageRecord(
            messageId = "msg-1",
            conversationId = "conv-1",
            direction = LocalMessageDirection.OUTBOUND,
            state = LocalMessageState.LOCAL_CREATED,
            createdAtWallMs = 10,
            authenticatedExpiryWallMs = 20,
            relayDeadlineWallMs = 15,
            body = "hello",
        )
        val encoded = LocalMessagePayloadCodec.encode(record)
        assertNotNull(encoded)
        assertEquals(record, LocalMessagePayloadCodec.parse(encoded!!))
        assertNull(LocalMessagePayloadCodec.parse(encoded + byteArrayOf(1)))
        encoded[encoded.size - 6] = 99
        assertNull(LocalMessagePayloadCodec.parse(encoded.copyOf().also { it[4] = 2 }))
    }

    @Test
    fun timeBoundRoundTripRejectsGarbage() {
        val bound = ConservativeTimeBound(9, 8, BootObservation(3, true))
        val encoded = TimeBoundPayloadCodec.encode(bound)
        assertEquals(bound, TimeBoundPayloadCodec.parse(encoded!!))
        assertNull(TimeBoundPayloadCodec.parse(encoded + byteArrayOf(0)))
        encoded[4] = 7
        assertNull(TimeBoundPayloadCodec.parse(encoded))
    }

    @Test
    fun unknownConversationMagicIsRejected() {
        val encoded = LocalConversationPayloadCodec.encode(
            LocalConversationShell("conv-1", LocalConversationState.RESET, null, 1, 1),
        )!!
        encoded[0] = 'X'.code.toByte()
        assertNull(LocalConversationPayloadCodec.parse(encoded))
    }

    @Test
    fun oversizedBodyIsRejected() {
        val body = "x".repeat(MAX_LOCAL_MESSAGE_BODY_BYTES + 1)
        val record = LocalMessageRecord(
            messageId = "msg-1",
            conversationId = "conv-1",
            direction = LocalMessageDirection.INBOUND,
            state = LocalMessageState.FAILED,
            createdAtWallMs = 1,
            authenticatedExpiryWallMs = 2,
            relayDeadlineWallMs = null,
            body = body,
        )
        assertNull(LocalMessagePayloadCodec.encode(record))
    }
}
