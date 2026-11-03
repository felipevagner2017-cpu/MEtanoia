package br.com.metanoia.domain

import java.time.LocalDate
import java.time.YearMonth

enum class RecurrenceFrequency { DAILY, WEEKDAYS, WEEKLY, MONTHLY }

object RecurrenceEngine {
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
