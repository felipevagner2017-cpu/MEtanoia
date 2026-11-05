package br.com.metanoia.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import android.content.Context
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

    @Test
    fun outboxPersistsStableOperationAndRetriesWithoutDuplicate() = runBlocking {
        val operation = SyncOutboxOperation(
            operationId = "op-00000001",
            recordId = "legacy-journal-0001",
            dimension = "MATRIMONIAL",
            recordType = "journal",
            operation = "upsert",
            payloadCiphertext = "v1.encrypted",
            baseRevision = 0,
            clientUpdatedAt = 100L
        )
        database.syncOutboxDao().enqueue(operation)
        assertEquals(1, database.syncOutboxDao().pendingCount())
        assertEquals(operation, database.syncOutboxDao().nextBatch(100L).single())
        database.syncOutboxDao().scheduleRetry(operation.operationId, 500L, "offline")
        assertTrue(database.syncOutboxDao().nextBatch(499L).isEmpty())
        assertEquals(1, database.syncOutboxDao().nextBatch(500L).size)
        database.syncOutboxDao().markApplied(operation.operationId)
        assertEquals(0, database.syncOutboxDao().pendingCount())
    }
}
