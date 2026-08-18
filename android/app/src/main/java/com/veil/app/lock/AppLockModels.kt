package com.veil.app.lock

import com.veil.app.security.AuthenticatorAvailability
import com.veil.app.security.ProtectionStatus

internal enum class AppLockSessionState {
    EVALUATING,
    LOCK_NOT_REQUIRED,
    LOCKED,
    AUTHENTICATING,
    UNLOCKED,
    UNAVAILABLE,
}

internal enum class AppLockError {
    AUTH_NOT_CONFIGURED,
    AUTH_CANCELLED,
    AUTH_UNAVAILABLE,
    AUTH_LOCKED_OUT,
    AUTH_FAILED,
    PROTECTED_STATE_UNAVAILABLE,
    STATE_UPDATE_FAILED,
}

internal fun AppLockError.userMessage(): String? = when (this) {
    AppLockError.AUTH_NOT_CONFIGURED ->
        "A device screen lock must be configured before App Lock can be used."
    AppLockError.AUTH_CANCELLED -> null
    AppLockError.AUTH_UNAVAILABLE -> "Device authentication is temporarily unavailable."
    AppLockError.AUTH_LOCKED_OUT -> "Too many attempts. Try again later."
    AppLockError.AUTH_FAILED -> "Could not authenticate."
    AppLockError.PROTECTED_STATE_UNAVAILABLE -> "Protected local state is unavailable."
    AppLockError.STATE_UPDATE_FAILED ->
        "Protected local state could not be updated. Existing data has not been changed."
}

internal data class AppPrivacyViewState(
    val protectionStatus: ProtectionStatus = ProtectionStatus.CHECKING,
    val session: AppLockSessionState = AppLockSessionState.EVALUATING,
    val appLockEnabled: Boolean = false,
    val appLockPreferenceKnown: Boolean = false,
    val authenticatorAvailability: AuthenticatorAvailability = AuthenticatorAvailability.NOT_CONFIGURED,
    val error: AppLockError? = null,
    val preferenceChangeInProgress: Boolean = false,
)
