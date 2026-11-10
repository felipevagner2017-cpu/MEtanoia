package br.com.metanoia.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AgendaDao {
    @Query("SELECT * FROM agenda_items ORDER BY scheduledAt ASC")
    fun observeAll(): Flow<List<AgendaItem>>

    @Query("SELECT * FROM agenda_items WHERE scheduledAt BETWEEN :start AND :end ORDER BY scheduledAt ASC")
    fun observeRange(start: Long, end: Long): Flow<List<AgendaItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: AgendaItem): Long

    @Update
    suspend fun update(item: AgendaItem)

    @Update
    suspend fun updateAll(items: List<AgendaItem>)

    @Query("SELECT * FROM agenda_items WHERE seriesId = :seriesId ORDER BY scheduledAt ASC")
    suspend fun findSeries(seriesId: String): List<AgendaItem>

    @Query("DELETE FROM agenda_items WHERE seriesId = :seriesId")
    suspend fun deleteSeries(seriesId: String): Int

    @Delete
    suspend fun delete(item: AgendaItem)

    @Query("UPDATE agenda_items SET completed = :completed WHERE id = :id")
    suspend fun setCompleted(id: Long, completed: Boolean)

    @Query("SELECT * FROM agenda_items WHERE reminderAt IS NOT NULL AND scheduledAt > :now AND completed = 0")
    suspend fun pendingReminders(now: Long): List<AgendaItem>

    @Query("SELECT * FROM agenda_items WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): AgendaItem?
}
