package com.veil.app.security

internal enum class AuthenticatorAvailability {
    AVAILABLE,
    NOT_CONFIGURED,
    TEMPORARILY_UNAVAILABLE,
}

internal enum class AuthenticationResult {
    SUCCESS,
    CANCELLED,
    NOT_CONFIGURED,
    TEMPORARILY_UNAVAILABLE,
    LOCKED_OUT,
    ERROR,
}

internal enum class AuthReason {
    UNLOCK,
    ENABLE,
    DISABLE,
}

/**
 * Narrow platform-authentication boundary for local UI access control.
 * Success is not cryptographic unlock and is never a Veil identity proof.
 */
internal interface AppAuthenticator {
    fun availability(): AuthenticatorAvailability
    fun authenticate(reason: AuthReason, onResult: (AuthenticationResult) -> Unit)
    fun cancel()
}
