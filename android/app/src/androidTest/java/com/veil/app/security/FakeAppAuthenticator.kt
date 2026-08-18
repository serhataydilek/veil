package com.veil.app.security

internal class FakeAppAuthenticator(
    var availabilityValue: AuthenticatorAvailability = AuthenticatorAvailability.AVAILABLE,
    var nextResult: AuthenticationResult = AuthenticationResult.SUCCESS,
    var completeImmediately: Boolean = true,
) : AppAuthenticator {
    var authenticateCalls = 0
    private var pending: ((AuthenticationResult) -> Unit)? = null

    override fun availability(): AuthenticatorAvailability = availabilityValue

    override fun authenticate(reason: AuthReason, onResult: (AuthenticationResult) -> Unit) {
        authenticateCalls += 1
        if (completeImmediately) {
            onResult(nextResult)
        } else {
            pending = onResult
        }
    }

    fun complete(result: AuthenticationResult = nextResult) {
        val callback = pending
        pending = null
        callback?.invoke(result)
    }

    override fun cancel() {
        val callback = pending
        pending = null
        callback?.invoke(AuthenticationResult.CANCELLED)
    }
}
