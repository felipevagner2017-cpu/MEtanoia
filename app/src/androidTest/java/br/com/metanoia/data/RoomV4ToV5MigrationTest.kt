package br.com.metanoia.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class RoomV4ToV5MigrationTest {
    @Test
    fun migratesEffectiveV4SchemaAndPreservesEveryExistingEntity() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "room_v4_to_v5_migration_test.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()

        createVersion4Fixture(file)
        val legacy = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        try {
            assertEquals(4, legacy.version)
            assertEquals(
                setOf("android_metadata", "agenda_items", "journal_entries", "review_entries", "confession_entries", "daily_analysis", "sync_outbox"),
                tableNames(legacy)
            )
            assertEquals(1, rowCount(legacy, "agenda_items"))
            assertEquals(1, rowCount(legacy, "journal_entries"))
            assertEquals(1, rowCount(legacy, "review_entries"))
            assertEquals(1, rowCount(legacy, "confession_entries"))
            assertEquals(1, rowCount(legacy, "daily_analysis"))
            assertEquals(1, rowCount(legacy, "sync_outbox"))
        } finally {
            legacy.close()
        }

        val migrated = Room.databaseBuilder(context, MetanoiaDatabase::class.java, name)
            .addMigrations(MetanoiaDatabase.MIGRATION_4_5)
            .build()
        try {
            assertEquals("Compromisso legado", migrated.agendaDao().findById(41L)?.title)
            assertEquals("Diário legado", migrated.journalDao().findForDay("2026-11-04")?.title)
            assertEquals("Revisão preservada", migrated.reviewDao().observeHistory().first().single().reflection)
            assertEquals("Confissão preservada", migrated.confessionDao().findForDay("2026-11-04")?.content)
            assertEquals("Resumo preservado", migrated.dailyAnalysisDao().findForDay("2026-11-04")?.summary)
            assertEquals("v1.ciphertext", migrated.syncOutboxDao().historyForRecord("legacy-record").single().payloadCiphertext)

            val db = migrated.openHelper.readableDatabase
            assertEquals(5, db.version)
            val expectedTables = setOf(
                "agenda_items", "journal_entries", "review_entries", "confession_entries",
                "daily_analysis", "sync_outbox", "sync_nonce_counters", "android_metadata", "room_master_table"
            )
            assertTrue(tableNames(db).containsAll(expectedTables))
            assertEquals(1, rowCount(db, "agenda_items"))
            assertEquals(1, rowCount(db, "journal_entries"))
            assertEquals(1, rowCount(db, "review_entries"))
            assertEquals(1, rowCount(db, "confession_entries"))
            assertEquals(1, rowCount(db, "daily_analysis"))
            assertEquals(1, rowCount(db, "sync_outbox"))
            assertEquals(0, rowCount(db, "sync_nonce_counters"))

            val nonceColumns = mutableListOf<Triple<String, String, Int>>()
            db.query("PRAGMA table_info(sync_nonce_counters)").use { cursor ->
                while (cursor.moveToNext()) {
                    nonceColumns += Triple(
                        cursor.getString(cursor.getColumnIndexOrThrow("name")),
                        cursor.getString(cursor.getColumnIndexOrThrow("type")),
                        cursor.getInt(cursor.getColumnIndexOrThrow("pk"))
                    )
                }
            }
            assertEquals(
                listOf(
                    Triple("accountId", "TEXT", 1),
                    Triple("deviceId", "TEXT", 2),
                    Triple("purpose", "TEXT", 3),
                    Triple("nextCounter", "INTEGER", 0)
                ),
                nonceColumns
            )

            val outboxIndexes = mutableSetOf<String>()
            db.query("PRAGMA index_list(sync_outbox)").use { cursor ->
                while (cursor.moveToNext()) outboxIndexes += cursor.getString(cursor.getColumnIndexOrThrow("name"))
            }
            assertTrue(outboxIndexes.contains("index_sync_outbox_status_nextAttemptAt"))
            assertTrue(outboxIndexes.contains("index_sync_outbox_recordId_createdAt"))

            db.query("PRAGMA foreign_key_check").use { assertFalse("Foreign-key violations found", it.moveToFirst()) }
            db.query("PRAGMA integrity_check").use {
                assertTrue("SQLite integrity_check failed", it.moveToFirst())
                assertEquals("ok", it.getString(0))
            }
        } finally {
            migrated.close()
            context.deleteDatabase(name)
        }
    }

    private fun createVersion4Fixture(file: File) {
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            db.execSQL("""CREATE TABLE agenda_items (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                title TEXT NOT NULL, description TEXT NOT NULL, dimension TEXT NOT NULL,
                kind TEXT NOT NULL, scheduledAt INTEGER NOT NULL, completed INTEGER NOT NULL,
                recurrenceRule TEXT, reminderAt INTEGER, createdAt INTEGER NOT NULL
            )""")
            db.execSQL("""CREATE TABLE journal_entries (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL,
                content TEXT NOT NULL, dimension TEXT NOT NULL, createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL, dayKey TEXT NOT NULL DEFAULT ''
            )""")
            db.execSQL("""CREATE TABLE review_entries (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, period TEXT NOT NULL,
                periodStart INTEGER NOT NULL, periodEnd INTEGER NOT NULL,
                reflection TEXT NOT NULL, createdAt INTEGER NOT NULL
            )""")
            db.execSQL("""CREATE TABLE confession_entries (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, dayKey TEXT NOT NULL,
                content TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL
            )""")
            db.execSQL("""CREATE TABLE daily_analysis (
                dayKey TEXT NOT NULL PRIMARY KEY, categories TEXT NOT NULL, dimensions TEXT NOT NULL,
                emotionalState TEXT NOT NULL, summary TEXT NOT NULL, attention TEXT NOT NULL,
                learning TEXT NOT NULL, direction TEXT NOT NULL, nextStep TEXT NOT NULL,
                scriptureReference TEXT NOT NULL, scriptureContext TEXT NOT NULL, updatedAt INTEGER NOT NULL
            )""")
            db.execSQL("""CREATE TABLE sync_outbox (
                operationId TEXT NOT NULL PRIMARY KEY, recordId TEXT NOT NULL, dimension TEXT NOT NULL,
                recordType TEXT NOT NULL, operation TEXT NOT NULL, payloadCiphertext TEXT NOT NULL,
                encryptionVersion INTEGER NOT NULL, baseRevision INTEGER NOT NULL,
                clientUpdatedAt INTEGER NOT NULL, status TEXT NOT NULL, attemptCount INTEGER NOT NULL,
                nextAttemptAt INTEGER NOT NULL, lastError TEXT, createdAt INTEGER NOT NULL
            )""")
            db.execSQL("CREATE INDEX index_sync_outbox_status_nextAttemptAt ON sync_outbox(status, nextAttemptAt)")
            db.execSQL("CREATE INDEX index_sync_outbox_recordId_createdAt ON sync_outbox(recordId, createdAt)")

            db.execSQL(
                "INSERT INTO agenda_items(id,title,description,dimension,kind,scheduledAt,completed,recurrenceRule,reminderAt,createdAt) VALUES(41,'Compromisso legado','Descrição','ROUTINE','APPOINTMENT',1793782800000,0,NULL,NULL,1793780000000)"
            )
            db.execSQL(
                "INSERT INTO journal_entries(id,title,content,dimension,createdAt,updatedAt,dayKey) VALUES(42,'Diário legado','Conteúdo diário','INDIVIDUAL',1793780000000,1793780000001,'2026-11-04')"
            )
            db.execSQL(
                "INSERT INTO review_entries(id,period,periodStart,periodEnd,reflection,createdAt) VALUES(43,'WEEKLY',1793400000000,1794000000000,'Revisão preservada',1793780000000)"
            )
            db.execSQL(
                "INSERT INTO confession_entries(id,dayKey,content,createdAt,updatedAt) VALUES(44,'2026-11-04','Confissão preservada',1793780000000,1793780000001)"
            )
            db.execSQL(
                "INSERT INTO daily_analysis(dayKey,categories,dimensions,emotionalState,summary,attention,learning,direction,nextStep,scriptureReference,scriptureContext,updatedAt) VALUES('2026-11-04','[\"gratidão\"]','[\"INDIVIDUAL\"]','sereno','Resumo preservado','','aprendizado','direção','próximo passo','Salmo 23','Contexto',1793780000001)"
            )
            db.execSQL(
                "INSERT INTO sync_outbox(operationId,recordId,dimension,recordType,operation,payloadCiphertext,encryptionVersion,baseRevision,clientUpdatedAt,status,attemptCount,nextAttemptAt,lastError,createdAt) VALUES('op-v1','legacy-record','INDIVIDUAL','journal','upsert','v1.ciphertext',1,0,1793780000001,'pending',0,0,NULL,1793780000000)"
            )
            db.version = 4
        } finally {
            db.close()
        }
    }

    private fun tableNames(db: SQLiteDatabase): Set<String> {
        val names = mutableSetOf<String>()
        db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'", null).use {
            while (it.moveToNext()) names += it.getString(0)
        }
        return names
    }

    private fun tableNames(db: androidx.sqlite.db.SupportSQLiteDatabase): Set<String> {
        val names = mutableSetOf<String>()
        db.query("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'").use {
            while (it.moveToNext()) names += it.getString(0)
        }
        return names
    }

    private fun rowCount(db: SQLiteDatabase, table: String): Int =
        db.rawQuery("SELECT COUNT(*) FROM $table", null).use { it.moveToFirst(); it.getInt(0) }

    private fun rowCount(db: androidx.sqlite.db.SupportSQLiteDatabase, table: String): Int =
        db.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }
}
