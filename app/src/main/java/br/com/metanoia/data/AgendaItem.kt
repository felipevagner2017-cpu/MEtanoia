package br.com.metanoia.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class LifeDimension { SPIRITUAL, MATRIMONIAL, PROFESSIONAL, INDIVIDUAL, ROUTINE }
enum class AgendaKind { TASK, APPOINTMENT, HABIT }

@Entity(tableName = "agenda_items", indices = [Index(value = ["seriesId"])])
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
    @ColumnInfo(defaultValue = "''") val location: String = "",
    val endAt: Long? = null,
    @ColumnInfo(defaultValue = "'120,60,30'") val reminderOffsets: String = "120,60,30",
    val seriesId: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
