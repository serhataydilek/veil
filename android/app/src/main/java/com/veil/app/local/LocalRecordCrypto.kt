package com.veil.app.local

import com.veil.app.security.ProtectedBlob
import com.veil.app.security.ProtectedStateFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal enum class LocalRecordType(val discriminant: Byte) {
    CONVERSATION(1),
    MESSAGE(2),
    TIME_BOUND(3),
}

/**
 * Domain-separated AAD for local record encryption.
 * Record classes never share indistinguishable associated data.
 */
internal object LocalRecordAad {
    private val domain = "veil.local-record.v1".encodeToByteArray()
    internal const val FORMAT_VERSION = 1
    internal const val MAX_RECORD_ID_BYTES = 64

    fun encode(type: LocalRecordType, recordLocalId: String, formatVersion: Int = FORMAT_VERSION): ByteArray {
        val idBytes = recordLocalId.encodeToByteArray()
        require(idBytes.isNotEmpty() && idBytes.size <= MAX_RECORD_ID_BYTES)
        require(formatVersion in 0..255)
        return ByteBuffer.allocate(domain.size + 1 + 2 + idBytes.size + 1)
            .order(ByteOrder.BIG_ENDIAN)
            .put(domain)
            .put(type.discriminant)
            .putShort(idBytes.size.toShort())
            .put(idBytes)
            .put(formatVersion.toByte())
            .array()
    }
}

internal interface LocalRecordCipher {
    @Throws(GeneralSecurityException::class)
    fun encrypt(key: SecretKey, plaintext: ByteArray, aad: ByteArray): ProtectedBlob

    @Throws(GeneralSecurityException::class)
    fun decrypt(key: SecretKey, blob: ProtectedBlob, aad: ByteArray): ByteArray
}

/**
 * AES-GCM local-record cipher. Reuses the Phase 1B Keystore key but never the
 * `veil.local-state.v1` AAD used by [com.veil.app.security.AesGcmProtectedBlobCipher].
 */
internal class AesGcmLocalRecordCipher : LocalRecordCipher {
    override fun encrypt(key: SecretKey, plaintext: ByteArray, aad: ByteArray): ProtectedBlob {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(aad)
        val iv = cipher.iv ?: throw GeneralSecurityException("missing IV")
        if (iv.size != ProtectedStateFormat.IV_LENGTH) {
            throw GeneralSecurityException("unexpected IV length")
        }
        return ProtectedBlob(iv, cipher.doFinal(plaintext))
    }

    override fun decrypt(key: SecretKey, blob: ProtectedBlob, aad: ByteArray): ByteArray {
        if (blob.iv.size != ProtectedStateFormat.IV_LENGTH) {
            throw GeneralSecurityException("unexpected IV length")
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, blob.iv))
        cipher.updateAAD(aad)
        return cipher.doFinal(blob.ciphertext)
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
    }
}

/**
 * Versioned envelope for an encrypted local record blob stored in SQLite.
 *
 * This size bound is a defensive local-persistence limit. It is not a Veil
 * protocol or network message-size policy.
 */
internal object LocalRecordFormat {
    private val magic = byteArrayOf('V'.code.toByte(), 'L'.code.toByte(), 'R'.code.toByte(), '1'.code.toByte())
    private const val version: Byte = 1
    internal const val IV_LENGTH = ProtectedStateFormat.IV_LENGTH
    private const val headerLength = 4 + 1 + 1 + 4
    private const val minimumCiphertextLength = 16
    /** Defensive local persistence ciphertext bound; not a protocol limit. */
    internal const val MAX_CIPHERTEXT_LENGTH = 128 * 1024
    internal const val MAX_ENCODED_LENGTH = headerLength + IV_LENGTH + MAX_CIPHERTEXT_LENGTH

    fun encode(blob: ProtectedBlob): ByteArray? {
        if (blob.iv.size != IV_LENGTH) return null
        if (blob.ciphertext.size !in minimumCiphertextLength..MAX_CIPHERTEXT_LENGTH) return null
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
        if (bytes.size > MAX_ENCODED_LENGTH) return null
        val input = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        val actualMagic = ByteArray(magic.size)
        input.get(actualMagic)
        if (!actualMagic.contentEquals(magic) || input.get() != version) return null
        val actualIvLength = input.get().toInt() and 0xff
        if (actualIvLength != IV_LENGTH) return null
        val ciphertextLength = input.int
        if (ciphertextLength !in minimumCiphertextLength..MAX_CIPHERTEXT_LENGTH) return null
        if (input.remaining() != actualIvLength + ciphertextLength) return null
        val iv = ByteArray(actualIvLength)
        val ciphertext = ByteArray(ciphertextLength)
        input.get(iv)
        input.get(ciphertext)
        return ProtectedBlob(iv, ciphertext)
    }
}
