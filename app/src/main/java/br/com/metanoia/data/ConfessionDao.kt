package br.com.metanoia.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ConfessionDao {
    @Query("SELECT * FROM confession_entries ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ConfessionEntry>>

    @Query("SELECT * FROM confession_entries WHERE dayKey = :dayKey ORDER BY updatedAt DESC LIMIT 1")
    suspend fun findForDay(dayKey: String): ConfessionEntry?

    @Query("DELETE FROM confession_entries WHERE dayKey = :dayKey")
    suspend fun deleteForDay(dayKey: String)

    @Insert
    suspend fun insert(entry: ConfessionEntry): Long

    @Update
    suspend fun update(entry: ConfessionEntry)
}
