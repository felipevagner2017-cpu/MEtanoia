package br.com.metanoia.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_analysis")
data class DailyAnalysisEntry(
    @PrimaryKey val dayKey: String,
    val categories: String,
    val dimensions: String,
    val emotionalState: String,
    val summary: String,
    val attention: String,
    val learning: String,
    val direction: String,
    val nextStep: String,
    val scriptureReference: String,
    val scriptureContext: String,
    val updatedAt: Long = System.currentTimeMillis()
)
