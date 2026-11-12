package br.com.metanoia.data
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)
class RoomV5ToV6MigrationTest {
 @Test fun migratesV5FixturePreservingRowsAndCreatingStableMetadata()=runBlocking {
  val context=ApplicationProvider.getApplicationContext<Context>(); val name="room_v5_v6_fixture.db"
  context.deleteDatabase(name); val file=context.getDatabasePath(name); file.parentFile?.mkdirs()
  val legacy=SQLiteDatabase.openOrCreateDatabase(file,null)
  try {
   legacy.execSQL("CREATE TABLE agenda_items(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,title TEXT NOT NULL,description TEXT NOT NULL,dimension TEXT NOT NULL,kind TEXT NOT NULL,scheduledAt INTEGER NOT NULL,completed INTEGER NOT NULL,recurrenceRule TEXT,reminderAt INTEGER,createdAt INTEGER NOT NULL)")
   legacy.execSQL("CREATE TABLE journal_entries(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,title TEXT NOT NULL,content TEXT NOT NULL,dimension TEXT NOT NULL,createdAt INTEGER NOT NULL,updatedAt INTEGER NOT NULL,dayKey TEXT NOT NULL DEFAULT '')")
   legacy.execSQL("CREATE TABLE review_entries(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,period TEXT NOT NULL,periodStart INTEGER NOT NULL,periodEnd INTEGER NOT NULL,reflection TEXT NOT NULL,createdAt INTEGER NOT NULL)")
   legacy.execSQL("CREATE TABLE confession_entries(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,dayKey TEXT NOT NULL,content TEXT NOT NULL,createdAt INTEGER NOT NULL,updatedAt INTEGER NOT NULL)")
   legacy.execSQL("CREATE TABLE daily_analysis(dayKey TEXT NOT NULL PRIMARY KEY,categories TEXT NOT NULL,dimensions TEXT NOT NULL,emotionalState TEXT NOT NULL,summary TEXT NOT NULL,attention TEXT NOT NULL,learning TEXT NOT NULL,direction TEXT NOT NULL,nextStep TEXT NOT NULL,scriptureReference TEXT NOT NULL,scriptureContext TEXT NOT NULL,updatedAt INTEGER NOT NULL)")
   legacy.execSQL("CREATE TABLE sync_outbox(operationId TEXT NOT NULL PRIMARY KEY,recordId TEXT NOT NULL,dimension TEXT NOT NULL,recordType TEXT NOT NULL,operation TEXT NOT NULL,payloadCiphertext TEXT NOT NULL,encryptionVersion INTEGER NOT NULL,baseRevision INTEGER NOT NULL,clientUpdatedAt INTEGER NOT NULL,status TEXT NOT NULL,attemptCount INTEGER NOT NULL,nextAttemptAt INTEGER NOT NULL,lastError TEXT,createdAt INTEGER NOT NULL)")
   legacy.execSQL("CREATE INDEX index_sync_outbox_status_nextAttemptAt ON sync_outbox(status,nextAttemptAt)")
   legacy.execSQL("CREATE INDEX index_sync_outbox_recordId_createdAt ON sync_outbox(recordId,createdAt)")
   legacy.execSQL("CREATE TABLE sync_nonce_counters(accountId TEXT NOT NULL,deviceId TEXT NOT NULL,purpose TEXT NOT NULL,nextCounter INTEGER NOT NULL,PRIMARY KEY(accountId,deviceId,purpose))")
   legacy.execSQL("INSERT INTO agenda_items VALUES(501,'Agenda V5','descrição','ROUTINE','TASK',1793900000000,0,NULL,NULL,1793890000000)")
   legacy.execSQL("INSERT INTO journal_entries VALUES(502,'Diário V5','texto','INDIVIDUAL',1793890000000,1793890000001,'2026-11-05')")
   legacy.execSQL("INSERT INTO review_entries VALUES(503,'WEEKLY',1793400000000,1794000000000,'Revisão V5',1793890000000)")
   legacy.execSQL("INSERT INTO confession_entries VALUES(504,'2026-11-05','Confissão V5',1793890000000,1793890000001)")
   legacy.execSQL("INSERT INTO daily_analysis VALUES('2026-11-05','[]','[]','sereno','Análise V5','','','','','','',1793890000001)")
   legacy.execSQL("INSERT INTO sync_outbox VALUES('op-v5','legacy-record-v5','DAILY','journal','upsert','v1.ciphertext-v5',1,0,1793890000001,'pending',0,0,NULL,1793890000000)")
   legacy.version=5
  } finally { legacy.close() }
  val db=Room.databaseBuilder(context,MetanoiaDatabase::class.java,name).addMigrations(MetanoiaDatabase.MIGRATION_5_6,MetanoiaDatabase.MIGRATION_6_7,MetanoiaDatabase.MIGRATION_7_8, MetanoiaDatabase.MIGRATION_8_9, MetanoiaDatabase.MIGRATION_9_10).build()
  try {
   assertEquals(10,db.openHelper.readableDatabase.version)
   assertEquals("Agenda V5",db.agendaDao().findById(501L)?.title)
   assertEquals("Diário V5",db.journalDao().findForDay("2026-11-05")?.title)
   assertEquals("Revisão V5",db.reviewDao().observeHistory().first().single().reflection)
   assertEquals("Confissão V5",db.confessionDao().findForDay("2026-11-05")?.content)
   assertEquals("Análise V5",db.dailyAnalysisDao().findForDay("2026-11-05")?.summary)
   val old=db.openHelper.readableDatabase.query("SELECT payloadCiphertext,status,sourceDeviceId,purpose FROM sync_outbox")
   old.use { c -> assertTrue(c.moveToFirst()); assertEquals("v1.ciphertext-v5",c.getString(0)); assertEquals("quarantined",c.getString(1)); assertEquals("",c.getString(2)); assertEquals("record",c.getString(3)) }
   val meta=db.syncRecordMetadataDao()
   assertEquals(5,meta.count()); assertEquals("legacy-agenda_items-501",meta.find("agenda_items","501")?.recordId)
   assertEquals("DAILY",meta.find("agenda_items","501")?.dimension); assertEquals("local",meta.find("agenda_items","501")?.syncState)
   assertEquals(0,meta.find("agenda_items","501")?.encryptionVersion)
   assertEquals(1,db.syncRemoteRecordDao().insertIfAbsent(SyncRemoteRecord(accountId="account-test",recordId="remote-v5-test",dimension="DAILY",recordType="journal",payloadCiphertext="v2.cipher",encryptionVersion=2,revision=3,clientUpdatedAt=1793890000000,serverUpdatedAt=1793890001000,deletedAt=null,sourceDeviceId="device-test")))
   assertEquals("remote-v5-test",db.syncRemoteRecordDao().find("account-test","remote-v5-test")?.recordId)
   assertEquals(1,db.syncRemoteRecordDao().pending("account-test").size)
   db.syncCursorDao().save(SyncCursorEntity(accountId="account-test",deviceId="device-test",serverUpdatedAt=1793890001000,afterRecordId="remote-v5-test"))
   assertEquals("remote-v5-test",db.syncCursorDao().get("account-test","device-test")?.afterRecordId)
   assertEquals(5,db.syncRecordMetadataDao().pendingOrConflicted().size)
   val sqlite=db.openHelper.readableDatabase
   sqlite.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
   sqlite.query("PRAGMA integrity_check").use { assertTrue(it.moveToFirst()); assertEquals("ok",it.getString(0)) }
  } finally { db.close(); context.deleteDatabase(name) }
 }
}
