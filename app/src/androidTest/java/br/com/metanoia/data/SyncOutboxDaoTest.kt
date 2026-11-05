package br.com.metanoia.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SyncOutboxDaoTest {
    private lateinit var database: MetanoiaDatabase

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            MetanoiaDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    private fun operation(account: String, id: String = "op-00000001") = SyncOutboxOperation(
        accountId = account,
        operationId = id,
        recordId = "journal-0001",
        dimension = "MATRIMONIAL",
        recordType = "journal",
        operation = "upsert",
        payloadCiphertext = "v1.encrypted",
        baseRevision = 0,
        clientUpdatedAt = 100L
    )

    @Test
    fun outboxPersistsStableOperationAndRetriesWithoutDuplicate() = runBlocking {
        val dao = database.syncOutboxDao()
        val operation = operation("account-A")
        dao.enqueue(operation)
        assertEquals(1, dao.pendingCount("account-A"))
        assertEquals(0, dao.pendingCount("account-B"))
        assertEquals(operation, dao.nextBatch("account-A", 100L).single())
        dao.scheduleRetry("account-A", operation.operationId, 500L, "offline")
        assertTrue(dao.nextBatch("account-A", 499L).isEmpty())
        assertEquals(1, dao.nextBatch("account-A", 500L).size)
        assertEquals(1, dao.markApplied("account-A", operation.operationId))
        assertEquals(0, dao.pendingCount("account-A"))
    }

    @Test
    fun sameOperationIdIsIsolatedByAccountAndAccountSwitchCannotLeakBatch() = runBlocking {
        val dao = database.syncOutboxDao()
        dao.enqueue(operation("account-A"))
        dao.enqueue(operation("account-B"))
        assertEquals(1, dao.nextBatch("account-A", 100L).size)
        assertEquals(1, dao.nextBatch("account-B", 100L).size)
        assertEquals("account-A", dao.nextBatch("account-A", 100L).single().accountId)
        assertEquals(1, dao.markApplied("account-B", "op-00000001"))
        // Updating B must not change A's pending operation.
        assertEquals(1, dao.pendingCount("account-A"))
        assertEquals(1, dao.pendingCount("account-A"))
        assertEquals(0, dao.delete("account-B", "does-not-exist"))
        assertEquals(1, dao.delete("account-B", "op-00000001"))
        assertEquals(1, dao.pendingCount("account-A"))
        assertEquals(0, dao.pendingCount("account-B"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankAccountCannotReadPendingOperations() = runBlocking {
        database.syncOutboxDao().nextBatch("", 100L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankAccountCannotEnqueueOperation() = runBlocking {
        database.syncOutboxDao().enqueue(operation(""))
    }
}
