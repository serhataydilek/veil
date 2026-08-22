package com.veil.app.local

import com.veil.app.security.ProtectedBlob
import java.security.GeneralSecurityException
import javax.crypto.SecretKey

internal class FakeLocalRecordCipher(
    private val inner: LocalRecordCipher = AesGcmLocalRecordCipher(),
) : LocalRecordCipher {
    var decryptFailure: ((LocalRecordType) -> Exception?)? = null
    var encryptFailure: ((LocalRecordType) -> Exception?)? = null
    var encryptBadIv = false

    override fun encrypt(key: SecretKey, plaintext: ByteArray, aad: ByteArray): ProtectedBlob {
        val type = recordTypeOf(aad)
        if (type != null) {
            encryptFailure?.invoke(type)?.let { throw it }
        }
        if (encryptBadIv) {
            return ProtectedBlob(ByteArray(1) { 0 }, ByteArray(16) { 1 })
        }
        return inner.encrypt(key, plaintext, aad)
    }

    override fun decrypt(key: SecretKey, blob: ProtectedBlob, aad: ByteArray): ByteArray {
        val type = recordTypeOf(aad)
        if (type != null) {
            decryptFailure?.invoke(type)?.let { throw it }
        }
        return inner.decrypt(key, blob, aad)
    }

    private fun recordTypeOf(aad: ByteArray): LocalRecordType? {
        val domain = "veil.local-record.v1".encodeToByteArray()
        if (aad.size <= domain.size) return null
        val discriminant = aad[domain.size]
        return LocalRecordType.entries.firstOrNull { it.discriminant == discriminant }
    }
}
