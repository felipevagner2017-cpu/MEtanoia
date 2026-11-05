package br.com.metanoia.domain

import java.time.LocalDate
import java.time.YearMonth

enum class RecurrenceFrequency { DAILY, WEEKDAYS, WEEKLY, MONTHLY }

object RecurrenceEngine {
    fun datesUntil(start: LocalDate, frequency: RecurrenceFrequency, endInclusive: LocalDate): List<LocalDate> {
        require(!endInclusive.isBefore(start)) { "A data final deve ser igual ou posterior à inicial." }
        val dates = mutableListOf<LocalDate>()
        var current = start
        while (!current.isAfter(endInclusive)) {
            dates += current
            current = nextDate(current, frequency)
        }
        return dates
    }

    fun nextDate(after: LocalDate, frequency: RecurrenceFrequency): LocalDate = when (frequency) {
        RecurrenceFrequency.DAILY -> after.plusDays(1)
        RecurrenceFrequency.WEEKDAYS -> {
            var candidate = after.plusDays(1)
            while (candidate.dayOfWeek.value >= 6) candidate = candidate.plusDays(1)
            candidate
        }
        RecurrenceFrequency.WEEKLY -> after.plusWeeks(1)
        RecurrenceFrequency.MONTHLY -> {
            val nextMonth = YearMonth.from(after).plusMonths(1)
            nextMonth.atDay(minOf(after.dayOfMonth, nextMonth.lengthOfMonth()))
        }
    }
}
