package br.com.metanoia.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyAnalysisDao {
    @Query("SELECT * FROM daily_analysis ORDER BY dayKey DESC")
    fun observeAll(): Flow<List<DailyAnalysisEntry>>

    @Query("SELECT * FROM daily_analysis WHERE dayKey = :dayKey LIMIT 1")
    suspend fun findForDay(dayKey: String): DailyAnalysisEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(entry: DailyAnalysisEntry)
}
