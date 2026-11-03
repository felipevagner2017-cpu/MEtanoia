package br.com.metanoia.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ReviewPeriod { WEEKLY, MONTHLY }

@Entity(tableName = "review_entries")
data class ReviewEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val period: ReviewPeriod,
    val periodStart: Long,
    val periodEnd: Long,
    val reflection: String,
    val createdAt: Long = System.currentTimeMillis()
)
