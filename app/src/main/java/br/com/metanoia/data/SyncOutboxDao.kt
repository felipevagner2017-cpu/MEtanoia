package br.com.metanoia.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface SyncOutboxDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun enqueue(operation: SyncOutboxOperation)

    @Query("SELECT * FROM sync_outbox WHERE status IN ('pending','retry') AND nextAttemptAt <= :now ORDER BY createdAt ASC LIMIT :limit")
    suspend fun nextBatch(now: Long, limit: Int = 50): List<SyncOutboxOperation>

    @Query("UPDATE sync_outbox SET status='applied', lastError=NULL WHERE operationId=:operationId")
    suspend fun markApplied(operationId: String)

    @Query("UPDATE sync_outbox SET status='conflict', lastError=:reason WHERE operationId=:operationId")
    suspend fun markConflict(operationId: String, reason: String)

    @Query("UPDATE sync_outbox SET status='retry', attemptCount=attemptCount+1, nextAttemptAt=:nextAttemptAt, lastError=:reason WHERE operationId=:operationId")
    suspend fun scheduleRetry(operationId: String, nextAttemptAt: Long, reason: String)

    @Query("SELECT COUNT(*) FROM sync_outbox WHERE status IN ('pending','retry')")
    suspend fun pendingCount(): Int

    @Query("SELECT * FROM sync_outbox WHERE recordId=:recordId ORDER BY createdAt DESC")
    suspend fun historyForRecord(recordId: String): List<SyncOutboxOperation>
}
