package com.veil.app.local

import android.content.Context
import com.veil.app.core.CoreBridgeStatus
import com.veil.app.core.RustCoreBridge
import com.veil.app.security.LocalProtectionKeyStore
import com.veil.app.security.ProtectionStatus
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class LocalDataStatus {
    WAITING_FOR_PROTECTION,
    CHECKING,
    PURGING,
    READY,
    KEY_UNAVAILABLE,
    CORRUPT_OR_UNREADABLE,
    POLICY_UNAVAILABLE,
    INCOMPATIBLE,
    ERROR,
}

internal sealed interface RetentionPolicyLoad {
    data class Available(val policy: RetentionPolicy) : RetentionPolicyLoad
    data object Unavailable : RetentionPolicyLoad
    data object Incompatible : RetentionPolicyLoad
}

internal class RustRetentionPolicyLoader(
    private val loadSnapshot: () -> com.veil.app.core.CoreBridgeSnapshot,
) {
    constructor(bridge: RustCoreBridge) : this({ bridge.load() })

    fun load(): RetentionPolicyLoad {
        val snapshot = loadSnapshot()
        return when (snapshot.status) {
            CoreBridgeStatus.AVAILABLE -> {
                val seconds = snapshot.maxMessageAvailabilitySeconds
                    ?: return RetentionPolicyLoad.Unavailable
                val policy = retentionPolicyFromSeconds(seconds)
                    ?: return RetentionPolicyLoad.Unavailable
                RetentionPolicyLoad.Available(policy)
            }
            CoreBridgeStatus.UNAVAILABLE -> RetentionPolicyLoad.Unavailable
            CoreBridgeStatus.INCOMPATIBLE -> RetentionPolicyLoad.Incompatible
        }
    }
}

internal sealed interface LocalStoreFactoryResult {
    data class Opened(val store: LocalRecordStore) : LocalStoreFactoryResult
    data object Incompatible : LocalStoreFactoryResult
    data object Unreadable : LocalStoreFactoryResult
}

internal fun interface LocalRecordStoreFactory {
    fun open(): LocalStoreFactoryResult
}

internal class LocalDataController(
    private val keyStore: LocalProtectionKeyStore,
    private val storeFactory: LocalRecordStoreFactory,
    private val cipher: LocalRecordCipher,
    private val clock: RetentionClock,
    private val policyLoader: RustRetentionPolicyLoader,
    private val workerDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) {
    private val mutableStatus = MutableStateFlow(LocalDataStatus.WAITING_FOR_PROTECTION)
    val status: StateFlow<LocalDataStatus> = mutableStatus
    private val starting = AtomicBoolean(false)
    @Volatile
    private var session: LocalStoreSession? = null

    fun onProtectionStatus(protectionStatus: ProtectionStatus) {
        if (protectionStatus == ProtectionStatus.READY) {
            start()
        } else if (protectionStatus != ProtectionStatus.CHECKING &&
            protectionStatus != ProtectionStatus.PROVISIONING
        ) {
            closeSession()
            mutableStatus.value = LocalDataStatus.WAITING_FOR_PROTECTION
        }
    }

    fun start() {
        if (!starting.compareAndSet(false, true)) return
        scope.launch {
            mutableStatus.value = LocalDataStatus.CHECKING
            closeSession()
            try {
                val policy = withContext(workerDispatcher) { policyLoader.load() }
                val loadedPolicy = when (policy) {
                    RetentionPolicyLoad.Unavailable -> {
                        mutableStatus.value = LocalDataStatus.POLICY_UNAVAILABLE
                        return@launch
                    }
                    RetentionPolicyLoad.Incompatible -> {
                        mutableStatus.value = LocalDataStatus.INCOMPATIBLE
                        return@launch
                    }
                    is RetentionPolicyLoad.Available -> policy.policy
                }
                val factoryResult = withContext(workerDispatcher) { storeFactory.open() }
                val store = when (factoryResult) {
                    LocalStoreFactoryResult.Incompatible -> {
                        mutableStatus.value = LocalDataStatus.INCOMPATIBLE
                        return@launch
                    }
                    LocalStoreFactoryResult.Unreadable -> {
                        mutableStatus.value = LocalDataStatus.CORRUPT_OR_UNREADABLE
                        return@launch
                    }
                    is LocalStoreFactoryResult.Opened -> factoryResult.store
                }
                val opened = withContext(workerDispatcher) {
                    LocalStoreSession.open(keyStore, store, cipher, clock, loadedPolicy)
                }
                when (opened) {
                    LocalStoreOpenResult.KeyUnavailable -> {
                        store.close()
                        mutableStatus.value = LocalDataStatus.KEY_UNAVAILABLE
                    }
                    LocalStoreOpenResult.Unreadable -> {
                        store.close()
                        mutableStatus.value = LocalDataStatus.CORRUPT_OR_UNREADABLE
                    }
                    is LocalStoreOpenResult.Ready -> {
                        mutableStatus.value = LocalDataStatus.PURGING
                        withContext(workerDispatcher) { opened.session.refreshTimeAndPurge() }
                        session = opened.session
                        mutableStatus.value = LocalDataStatus.READY
                    }
                }
            } catch (_: RuntimeException) {
                closeSession()
                mutableStatus.value = LocalDataStatus.ERROR
            } finally {
                starting.set(false)
            }
        }
    }

    fun cancel() {
        closeSession()
        scope.cancel()
    }

    /**
     * Message-capable UI must not read this until [LocalDataStatus.READY].
     * Expired plaintext is never returned.
     */
    fun renderableMessages(conversationId: String): List<LocalMessageRecord>? {
        if (mutableStatus.value != LocalDataStatus.READY) return null
        return session?.messages?.listValidUnexpired(conversationId)
    }

    fun renderableConversations(): List<LocalConversationShell>? {
        if (mutableStatus.value != LocalDataStatus.READY) return null
        return session?.conversations?.list()
    }

    fun sessionForTests(): LocalStoreSession? = session?.takeIf { mutableStatus.value == LocalDataStatus.READY }

    private fun closeSession() {
        session?.close()
        session = null
    }
}

internal class SqliteLocalRecordStoreFactory(
    private val context: Context,
    private val name: String = LOCAL_DATABASE_NAME,
) : LocalRecordStoreFactory {
    override fun open(): LocalStoreFactoryResult = when (val opened = LocalDatabase.open(context, name)) {
        is LocalDatabaseOpenResult.Opened -> LocalStoreFactoryResult.Opened(opened.store)
        LocalDatabaseOpenResult.Incompatible -> LocalStoreFactoryResult.Incompatible
        is LocalDatabaseOpenResult.Unreadable -> LocalStoreFactoryResult.Unreadable
    }
}

internal object LocalDataWiper {
    fun deleteDatabase(context: Context, name: String = LOCAL_DATABASE_NAME): Boolean =
        LocalDatabase.delete(context, name)
}
