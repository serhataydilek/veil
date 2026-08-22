package com.veil.app.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper

internal const val LOCAL_DATABASE_NAME = "veil-local.db"
internal const val LOCAL_SCHEMA_VERSION = 2

internal sealed interface LocalDatabaseOpenResult {
    data class Opened(val store: SqliteLocalRecordStore) : LocalDatabaseOpenResult
    data object Incompatible : LocalDatabaseOpenResult
    data class Unreadable(val reason: String? = null) : LocalDatabaseOpenResult
}

internal class LocalDatabaseHelper(
    context: Context,
    name: String = LOCAL_DATABASE_NAME,
) : SQLiteOpenHelper(context, name, null, LOCAL_SCHEMA_VERSION) {
    init {
        setWriteAheadLoggingEnabled(false)
    }

    override fun onConfigure(db: SQLiteDatabase) {
        setWriteAheadLoggingEnabled(false)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE local_meta (
              meta_key TEXT PRIMARY KEY NOT NULL,
              ciphertext BLOB NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE conversations (
              conversation_id TEXT PRIMARY KEY NOT NULL,
              ciphertext BLOB NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE messages (
              message_id TEXT PRIMARY KEY NOT NULL,
              conversation_id TEXT NOT NULL,
              expiry_hint_ms INTEGER NOT NULL,
              ciphertext BLOB NOT NULL,
              FOREIGN KEY (conversation_id) REFERENCES conversations(conversation_id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_messages_expiry_hint ON messages(expiry_hint_ms)")
        db.execSQL("CREATE INDEX idx_messages_conversation ON messages(conversation_id)")
        createSecurityRecords(db)
    }

    override fun onOpen(db: SQLiteDatabase) {
        db.rawQuery("PRAGMA secure_delete = ON", null).use { cursor ->
            cursor.moveToFirst()
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion == 1 && newVersion == 2) {
            db.beginTransaction()
            try { createSecurityRecords(db); db.setTransactionSuccessful() } finally { db.endTransaction() }
        } else throw IncompatibleLocalSchemaException()
    }

    private fun createSecurityRecords(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE security_records (owner_id TEXT NOT NULL, slot_id TEXT NOT NULL, ciphertext BLOB NOT NULL, PRIMARY KEY(owner_id, slot_id))")
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        throw IncompatibleLocalSchemaException()
    }
}

internal object LocalDatabase {
    fun open(context: Context, name: String = LOCAL_DATABASE_NAME): LocalDatabaseOpenResult {
        val helper = LocalDatabaseHelper(context.applicationContext, name)
        return try {
            val db = helper.writableDatabase
            if (db.version != LOCAL_SCHEMA_VERSION) {
                helper.close()
                LocalDatabaseOpenResult.Incompatible
            } else {
                LocalDatabaseOpenResult.Opened(SqliteLocalRecordStore(helper))
            }
        } catch (error: IncompatibleLocalSchemaException) {
            helper.close()
            LocalDatabaseOpenResult.Incompatible
        } catch (error: SQLiteException) {
            val incompatible = generateSequence(error as Throwable) { it.cause }
                .any { it is IncompatibleLocalSchemaException }
            helper.close()
            if (incompatible) {
                LocalDatabaseOpenResult.Incompatible
            } else {
                LocalDatabaseOpenResult.Unreadable(error.javaClass.simpleName)
            }
        }
    }

    fun delete(context: Context, name: String = LOCAL_DATABASE_NAME): Boolean =
        context.applicationContext.deleteDatabase(name)
}

internal class SqliteLocalRecordStore(
    private val helper: LocalDatabaseHelper,
    private val afterSecurityRecordWriteForTesting: (() -> Unit)? = null,
) : LocalRecordStore {
    private val db: SQLiteDatabase = helper.writableDatabase

    override fun <T> transact(block: () -> T): T {
        db.beginTransaction()
        try {
            val result = block()
            db.setTransactionSuccessful()
            return result
        } finally {
            db.endTransaction()
        }
    }

    override fun insertConversation(row: StoredConversationRow) {
        db.insertOrThrow(TABLE_CONVERSATIONS, null, conversationValues(row))
    }

    override fun updateConversation(row: StoredConversationRow) {
        val updated = db.update(
            TABLE_CONVERSATIONS,
            conversationValues(row),
            "$COL_CONVERSATION_ID = ?",
            arrayOf(row.conversationId),
        )
        if (updated != 1) error("conversation missing")
    }

    override fun loadConversation(conversationId: String): StoredConversationRow? =
        db.query(
            TABLE_CONVERSATIONS,
            arrayOf(COL_CONVERSATION_ID, COL_CIPHERTEXT),
            "$COL_CONVERSATION_ID = ?",
            arrayOf(conversationId),
            null,
            null,
            null,
        ).use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            StoredConversationRow(
                conversationId = cursor.getString(0),
                ciphertext = cursor.getBlob(1),
            )
        }

    override fun listConversations(): List<StoredConversationRow> =
        db.query(
            TABLE_CONVERSATIONS,
            arrayOf(COL_CONVERSATION_ID, COL_CIPHERTEXT),
            null,
            null,
            null,
            null,
            COL_CONVERSATION_ID,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        StoredConversationRow(
                            conversationId = cursor.getString(0),
                            ciphertext = cursor.getBlob(1),
                        ),
                    )
                }
            }
        }

    override fun deleteConversation(conversationId: String) {
        db.delete(TABLE_CONVERSATIONS, "$COL_CONVERSATION_ID = ?", arrayOf(conversationId))
    }

    override fun deleteMessagesForConversation(conversationId: String) {
        db.delete(TABLE_MESSAGES, "$COL_CONVERSATION_ID = ?", arrayOf(conversationId))
    }

    override fun insertMessage(row: StoredMessageRow) {
        db.insertOrThrow(TABLE_MESSAGES, null, messageValues(row))
    }

    override fun updateMessage(row: StoredMessageRow) {
        val updated = db.update(
            TABLE_MESSAGES,
            messageValues(row),
            "$COL_MESSAGE_ID = ?",
            arrayOf(row.messageId),
        )
        if (updated != 1) error("message missing")
    }

    override fun loadMessage(messageId: String): StoredMessageRow? =
        db.query(
            TABLE_MESSAGES,
            arrayOf(COL_MESSAGE_ID, COL_CONVERSATION_ID, COL_EXPIRY_HINT, COL_CIPHERTEXT),
            "$COL_MESSAGE_ID = ?",
            arrayOf(messageId),
            null,
            null,
            null,
        ).use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            StoredMessageRow(
                messageId = cursor.getString(0),
                conversationId = cursor.getString(1),
                expiryHintMs = cursor.getLong(2),
                ciphertext = cursor.getBlob(3),
            )
        }

    override fun listMessagesForConversation(conversationId: String): List<StoredMessageRow> =
        db.query(
            TABLE_MESSAGES,
            arrayOf(COL_MESSAGE_ID, COL_CONVERSATION_ID, COL_EXPIRY_HINT, COL_CIPHERTEXT),
            "$COL_CONVERSATION_ID = ?",
            arrayOf(conversationId),
            null,
            null,
            COL_MESSAGE_ID,
        ).use { cursor -> readMessages(cursor) }

    override fun listAllMessages(): List<StoredMessageRow> =
        db.query(
            TABLE_MESSAGES,
            arrayOf(COL_MESSAGE_ID, COL_CONVERSATION_ID, COL_EXPIRY_HINT, COL_CIPHERTEXT),
            null,
            null,
            null,
            null,
            COL_MESSAGE_ID,
        ).use { cursor -> readMessages(cursor) }

    override fun deleteMessage(messageId: String) {
        db.delete(TABLE_MESSAGES, "$COL_MESSAGE_ID = ?", arrayOf(messageId))
    }

    override fun deleteMessagesWithExpiryHintAtOrBefore(hintMs: Long): Int =
        db.delete(TABLE_MESSAGES, "$COL_EXPIRY_HINT <= ?", arrayOf(hintMs.toString()))

    override fun deleteAllMessages(): Int = db.delete(TABLE_MESSAGES, null, null)

    override fun messageCount(): Int =
        db.rawQuery("SELECT COUNT(*) FROM $TABLE_MESSAGES", null).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }

    override fun loadMeta(metaKey: String): ByteArray? =
        db.query(
            TABLE_META,
            arrayOf(COL_CIPHERTEXT),
            "$COL_META_KEY = ?",
            arrayOf(metaKey),
            null,
            null,
            null,
        ).use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            cursor.getBlob(0)
        }

    override fun upsertMeta(metaKey: String, ciphertext: ByteArray): Boolean {
        return try {
            val values = ContentValues().apply {
                put(COL_META_KEY, metaKey)
                put(COL_CIPHERTEXT, ciphertext)
            }
            db.insertWithOnConflict(TABLE_META, null, values, SQLiteDatabase.CONFLICT_REPLACE) != -1L
        } catch (_: SQLiteException) {
            false
        }
    }

    override fun deleteMeta(metaKey: String) {
        db.delete(TABLE_META, "$COL_META_KEY = ?", arrayOf(metaKey))
    }

    override fun close() {
        helper.close()
    }

    fun loadSecurityRecord(ownerId: String, slotId: String): ByteArray? = db.query("security_records", arrayOf("ciphertext"), "owner_id = ? AND slot_id = ?", arrayOf(ownerId, slotId), null, null, null).use { if (it.moveToFirst()) it.getBlob(0) else null }
    fun upsertSecurityRecord(ownerId: String, slotId: String, ciphertext: ByteArray) {
        val values = ContentValues().apply { put("owner_id", ownerId); put("slot_id", slotId); put("ciphertext", ciphertext) }
        if (db.insertWithOnConflict("security_records", null, values, SQLiteDatabase.CONFLICT_REPLACE) == -1L) error("security record persist failed")
        afterSecurityRecordWriteForTesting?.invoke()
    }
    fun deleteSecurityRecord(ownerId: String, slotId: String) { db.delete("security_records", "owner_id = ? AND slot_id = ?", arrayOf(ownerId, slotId)) }

    internal fun pragmaValue(pragma: String): String =
        db.rawQuery("PRAGMA $pragma", null).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getString(0)
        }

    private fun conversationValues(row: StoredConversationRow) = ContentValues().apply {
        put(COL_CONVERSATION_ID, row.conversationId)
        put(COL_CIPHERTEXT, row.ciphertext)
    }

    private fun messageValues(row: StoredMessageRow) = ContentValues().apply {
        put(COL_MESSAGE_ID, row.messageId)
        put(COL_CONVERSATION_ID, row.conversationId)
        put(COL_EXPIRY_HINT, row.expiryHintMs)
        put(COL_CIPHERTEXT, row.ciphertext)
    }

    private fun readMessages(cursor: android.database.Cursor): List<StoredMessageRow> = buildList {
        while (cursor.moveToNext()) {
            add(
                StoredMessageRow(
                    messageId = cursor.getString(0),
                    conversationId = cursor.getString(1),
                    expiryHintMs = cursor.getLong(2),
                    ciphertext = cursor.getBlob(3),
                ),
            )
        }
    }

    private companion object {
        const val TABLE_META = "local_meta"
        const val TABLE_CONVERSATIONS = "conversations"
        const val TABLE_MESSAGES = "messages"
        const val COL_META_KEY = "meta_key"
        const val COL_CONVERSATION_ID = "conversation_id"
        const val COL_MESSAGE_ID = "message_id"
        const val COL_EXPIRY_HINT = "expiry_hint_ms"
        const val COL_CIPHERTEXT = "ciphertext"
    }
}
