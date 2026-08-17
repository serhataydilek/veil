package com.veil.app.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.ProviderException
import java.io.IOException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory

internal sealed interface ExistingKeyResult {
    data class Available(val key: SecretKey) : ExistingKeyResult
    data object Missing : ExistingKeyResult
    data object Unavailable : ExistingKeyResult
}

internal enum class ProtectionSecurityLevel {
    SOFTWARE,
    TRUSTED_ENVIRONMENT,
    STRONGBOX,
    UNKNOWN,
}

internal interface LocalProtectionKeyStore {
    fun existingKey(): ExistingKeyResult
    fun provisioningKey(): ExistingKeyResult
    fun deleteKey(): Boolean
    fun securityLevel(): ProtectionSecurityLevel
}

/** Android-only key management for local encrypted state, never a Veil identity key. */
internal class AndroidLocalProtectionKeyStore(private val alias: String = DEFAULT_ALIAS) : LocalProtectionKeyStore {
    override fun existingKey(): ExistingKeyResult = try {
        val keyStore = keyStore()
        if (!keyStore.containsAlias(alias)) return ExistingKeyResult.Missing
        val key = keyStore.getKey(alias, null) as? SecretKey ?: return ExistingKeyResult.Unavailable
        ExistingKeyResult.Available(key)
    } catch (_: GeneralSecurityException) {
        ExistingKeyResult.Unavailable
    } catch (_: ProviderException) {
        ExistingKeyResult.Unavailable
    } catch (_: IOException) {
        ExistingKeyResult.Unavailable
    }

    override fun provisioningKey(): ExistingKeyResult = when (val existing = existingKey()) {
        is ExistingKeyResult.Available -> existing
        ExistingKeyResult.Unavailable -> ExistingKeyResult.Unavailable
        ExistingKeyResult.Missing -> try {
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
            val specification = KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
            ExistingKeyResult.Available(generator.apply { init(specification) }.generateKey())
        } catch (_: GeneralSecurityException) {
            ExistingKeyResult.Unavailable
        } catch (_: ProviderException) {
            ExistingKeyResult.Unavailable
        } catch (_: IOException) {
            ExistingKeyResult.Unavailable
        }
    }

    override fun deleteKey(): Boolean = try {
        val keyStore = keyStore()
        if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
        !keyStore.containsAlias(alias)
    } catch (_: GeneralSecurityException) {
        false
    } catch (_: ProviderException) {
        false
    } catch (_: IOException) {
        false
    }

    override fun securityLevel(): ProtectionSecurityLevel {
        val key = (existingKey() as? ExistingKeyResult.Available)?.key ?: return ProtectionSecurityLevel.UNKNOWN
        return try {
            val keyInfo = SecretKeyFactory.getInstance(key.algorithm, ANDROID_KEY_STORE)
                .getKeySpec(key, KeyInfo::class.java) as? KeyInfo ?: return ProtectionSecurityLevel.UNKNOWN
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                when (keyInfo.getSecurityLevel()) {
                    KeyProperties.SECURITY_LEVEL_SOFTWARE -> ProtectionSecurityLevel.SOFTWARE
                    KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> ProtectionSecurityLevel.TRUSTED_ENVIRONMENT
                    KeyProperties.SECURITY_LEVEL_STRONGBOX -> ProtectionSecurityLevel.STRONGBOX
                    else -> ProtectionSecurityLevel.UNKNOWN
                }
            } else if (keyInfo.isInsideSecureHardware()) {
                ProtectionSecurityLevel.TRUSTED_ENVIRONMENT
            } else {
                ProtectionSecurityLevel.SOFTWARE
            }
        } catch (_: GeneralSecurityException) {
            ProtectionSecurityLevel.UNKNOWN
        } catch (_: ProviderException) {
            ProtectionSecurityLevel.UNKNOWN
        } catch (_: IOException) {
            ProtectionSecurityLevel.UNKNOWN
        }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val DEFAULT_ALIAS = "veil.local-protection.v1"
    }
}
