package com.veil.app.local

import android.content.Context
import com.veil.app.core.CoreBridgeStatus
import com.veil.app.core.RustCoreBridge
import com.veil.app.security.LocalProtectionKeyStore
import com.veil.app.security.ProtectionStatus
import java.security.GeneralSecurityException
import java.security.ProviderException
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

private sealed interface LocalStartupOpen {
    data class PendingPurge(val session: LocalStoreSession) : LocalStartupOpen
    data class Failed(val status: LocalDataStatus) : LocalStartupOpen
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
            var pending: LocalStoreSession? = null
            try {
                val opened = withContext(workerDispatcher) { openSession() }
                when (opened) {
                    is LocalStartupOpen.Failed -> mutableStatus.value = opened.status
                    is LocalStartupOpen.PendingPurge -> {
                        pending = opened.session
                        mutableStatus.value = LocalDataStatus.PURGING
                        val refreshed = withContext(workerDispatcher) { opened.session.refreshTimeAndPurge() }
                        when (refreshed) {
                            is TimeRefreshResult.Advanced -> {
                                session = opened.session
                                pending = null
                                mutableStatus.value = LocalDataStatus.READY
                            }
                            TimeRefreshResult.KeyUnavailable -> {
                                opened.session.close()
                                pending = null
                                mutableStatus.value = LocalDataStatus.KEY_UNAVAILABLE
                            }
                            TimeRefreshResult.PersistFailed -> {
                                opened.session.close()
                                pending = null
                                mutableStatus.value = LocalDataStatus.ERROR
                            }
                        }
                    }
                }
            } catch (_: ProviderException) {
                pending?.close()
                closeSession()
                mutableStatus.value = LocalDataStatus.KEY_UNAVAILABLE
            } catch (error: GeneralSecurityException) {
                pending?.close()
                closeSession()
                mutableStatus.value = when (LocalCryptoFailures.classify(error)) {
                    LocalCryptoFailureKind.KeyUnavailable -> LocalDataStatus.KEY_UNAVAILABLE
                    LocalCryptoFailureKind.AuthenticationFailed -> LocalDataStatus.CORRUPT_OR_UNREADABLE
                }
            } catch (_: RuntimeException) {
                pending?.close()
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
     * Expired plaintext is never returned. Key or provider unavailability is
     * never presented as an empty conversation.
     */
    fun renderableMessages(conversationId: String): List<LocalMessageRecord>? {
        if (mutableStatus.value != LocalDataStatus.READY) return null
        val current = session ?: return null
        return when (val result = current.messages.listValidUnexpired(conversationId)) {
            is LocalMessageListResult.Available -> result.records
            LocalMessageListResult.KeyUnavailable -> {
                failClosed(LocalDataStatus.KEY_UNAVAILABLE)
                null
            }
            LocalMessageListResult.Unavailable -> {
                failClosed(LocalDataStatus.ERROR)
                null
            }
        }
    }

    fun renderableConversations(): List<LocalConversationShell>? {
        if (mutableStatus.value != LocalDataStatus.READY) return null
        val current = session ?: return null
        return when (val result = current.conversations.list()) {
            is LocalConversationListResult.Available -> result.shells
            LocalConversationListResult.KeyUnavailable -> {
                failClosed(LocalDataStatus.KEY_UNAVAILABLE)
                null
            }
        }
    }

    fun sessionForTests(): LocalStoreSession? = session?.takeIf { mutableStatus.value == LocalDataStatus.READY }

    private fun openSession(): LocalStartupOpen {
        val policy = when (val loaded = policyLoader.load()) {
            RetentionPolicyLoad.Unavailable -> return LocalStartupOpen.Failed(LocalDataStatus.POLICY_UNAVAILABLE)
            RetentionPolicyLoad.Incompatible -> return LocalStartupOpen.Failed(LocalDataStatus.INCOMPATIBLE)
            is RetentionPolicyLoad.Available -> loaded.policy
        }
        val store = when (val factoryResult = storeFactory.open()) {
            LocalStoreFactoryResult.Incompatible -> return LocalStartupOpen.Failed(LocalDataStatus.INCOMPATIBLE)
            LocalStoreFactoryResult.Unreadable -> return LocalStartupOpen.Failed(LocalDataStatus.CORRUPT_OR_UNREADABLE)
            is LocalStoreFactoryResult.Opened -> factoryResult.store
        }
        return when (val opened = LocalStoreSession.open(keyStore, store, cipher, clock, policy)) {
            LocalStoreOpenResult.KeyUnavailable -> {
                store.close()
                LocalStartupOpen.Failed(LocalDataStatus.KEY_UNAVAILABLE)
            }
            LocalStoreOpenResult.Unreadable -> {
                store.close()
                LocalStartupOpen.Failed(LocalDataStatus.CORRUPT_OR_UNREADABLE)
            }
            is LocalStoreOpenResult.Ready -> LocalStartupOpen.PendingPurge(opened.session)
        }
    }

    private fun failClosed(status: LocalDataStatus) {
        closeSession()
        mutableStatus.value = status
    }

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
