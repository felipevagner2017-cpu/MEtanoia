package br.com.metanoia.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class RecurrenceEngineTest {
    @Test fun dailyMovesOneDayForward() {
        assertEquals(LocalDate.of(2026, 11, 4), RecurrenceEngine.nextDate(LocalDate.of(2026, 11, 3), RecurrenceFrequency.DAILY))
    }

    @Test fun weekdaysSkipsWeekend() {
        assertEquals(LocalDate.of(2026, 11, 9), RecurrenceEngine.nextDate(LocalDate.of(2026, 11, 6), RecurrenceFrequency.WEEKDAYS))
    }

    @Test fun monthlyClampsToLastDayOfShorterMonth() {
        assertEquals(LocalDate.of(2027, 2, 28), RecurrenceEngine.nextDate(LocalDate.of(2027, 1, 31), RecurrenceFrequency.MONTHLY))
    }
}
