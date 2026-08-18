package com.veil.app.security

import android.app.Activity
import android.app.KeyguardManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** Production adapter. Test fakes must not live in main source. */
internal class AndroidAppAuthenticator(
    private val activity: FragmentActivity,
) : AppAuthenticator {
    private val biometricManager = BiometricManager.from(activity)
    private val keyguardManager = activity.getSystemService(KeyguardManager::class.java)
    private var biometricPrompt: BiometricPrompt? = null
    private var pendingResult: ((AuthenticationResult) -> Unit)? = null

    private val deviceCredentialLauncher = activity.activityResultRegistry.register(
        DEVICE_CREDENTIAL_KEY,
        activity,
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        complete(
            if (result.resultCode == Activity.RESULT_OK) {
                AuthenticationResult.SUCCESS
            } else {
                AuthenticationResult.CANCELLED
            },
        )
    }

    override fun availability(): AuthenticatorAvailability = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> api30Availability()
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> api28Availability()
        else -> api26Availability()
    }

    override fun authenticate(reason: AuthReason, onResult: (AuthenticationResult) -> Unit) {
        pendingResult = onResult
        when (availability()) {
            AuthenticatorAvailability.NOT_CONFIGURED -> {
                complete(AuthenticationResult.NOT_CONFIGURED)
                return
            }
            AuthenticatorAvailability.TEMPORARILY_UNAVAILABLE -> {
                complete(AuthenticationResult.TEMPORARILY_UNAVAILABLE)
                return
            }
            AuthenticatorAvailability.AVAILABLE -> Unit
        }
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> authenticateApi30()
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> authenticateApi28()
            else -> authenticateWithDeviceCredential()
        }
    }

    override fun cancel() {
        biometricPrompt?.cancelAuthentication()
        biometricPrompt = null
        complete(AuthenticationResult.CANCELLED)
    }

    private fun api30Availability(): AuthenticatorAvailability =
        mapStrongOrCredential(biometricManager.canAuthenticate(STRONG_OR_CREDENTIAL))

    private fun api28Availability(): AuthenticatorAvailability {
        if (keyguardManager.isDeviceSecure) return AuthenticatorAvailability.AVAILABLE
        return when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> AuthenticatorAvailability.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE,
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED,
            -> AuthenticatorAvailability.TEMPORARILY_UNAVAILABLE
            else -> AuthenticatorAvailability.NOT_CONFIGURED
        }
    }

    private fun api26Availability(): AuthenticatorAvailability =
        if (keyguardManager.isDeviceSecure) {
            AuthenticatorAvailability.AVAILABLE
        } else {
            AuthenticatorAvailability.NOT_CONFIGURED
        }

    private fun mapStrongOrCredential(code: Int): AuthenticatorAvailability = when (code) {
        BiometricManager.BIOMETRIC_SUCCESS -> AuthenticatorAvailability.AVAILABLE
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> AuthenticatorAvailability.NOT_CONFIGURED
        BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE,
        BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED,
        -> if (keyguardManager.isDeviceSecure) {
            AuthenticatorAvailability.AVAILABLE
        } else {
            AuthenticatorAvailability.TEMPORARILY_UNAVAILABLE
        }
        else -> if (keyguardManager.isDeviceSecure) {
            AuthenticatorAvailability.AVAILABLE
        } else {
            AuthenticatorAvailability.NOT_CONFIGURED
        }
    }

    private fun authenticateApi30() {
        val info = try {
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(PROMPT_TITLE)
                .setAllowedAuthenticators(STRONG_OR_CREDENTIAL)
                .build()
        } catch (_: IllegalArgumentException) {
            complete(AuthenticationResult.ERROR)
            return
        }
        authenticateWithPrompt(info)
    }

    private fun authenticateApi28() {
        when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                val info = try {
                    BiometricPrompt.PromptInfo.Builder()
                        .setTitle(PROMPT_TITLE)
                        .setNegativeButtonText(CANCEL_LABEL)
                        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                        .build()
                } catch (_: IllegalArgumentException) {
                    complete(AuthenticationResult.ERROR)
                    return
                }
                authenticateWithPrompt(info)
            }
            else -> authenticateWithDeviceCredential()
        }
    }

    private fun authenticateWithPrompt(info: BiometricPrompt.PromptInfo) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    complete(AuthenticationResult.SUCCESS)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    complete(mapPromptError(errorCode))
                }
            },
        )
        biometricPrompt = prompt
        prompt.authenticate(info)
    }

    private fun authenticateWithDeviceCredential() {
        @Suppress("DEPRECATION")
        val intent = keyguardManager.createConfirmDeviceCredentialIntent(PROMPT_TITLE, "")
        if (intent == null) {
            complete(AuthenticationResult.NOT_CONFIGURED)
            return
        }
        deviceCredentialLauncher.launch(intent)
    }

    private fun mapPromptError(errorCode: Int): AuthenticationResult = when (errorCode) {
        BiometricPrompt.ERROR_USER_CANCELED,
        BiometricPrompt.ERROR_CANCELED,
        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
        -> AuthenticationResult.CANCELLED
        BiometricPrompt.ERROR_LOCKOUT,
        BiometricPrompt.ERROR_LOCKOUT_PERMANENT,
        -> AuthenticationResult.LOCKED_OUT
        BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL,
        BiometricPrompt.ERROR_NO_BIOMETRICS,
        BiometricPrompt.ERROR_HW_NOT_PRESENT,
        -> AuthenticationResult.NOT_CONFIGURED
        BiometricPrompt.ERROR_HW_UNAVAILABLE,
        BiometricPrompt.ERROR_TIMEOUT,
        -> AuthenticationResult.TEMPORARILY_UNAVAILABLE
        else -> AuthenticationResult.ERROR
    }

    private fun complete(result: AuthenticationResult) {
        val callback = pendingResult
        pendingResult = null
        biometricPrompt = null
        callback?.invoke(result)
    }

    private companion object {
        const val DEVICE_CREDENTIAL_KEY = "veil.device.credential"
        const val PROMPT_TITLE = "Unlock Veil"
        const val CANCEL_LABEL = "Cancel"
        const val STRONG_OR_CREDENTIAL =
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
    }
}
