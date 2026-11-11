package br.com.metanoia.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
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
        ConfessionEntry::class, DailyAnalysisEntry::class, SyncOutboxOperation::class,
        SyncNonceCounterEntity::class, SyncRecordMetadata::class, SyncCursorEntity::class, SyncRemoteRecord::class,
        FastingPlan::class, FastingDailyEntry::class
    ],
    version = 10, 
    exportSchema = false
)
@TypeConverters(AgendaConverters::class)
abstract class MetanoiaDatabase : RoomDatabase() {
    abstract fun agendaDao(): AgendaDao
    abstract fun journalDao(): JournalDao
    abstract fun reviewDao(): ReviewDao
    abstract fun confessionDao(): ConfessionDao
    abstract fun dailyAnalysisDao(): DailyAnalysisDao
    abstract fun syncOutboxDao(): SyncOutboxDao
    abstract fun syncNonceCounterDao(): SyncNonceCounterDao
    abstract fun syncRecordMetadataDao(): SyncRecordMetadataDao
    abstract fun syncCursorDao(): SyncCursorDao
    abstract fun syncRemoteRecordDao(): SyncRemoteRecordDao
    abstract fun fastingDao(): FastingDao

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

        internal val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS sync_nonce_counters (
                        accountId TEXT NOT NULL,
                        deviceId TEXT NOT NULL,
                        purpose TEXT NOT NULL,
                        nextCounter INTEGER NOT NULL,
                        PRIMARY KEY(accountId, deviceId, purpose)
                    )"""
                )
            }
        }

        internal val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sync_outbox ADD COLUMN sourceDeviceId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE sync_outbox ADD COLUMN purpose TEXT NOT NULL DEFAULT 'record'")
                db.execSQL("""CREATE TABLE sync_record_metadata (entityType TEXT NOT NULL,localId TEXT NOT NULL,recordId TEXT NOT NULL,dimension TEXT NOT NULL,recordType TEXT NOT NULL,revision INTEGER NOT NULL,syncState TEXT NOT NULL,sourceDeviceId TEXT,encryptionVersion INTEGER NOT NULL,remoteUpdatedAt INTEGER NOT NULL,updatedAt INTEGER NOT NULL,PRIMARY KEY(entityType,localId))""")
                db.execSQL("CREATE UNIQUE INDEX index_sync_record_metadata_recordId ON sync_record_metadata(recordId)")
                db.execSQL("CREATE INDEX index_sync_record_metadata_syncState_updatedAt ON sync_record_metadata(syncState,updatedAt)")
                db.execSQL("""CREATE TABLE sync_cursors (accountId TEXT NOT NULL,deviceId TEXT NOT NULL,serverUpdatedAt INTEGER NOT NULL,afterRecordId TEXT NOT NULL,updatedAt INTEGER NOT NULL,PRIMARY KEY(accountId,deviceId))""")
                db.execSQL("""CREATE TABLE sync_remote_records (recordId TEXT NOT NULL PRIMARY KEY,dimension TEXT NOT NULL,recordType TEXT NOT NULL,payloadCiphertext TEXT NOT NULL,encryptionVersion INTEGER NOT NULL,revision INTEGER NOT NULL,clientUpdatedAt INTEGER NOT NULL,serverUpdatedAt INTEGER NOT NULL,deletedAt INTEGER,sourceDeviceId TEXT,status TEXT NOT NULL,lastError TEXT)""")
                db.execSQL("CREATE INDEX index_sync_remote_records_serverUpdatedAt_recordId ON sync_remote_records(serverUpdatedAt,recordId)")
                val now=System.currentTimeMillis()
                fun seed(table:String,id:String,dim:String,type:String) {
                    db.execSQL("""INSERT INTO sync_record_metadata(entityType,localId,recordId,dimension,recordType,revision,syncState,sourceDeviceId,encryptionVersion,remoteUpdatedAt,updatedAt) SELECT '$table',CAST($id AS TEXT),'legacy-$table-'||CAST($id AS TEXT),$dim,'$type',0,'local',NULL,0,0,$now FROM $table""")
                }
                seed("agenda_items","id","CASE dimension WHEN 'SPIRITUAL' THEN 'DEVOTION' WHEN 'MATRIMONIAL' THEN 'MATRIMONIAL' WHEN 'PROFESSIONAL' THEN 'PROFESSIONAL' ELSE 'DAILY' END","agenda")
                seed("journal_entries","id","CASE dimension WHEN 'SPIRITUAL' THEN 'DEVOTION' WHEN 'MATRIMONIAL' THEN 'MATRIMONIAL' WHEN 'PROFESSIONAL' THEN 'PROFESSIONAL' ELSE 'DAILY' END","journal")
                seed("review_entries","id","'DAILY'","review")
                seed("confession_entries","id","'DAILY'","confession")
                seed("daily_analysis","dayKey","'DAILY'","daily_analysis")
            }
        }


        internal val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE sync_remote_records_new (
                    accountId TEXT NOT NULL DEFAULT '', recordId TEXT NOT NULL, dimension TEXT NOT NULL,
                    recordType TEXT NOT NULL, payloadCiphertext TEXT NOT NULL, encryptionVersion INTEGER NOT NULL,
                    revision INTEGER NOT NULL, clientUpdatedAt INTEGER NOT NULL, serverUpdatedAt INTEGER NOT NULL,
                    deletedAt INTEGER, sourceDeviceId TEXT, status TEXT NOT NULL, lastError TEXT,
                    PRIMARY KEY(accountId, recordId)
                )""")
                db.execSQL("""INSERT INTO sync_remote_records_new(
                    accountId,recordId,dimension,recordType,payloadCiphertext,encryptionVersion,revision,
                    clientUpdatedAt,serverUpdatedAt,deletedAt,sourceDeviceId,status,lastError
                ) SELECT '',recordId,dimension,recordType,payloadCiphertext,encryptionVersion,revision,
                    clientUpdatedAt,serverUpdatedAt,deletedAt,sourceDeviceId,'quarantined',
                    COALESCE(lastError,'Legacy inbox item has no authenticated account binding; re-fetch after sign-in')
                    FROM sync_remote_records""")
                db.execSQL("DROP TABLE sync_remote_records")
                db.execSQL("ALTER TABLE sync_remote_records_new RENAME TO sync_remote_records")
                db.execSQL("CREATE INDEX index_sync_remote_records_accountId_serverUpdatedAt_recordId ON sync_remote_records(accountId,serverUpdatedAt,recordId)")
            }
        }

        internal val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE sync_outbox_new (
                    accountId TEXT NOT NULL DEFAULT '',
                    operationId TEXT NOT NULL,
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
                    createdAt INTEGER NOT NULL,
                    sourceDeviceId TEXT NOT NULL DEFAULT '',
                    purpose TEXT NOT NULL DEFAULT 'record',
                    PRIMARY KEY(accountId, operationId)
                )""")
                db.execSQL("""INSERT INTO sync_outbox_new(
                    accountId,operationId,recordId,dimension,recordType,operation,payloadCiphertext,
                    encryptionVersion,baseRevision,clientUpdatedAt,status,attemptCount,nextAttemptAt,
                    lastError,createdAt,sourceDeviceId,purpose
                ) SELECT '',operationId,recordId,dimension,recordType,operation,payloadCiphertext,
                    encryptionVersion,baseRevision,clientUpdatedAt,'quarantined',attemptCount,nextAttemptAt,
                    COALESCE(lastError,'Legacy outbox operation has no authenticated account binding; manual recovery required'),
                    createdAt,sourceDeviceId,purpose
                FROM sync_outbox""")
                db.execSQL("DROP TABLE sync_outbox")
                db.execSQL("ALTER TABLE sync_outbox_new RENAME TO sync_outbox")
                db.execSQL("CREATE INDEX index_sync_outbox_accountId_status_nextAttemptAt ON sync_outbox(accountId,status,nextAttemptAt)")
                db.execSQL("CREATE INDEX index_sync_outbox_accountId_recordId_createdAt ON sync_outbox(accountId,recordId,createdAt)")
            }
        }

        internal val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE agenda_items ADD COLUMN location TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE agenda_items ADD COLUMN endAt INTEGER")
                db.execSQL("ALTER TABLE agenda_items ADD COLUMN reminderOffsets TEXT NOT NULL DEFAULT '120,60,30'")
                db.execSQL("ALTER TABLE agenda_items ADD COLUMN seriesId TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_agenda_items_seriesId ON agenda_items(seriesId)")
            }
        }

        internal val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE fasting_plans (
                    id TEXT NOT NULL PRIMARY KEY,
                    dimension TEXT NOT NULL,
                    type TEXT NOT NULL,
                    identifiedNeed TEXT NOT NULL,
                    recommendationReason TEXT NOT NULL,
                    purpose TEXT NOT NULL,
                    scriptureReferences TEXT NOT NULL,
                    scriptureContext TEXT NOT NULL,
                    preparation TEXT NOT NULL,
                    dailyGuidance TEXT NOT NULL,
                    durationDays INTEGER NOT NULL,
                    startAt INTEGER NOT NULL,
                    endAt INTEGER NOT NULL,
                    foodAbstinence INTEGER NOT NULL,
                    status TEXT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    completedAt INTEGER
                )""")
                db.execSQL("CREATE INDEX index_fasting_plans_dimension_createdAt ON fasting_plans(dimension, createdAt)")
                db.execSQL("CREATE INDEX index_fasting_plans_status ON fasting_plans(status)")
                db.execSQL("""CREATE TABLE fasting_daily_entries (
                    id TEXT NOT NULL PRIMARY KEY,
                    planId TEXT NOT NULL,
                    dayIndex INTEGER NOT NULL,
                    reflection TEXT NOT NULL,
                    experience TEXT NOT NULL,
                    recordedAt INTEGER NOT NULL
                )""")
                db.execSQL("CREATE UNIQUE INDEX index_fasting_daily_entries_planId_dayIndex ON fasting_daily_entries(planId, dayIndex)")
            }
        }

        @Volatile private var INSTANCE: MetanoiaDatabase? = null
        fun get(context: Context): MetanoiaDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    val appContext = context.applicationContext
                    val databaseKey = SqlCipherDatabaseMigration.ensureEncrypted(appContext)
                    val encryptedFactory = SupportOpenHelperFactory(databaseKey.copyOf())
                    databaseKey.fill(0)
                    Room.databaseBuilder(
                        appContext,
                        MetanoiaDatabase::class.java,
                        SqlCipherDatabaseMigration.DATABASE_NAME
                    ).openHelperFactory(encryptedFactory)
                        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
                        .build().also { INSTANCE = it }
                }
            }
    }
}
