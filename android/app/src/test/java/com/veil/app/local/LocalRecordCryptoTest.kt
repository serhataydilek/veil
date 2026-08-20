package com.veil.app.local

import com.veil.app.security.ProtectedBlob
import com.veil.app.security.generatedAesKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalRecordCryptoTest {
    @Test
    fun envelopeRejectsTrailingBytesUnknownVersionAndOversize() {
        val blob = ProtectedBlob(ByteArray(LocalRecordFormat.IV_LENGTH) { 1 }, ByteArray(16) { 2 })
        val encoded = LocalRecordFormat.encode(blob)!!
        assertEquals(blob.ciphertext.toList(), LocalRecordFormat.decode(encoded)?.ciphertext?.toList())
        assertNull(LocalRecordFormat.decode(encoded + byteArrayOf(0)))
        encoded[4] = 9
        assertNull(LocalRecordFormat.decode(encoded))
        assertNull(LocalRecordFormat.decode(ByteArray(LocalRecordFormat.MAX_ENCODED_LENGTH + 1)))
    }

    @Test
    fun recordClassesUseDistinctAad() {
        assertNotEquals(
            LocalRecordAad.encode(LocalRecordType.CONVERSATION, "same-id").toList(),
            LocalRecordAad.encode(LocalRecordType.MESSAGE, "same-id").toList(),
        )
        assertNotEquals(
            LocalRecordAad.encode(LocalRecordType.MESSAGE, "same-id").toList(),
            LocalRecordAad.encode(LocalRecordType.TIME_BOUND, "same-id").toList(),
        )
    }

    @Test
    fun cipherRejectsCrossTypeAad() {
        val key = generatedAesKey()
        val cipher = AesGcmLocalRecordCipher()
        val plaintext = "record".encodeToByteArray()
        val blob = cipher.encrypt(
            key,
            plaintext,
            LocalRecordAad.encode(LocalRecordType.MESSAGE, "msg-1"),
        )
        try {
            cipher.decrypt(key, blob, LocalRecordAad.encode(LocalRecordType.CONVERSATION, "msg-1"))
            throw AssertionError("cross-type AAD must fail closed")
        } catch (_: Exception) {
        }
        assertEquals(
            "record",
            cipher.decrypt(key, blob, LocalRecordAad.encode(LocalRecordType.MESSAGE, "msg-1")).decodeToString(),
        )
    }
}
