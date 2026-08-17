package com.veil.app.security

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

/** Small application-state boundary keeping local protection I/O out of Compose. */
internal class LocalProtectionController(
    private val protectedState: ProtectedStateStore,
    private val workerDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) {
    private val mutableStatus = MutableStateFlow(ProtectionStatus.CHECKING)
    val status: StateFlow<ProtectionStatus> = mutableStatus
    private val provisioning = AtomicBoolean(false)

    init {
        loadStatus()
    }

    fun loadStatus() {
        scope.launch {
            mutableStatus.value = ProtectionStatus.CHECKING
            mutableStatus.value = withContext(workerDispatcher) { protectedState.currentStatus() }
        }
    }

    fun prepare() {
        if (!provisioning.compareAndSet(false, true)) return
        scope.launch {
            mutableStatus.value = ProtectionStatus.PROVISIONING
            try {
                mutableStatus.value = withContext(workerDispatcher) { protectedState.provision() }
            } finally {
                provisioning.set(false)
            }
        }
    }

    fun cancel() = scope.cancel()
}
