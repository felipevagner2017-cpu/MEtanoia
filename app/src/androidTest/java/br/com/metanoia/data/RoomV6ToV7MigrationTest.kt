package br.com.metanoia.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomV6ToV7MigrationTest {
    @Test
    fun isolatesIdenticalRecordIdsAcrossAccountsAndQuarantinesUnboundLegacyRows() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "room_v6_v7_fixture.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        val legacy = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            createV6Schema(legacy)
            legacy.execSQL("""INSERT INTO sync_remote_records VALUES(
                'same-record','DAILY','journal','legacy-ciphertext',1,4,100,200,NULL,'old-device','received',NULL
            )""")
            legacy.version = 6
        } finally { legacy.close() }

        val db = Room.databaseBuilder(context, MetanoiaDatabase::class.java, name)
            .addMigrations(MetanoiaDatabase.MIGRATION_6_7, MetanoiaDatabase.MIGRATION_7_8, MetanoiaDatabase.MIGRATION_8_9, MetanoiaDatabase.MIGRATION_9_10).build()
        try {
            val dao = db.syncRemoteRecordDao()
            val legacyRow = dao.find("", "same-record")
            assertNotNull(legacyRow)
            assertEquals("legacy-ciphertext", legacyRow!!.payloadCiphertext)
            assertEquals("quarantined", legacyRow.status)
            assertTrue(legacyRow.lastError!!.contains("no authenticated account binding"))
            assertTrue(dao.pending("").isEmpty())
            assertEquals(0, dao.markApplied("", "same-record", 4))
            assertEquals("quarantined", dao.find("", "same-record")?.status)

            val accountA = SyncRemoteRecord(
                accountId="account-A", recordId="same-record", dimension="DAILY",
                recordType="journal", payloadCiphertext="ciphertext-A",
                encryptionVersion=1, revision=5, clientUpdatedAt=300,
                serverUpdatedAt=400, deletedAt=null, sourceDeviceId="device-A"
            )
            val accountB = accountA.copy(
                accountId="account-B", payloadCiphertext="ciphertext-B",
                revision=8, sourceDeviceId="device-B"
            )
            assertTrue(dao.insertIfAbsent(accountA) != -1L)
            assertTrue(dao.insertIfAbsent(accountB) != -1L)
            assertEquals("ciphertext-A", dao.find("account-A","same-record")?.payloadCiphertext)
            assertEquals("ciphertext-B", dao.find("account-B","same-record")?.payloadCiphertext)
            assertEquals(1, dao.pending("account-A").size)
            assertEquals(1, dao.pending("account-B").size)

            assertEquals(1, dao.markApplied("account-A","same-record",5))
            assertEquals("applied", dao.find("account-A","same-record")?.status)
            assertEquals("received", dao.find("account-B","same-record")?.status)
            assertEquals(1, dao.pending("account-B").size)

            val sqlite = db.openHelper.readableDatabase
            val primaryKey = mutableListOf<Pair<String,Int>>()
            sqlite.query("PRAGMA table_info(sync_remote_records)").use { cursor ->
                while(cursor.moveToNext()) {
                    val position=cursor.getInt(cursor.getColumnIndexOrThrow("pk"))
                    if(position>0) primaryKey += cursor.getString(cursor.getColumnIndexOrThrow("name")) to position
                }
            }
            assertEquals(listOf("accountId" to 1,"recordId" to 2),primaryKey)
            val indexes=mutableSetOf<String>()
            sqlite.query("PRAGMA index_list(sync_remote_records)").use {
                while(it.moveToNext()) indexes += it.getString(it.getColumnIndexOrThrow("name"))
            }
            val targetIndex = "index_sync_remote_records_accountId_serverUpdatedAt_recordId"
            assertTrue(indexes.contains(targetIndex))
            val indexColumns = mutableListOf<Pair<String, Int>>()
            sqlite.query("PRAGMA index_info('$targetIndex')").use { cursor ->
                while (cursor.moveToNext()) {
                    indexColumns += cursor.getString(cursor.getColumnIndexOrThrow("name")) to
                        cursor.getInt(cursor.getColumnIndexOrThrow("seqno"))
                }
            }
            assertEquals(
                listOf("accountId" to 0, "serverUpdatedAt" to 1, "recordId" to 2),
                indexColumns
            )

            // The delete predicate must include accountId: deleting A cannot delete B.
            assertEquals(1, dao.delete("account-A", "same-record"))
            assertNull(dao.find("account-A", "same-record"))
            assertEquals("ciphertext-B", dao.find("account-B", "same-record")?.payloadCiphertext)
            assertTrue(dao.insertIfAbsent(accountA) != -1L)
            assertEquals("ciphertext-A", dao.find("account-A", "same-record")?.payloadCiphertext)

            // A quarantined legacy row is never visible in either authenticated account scope.
            assertFalse(dao.pending("account-A").any { it.accountId.isBlank() })
            assertFalse(dao.pending("account-B").any { it.accountId.isBlank() })
            sqlite.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
            sqlite.query("PRAGMA integrity_check").use {
                assertTrue(it.moveToFirst())
                assertEquals("ok",it.getString(0))
            }
        } finally { db.close() }

        val reopened=Room.databaseBuilder(context,MetanoiaDatabase::class.java,name).build()
        try {
            assertEquals("ciphertext-A",reopened.syncRemoteRecordDao().find("account-A","same-record")?.payloadCiphertext)
            assertEquals("ciphertext-B",reopened.syncRemoteRecordDao().find("account-B","same-record")?.payloadCiphertext)
            assertEquals("legacy-ciphertext",reopened.syncRemoteRecordDao().find("","same-record")?.payloadCiphertext)
        } finally {
            reopened.close()
            context.deleteDatabase(name)
        }
    }

    private fun createV6Schema(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE agenda_items(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,title TEXT NOT NULL,description TEXT NOT NULL,dimension TEXT NOT NULL,kind TEXT NOT NULL,scheduledAt INTEGER NOT NULL,completed INTEGER NOT NULL,recurrenceRule TEXT,reminderAt INTEGER,createdAt INTEGER NOT NULL)""")
        db.execSQL("""CREATE TABLE journal_entries(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,title TEXT NOT NULL,content TEXT NOT NULL,dimension TEXT NOT NULL,createdAt INTEGER NOT NULL,updatedAt INTEGER NOT NULL,dayKey TEXT NOT NULL DEFAULT '')""")
        db.execSQL("""CREATE TABLE review_entries(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,period TEXT NOT NULL,periodStart INTEGER NOT NULL,periodEnd INTEGER NOT NULL,reflection TEXT NOT NULL,createdAt INTEGER NOT NULL)""")
        db.execSQL("""CREATE TABLE confession_entries(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,dayKey TEXT NOT NULL,content TEXT NOT NULL,createdAt INTEGER NOT NULL,updatedAt INTEGER NOT NULL)""")
        db.execSQL("""CREATE TABLE daily_analysis(dayKey TEXT NOT NULL PRIMARY KEY,categories TEXT NOT NULL,dimensions TEXT NOT NULL,emotionalState TEXT NOT NULL,summary TEXT NOT NULL,attention TEXT NOT NULL,learning TEXT NOT NULL,direction TEXT NOT NULL,nextStep TEXT NOT NULL,scriptureReference TEXT NOT NULL,scriptureContext TEXT NOT NULL,updatedAt INTEGER NOT NULL)""")
        db.execSQL("""CREATE TABLE sync_outbox(operationId TEXT NOT NULL PRIMARY KEY,recordId TEXT NOT NULL,dimension TEXT NOT NULL,recordType TEXT NOT NULL,operation TEXT NOT NULL,payloadCiphertext TEXT NOT NULL,encryptionVersion INTEGER NOT NULL,baseRevision INTEGER NOT NULL,clientUpdatedAt INTEGER NOT NULL,status TEXT NOT NULL,attemptCount INTEGER NOT NULL,nextAttemptAt INTEGER NOT NULL,lastError TEXT,createdAt INTEGER NOT NULL,sourceDeviceId TEXT NOT NULL DEFAULT '',purpose TEXT NOT NULL DEFAULT 'record')""")
        db.execSQL("CREATE INDEX index_sync_outbox_status_nextAttemptAt ON sync_outbox(status,nextAttemptAt)")
        db.execSQL("CREATE INDEX index_sync_outbox_recordId_createdAt ON sync_outbox(recordId,createdAt)")
        db.execSQL("""CREATE TABLE sync_nonce_counters(accountId TEXT NOT NULL,deviceId TEXT NOT NULL,purpose TEXT NOT NULL,nextCounter INTEGER NOT NULL,PRIMARY KEY(accountId,deviceId,purpose))""")
        db.execSQL("""CREATE TABLE sync_record_metadata(entityType TEXT NOT NULL,localId TEXT NOT NULL,recordId TEXT NOT NULL,dimension TEXT NOT NULL,recordType TEXT NOT NULL,revision INTEGER NOT NULL,syncState TEXT NOT NULL,sourceDeviceId TEXT,encryptionVersion INTEGER NOT NULL,remoteUpdatedAt INTEGER NOT NULL,updatedAt INTEGER NOT NULL,PRIMARY KEY(entityType,localId))""")
        db.execSQL("CREATE UNIQUE INDEX index_sync_record_metadata_recordId ON sync_record_metadata(recordId)")
        db.execSQL("CREATE INDEX index_sync_record_metadata_syncState_updatedAt ON sync_record_metadata(syncState,updatedAt)")
        db.execSQL("""CREATE TABLE sync_cursors(accountId TEXT NOT NULL,deviceId TEXT NOT NULL,serverUpdatedAt INTEGER NOT NULL,afterRecordId TEXT NOT NULL,updatedAt INTEGER NOT NULL,PRIMARY KEY(accountId,deviceId))""")
        db.execSQL("""CREATE TABLE sync_remote_records(recordId TEXT NOT NULL PRIMARY KEY,dimension TEXT NOT NULL,recordType TEXT NOT NULL,payloadCiphertext TEXT NOT NULL,encryptionVersion INTEGER NOT NULL,revision INTEGER NOT NULL,clientUpdatedAt INTEGER NOT NULL,serverUpdatedAt INTEGER NOT NULL,deletedAt INTEGER,sourceDeviceId TEXT,status TEXT NOT NULL,lastError TEXT)""")
        db.execSQL("CREATE INDEX index_sync_remote_records_serverUpdatedAt_recordId ON sync_remote_records(serverUpdatedAt,recordId)")
    }
}
