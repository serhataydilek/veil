package com.veil.app.local

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.veil.app.core.CoreBridgeSnapshot
import com.veil.app.core.CoreBridgeStatus
import com.veil.app.core.RustCoreBridge
import com.veil.app.security.AndroidLocalProtectionKeyStore
import com.veil.app.security.ExistingKeyResult
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AndroidLocalRetentionStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val keys = AndroidLocalProtectionKeyStore(TEST_ALIAS)
    private val recording = RecordingLocalProtectionKeyStore(keys)
    private val clock = FakeRetentionClock(wallMs = CREATED, elapsedMs = 0)
    private val policy = retentionPolicyFromSeconds(24L * 60 * 60)!!

    @Before
    fun setUp() {
        LocalDatabase.delete(context, TEST_DB)
        keys.deleteKey()
        assertTrue(keys.provisioningKey() is ExistingKeyResult.Available)
    }

    @After
    fun tearDown() {
        LocalDatabase.delete(context, TEST_DB)
        keys.deleteKey()
    }

    @Test
    fun databaseCreateReopenAndEncryptedRoundTrip() {
        val first = openSession()
        assertTrue(first.conversations.upsert(shell()))
        assertTrue(first.messages.insert(message(CREATED + 60_000)))
        val loaded = first.messages.listValidUnexpired(CONV).single()
        assertEquals("hello", loaded.body)
        assertEquals("desk", first.conversations.load(CONV)?.localAlias)
        first.close()

        val second = openSession()
        val retained = second.messages.listValidUnexpired(CONV).single()
        assertEquals("hello", retained.body)
        assertEquals(LocalConversationState.ESTABLISHING, second.conversations.list().single().state)
        second.close()
    }

    @Test
    fun expiredRowsArePurged() {
        val session = openSession()
        assertTrue(session.conversations.upsert(shell()))
        assertTrue(session.messages.insert(message(CREATED + 1_000)))
        clock.elapsedMs = 1_000
        assertTrue(session.messages.listValidUnexpired(CONV).isEmpty())
        session.close()
        assertEquals(0, rawMessageCount())
    }

    @Test
    fun conversationDeleteCascadesMessages() {
        val session = openSession()
        assertTrue(session.conversations.upsert(shell()))
        assertTrue(session.messages.insert(message(CREATED + 1_000)))
        session.destroyConversation(CONV)
        assertNull(session.conversations.load(CONV))
        session.close()
        assertEquals(0, rawMessageCount())
    }

    @Test
    fun retainShellWhileDeletingMessages() {
        val session = openSession()
        assertTrue(session.conversations.upsert(shell()))
        assertTrue(session.messages.insert(message(CREATED + 1_000)))
        session.conversations.deleteMessagesRetainingShell(CONV)
        assertEquals(CONV, session.conversations.load(CONV)?.conversationId)
        assertTrue(session.messages.listValidUnexpired(CONV).isEmpty())
        session.close()
    }

    @Test
    fun corruptMessageCiphertextNeverRenders() {
        val session = openSession()
        assertTrue(session.conversations.upsert(shell()))
        assertTrue(session.messages.insert(message(CREATED + 8_000)))
        session.close()
        SQLiteDatabase.openDatabase(
            context.getDatabasePath(TEST_DB).path,
            null,
            SQLiteDatabase.OPEN_READWRITE,
        ).use { db ->
            db.execSQL("UPDATE messages SET ciphertext = ?", arrayOf(ByteArray(48) { 9 }))
        }
        val reopened = openSession()
        assertNull(reopened.messages.loadValidUnexpired("msg-1"))
        assertTrue(reopened.messages.listValidUnexpired(CONV).isEmpty())
        reopened.close()
        assertEquals(0, rawMessageCount())
    }

    @Test
    fun missingKeystoreKeyDoesNotGenerateReplacement() {
        val session = openSession()
        assertTrue(session.conversations.upsert(shell()))
        assertTrue(session.messages.insert(message(CREATED + 8_000)))
        session.close()
        val before = recording.provisioningCalls
        assertTrue(keys.deleteKey())
        val store = (LocalDatabase.open(context, TEST_DB) as LocalDatabaseOpenResult.Opened).store
        val result = LocalStoreSession.open(recording, store, AesGcmLocalRecordCipher(), clock, policy)
        store.close()
        assertTrue(result is LocalStoreOpenResult.KeyUnavailable)
        assertEquals(before, recording.provisioningCalls)
        assertTrue(context.getDatabasePath(TEST_DB).exists())
    }

    @Test
    fun plaintextMarkerIsAbsentFromRawSqlite() {
        val marker = "VEIL_P1G_MARKER_${UUID.randomUUID()}"
        val session = openSession()
        assertTrue(
            session.conversations.upsert(
                shell().copy(localAlias = "${marker}_ALIAS"),
            ),
        )
        assertTrue(
            session.messages.insert(
                message(CREATED + 60_000).copy(body = "${marker}_BODY"),
            ),
        )
        session.close()
        val dbFile = context.getDatabasePath(TEST_DB)
        val fileText = dbFile.readBytes().toString(Charsets.ISO_8859_1)
        assertFalse(fileText.contains(marker))
        SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT ciphertext FROM conversations", null).use { cursor ->
                while (cursor.moveToNext()) {
                    assertFalse(cursor.getBlob(0).toString(Charsets.ISO_8859_1).contains(marker))
                }
            }
            db.rawQuery("SELECT ciphertext FROM messages", null).use { cursor ->
                while (cursor.moveToNext()) {
                    assertFalse(cursor.getBlob(0).toString(Charsets.ISO_8859_1).contains(marker))
                }
            }
        }
    }

    @Test
    fun unknownSchemaVersionIsIncompatibleWithoutDestructiveMigration() {
        LocalDatabase.delete(context, TEST_DB)
        val path = context.getDatabasePath(TEST_DB)
        path.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            db.execSQL("CREATE TABLE local_meta (meta_key TEXT PRIMARY KEY, ciphertext BLOB)")
            db.execSQL("PRAGMA user_version = 99")
        }
        val opened = LocalDatabase.open(context, TEST_DB)
        assertTrue(opened is LocalDatabaseOpenResult.Incompatible)
        assertTrue(path.exists())
    }

    @Test
    fun journalModeIsDeleteNotWal() {
        val opened = when (val result = LocalDatabase.open(context, TEST_DB)) {
            is LocalDatabaseOpenResult.Opened -> result
            else -> error("database open failed: $result")
        }
        val journal = opened.store.pragmaValue("journal_mode").lowercase()
        assertTrue(journal != "wal")
        assertEquals("1", opened.store.pragmaValue("secure_delete"))
        assertEquals("1", opened.store.pragmaValue("foreign_keys"))
        opened.store.close()
    }

    @Test
    fun rustPolicyOpensEmptyStoreToReadyAfterPurge() {
        val snapshot = RustCoreBridge().load()
        assertEquals(CoreBridgeStatus.AVAILABLE, snapshot.status)
        assertNotNull(snapshot.maxMessageAvailabilitySeconds)
        val controller = LocalDataController(
            keyStore = keys,
            storeFactory = SqliteLocalRecordStoreFactory(context, TEST_DB),
            cipher = AesGcmLocalRecordCipher(),
            clock = clock,
            policyLoader = RustRetentionPolicyLoader(RustCoreBridge()),
            workerDispatcher = Dispatchers.Unconfined,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )
        controller.start()
        assertEquals(LocalDataStatus.READY, runBlocking { controller.status.first { it == LocalDataStatus.READY } })
        assertTrue(controller.renderableConversations()!!.isEmpty())
        assertTrue(controller.renderableMessages(CONV)!!.isEmpty())
        controller.cancel()
    }

    @Test
    fun expiredMessagePurgeCompletesBeforeReadyOnSqlite() {
        var session = openSession()
        assertTrue(session.conversations.upsert(shell()))
        assertTrue(session.messages.insert(message(CREATED + 500)))
        session.close()
        clock.elapsedMs = 500
        val readyDuringPurge = AtomicBoolean(false)
        val leaked = AtomicReference<List<LocalMessageRecord>?>(emptyList())
        val firstPurge = AtomicBoolean(true)
        lateinit var controller: LocalDataController
        val innerFactory = SqliteLocalRecordStoreFactory(context, TEST_DB)
        controller = LocalDataController(
            keyStore = keys,
            storeFactory = LocalRecordStoreFactory {
                when (val opened = innerFactory.open()) {
                    is LocalStoreFactoryResult.Opened -> LocalStoreFactoryResult.Opened(
                        object : LocalRecordStore by opened.store {
                            override fun deleteMessagesWithExpiryHintAtOrBefore(hintMs: Long): Int {
                                if (firstPurge.compareAndSet(true, false)) {
                                    readyDuringPurge.set(controller.status.value == LocalDataStatus.READY)
                                    leaked.set(controller.renderableMessages(CONV))
                                }
                                return opened.store.deleteMessagesWithExpiryHintAtOrBefore(hintMs)
                            }
                        },
                    )
                    else -> opened
                }
            },
            cipher = AesGcmLocalRecordCipher(),
            clock = clock,
            policyLoader = RustRetentionPolicyLoader {
                CoreBridgeSnapshot(
                    status = CoreBridgeStatus.AVAILABLE,
                    maxMessageAvailabilitySeconds = 24L * 60 * 60,
                )
            },
            workerDispatcher = Dispatchers.Unconfined,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )
        assertNull(controller.renderableMessages(CONV))
        controller.start()
        assertEquals(LocalDataStatus.READY, runBlocking { controller.status.first { it == LocalDataStatus.READY } })
        assertFalse(readyDuringPurge.get())
        assertNull(leaked.get())
        assertTrue(controller.renderableMessages(CONV)!!.isEmpty())
        controller.cancel()
    }

    private fun openSession(): LocalStoreSession {
        val opened = when (val result = LocalDatabase.open(context, TEST_DB)) {
            is LocalDatabaseOpenResult.Opened -> result.store
            else -> error("database open failed: $result")
        }
        return (LocalStoreSession.open(keys, opened, AesGcmLocalRecordCipher(), clock, policy) as LocalStoreOpenResult.Ready).session
    }

    private fun shell() = LocalConversationShell(
        conversationId = CONV,
        state = LocalConversationState.ESTABLISHING,
        localAlias = "desk",
        createdAtWallMs = CREATED,
        updatedAtWallMs = CREATED,
    )

    private fun message(expiry: Long) = LocalMessageRecord(
        messageId = "msg-1",
        conversationId = CONV,
        direction = LocalMessageDirection.OUTBOUND,
        state = LocalMessageState.LOCAL_CREATED,
        createdAtWallMs = CREATED,
        authenticatedExpiryWallMs = expiry,
        relayDeadlineWallMs = null,
        body = "hello",
    )

    private fun rawMessageCount(): Int =
        SQLiteDatabase.openDatabase(
            context.getDatabasePath(TEST_DB).path,
            null,
            SQLiteDatabase.OPEN_READONLY,
        ).use { db ->
            db.rawQuery("SELECT COUNT(*) FROM messages", null).use { cursor ->
                cursor.moveToFirst()
                cursor.getInt(0)
            }
        }

    private companion object {
        const val TEST_ALIAS = "veil.test.local-record.v1"
        const val TEST_DB = "veil-test-local.db"
        const val CONV = "conv-1"
        const val CREATED = 1_700_000_000_000L
    }
}
