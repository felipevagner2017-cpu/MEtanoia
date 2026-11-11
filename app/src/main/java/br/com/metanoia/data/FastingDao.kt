package br.com.metanoia.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FastingDao {
    @Query("SELECT * FROM fasting_plans ORDER BY createdAt DESC")
    fun observePlans(): Flow<List<FastingPlan>>

    @Query("SELECT * FROM fasting_plans WHERE dimension = :dimension ORDER BY createdAt DESC")
    fun observeByDimension(dimension: LifeDimension): Flow<List<FastingPlan>>

    @Query("SELECT * FROM fasting_plans WHERE id = :id LIMIT 1")
    suspend fun findPlan(id: String): FastingPlan?

    @Query("SELECT * FROM fasting_plans ORDER BY createdAt DESC LIMIT :limit")
    suspend fun recentPlans(limit: Int = 30): List<FastingPlan>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPlan(plan: FastingPlan)

    @Update
    suspend fun updatePlan(plan: FastingPlan)

    @Query("SELECT * FROM fasting_daily_entries WHERE planId = :planId ORDER BY dayIndex ASC")
    fun observeEntries(planId: String): Flow<List<FastingDailyEntry>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDailyEntry(entry: FastingDailyEntry)

    @Query("SELECT * FROM fasting_daily_entries WHERE planId = :planId AND dayIndex = :dayIndex LIMIT 1")
    suspend fun findDailyEntry(planId: String, dayIndex: Int): FastingDailyEntry?
}
