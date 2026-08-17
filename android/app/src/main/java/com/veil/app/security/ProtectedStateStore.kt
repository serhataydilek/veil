package com.veil.app.security

import android.content.Context
import java.io.IOException
import java.security.GeneralSecurityException

/**
 * Owns only the encrypted provisioning sentinel. It must never be used as a
 * protocol-identity store or to replace unreadable existing state.
 */
internal class ProtectedStateStore(
    private val keyStore: LocalProtectionKeyStore,
    private val stateFile: ProtectedStateFile,
    private val cipher: ProtectedBlobCipher,
) {
    fun currentStatus(): ProtectionStatus {
        if (!stateFile.exists()) return ProtectionStatus.NOT_PROVISIONED
        val key = when (val result = keyStore.existingKey()) {
            is ExistingKeyResult.Available -> result.key
            ExistingKeyResult.Missing, ExistingKeyResult.Unavailable -> return ProtectionStatus.KEY_UNAVAILABLE
        }
        val encoded = try {
            stateFile.read()
        } catch (_: IOException) {
            return ProtectionStatus.CORRUPT_OR_UNREADABLE
        }
        val blob = ProtectedStateFormat.decode(encoded) ?: return ProtectionStatus.CORRUPT_OR_UNREADABLE
        return try {
            if (cipher.decrypt(key, blob).contentEquals(SENTINEL)) ProtectionStatus.READY else ProtectionStatus.CORRUPT_OR_UNREADABLE
        } catch (_: GeneralSecurityException) {
            ProtectionStatus.CORRUPT_OR_UNREADABLE
        }
    }

    /** Provisioning is the only code path permitted to create the local key. */
    fun provision(): ProtectionStatus {
        if (stateFile.exists()) return currentStatus()
        val key = when (val result = keyStore.provisioningKey()) {
            is ExistingKeyResult.Available -> result.key
            ExistingKeyResult.Missing, ExistingKeyResult.Unavailable -> return ProtectionStatus.ERROR
        }
        val bytes = try {
            val blob = cipher.encrypt(key, SENTINEL)
            ProtectedStateFormat.encode(blob)
        } catch (_: GeneralSecurityException) {
            return ProtectionStatus.ERROR
        }
        if (!stateFile.write(bytes)) return ProtectionStatus.ERROR
        return currentStatus()
    }

    fun purge(): PurgeResult {
        if (!keyStore.deleteKey()) return PurgeResult(false, ProtectionStatus.ERROR)
        if (!stateFile.delete()) return PurgeResult(false, ProtectionStatus.KEY_UNAVAILABLE)
        return if (!stateFile.exists() && keyStore.existingKey() is ExistingKeyResult.Missing) {
            PurgeResult(true, ProtectionStatus.PURGED)
        } else {
            PurgeResult(false, ProtectionStatus.ERROR)
        }
    }

    fun protectionSecurityLevel(): ProtectionSecurityLevel = keyStore.securityLevel()

    private companion object {
        val SENTINEL = "LOCAL_PROTECTION_READY:1".encodeToByteArray()
    }
}

internal fun protectedStateStore(context: Context): ProtectedStateStore = ProtectedStateStore(
    keyStore = AndroidLocalProtectionKeyStore(),
    stateFile = AndroidAtomicProtectedStateFile(context.applicationContext),
    cipher = AesGcmProtectedBlobCipher(),
)
