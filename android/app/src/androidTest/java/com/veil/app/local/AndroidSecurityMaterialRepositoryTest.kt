package com.veil.app.local

import androidx.test.platform.app.InstrumentationRegistry
import com.veil.app.security.AndroidLocalProtectionKeyStore
import com.veil.app.security.ExistingKeyResult
import com.veil.app.security.ProtectedBlob
import java.security.GeneralSecurityException
import javax.crypto.SecretKey
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AndroidSecurityMaterialRepositoryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val keys = AndroidLocalProtectionKeyStore(TEST_ALIAS)
    private lateinit var key: SecretKey

    @Before fun setUp() {
        LocalDatabase.delete(context, TEST_DB)
        keys.deleteKey()
        key = (keys.provisioningKey() as ExistingKeyResult.Available).key
    }

    @After fun tearDown() {
        LocalDatabase.delete(context, TEST_DB)
        keys.deleteKey()
    }

    @Test fun rollbackRestoresAllPersistedRowsAfterMultipleMutations() = withRepository { repository ->
        seed(repository)
        expectFailure { repository.transaction { put(A, SLOT, newA); delete(B, SLOT); put(C, SLOT, newC); error("body failure") } }
        assertState(repository, oldA, oldB, oldC)
    }

    @Test fun exceptionAfterFirstPutRollsBackPersistedRow() = withRepository { repository ->
        repository.put(A, SLOT, oldA)
        expectFailure { repository.transaction { put(A, SLOT, newA); error("after first put") } }
        assertState(repository, oldA, null, null)
    }

    @Test fun exceptionAfterReplacementRestoresExactPersistedValue() = withRepository { repository ->
        repository.put(A, SLOT, oldA); repository.put(B, SLOT, oldB)
        expectFailure { repository.transaction { put(A, SLOT, newA); put(B, SLOT, newB); error("after replacement") } }
        assertState(repository, oldA, oldB, null)
    }

    @Test fun exceptionAfterDeleteRestoresDeletedPersistedRow() = withRepository { repository ->
        seed(repository)
        expectFailure { repository.transaction { delete(B, SLOT); error("after delete") } }
        assertState(repository, oldA, oldB, oldC)
    }

    @Test fun twoPutsCommit() = withRepository { repository ->
        repository.transaction { put(A, SLOT, newA); put(B, SLOT, newB) }
        assertState(repository, newA, newB, null)
    }

    @Test fun threePutsCommit() = withRepository { repository ->
        repository.transaction { put(A, SLOT, newA); put(B, SLOT, newB); put(C, SLOT, newC) }
        assertState(repository, newA, newB, newC)
    }

    @Test fun putAndDeleteCommit() = withRepository { repository ->
        repository.put(A, SLOT, oldA); repository.put(B, SLOT, oldB)
        repository.transaction { put(C, SLOT, newC); delete(B, SLOT) }
        assertState(repository, oldA, null, newC)
    }

    @Test fun replaceAndDeleteCommit() = withRepository { repository ->
        seed(repository)
        repository.transaction { put(A, SLOT, newA); delete(B, SLOT) }
        assertState(repository, newA, null, oldC)
    }

    @Test fun transactionGetReadsOwnCommittedWrite() = withRepository { repository ->
        repository.put(A, SLOT, oldA)
        repository.transaction { put(A, SLOT, newA); assertArrayEquals(newA, get(A, SLOT)) }
        assertArrayEquals(newA, repository.get(A, SLOT))
    }

    @Test fun transactionGetReadsOwnWriteThenRollbackRestoresOldValue() = withRepository { repository ->
        repository.put(A, SLOT, oldA)
        expectFailure { repository.transaction { put(A, SLOT, newA); assertArrayEquals(newA, get(A, SLOT)); error("rollback") } }
        assertArrayEquals(oldA, repository.get(A, SLOT))
    }

    @Test fun encryptionFailureAfterEarlierSqlWriteRollsBackAndPropagates() {
        withRepository { it.put(A, SLOT, oldA) }
        var calls = 0
        val failingCipher = object : LocalRecordCipher {
            private val inner = AesGcmLocalRecordCipher()
            override fun encrypt(key: SecretKey, plaintext: ByteArray, aad: ByteArray): ProtectedBlob {
                calls += 1
                if (calls == 2) throw GeneralSecurityException("forced encryption failure")
                return inner.encrypt(key, plaintext, aad)
            }
            override fun decrypt(key: SecretKey, blob: ProtectedBlob, aad: ByteArray): ByteArray = inner.decrypt(key, blob, aad)
        }
        withRepository(cipher = failingCipher) { repository ->
            expectFailure { repository.transaction { put(A, SLOT, newA); put(B, SLOT, newB) } }
            assertTrue(calls == 2)
            assertState(repository, oldA, null, null)
        }
    }

    @Test fun persistenceFailureAfterEarlierSqlWriteRollsBackAndPropagates() {
        withRepository { seed(it) }
        var writes = 0
        withRepository(afterSecurityRecordWriteForTesting = { if (++writes == 2) error("forced persistence failure") }) { repository ->
            expectFailure { repository.transaction { put(A, SLOT, newA); put(B, SLOT, newB) } }
            assertTrue(writes == 2)
            assertState(repository, oldA, oldB, oldC)
        }
    }

    @Test fun standaloneReplacePreservesUnrelatedOwnerSlotRow() = withRepository { repository ->
        repository.put(A, SLOT, oldA); repository.put(A, OTHER_SLOT, oldB)
        assertTrue(repository.put(A, SLOT, newA))
        assertArrayEquals(newA, repository.get(A, SLOT))
        assertArrayEquals(oldB, repository.get(A, OTHER_SLOT))
    }

    @Test fun replacementRollbackRestoresOldValueWithoutChangingUnrelatedRow() = withRepository { repository ->
        repository.put(A, SLOT, oldA); repository.put(A, OTHER_SLOT, oldB)
        expectFailure { repository.transaction { put(A, SLOT, newA); error("rollback replacement") } }
        assertArrayEquals(oldA, repository.get(A, SLOT))
        assertArrayEquals(oldB, repository.get(A, OTHER_SLOT))
    }

    @Test fun nestedTransactionRejectsAndRollsBackOuterPersistedMutation() = withRepository { repository ->
        repository.put(A, SLOT, oldA)
        expectFailure { repository.transaction { put(A, SLOT, newA); repository.transaction { put(B, SLOT, newB) } } }
        assertState(repository, oldA, null, null)
    }

    private fun seed(repository: SecurityMaterialRepository) {
        assertTrue(repository.put(A, SLOT, oldA)); assertTrue(repository.put(B, SLOT, oldB)); assertTrue(repository.put(C, SLOT, oldC))
    }

    private fun assertState(repository: SecurityMaterialRepository, a: ByteArray?, b: ByteArray?, c: ByteArray?) {
        assertPayload(a, repository.get(A, SLOT)); assertPayload(b, repository.get(B, SLOT)); assertPayload(c, repository.get(C, SLOT))
    }

    private fun assertPayload(expected: ByteArray?, actual: ByteArray?) {
        if (expected == null) assertNull(actual) else assertArrayEquals(expected, actual)
    }

    private fun expectFailure(block: () -> Unit) {
        try { block(); throw AssertionError("expected transaction failure") } catch (_: IllegalStateException) {}
    }

    private fun withRepository(
        cipher: LocalRecordCipher = AesGcmLocalRecordCipher(),
        afterSecurityRecordWriteForTesting: (() -> Unit)? = null,
        block: (SecurityMaterialRepository) -> Unit,
    ) {
        val store = SqliteLocalRecordStore(LocalDatabaseHelper(context, TEST_DB), afterSecurityRecordWriteForTesting)
        try { block(SecurityMaterialRepository(key, cipher, store)) } finally { store.close() }
    }

    private companion object {
        const val TEST_DB = "veil-test-security-material.db"
        const val TEST_ALIAS = "veil.test.security-material.v1"
        const val A = "owner-a"
        const val B = "owner-b"
        const val C = "owner-c"
        const val SLOT = "slot"
        const val OTHER_SLOT = "other-slot"
        val oldA = byteArrayOf(1)
        val oldB = byteArrayOf(2)
        val oldC = byteArrayOf(3)
        val newA = byteArrayOf(11)
        val newB = byteArrayOf(12)
        val newC = byteArrayOf(13)
    }
}
