package br.com.metanoia.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ReviewDao {
    @Query("SELECT * FROM review_entries ORDER BY periodStart DESC")
    fun observeHistory(): Flow<List<ReviewEntry>>

    @Insert
    suspend fun insert(review: ReviewEntry): Long

    @Update
    suspend fun update(review: ReviewEntry)

    @Delete
    suspend fun delete(review: ReviewEntry)
}
