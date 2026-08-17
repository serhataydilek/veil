package com.veil.app.security

import java.nio.ByteBuffer
import java.nio.ByteOrder

internal data class ProtectedBlob(val iv: ByteArray, val ciphertext: ByteArray)

/** Tiny bounded binary envelope for the local protection sentinel only. */
internal object ProtectedStateFormat {
    private val magic = byteArrayOf('V'.code.toByte(), 'L'.code.toByte(), 'P'.code.toByte(), '1'.code.toByte())
    private const val version: Byte = 1
    internal const val IV_LENGTH = 12
    private const val headerLength = 4 + 1 + 1 + 4
    private const val minimumCiphertextLength = 16
    internal const val maxCiphertextLength = 4 * 1024
    /** Maximum complete VLP1 envelope accepted from the physical state file. */
    internal const val MAX_ENCODED_LENGTH = headerLength + IV_LENGTH + maxCiphertextLength

    fun encode(blob: ProtectedBlob): ByteArray {
        require(blob.iv.size == IV_LENGTH)
        require(blob.ciphertext.size in minimumCiphertextLength..maxCiphertextLength)
        return ByteBuffer.allocate(headerLength + blob.iv.size + blob.ciphertext.size)
            .order(ByteOrder.BIG_ENDIAN)
            .put(magic)
            .put(version)
            .put(blob.iv.size.toByte())
            .putInt(blob.ciphertext.size)
            .put(blob.iv)
            .put(blob.ciphertext)
            .array()
    }

    fun decode(bytes: ByteArray): ProtectedBlob? {
        if (bytes.size < headerLength + IV_LENGTH + minimumCiphertextLength) return null
        val input = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        val actualMagic = ByteArray(magic.size)
        input.get(actualMagic)
        if (!actualMagic.contentEquals(magic) || input.get() != version) return null
        val actualIvLength = input.get().toInt() and 0xff
        if (actualIvLength != IV_LENGTH) return null
        val ciphertextLength = input.int
        if (ciphertextLength !in minimumCiphertextLength..maxCiphertextLength) return null
        if (input.remaining() != actualIvLength + ciphertextLength) return null
        val iv = ByteArray(actualIvLength)
        val ciphertext = ByteArray(ciphertextLength)
        input.get(iv)
        input.get(ciphertext)
        return ProtectedBlob(iv, ciphertext)
    }
}
