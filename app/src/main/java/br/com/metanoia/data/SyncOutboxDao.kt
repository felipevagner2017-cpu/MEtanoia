package br.com.metanoia.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Every mutating or reading operation is scoped to a non-empty authenticated account.
 * Legacy rows are intentionally invisible because their owner cannot be proven.
 */
@Dao
abstract class SyncOutboxDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertScoped(operation: SyncOutboxOperation)

    open suspend fun enqueue(operation: SyncOutboxOperation) {
        require(operation.accountId.isNotBlank()) { "A operação precisa estar vinculada a uma conta autenticada." }
        require(operation.status != "quarantined") { "Operações em quarentena não podem ser reenfileiradas." }
        insertScoped(operation)
    }

    @Query("""
        SELECT * FROM sync_outbox
        WHERE accountId = :accountId AND accountId != ''
          AND status IN ('pending','retry') AND nextAttemptAt <= :now
        ORDER BY createdAt ASC, operationId ASC LIMIT :limit
    """)
    protected abstract suspend fun selectNextBatch(accountId: String, now: Long, limit: Int): List<SyncOutboxOperation>

    open suspend fun nextBatch(accountId: String, now: Long, limit: Int = 50): List<SyncOutboxOperation> {
        require(accountId.isNotBlank()) { "Conta autenticada inválida." }
        require(limit in 1..500)
        return selectNextBatch(accountId, now, limit)
    }

    @Query("""
        UPDATE sync_outbox SET status='applied', lastError=NULL
        WHERE accountId=:accountId AND accountId!='' AND operationId=:operationId
          AND status IN ('pending','retry')
    """)
    protected abstract suspend fun updateApplied(accountId: String, operationId: String): Int

    open suspend fun markApplied(accountId: String, operationId: String): Int {
        require(accountId.isNotBlank())
        return updateApplied(accountId, operationId)
    }

    @Query("""
        UPDATE sync_outbox SET status='conflict', lastError=:reason
        WHERE accountId=:accountId AND accountId!='' AND operationId=:operationId
          AND status IN ('pending','retry','conflict')
    """)
    protected abstract suspend fun updateConflict(accountId: String, operationId: String, reason: String): Int

    open suspend fun markConflict(accountId: String, operationId: String, reason: String): Int {
        require(accountId.isNotBlank())
        return updateConflict(accountId, operationId, reason)
    }

    @Query("""
        UPDATE sync_outbox
        SET status='retry', attemptCount=attemptCount+1, nextAttemptAt=:nextAttemptAt, lastError=:reason
        WHERE accountId=:accountId AND accountId!='' AND operationId=:operationId
          AND status IN ('pending','retry')
    """)
    protected abstract suspend fun updateRetry(
        accountId: String, operationId: String, nextAttemptAt: Long, reason: String
    ): Int

    open suspend fun scheduleRetry(accountId: String, operationId: String, nextAttemptAt: Long, reason: String): Int {
        require(accountId.isNotBlank())
        return updateRetry(accountId, operationId, nextAttemptAt, reason)
    }

    @Query("SELECT COUNT(*) FROM sync_outbox WHERE accountId=:accountId AND accountId!='' AND status IN ('pending','retry')")
    protected abstract suspend fun countPending(accountId: String): Int

    open suspend fun pendingCount(accountId: String): Int {
        require(accountId.isNotBlank())
        return countPending(accountId)
    }

    @Query("""
        SELECT * FROM sync_outbox
        WHERE accountId=:accountId AND accountId!='' AND recordId=:recordId
        ORDER BY createdAt DESC, operationId DESC
    """)
    protected abstract suspend fun selectHistory(accountId: String, recordId: String): List<SyncOutboxOperation>

    open suspend fun historyForRecord(accountId: String, recordId: String): List<SyncOutboxOperation> {
        require(accountId.isNotBlank())
        return selectHistory(accountId, recordId)
    }

    @Query("DELETE FROM sync_outbox WHERE accountId=:accountId AND accountId!='' AND operationId=:operationId")
    protected abstract suspend fun deleteScoped(accountId: String, operationId: String): Int

    open suspend fun delete(accountId: String, operationId: String): Int {
        require(accountId.isNotBlank())
        return deleteScoped(accountId, operationId)
    }
}
