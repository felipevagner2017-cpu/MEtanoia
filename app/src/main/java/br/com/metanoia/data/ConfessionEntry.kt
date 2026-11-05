package br.com.metanoia.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "confession_entries")
data class ConfessionEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayKey: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
