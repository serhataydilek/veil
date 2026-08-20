package com.veil.app.local

import com.veil.app.core.CoreBridgeSnapshot
import com.veil.app.core.CoreBridgeStatus
import com.veil.app.security.ProtectionStatus
import com.veil.app.security.TestLocalProtectionKeyStore
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalDataControllerTest {
    private val policySeconds = 24L * 60 * 60
    private val created = 1_000_000L

    @Test
    fun expiredMessagesArePurgedBeforeReadyAndCannotBeReadEarlier() {
        val store = InMemoryLocalRecordStore()
        val keys = TestLocalProtectionKeyStore().also { it.provisioningKey() }
        val clock = FakeRetentionClock(wallMs = created, elapsedMs = 0)
        val setup = open(keys, store, clock)
        assertTrue(
            setup.conversations.upsert(
                LocalConversationShell("conv-1", LocalConversationState.ESTABLISHING, null, created, created),
            ),
        )
        assertTrue(
            setup.messages.insert(
                LocalMessageRecord(
                    messageId = "msg-1",
                    conversationId = "conv-1",
                    direction = LocalMessageDirection.OUTBOUND,
                    state = LocalMessageState.LOCAL_CREATED,
                    createdAtWallMs = created,
                    authenticatedExpiryWallMs = created + 1_000,
                    relayDeadlineWallMs = null,
                    body = "secret-body",
                ),
            ),
        )
        setup.close()
        clock.wallMs = created + 1_000
        clock.elapsedMs = 1_000

        val readyDuringPurge = AtomicBoolean(false)
        val leakedDuringPurge = AtomicReference<List<LocalMessageRecord>?>(emptyList())
        val firstPurge = AtomicBoolean(true)
        lateinit var controller: LocalDataController
        val gated = object : LocalRecordStore by store {
            override fun deleteMessagesWithExpiryHintAtOrBefore(hintMs: Long): Int {
                if (firstPurge.compareAndSet(true, false)) {
                    readyDuringPurge.set(controller.status.value == LocalDataStatus.READY)
                    leakedDuringPurge.set(controller.renderableMessages("conv-1"))
                }
                return store.deleteMessagesWithExpiryHintAtOrBefore(hintMs)
            }
        }
        controller = LocalDataController(
            keyStore = keys,
            storeFactory = LocalRecordStoreFactory { LocalStoreFactoryResult.Opened(gated) },
            cipher = AesGcmLocalRecordCipher(),
            clock = clock,
            policyLoader = RustRetentionPolicyLoader {
                CoreBridgeSnapshot(
                    status = CoreBridgeStatus.AVAILABLE,
                    maxMessageAvailabilitySeconds = policySeconds,
                )
            },
            workerDispatcher = Dispatchers.Unconfined,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )
        assertNull(controller.renderableMessages("conv-1"))
        controller.start()
        assertEquals(LocalDataStatus.READY, runBlocking { controller.status.first { it == LocalDataStatus.READY } })
        assertFalse(readyDuringPurge.get())
        assertNull(leakedDuringPurge.get())
        assertTrue(controller.renderableMessages("conv-1")!!.isEmpty())
        assertEquals(0, store.messageCount())
        controller.cancel()
    }

    @Test
    fun unavailableRustPolicyFailsClosedWithoutSubstitutingMaximum() {
        val controller = LocalDataController(
            keyStore = TestLocalProtectionKeyStore().also { it.provisioningKey() },
            storeFactory = LocalRecordStoreFactory { LocalStoreFactoryResult.Opened(InMemoryLocalRecordStore()) },
            cipher = AesGcmLocalRecordCipher(),
            clock = FakeRetentionClock(1, 0),
            policyLoader = RustRetentionPolicyLoader {
                CoreBridgeSnapshot(status = CoreBridgeStatus.UNAVAILABLE)
            },
            workerDispatcher = Dispatchers.Unconfined,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )
        controller.start()
        assertEquals(
            LocalDataStatus.POLICY_UNAVAILABLE,
            runBlocking { controller.status.first { it != LocalDataStatus.CHECKING && it != LocalDataStatus.WAITING_FOR_PROTECTION } },
        )
        assertNull(controller.renderableMessages("conv-1"))
        controller.cancel()
    }

    @Test
    fun incompatiblePolicyFailsClosed() {
        val controller = LocalDataController(
            keyStore = TestLocalProtectionKeyStore().also { it.provisioningKey() },
            storeFactory = LocalRecordStoreFactory { LocalStoreFactoryResult.Opened(InMemoryLocalRecordStore()) },
            cipher = AesGcmLocalRecordCipher(),
            clock = FakeRetentionClock(1, 0),
            policyLoader = RustRetentionPolicyLoader {
                CoreBridgeSnapshot(status = CoreBridgeStatus.INCOMPATIBLE, contractVersion = 2u)
            },
            workerDispatcher = Dispatchers.Unconfined,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )
        controller.start()
        assertEquals(
            LocalDataStatus.INCOMPATIBLE,
            runBlocking { controller.status.first { it == LocalDataStatus.INCOMPATIBLE } },
        )
        controller.cancel()
    }

    @Test
    fun protectionDropDuringPurgeNeverPublishesReady() {
        val store = InMemoryLocalRecordStore()
        val keys = TestLocalProtectionKeyStore().also { it.provisioningKey() }
        val clock = FakeRetentionClock(wallMs = created, elapsedMs = 0)
        val setup = open(keys, store, clock)
        assertTrue(
            setup.conversations.upsert(
                LocalConversationShell("conv-1", LocalConversationState.ESTABLISHING, null, created, created),
            ),
        )
        assertTrue(
            setup.messages.insert(
                LocalMessageRecord(
                    messageId = "msg-1",
                    conversationId = "conv-1",
                    direction = LocalMessageDirection.OUTBOUND,
                    state = LocalMessageState.LOCAL_CREATED,
                    createdAtWallMs = created,
                    authenticatedExpiryWallMs = created + 5_000,
                    relayDeadlineWallMs = null,
                    body = "secret-body",
                ),
            ),
        )
        setup.close()

        lateinit var controller: LocalDataController
        val firstPurge = AtomicBoolean(true)
        val gated = object : LocalRecordStore by store {
            override fun deleteMessagesWithExpiryHintAtOrBefore(hintMs: Long): Int {
                if (firstPurge.compareAndSet(true, false)) {
                    controller.onProtectionStatus(ProtectionStatus.KEY_UNAVAILABLE)
                }
                return store.deleteMessagesWithExpiryHintAtOrBefore(hintMs)
            }
        }
        controller = LocalDataController(
            keyStore = keys,
            storeFactory = LocalRecordStoreFactory { LocalStoreFactoryResult.Opened(gated) },
            cipher = AesGcmLocalRecordCipher(),
            clock = clock,
            policyLoader = RustRetentionPolicyLoader {
                CoreBridgeSnapshot(
                    status = CoreBridgeStatus.AVAILABLE,
                    maxMessageAvailabilitySeconds = policySeconds,
                )
            },
            workerDispatcher = Dispatchers.Unconfined,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )
        controller.onProtectionStatus(ProtectionStatus.READY)
        assertEquals(
            LocalDataStatus.WAITING_FOR_PROTECTION,
            runBlocking {
                controller.status.first {
                    it == LocalDataStatus.WAITING_FOR_PROTECTION ||
                        it == LocalDataStatus.READY ||
                        it == LocalDataStatus.ERROR
                }
            },
        )
        assertNull(controller.renderableMessages("conv-1"))
        assertNull(controller.sessionForTests())
        assertEquals(1, store.messageCount())
        controller.cancel()
    }

    private fun open(
        keys: TestLocalProtectionKeyStore,
        store: InMemoryLocalRecordStore,
        clock: FakeRetentionClock,
    ): LocalStoreSession {
        return (
            LocalStoreSession.open(
                keys,
                store,
                AesGcmLocalRecordCipher(),
                clock,
                retentionPolicyFromSeconds(policySeconds)!!,
            ) as LocalStoreOpenResult.Ready
            ).session
    }
}
