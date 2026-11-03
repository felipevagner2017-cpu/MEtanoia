package br.com.metanoia.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class LifeDimension { SPIRITUAL, MATRIMONIAL, PROFESSIONAL, INDIVIDUAL, ROUTINE }
enum class AgendaKind { TASK, APPOINTMENT, HABIT }

@Entity(tableName = "agenda_items")
data class AgendaItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val dimension: LifeDimension = LifeDimension.ROUTINE,
    val kind: AgendaKind = AgendaKind.TASK,
    val scheduledAt: Long,
    val completed: Boolean = false,
    val recurrenceRule: String? = null,
    val reminderAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)
