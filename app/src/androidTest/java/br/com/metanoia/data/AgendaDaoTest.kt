package br.com.metanoia.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AgendaDaoTest {
    private lateinit var database: MetanoiaDatabase
    private val dao get() = database.agendaDao()

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MetanoiaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun rangeQueryReturnsOnlyTheSelectedLocalDayAndCrudUpdatesRoom() = runBlocking {
        val zone = ZoneId.of("America/Sao_Paulo")
        val day = LocalDate.of(2026, 11, 4)
        val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

        val today = AgendaItem(title = "Ensaio", scheduledAt = day.atTime(9, 0).atZone(zone).toInstant().toEpochMilli())
        val tomorrow = AgendaItem(title = "Outro dia", scheduledAt = day.plusDays(1).atTime(9, 0).atZone(zone).toInstant().toEpochMilli())
        val id = dao.insert(today)
        dao.insert(tomorrow)

        assertEquals(listOf("Ensaio"), dao.observeRange(start, end).first().map { it.title })

        val stored = dao.observeRange(start, end).first().single()
        dao.update(stored.copy(title = "Ensaio atualizado", description = "Passagem de som"))
        val edited = dao.observeRange(start, end).first().single()
        assertEquals("Ensaio atualizado", edited.title)
        assertEquals("Passagem de som", edited.description)

        dao.setCompleted(id, true)
        assertTrue(dao.observeRange(start, end).first().single().completed)
        dao.delete(dao.observeRange(start, end).first().single())
        assertTrue(dao.observeRange(start, end).first().isEmpty())
        assertFalse(dao.observeAll().first().isEmpty())
    }
    @Test
    fun dailyDiaryLookupDoesNotConfuseDimensionRecordsWithDiary() = runBlocking {
        val dayKey = "2026-11-04"
        database.journalDao().insert(
            JournalEntry(title = "Estudo bíblico", content = "Registro espiritual", dimension = LifeDimension.SPIRITUAL, dayKey = dayKey)
        )
        assertEquals(null, database.journalDao().findForDay(dayKey))

        database.journalDao().insert(
            JournalEntry(title = "Relato diário", content = "Como foi meu dia", dimension = LifeDimension.INDIVIDUAL, dayKey = dayKey)
        )
        assertEquals("Relato diário", database.journalDao().findForDay(dayKey)?.title)
    }

}
