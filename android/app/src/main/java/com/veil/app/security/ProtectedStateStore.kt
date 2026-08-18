package com.veil.app.security

import android.content.Context
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.ProviderException
import javax.crypto.SecretKey

/**
 * Owns encrypted local application state. It must never be used as a
 * protocol-identity store or to replace unreadable existing state.
 */
internal class ProtectedStateStore(
    private val keyStore: LocalProtectionKeyStore,
    private val stateFile: ProtectedStateFile,
    private val cipher: ProtectedBlobCipher,
) {
    fun currentStatus(): ProtectionStatus = load().status

    fun load(): ProtectedLoadResult {
        if (!stateFile.exists()) return ProtectedLoadResult(ProtectionStatus.NOT_PROVISIONED, null)
        val key = when (val result = try {
            keyStore.existingKey()
        } catch (_: ProviderException) {
            return ProtectedLoadResult(ProtectionStatus.KEY_UNAVAILABLE, null)
        }) {
            is ExistingKeyResult.Available -> result.key
            ExistingKeyResult.Missing, ExistingKeyResult.Unavailable ->
                return ProtectedLoadResult(ProtectionStatus.KEY_UNAVAILABLE, null)
        }
        val encoded = try {
            stateFile.read(ProtectedStateFormat.MAX_ENCODED_LENGTH)
        } catch (_: IOException) {
            return ProtectedLoadResult(ProtectionStatus.CORRUPT_OR_UNREADABLE, null)
        }
        val blob = ProtectedStateFormat.decode(encoded)
            ?: return ProtectedLoadResult(ProtectionStatus.CORRUPT_OR_UNREADABLE, null)
        val plaintext = try {
            cipher.decrypt(key, blob)
        } catch (_: GeneralSecurityException) {
            return ProtectedLoadResult(ProtectionStatus.CORRUPT_OR_UNREADABLE, null)
        } catch (_: ProviderException) {
            return ProtectedLoadResult(ProtectionStatus.CORRUPT_OR_UNREADABLE, null)
        }
        val payload = ProtectedLocalPayloadCodec.parse(plaintext)
            ?: return ProtectedLoadResult(ProtectionStatus.CORRUPT_OR_UNREADABLE, null)
        return ProtectedLoadResult(ProtectionStatus.READY, payload)
    }

    /** Provisioning is the only code path permitted to create the local key. */
    fun provision(): ProtectionStatus {
        if (stateFile.exists()) return currentStatus()
        val key = when (val result = try {
            keyStore.provisioningKey()
        } catch (_: ProviderException) {
            return ProtectionStatus.ERROR
        }) {
            is ExistingKeyResult.Available -> result.key
            ExistingKeyResult.Missing, ExistingKeyResult.Unavailable -> return ProtectionStatus.ERROR
        }
        if (!persistPayload(key, appLockEnabled = false)) return ProtectionStatus.ERROR
        return currentStatus()
    }

    fun writeAppLockEnabled(enabled: Boolean): Boolean {
        val loaded = load()
        if (loaded.status != ProtectionStatus.READY || loaded.payload == null) return false
        val key = when (val result = try {
            keyStore.existingKey()
        } catch (_: ProviderException) {
            return false
        }) {
            is ExistingKeyResult.Available -> result.key
            ExistingKeyResult.Missing, ExistingKeyResult.Unavailable -> return false
        }
        if (!persistPayload(key, enabled)) return false
        val verified = load()
        val payload = verified.payload ?: return false
        return verified.status == ProtectionStatus.READY &&
            !payload.fromLegacy &&
            payload.appLockEnabled == enabled
    }

    /**
     * Converts a decrypted Phase 1B sentinel into the Phase 1C payload.
     * A failed write leaves the previous valid file in place.
     */
    fun migrateLegacyIfPresent(): Boolean {
        val loaded = load()
        if (loaded.status != ProtectionStatus.READY) return false
        val payload = loaded.payload ?: return false
        if (!payload.fromLegacy) return true
        return writeAppLockEnabled(false)
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

    private fun persistPayload(key: SecretKey, appLockEnabled: Boolean): Boolean {
        val bytes = try {
            val blob = cipher.encrypt(key, ProtectedLocalPayloadCodec.encode(appLockEnabled))
            if (blob.iv.size != ProtectedStateFormat.IV_LENGTH) return false
            ProtectedStateFormat.encode(blob)
        } catch (_: GeneralSecurityException) {
            return false
        } catch (_: ProviderException) {
            return false
        }
        return stateFile.write(bytes)
    }
}

internal fun protectedStateStore(context: Context): ProtectedStateStore = ProtectedStateStore(
    keyStore = AndroidLocalProtectionKeyStore(),
    stateFile = AndroidAtomicProtectedStateFile(context.applicationContext),
    cipher = AesGcmProtectedBlobCipher(),
)
