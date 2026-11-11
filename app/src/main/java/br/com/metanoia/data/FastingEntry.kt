package br.com.metanoia.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A fasting practice is recorded as a voluntary spiritual/reflection plan.
 * Food abstinence is opt-in and defaults to false; non-food alternatives are the default.
 */
@Entity(
    tableName = "fasting_plans",
    indices = [Index(value = ["dimension", "createdAt"]), Index(value = ["status"])]
)
data class FastingPlan(
    @PrimaryKey val id: String,
    val dimension: LifeDimension,
    val type: String,
    val identifiedNeed: String,
    val recommendationReason: String,
    val purpose: String,
    val scriptureReferences: String,
    val scriptureContext: String,
    val preparation: String,
    val dailyGuidance: String,
    val durationDays: Int,
    val startAt: Long,
    val endAt: Long,
    val foodAbstinence: Boolean = false,
    val status: String = "PROPOSED",
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)

@Entity(
    tableName = "fasting_daily_entries",
    indices = [Index(value = ["planId", "dayIndex"], unique = true)]
)
data class FastingDailyEntry(
    @PrimaryKey val id: String,
    val planId: String,
    val dayIndex: Int,
    val reflection: String,
    val experience: String,
    val recordedAt: Long = System.currentTimeMillis()
)
