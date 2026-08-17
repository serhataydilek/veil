package com.veil.app.security

import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal interface ProtectedBlobCipher {
    @Throws(GeneralSecurityException::class)
    fun encrypt(key: SecretKey, plaintext: ByteArray): ProtectedBlob

    @Throws(GeneralSecurityException::class)
    fun decrypt(key: SecretKey, blob: ProtectedBlob): ByteArray
}

internal class AesGcmProtectedBlobCipher : ProtectedBlobCipher {
    override fun encrypt(key: SecretKey, plaintext: ByteArray): ProtectedBlob {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(AAD)
        return ProtectedBlob(cipher.iv, cipher.doFinal(plaintext))
    }

    override fun decrypt(key: SecretKey, blob: ProtectedBlob): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, blob.iv))
        cipher.updateAAD(AAD)
        return cipher.doFinal(blob.ciphertext)
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
        val AAD = "veil.local-state.v1".encodeToByteArray()
    }
}
