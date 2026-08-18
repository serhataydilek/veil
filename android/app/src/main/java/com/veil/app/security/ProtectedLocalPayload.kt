package com.veil.app.security

/**
 * Versioned plaintext that exists only inside the Phase 1B VLP1 envelope.
 * It never records unlock status, authentication method, or identity data.
 */
internal data class ProtectedLocalPayload(
    val schemaVersion: Int,
    val appLockEnabled: Boolean,
    val fromLegacy: Boolean = false,
)

internal object ProtectedLocalPayloadCodec {
    internal const val SCHEMA_VERSION = 1
    internal const val ENCODED_LENGTH = 7
    internal val LEGACY_SENTINEL = "LOCAL_PROTECTION_READY:1".encodeToByteArray()
    private val magic = byteArrayOf('V'.code.toByte(), 'L'.code.toByte(), 'S'.code.toByte(), '1'.code.toByte())

    fun encode(appLockEnabled: Boolean): ByteArray = byteArrayOf(
        magic[0],
        magic[1],
        magic[2],
        magic[3],
        SCHEMA_VERSION.toByte(),
        READY_MARKER,
        if (appLockEnabled) 1 else 0,
    )

    fun parse(plaintext: ByteArray): ProtectedLocalPayload? {
        if (plaintext.contentEquals(LEGACY_SENTINEL)) {
            return ProtectedLocalPayload(
                schemaVersion = SCHEMA_VERSION,
                appLockEnabled = false,
                fromLegacy = true,
            )
        }
        if (plaintext.size != ENCODED_LENGTH) return null
        if (!plaintext.copyOfRange(0, magic.size).contentEquals(magic)) return null
        val version = plaintext[4].toInt() and 0xff
        if (version != SCHEMA_VERSION) return null
        if (plaintext[5] != READY_MARKER) return null
        val appLockEnabled = when (plaintext[6]) {
            0.toByte() -> false
            1.toByte() -> true
            else -> return null
        }
        return ProtectedLocalPayload(
            schemaVersion = version,
            appLockEnabled = appLockEnabled,
            fromLegacy = false,
        )
    }

    private const val READY_MARKER: Byte = 1
}
