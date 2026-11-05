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
    @Test fun dailyGeneratesEveryDateThroughTheHorizon() {
        assertEquals(
            listOf(LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 5)),
            RecurrenceEngine.datesUntil(LocalDate.of(2026, 11, 3), RecurrenceFrequency.DAILY, LocalDate.of(2026, 11, 5))
        )
    }

    @Test fun weekdaysGeneratesOnlyMondayThroughFriday() {
        assertEquals(
            listOf(LocalDate.of(2026, 11, 6), LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 10)),
            RecurrenceEngine.datesUntil(LocalDate.of(2026, 11, 6), RecurrenceFrequency.WEEKDAYS, LocalDate.of(2026, 11, 10))
        )
    }
}
