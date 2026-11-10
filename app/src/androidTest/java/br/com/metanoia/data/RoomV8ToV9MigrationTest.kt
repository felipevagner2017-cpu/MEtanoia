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
            legacy.execSQL("""INSERT INTO agenda_items(
                id,title,description,dimension,kind,scheduledAt,completed,recurrenceRule,reminderAt,createdAt
            ) VALUES(42,'Consulta','Retorno','ROUTINE','APPOINTMENT',1700000000000,0,NULL,1699992800000,1690000000000)""")
            legacy.version = 8
        } finally { legacy.close() }

        val db = Room.databaseBuilder(context, MetanoiaDatabase::class.java, name)
            .addMigrations(MetanoiaDatabase.MIGRATION_8_9)
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
