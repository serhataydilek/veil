package com.veil.app.lock

import com.veil.app.security.AppAuthenticator
import com.veil.app.security.AuthReason
import com.veil.app.security.AuthenticationResult
import com.veil.app.security.AuthenticatorAvailability
import com.veil.app.security.ProtectedLoadResult
import com.veil.app.security.ProtectedStateStore
import com.veil.app.security.ProtectionStatus
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * In-memory app-lock session. UNLOCKED is never persisted and is reconstructed
 * as LOCKED after process death whenever the protected preference is enabled.
 */
internal class AppPrivacyController(
    private val protectedState: ProtectedStateStore,
    private val workerDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) {
    private val mutableState = MutableStateFlow(AppPrivacyViewState())
    val state: StateFlow<AppPrivacyViewState> = mutableState
    private val provisioning = AtomicBoolean(false)
    private val authInFlight = AtomicBoolean(false)
    private var processInForeground = false
    private var pendingUnlock = false

    init {
        load()
    }

    fun load() {
        scope.launch {
            mutableState.value = mutableState.value.copy(
                protectionStatus = ProtectionStatus.CHECKING,
                session = AppLockSessionState.EVALUATING,
                appLockPreferenceKnown = false,
                error = null,
            )
            val loaded = withContext(workerDispatcher) { loadAndMigrate() }
            mutableState.value = stateFromLoad(loaded, mutableState.value)
        }
    }

    fun prepare() {
        if (!provisioning.compareAndSet(false, true)) return
        scope.launch {
            mutableState.value = mutableState.value.copy(protectionStatus = ProtectionStatus.PROVISIONING)
            try {
                val status = withContext(workerDispatcher) { protectedState.provision() }
                val loaded = withContext(workerDispatcher) { protectedState.load() }
                mutableState.value = stateFromLoad(loaded.copy(status = status), mutableState.value)
            } finally {
                provisioning.set(false)
            }
        }
    }

    fun refreshAvailability(authenticator: AppAuthenticator) {
        mutableState.value = mutableState.value.copy(authenticatorAvailability = authenticator.availability())
    }

    fun onProcessForeground() {
        processInForeground = true
        val current = mutableState.value
        if (pendingUnlock) {
            pendingUnlock = false
            authInFlight.set(false)
            mutableState.value = current.copy(
                session = AppLockSessionState.UNLOCKED,
                preferenceChangeInProgress = false,
                error = null,
            )
        }
    }

    fun onProcessBackground() {
        processInForeground = false
        val current = mutableState.value
        if (relockBlocked(current)) return
        if (current.appLockEnabled && current.session == AppLockSessionState.UNLOCKED) {
            mutableState.value = current.copy(session = AppLockSessionState.LOCKED, error = null)
        }
    }

    fun requestUnlock(authenticator: AppAuthenticator) {
        val current = mutableState.value
        if (current.session != AppLockSessionState.LOCKED) return
        if (current.protectionStatus != ProtectionStatus.READY) {
            mutableState.value = current.copy(error = AppLockError.PROTECTED_STATE_UNAVAILABLE)
            return
        }
        when (val availability = authenticator.availability()) {
            AuthenticatorAvailability.NOT_CONFIGURED -> {
                mutableState.value = current.copy(
                    authenticatorAvailability = availability,
                    error = AppLockError.AUTH_NOT_CONFIGURED,
                )
                return
            }
            AuthenticatorAvailability.TEMPORARILY_UNAVAILABLE -> {
                mutableState.value = current.copy(
                    authenticatorAvailability = availability,
                    error = AppLockError.AUTH_UNAVAILABLE,
                )
                return
            }
            AuthenticatorAvailability.AVAILABLE -> Unit
        }
        if (!authInFlight.compareAndSet(false, true)) return
        pendingUnlock = false
        mutableState.value = current.copy(
            session = AppLockSessionState.AUTHENTICATING,
            authenticatorAvailability = AuthenticatorAvailability.AVAILABLE,
            error = null,
        )
        authenticator.authenticate(AuthReason.UNLOCK) { result ->
            scope.launch { handleUnlockResult(result) }
        }
    }

    fun setAppLockEnabled(enabled: Boolean, authenticator: AppAuthenticator) {
        val current = mutableState.value
        if (current.appLockEnabled == enabled && current.appLockPreferenceKnown) return
        if (current.protectionStatus != ProtectionStatus.READY || !current.appLockPreferenceKnown) {
            mutableState.value = current.copy(error = AppLockError.PROTECTED_STATE_UNAVAILABLE)
            return
        }
        when (val availability = authenticator.availability()) {
            AuthenticatorAvailability.NOT_CONFIGURED -> {
                mutableState.value = current.copy(
                    authenticatorAvailability = availability,
                    error = AppLockError.AUTH_NOT_CONFIGURED,
                )
                return
            }
            AuthenticatorAvailability.TEMPORARILY_UNAVAILABLE -> {
                mutableState.value = current.copy(
                    authenticatorAvailability = availability,
                    error = AppLockError.AUTH_UNAVAILABLE,
                )
                return
            }
            AuthenticatorAvailability.AVAILABLE -> Unit
        }
        if (!authInFlight.compareAndSet(false, true)) return
        mutableState.value = current.copy(
            preferenceChangeInProgress = true,
            authenticatorAvailability = AuthenticatorAvailability.AVAILABLE,
            error = null,
        )
        val reason = if (enabled) AuthReason.ENABLE else AuthReason.DISABLE
        authenticator.authenticate(reason) { result ->
            scope.launch { handlePreferenceResult(enabled, result) }
        }
    }

    fun cancel() = scope.cancel()

    private fun handleUnlockResult(result: AuthenticationResult) {
        val current = mutableState.value
        if (result != AuthenticationResult.SUCCESS) {
            pendingUnlock = false
            authInFlight.set(false)
            mutableState.value = current.copy(
                session = AppLockSessionState.LOCKED,
                error = result.toAppLockError(),
            )
            return
        }
        if (processInForeground) {
            pendingUnlock = false
            authInFlight.set(false)
            mutableState.value = current.copy(
                session = AppLockSessionState.UNLOCKED,
                error = null,
            )
        } else {
            pendingUnlock = true
            mutableState.value = current.copy(
                session = AppLockSessionState.AUTHENTICATING,
                error = null,
            )
        }
    }

    private fun handlePreferenceResult(enabled: Boolean, result: AuthenticationResult) {
        if (result != AuthenticationResult.SUCCESS) {
            authInFlight.set(false)
            mutableState.value = mutableState.value.copy(
                preferenceChangeInProgress = false,
                error = result.toAppLockError(),
            )
            return
        }
        scope.launch {
            val persisted = withContext(workerDispatcher) { protectedState.writeAppLockEnabled(enabled) }
            authInFlight.set(false)
            val current = mutableState.value
            if (!persisted) {
                mutableState.value = current.copy(
                    preferenceChangeInProgress = false,
                    error = AppLockError.PROTECTED_STATE_UNAVAILABLE,
                )
                return@launch
            }
            mutableState.value = current.copy(
                appLockEnabled = enabled,
                appLockPreferenceKnown = true,
                preferenceChangeInProgress = false,
                session = if (enabled) AppLockSessionState.UNLOCKED else AppLockSessionState.LOCK_NOT_REQUIRED,
                error = null,
            )
        }
    }

    private fun loadAndMigrate(): ProtectedLoadResult {
        val initial = protectedState.load()
        if (initial.status == ProtectionStatus.READY && initial.payload?.fromLegacy == true) {
            protectedState.migrateLegacyIfPresent()
            return protectedState.load()
        }
        return initial
    }

    private fun stateFromLoad(loaded: ProtectedLoadResult, previous: AppPrivacyViewState): AppPrivacyViewState {
        val payload = loaded.payload
        val known = loaded.status == ProtectionStatus.READY && payload != null
        val enabled = payload?.appLockEnabled == true
        return previous.copy(
            protectionStatus = loaded.status,
            session = sessionAfterLoad(loaded.status, enabled, known),
            appLockEnabled = if (known) enabled else false,
            appLockPreferenceKnown = known,
            preferenceChangeInProgress = false,
            error = null,
        )
    }

    private fun sessionAfterLoad(
        status: ProtectionStatus,
        enabled: Boolean,
        known: Boolean,
    ): AppLockSessionState = when (status) {
        ProtectionStatus.KEY_UNAVAILABLE, ProtectionStatus.CORRUPT_OR_UNREADABLE ->
            AppLockSessionState.UNAVAILABLE
        ProtectionStatus.CHECKING, ProtectionStatus.PROVISIONING, ProtectionStatus.PURGING ->
            AppLockSessionState.EVALUATING
        ProtectionStatus.READY ->
            if (known && enabled) AppLockSessionState.LOCKED else AppLockSessionState.LOCK_NOT_REQUIRED
        else -> AppLockSessionState.LOCK_NOT_REQUIRED
    }

    private fun relockBlocked(current: AppPrivacyViewState): Boolean =
        current.session == AppLockSessionState.AUTHENTICATING ||
            current.preferenceChangeInProgress ||
            pendingUnlock

    private fun AuthenticationResult.toAppLockError(): AppLockError? = when (this) {
        AuthenticationResult.SUCCESS -> null
        AuthenticationResult.CANCELLED -> AppLockError.AUTH_CANCELLED
        AuthenticationResult.NOT_CONFIGURED -> AppLockError.AUTH_NOT_CONFIGURED
        AuthenticationResult.TEMPORARILY_UNAVAILABLE -> AppLockError.AUTH_UNAVAILABLE
        AuthenticationResult.LOCKED_OUT -> AppLockError.AUTH_LOCKED_OUT
        AuthenticationResult.ERROR -> AppLockError.AUTH_FAILED
    }
}
