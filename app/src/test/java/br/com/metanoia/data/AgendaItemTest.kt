package br.com.metanoia.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AgendaItemTest {
    @Test
    fun defaultItemStartsAsIncompleteRoutineTask() {
        val item = AgendaItem(title = "Revisar o dia", scheduledAt = 1L)
        assertEquals(AgendaKind.TASK, item.kind)
        assertEquals(LifeDimension.ROUTINE, item.dimension)
        assertFalse(item.completed)
    }

    @Test
    fun dimensionsRepresentAllFiveLifeAreas() {
        assertEquals(5, LifeDimension.entries.size)
    }
}
