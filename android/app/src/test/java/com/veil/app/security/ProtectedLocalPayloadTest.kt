package com.veil.app.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProtectedLocalPayloadTest {
    @Test
    fun legacySentinelDefaultsAppLockDisabled() {
        val payload = ProtectedLocalPayloadCodec.parse(ProtectedLocalPayloadCodec.LEGACY_SENTINEL)

        assertEquals(true, payload?.fromLegacy)
        assertEquals(false, payload?.appLockEnabled)
        assertEquals(ProtectedLocalPayloadCodec.SCHEMA_VERSION, payload?.schemaVersion)
    }

    @Test
    fun roundTripDisabledAndEnabledFlags() {
        val disabled = ProtectedLocalPayloadCodec.parse(ProtectedLocalPayloadCodec.encode(false))
        val enabled = ProtectedLocalPayloadCodec.parse(ProtectedLocalPayloadCodec.encode(true))

        assertEquals(false, disabled?.fromLegacy)
        assertEquals(false, disabled?.appLockEnabled)
        assertEquals(true, enabled?.appLockEnabled)
    }

    @Test
    fun trailingBytesAndUnknownFlagsFailClosed() {
        val encoded = ProtectedLocalPayloadCodec.encode(false)
        assertNull(ProtectedLocalPayloadCodec.parse(encoded + byteArrayOf(0)))
        assertNull(ProtectedLocalPayloadCodec.parse(encoded.copyOf().also { it[6] = 2 }))
        assertNull(ProtectedLocalPayloadCodec.parse(encoded.copyOf().also { it[5] = 0 }))
        assertNull(ProtectedLocalPayloadCodec.parse(encoded.copyOf().also { it[0] = 'X'.code.toByte() }))
        assertNull(ProtectedLocalPayloadCodec.parse(encoded.copyOf().also { it[4] = 99 }))
    }
}
