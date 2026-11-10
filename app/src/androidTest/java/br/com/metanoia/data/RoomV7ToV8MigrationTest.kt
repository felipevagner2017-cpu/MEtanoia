package br.com.metanoia.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomV7ToV8MigrationTest {
    @Test
    fun legacyOutboxIsPreservedButQuarantinedAndNewAccountsAreIsolated() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "room_v7_v8_fixture.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        val legacy = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            createV7Schema(legacy)
            legacy.execSQL("""
                INSERT INTO sync_outbox(
                    operationId,recordId,dimension,recordType,operation,payloadCiphertext,
                    encryptionVersion,baseRevision,clientUpdatedAt,status,attemptCount,
                    nextAttemptAt,lastError,createdAt,sourceDeviceId,purpose
                ) VALUES('legacy-op','legacy-record','DAILY','journal','upsert','legacy-cipher',
                    2,7,100,'retry',3,900,'temporary network error',50,'old-device','record')
            """.trimIndent())
            legacy.version = 7
        } finally { legacy.close() }

        val db = Room.databaseBuilder(context, MetanoiaDatabase::class.java, name)
            .addMigrations(MetanoiaDatabase.MIGRATION_7_8, MetanoiaDatabase.MIGRATION_8_9)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.syncOutboxDao()
            val legacyRow = db.openHelper.readableDatabase.query(
                "SELECT accountId,operationId,status,attemptCount,baseRevision,encryptionVersion,payloadCiphertext,lastError FROM sync_outbox"
            ).use { c ->
                assertTrue(c.moveToFirst())
                listOf(
                    c.getString(0), c.getString(1), c.getString(2), c.getInt(3).toString(),
                    c.getInt(4).toString(), c.getInt(5).toString(), c.getString(6), c.getString(7)
                )
            }
            assertEquals("", legacyRow[0])
            assertEquals("legacy-op", legacyRow[1])
            assertEquals("quarantined", legacyRow[2])
            assertEquals("3", legacyRow[3])
            assertEquals("7", legacyRow[4])
            assertEquals("2", legacyRow[5])
            assertEquals("legacy-cipher", legacyRow[6])
            assertEquals("temporary network error", legacyRow[7])
            assertTrue(dao.nextBatch("account-A", 10_000L).isEmpty())
            assertTrue(dao.nextBatch("account-B", 10_000L).isEmpty())

            val a = SyncOutboxOperation(
                accountId="account-A", operationId="shared-id", recordId="record-A",
                dimension="DAILY", recordType="journal", operation="upsert",
                payloadCiphertext="cipher-A", encryptionVersion=2, baseRevision=1,
                clientUpdatedAt=200L, sourceDeviceId="device-A", purpose="record"
            )
            val b = a.copy(accountId="account-B", recordId="record-B", payloadCiphertext="cipher-B")
            dao.enqueue(a)
            dao.enqueue(b)
            assertEquals(listOf("record-A"), dao.nextBatch("account-A", 10_000L).map { it.recordId })
            assertEquals(listOf("record-B"), dao.nextBatch("account-B", 10_000L).map { it.recordId })
            assertEquals(1, dao.markApplied("account-A", "shared-id"))
            assertEquals(1, dao.pendingCount("account-B"))
            assertEquals(1, dao.delete("account-A", "shared-id"))
            assertEquals(1, dao.pendingCount("account-B"))
            assertEquals("pending", dao.historyForRecord("account-B", "record-B").single().status)

            val sqlite = db.openHelper.readableDatabase
            sqlite.query("PRAGMA integrity_check").use {
                assertTrue(it.moveToFirst())
                assertEquals("ok", it.getString(0))
            }
            sqlite.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
            val pk = mutableListOf<Pair<String, Int>>()
            sqlite.query("PRAGMA table_info(sync_outbox)").use { c ->
                while (c.moveToNext()) {
                    val position = c.getInt(c.getColumnIndexOrThrow("pk"))
                    if (position > 0) pk += c.getString(c.getColumnIndexOrThrow("name")) to position
                }
            }
            assertEquals(listOf("accountId" to 1, "operationId" to 2), pk)
        } finally { db.close() }

        val reopened = Room.databaseBuilder(context, MetanoiaDatabase::class.java, name)
            .addMigrations(MetanoiaDatabase.MIGRATION_7_8, MetanoiaDatabase.MIGRATION_8_9).build()
        try {
            assertEquals(1, reopened.syncOutboxDao().pendingCount("account-B"))
            assertTrue(reopened.syncOutboxDao().nextBatch("account-A", 10_000L).isEmpty())
        } finally {
            reopened.close()
            context.deleteDatabase(name)
        }
    }

    private fun createV7Schema(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE agenda_items(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,title TEXT NOT NULL,description TEXT NOT NULL,dimension TEXT NOT NULL,kind TEXT NOT NULL,scheduledAt INTEGER NOT NULL,completed INTEGER NOT NULL,recurrenceRule TEXT,reminderAt INTEGER,createdAt INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE journal_entries(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,title TEXT NOT NULL,content TEXT NOT NULL,dimension TEXT NOT NULL,createdAt INTEGER NOT NULL,updatedAt INTEGER NOT NULL,dayKey TEXT NOT NULL DEFAULT '')")
        db.execSQL("CREATE TABLE review_entries(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,period TEXT NOT NULL,periodStart INTEGER NOT NULL,periodEnd INTEGER NOT NULL,reflection TEXT NOT NULL,createdAt INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE confession_entries(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,dayKey TEXT NOT NULL,content TEXT NOT NULL,createdAt INTEGER NOT NULL,updatedAt INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE daily_analysis(dayKey TEXT NOT NULL PRIMARY KEY,categories TEXT NOT NULL,dimensions TEXT NOT NULL,emotionalState TEXT NOT NULL,summary TEXT NOT NULL,attention TEXT NOT NULL,learning TEXT NOT NULL,direction TEXT NOT NULL,nextStep TEXT NOT NULL,scriptureReference TEXT NOT NULL,scriptureContext TEXT NOT NULL,updatedAt INTEGER NOT NULL)")
        db.execSQL("""CREATE TABLE sync_outbox(operationId TEXT NOT NULL PRIMARY KEY,recordId TEXT NOT NULL,dimension TEXT NOT NULL,recordType TEXT NOT NULL,operation TEXT NOT NULL,payloadCiphertext TEXT NOT NULL,encryptionVersion INTEGER NOT NULL,baseRevision INTEGER NOT NULL,clientUpdatedAt INTEGER NOT NULL,status TEXT NOT NULL,attemptCount INTEGER NOT NULL,nextAttemptAt INTEGER NOT NULL,lastError TEXT,createdAt INTEGER NOT NULL,sourceDeviceId TEXT NOT NULL DEFAULT '',purpose TEXT NOT NULL DEFAULT 'record')""")
        db.execSQL("CREATE INDEX index_sync_outbox_status_nextAttemptAt ON sync_outbox(status,nextAttemptAt)")
        db.execSQL("CREATE INDEX index_sync_outbox_recordId_createdAt ON sync_outbox(recordId,createdAt)")
        db.execSQL("CREATE TABLE sync_nonce_counters(accountId TEXT NOT NULL,deviceId TEXT NOT NULL,purpose TEXT NOT NULL,nextCounter INTEGER NOT NULL,PRIMARY KEY(accountId,deviceId,purpose))")
        db.execSQL("CREATE TABLE sync_record_metadata(entityType TEXT NOT NULL,localId TEXT NOT NULL,recordId TEXT NOT NULL,dimension TEXT NOT NULL,recordType TEXT NOT NULL,revision INTEGER NOT NULL,syncState TEXT NOT NULL,sourceDeviceId TEXT,encryptionVersion INTEGER NOT NULL,remoteUpdatedAt INTEGER NOT NULL,updatedAt INTEGER NOT NULL,PRIMARY KEY(entityType,localId))")
        db.execSQL("CREATE UNIQUE INDEX index_sync_record_metadata_recordId ON sync_record_metadata(recordId)")
        db.execSQL("CREATE INDEX index_sync_record_metadata_syncState_updatedAt ON sync_record_metadata(syncState,updatedAt)")
        db.execSQL("CREATE TABLE sync_cursors(accountId TEXT NOT NULL,deviceId TEXT NOT NULL,serverUpdatedAt INTEGER NOT NULL,afterRecordId TEXT NOT NULL,updatedAt INTEGER NOT NULL,PRIMARY KEY(accountId,deviceId))")
        db.execSQL("CREATE TABLE sync_remote_records(accountId TEXT NOT NULL DEFAULT '',recordId TEXT NOT NULL,dimension TEXT NOT NULL,recordType TEXT NOT NULL,payloadCiphertext TEXT NOT NULL,encryptionVersion INTEGER NOT NULL,revision INTEGER NOT NULL,clientUpdatedAt INTEGER NOT NULL,serverUpdatedAt INTEGER NOT NULL,deletedAt INTEGER,sourceDeviceId TEXT,status TEXT NOT NULL,lastError TEXT,PRIMARY KEY(accountId,recordId))")
        db.execSQL("CREATE INDEX index_sync_remote_records_accountId_serverUpdatedAt_recordId ON sync_remote_records(accountId,serverUpdatedAt,recordId)")
    }
}
