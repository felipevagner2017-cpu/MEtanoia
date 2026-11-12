package br.com.metanoia.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomV8ToV9MigrationTest {
    @Test
    fun addsAgendaFieldsWithoutLosingExistingCommitments() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "room_v8_v9_fixture.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        val legacy = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            legacy.execSQL("""CREATE TABLE agenda_items(
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                title TEXT NOT NULL, description TEXT NOT NULL, dimension TEXT NOT NULL,
                kind TEXT NOT NULL, scheduledAt INTEGER NOT NULL, completed INTEGER NOT NULL,
                recurrenceRule TEXT, reminderAt INTEGER, createdAt INTEGER NOT NULL
            )""")
            legacy.execSQL("CREATE TABLE journal_entries(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,title TEXT NOT NULL,content TEXT NOT NULL,dimension TEXT NOT NULL,createdAt INTEGER NOT NULL,updatedAt INTEGER NOT NULL,dayKey TEXT NOT NULL DEFAULT '')")
            legacy.execSQL("CREATE TABLE review_entries(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,period TEXT NOT NULL,periodStart INTEGER NOT NULL,periodEnd INTEGER NOT NULL,reflection TEXT NOT NULL,createdAt INTEGER NOT NULL)")
            legacy.execSQL("CREATE TABLE confession_entries(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,dayKey TEXT NOT NULL,content TEXT NOT NULL,createdAt INTEGER NOT NULL,updatedAt INTEGER NOT NULL)")
            legacy.execSQL("CREATE TABLE daily_analysis(dayKey TEXT NOT NULL PRIMARY KEY,categories TEXT NOT NULL,dimensions TEXT NOT NULL,emotionalState TEXT NOT NULL,summary TEXT NOT NULL,attention TEXT NOT NULL,learning TEXT NOT NULL,direction TEXT NOT NULL,nextStep TEXT NOT NULL,scriptureReference TEXT NOT NULL,scriptureContext TEXT NOT NULL,updatedAt INTEGER NOT NULL)")
            legacy.execSQL("""CREATE TABLE sync_outbox(accountId TEXT NOT NULL DEFAULT '',operationId TEXT NOT NULL,recordId TEXT NOT NULL,dimension TEXT NOT NULL,recordType TEXT NOT NULL,operation TEXT NOT NULL,payloadCiphertext TEXT NOT NULL,encryptionVersion INTEGER NOT NULL,baseRevision INTEGER NOT NULL,clientUpdatedAt INTEGER NOT NULL,status TEXT NOT NULL,attemptCount INTEGER NOT NULL,nextAttemptAt INTEGER NOT NULL,lastError TEXT,createdAt INTEGER NOT NULL,sourceDeviceId TEXT NOT NULL DEFAULT '',purpose TEXT NOT NULL DEFAULT 'record',PRIMARY KEY(accountId,operationId))""")
            legacy.execSQL("CREATE INDEX index_sync_outbox_accountId_status_nextAttemptAt ON sync_outbox(accountId,status,nextAttemptAt)")
            legacy.execSQL("CREATE INDEX index_sync_outbox_accountId_recordId_createdAt ON sync_outbox(accountId,recordId,createdAt)")
            legacy.execSQL("CREATE TABLE sync_nonce_counters(accountId TEXT NOT NULL,deviceId TEXT NOT NULL,purpose TEXT NOT NULL,nextCounter INTEGER NOT NULL,PRIMARY KEY(accountId,deviceId,purpose))")
            legacy.execSQL("CREATE TABLE sync_record_metadata(entityType TEXT NOT NULL,localId TEXT NOT NULL,recordId TEXT NOT NULL,dimension TEXT NOT NULL,recordType TEXT NOT NULL,revision INTEGER NOT NULL,syncState TEXT NOT NULL,sourceDeviceId TEXT,encryptionVersion INTEGER NOT NULL,remoteUpdatedAt INTEGER NOT NULL,updatedAt INTEGER NOT NULL,PRIMARY KEY(entityType,localId))")
            legacy.execSQL("CREATE UNIQUE INDEX index_sync_record_metadata_recordId ON sync_record_metadata(recordId)")
            legacy.execSQL("CREATE INDEX index_sync_record_metadata_syncState_updatedAt ON sync_record_metadata(syncState,updatedAt)")
            legacy.execSQL("CREATE TABLE sync_cursors(accountId TEXT NOT NULL,deviceId TEXT NOT NULL,serverUpdatedAt INTEGER NOT NULL,afterRecordId TEXT NOT NULL,updatedAt INTEGER NOT NULL,PRIMARY KEY(accountId,deviceId))")
            legacy.execSQL("CREATE TABLE sync_remote_records(accountId TEXT NOT NULL DEFAULT '',recordId TEXT NOT NULL,dimension TEXT NOT NULL,recordType TEXT NOT NULL,payloadCiphertext TEXT NOT NULL,encryptionVersion INTEGER NOT NULL,revision INTEGER NOT NULL,clientUpdatedAt INTEGER NOT NULL,serverUpdatedAt INTEGER NOT NULL,deletedAt INTEGER,sourceDeviceId TEXT,status TEXT NOT NULL,lastError TEXT,PRIMARY KEY(accountId,recordId))")
            legacy.execSQL("CREATE INDEX index_sync_remote_records_accountId_serverUpdatedAt_recordId ON sync_remote_records(accountId,serverUpdatedAt,recordId)")
            legacy.execSQL("""INSERT INTO agenda_items(
                id,title,description,dimension,kind,scheduledAt,completed,recurrenceRule,reminderAt,createdAt
            ) VALUES(42,'Consulta','Retorno','ROUTINE','APPOINTMENT',1700000000000,0,NULL,1699992800000,1690000000000)""")
            legacy.version = 8
        } finally { legacy.close() }

        val db = Room.databaseBuilder(context, MetanoiaDatabase::class.java, name)
            .addMigrations(MetanoiaDatabase.MIGRATION_8_9, MetanoiaDatabase.MIGRATION_9_10)
            .allowMainThreadQueries()
            .build()
        try {
            val sqlite = db.openHelper.readableDatabase
            sqlite.query("SELECT id,title,location,endAt,reminderOffsets,seriesId FROM agenda_items WHERE id=42").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(42L, cursor.getLong(0))
                assertEquals("Consulta", cursor.getString(1))
                assertEquals("", cursor.getString(2))
                assertTrue(cursor.isNull(3))
                assertEquals("120,60,30", cursor.getString(4))
                assertTrue(cursor.isNull(5))
            }
            sqlite.query("PRAGMA integrity_check").use {
                assertTrue(it.moveToFirst())
                assertEquals("ok", it.getString(0))
            }
            sqlite.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
