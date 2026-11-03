package br.com.metanoia.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class AgendaConverters {
    @TypeConverter fun dimensionToString(value: LifeDimension) = value.name
    @TypeConverter fun stringToDimension(value: String) = LifeDimension.valueOf(value)
    @TypeConverter fun kindToString(value: AgendaKind) = value.name
    @TypeConverter fun stringToKind(value: String) = AgendaKind.valueOf(value)
}

@Database(entities = [AgendaItem::class], version = 1, exportSchema = true)
@TypeConverters(AgendaConverters::class)
abstract class MetanoiaDatabase : RoomDatabase() {
    abstract fun agendaDao(): AgendaDao

    companion object {
        @Volatile private var INSTANCE: MetanoiaDatabase? = null
        fun get(context: Context): MetanoiaDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MetanoiaDatabase::class.java,
                    "metanoia.db"
                ).build().also { INSTANCE = it }
            }
    }
}
