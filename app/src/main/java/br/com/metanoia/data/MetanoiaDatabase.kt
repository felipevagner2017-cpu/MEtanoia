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
        ConfessionEntry::class, DailyAnalysisEntry::class, SyncOutboxOperation::class,\n        SyncNonceCounterEntity::class
    ],
    version = 5,
    exportSchema = false
)
@TypeConverters(AgendaConverters::class)
abstract class MetanoiaDatabase : RoomDatabase() {
    abstract fun agendaDao(): AgendaDao
    abstract fun journalDao(): JournalDao
    abstract fun reviewDao(): ReviewDao
    abstract fun confessionDao(): ConfessionDao
    abstract fun dailyAnalysisDao(): DailyAnalysisDao
    abstract fun syncOutboxDao(): SyncOutboxDao\n    abstract fun syncNonceCounterDao(): SyncNonceCounterDao

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

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS sync_outbox (
                        operationId TEXT NOT NULL PRIMARY KEY,
                        recordId TEXT NOT NULL,
                        dimension TEXT NOT NULL,
                        recordType TEXT NOT NULL,
                        operation TEXT NOT NULL,
                        payloadCiphertext TEXT NOT NULL,
                        encryptionVersion INTEGER NOT NULL,
                        baseRevision INTEGER NOT NULL,
                        clientUpdatedAt INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        attemptCount INTEGER NOT NULL,
                        nextAttemptAt INTEGER NOT NULL,
                        lastError TEXT,
                        createdAt INTEGER NOT NULL
                    )"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_outbox_status_nextAttemptAt ON sync_outbox(status, nextAttemptAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_outbox_recordId_createdAt ON sync_outbox(recordId, createdAt)")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    \"\"\"CREATE TABLE IF NOT EXISTS sync_nonce_counters (
                        accountId TEXT NOT NULL,
                        deviceId TEXT NOT NULL,
                        purpose TEXT NOT NULL,
                        nextCounter INTEGER NOT NULL,
                        PRIMARY KEY(accountId, deviceId, purpose)
                    )\"\"\"
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
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build().also { INSTANCE = it }
            }
    }
}
