package br.com.metanoia.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteConstraintException
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
class RoomV9ToV10MigrationTest {
    @Test
    fun addsFastingTablesAndPreservesAllExistingRecords() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "room_v9_v10_fixture.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        val legacy = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            legacy.execSQL("""CREATE TABLE agenda_items(
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                title TEXT NOT NULL, description TEXT NOT NULL, dimension TEXT NOT NULL,
                kind TEXT NOT NULL, scheduledAt INTEGER NOT NULL, completed INTEGER NOT NULL,
                recurrenceRule TEXT, reminderAt INTEGER, createdAt INTEGER NOT NULL,
                location TEXT NOT NULL DEFAULT '', endAt INTEGER, reminderOffsets TEXT NOT NULL DEFAULT '120,60,30', seriesId TEXT
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
            legacy.execSQL("CREATE INDEX index_agenda_items_seriesId ON agenda_items(seriesId)")
            legacy.execSQL("""INSERT INTO agenda_items(
                id,title,description,dimension,kind,scheduledAt,completed,recurrenceRule,reminderAt,createdAt
            ) VALUES(42,'Consulta','Retorno','ROUTINE','APPOINTMENT',1700000000000,0,NULL,1699992800000,1690000000000)""")
            legacy.execSQL("INSERT INTO journal_entries(id,title,content,dimension,createdAt,updatedAt,dayKey) VALUES(7,'Diário preservado','Registro anterior','SPIRITUAL',100,110,'2026-11-10')")
            legacy.execSQL("INSERT INTO review_entries(id,period,periodStart,periodEnd,reflection,createdAt) VALUES(8,'WEEKLY',1,7,'Revisão preservada',120)")
            legacy.execSQL("INSERT INTO confession_entries(id,dayKey,content,createdAt,updatedAt) VALUES(9,'2026-11-10','Confissão preservada',130,140)")
            legacy.execSQL("INSERT INTO daily_analysis(dayKey,categories,dimensions,emotionalState,summary,attention,learning,direction,nextStep,scriptureReference,scriptureContext,updatedAt) VALUES('2026-11-10','[]','{}','sereno','Resumo preservado','','','','','Salmo 23','Contexto',150)")
            legacy.execSQL("INSERT INTO sync_outbox(accountId,operationId,recordId,dimension,recordType,operation,payloadCiphertext,encryptionVersion,baseRevision,clientUpdatedAt,status,attemptCount,nextAttemptAt,lastError,createdAt,sourceDeviceId,purpose) VALUES('account-a','op-a','record-a','DAILY','journal','upsert','ciphertext',2,0,160,'pending',0,160,NULL,160,'device-a','record')")
            legacy.version = 9
        } finally { legacy.close() }

        val db = Room.databaseBuilder(context, MetanoiaDatabase::class.java, name)
            .addMigrations(MetanoiaDatabase.MIGRATION_9_10)
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
            sqlite.query("SELECT name FROM sqlite_master WHERE type='table' AND name IN ('fasting_plans','fasting_daily_entries') ORDER BY name").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("fasting_daily_entries", cursor.getString(0))
                assertTrue(cursor.moveToNext())
                assertEquals("fasting_plans", cursor.getString(0))
                assertFalse(cursor.moveToNext())
            }
            sqlite.query("SELECT title,content FROM journal_entries WHERE id=7").use { assertTrue(it.moveToFirst()); assertEquals("Diário preservado", it.getString(0)); assertEquals("Registro anterior", it.getString(1)) }
            sqlite.query("SELECT reflection FROM review_entries WHERE id=8").use { assertTrue(it.moveToFirst()); assertEquals("Revisão preservada", it.getString(0)) }
            sqlite.query("SELECT content FROM confession_entries WHERE id=9").use { assertTrue(it.moveToFirst()); assertEquals("Confissão preservada", it.getString(0)) }
            sqlite.query("SELECT summary FROM daily_analysis WHERE dayKey='2026-11-10'").use { assertTrue(it.moveToFirst()); assertEquals("Resumo preservado", it.getString(0)) }
            sqlite.query("SELECT payloadCiphertext FROM sync_outbox WHERE operationId='op-a'").use { assertTrue(it.moveToFirst()); assertEquals("ciphertext", it.getString(0)) }

            val dao = db.fastingDao()
            assertTrue(dao.recentPlans().isEmpty())
            assertEquals(null, dao.findPlan("missing"))
            val plan = FastingPlan("fast-1", LifeDimension.SPIRITUAL, "NON_FOOD", "Atenção", "Registro de teste", "Oração e reflexão", "Mateus 6:16–18", "Jesus ensina a sinceridade", "Escolha livre", "Leia e reflita", 1, 1000L, 2000L, createdAt = 1000L)
            dao.insertPlan(plan)
            assertEquals(plan, dao.findPlan("fast-1"))
            dao.updatePlan(plan.copy(status = "ACTIVE"))
            assertEquals("ACTIVE", dao.findPlan("fast-1")?.status)
            val reflection = FastingDailyEntry("entry-1", "fast-1", 0, "Reflexão inicial", "Em paz", 1100L)
            dao.insertDailyEntry(reflection)
            assertEquals(reflection, dao.findDailyEntry("fast-1", 0))
            var duplicateRejected = false
            try {
                dao.insertDailyEntry(reflection.copy(id = "entry-duplicate"))
            } catch (_: SQLiteConstraintException) {
                duplicateRejected = true
            }
            assertTrue("Duplicate plan/day entry must be rejected", duplicateRejected)
            dao.updateDailyEntry(reflection.copy(reflection = "Reflexão revisada"))
            assertEquals("Reflexão revisada", dao.findDailyEntry("fast-1", 0)?.reflection)
            assertEquals(1, dao.deleteDailyEntry("entry-1"))
            assertEquals(null, dao.findDailyEntry("fast-1", 0))
            assertEquals(1, dao.deletePlan("fast-1"))
            assertEquals(null, dao.findPlan("fast-1"))

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
