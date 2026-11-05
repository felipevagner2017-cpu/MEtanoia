package br.com.metanoia.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.PrimaryKey
import androidx.room.Transaction

@Entity(
    tableName = "sync_nonce_counters",
    primaryKeys = ["accountId", "deviceId", "purpose"]
)
data class SyncNonceCounterEntity(
    val accountId: String,
    val deviceId: String,
    val purpose: String,
    val nextCounter: Long = 0L
)

data class NonceCounterReservation(
    val startInclusive: Long,
    val endExclusive: Long
) {
    init { require(startInclusive >= 0L && endExclusive > startInclusive) }
    val size: Long get() = endExclusive - startInclusive
}

/**
 * Durable, transactional nonce counter. A reserved value is consumed even if
 * encryption or the caller later fails; gaps are safe, reuse is not.
 */
@Dao
abstract class SyncNonceCounterDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertIfMissing(counter: SyncNonceCounterEntity)

    @Query("""
        SELECT nextCounter FROM sync_nonce_counters
        WHERE accountId = :accountId AND deviceId = :deviceId AND purpose = :purpose
        LIMIT 1
    """)
    protected abstract suspend fun readNext(accountId: String, deviceId: String, purpose: String): Long?

    @Query("""
        UPDATE sync_nonce_counters SET nextCounter = :next
        WHERE accountId = :accountId AND deviceId = :deviceId AND purpose = :purpose
          AND nextCounter = :expected
    """)
    protected abstract suspend fun advance(
        accountId: String,
        deviceId: String,
        purpose: String,
        expected: Long,
        next: Long
    ): Int

    @Transaction
    open suspend fun reserveBlock(
        accountId: String,
        deviceId: String,
        purpose: String,
        blockSize: Int = 1
    ): NonceCounterReservation {
        require(accountId.isNotBlank() && deviceId.isNotBlank() && purpose.isNotBlank())
        require(blockSize in 1..65_536)
        insertIfMissing(SyncNonceCounterEntity(accountId, deviceId, purpose))
        val start = checkNotNull(readNext(accountId, deviceId, purpose)) {
            "Contador de nonce não pôde ser inicializado."
        }
        val end = Math.addExact(start, blockSize.toLong())
        check(advance(accountId, deviceId, purpose, start, end) == 1) {
            "Reserva concorrente do contador de nonce; tente novamente."
        }
        return NonceCounterReservation(start, end)
    }

    @Query("""
        SELECT nextCounter FROM sync_nonce_counters
        WHERE accountId = :accountId AND deviceId = :deviceId AND purpose = :purpose
        LIMIT 1
    """)
    abstract suspend fun currentNextCounter(accountId: String, deviceId: String, purpose: String): Long?
}
