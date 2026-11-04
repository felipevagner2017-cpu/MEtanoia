package br.com.metanoia.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class AgendaConverters {
    @TypeConverter fun dimensionToString(value: LifeDimension) = value.name
    @TypeConverter fun stringToDimension(value: String) = LifeDimension.valueOf(value)
    @TypeConverter fun kindToString(value: AgendaKind) = value.name
    @TypeConverter fun stringToKind(value: String) = AgendaKind.valueOf(value)
    @TypeConverter fun reviewPeriodToString(value: ReviewPeriod) = value.name
    @TypeConverter fun stringToReviewPeriod(value: String) = ReviewPeriod.valueOf(value)
}

@Database(
    entities = [
        AgendaItem::class, JournalEntry::class, ReviewEntry::class,
        ConfessionEntry::class, DailyAnalysisEntry::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(AgendaConverters::class)
abstract class MetanoiaDatabase : RoomDatabase() {
    abstract fun agendaDao(): AgendaDao
    abstract fun journalDao(): JournalDao
    abstract fun reviewDao(): ReviewDao
    abstract fun confessionDao(): ConfessionDao
    abstract fun dailyAnalysisDao(): DailyAnalysisDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS journal_entries (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        content TEXT NOT NULL,
                        dimension TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )"""
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS review_entries (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        period TEXT NOT NULL,
                        periodStart INTEGER NOT NULL,
                        periodEnd INTEGER NOT NULL,
                        reflection TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )"""
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE journal_entries ADD COLUMN dayKey TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    """UPDATE journal_entries
                       SET dayKey = strftime('%Y-%m-%d', createdAt / 1000, 'unixepoch', 'localtime')
                       WHERE dayKey = ''"""
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS confession_entries (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        dayKey TEXT NOT NULL,
                        content TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )"""
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS daily_analysis (
                        dayKey TEXT NOT NULL PRIMARY KEY,
                        categories TEXT NOT NULL,
                        dimensions TEXT NOT NULL,
                        emotionalState TEXT NOT NULL,
                        summary TEXT NOT NULL,
                        attention TEXT NOT NULL,
                        learning TEXT NOT NULL,
                        direction TEXT NOT NULL,
                        nextStep TEXT NOT NULL,
                        scriptureReference TEXT NOT NULL,
                        scriptureContext TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )"""
                )
            }
        }

        @Volatile private var INSTANCE: MetanoiaDatabase? = null
        fun get(context: Context): MetanoiaDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MetanoiaDatabase::class.java,
                    "metanoia.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build().also { INSTANCE = it }
            }
    }
}
