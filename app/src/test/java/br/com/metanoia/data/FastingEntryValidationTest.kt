package br.com.metanoia.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FastingEntryValidationTest {
    private val validPlan = FastingPlan(
        id = "plan-1",
        dimension = LifeDimension.SPIRITUAL,
        type = "NON_FOOD",
        identifiedNeed = "Atenção",
        recommendationReason = "Registro contextualizado",
        purpose = "Oração e reflexão voluntárias",
        scriptureReferences = "Mateus 6:16–18",
        scriptureContext = "Jesus ensina a evitar a ostentação",
        preparation = "Escolha livre e responsável",
        dailyGuidance = "Leia, ore e reflita",
        durationDays = 1,
        startAt = 1_000L,
        endAt = 2_000L,
        createdAt = 1_000L
    )

    @Test
    fun acceptsNonFoodPlansAndDoesNotImposeFortyDayLimit() {
        assertFalse(validPlan.foodAbstinence)
        assertEquals(45, validPlan.copy(durationDays = 45).durationDays)
    }

    @Test
    fun rejectsInvalidPlanValues() {
        expectIllegalArgument { validPlan.copy(id = " ") }
        expectIllegalArgument { validPlan.copy(type = "UNKNOWN") }
        expectIllegalArgument { validPlan.copy(durationDays = 0) }
        expectIllegalArgument { validPlan.copy(startAt = 2_000L, endAt = 1_000L) }
        expectIllegalArgument { validPlan.copy(foodAbstinence = true) }
    }

    @Test
    fun rejectsInvalidDailyReflectionValues() {
        val entry = FastingDailyEntry(
            id = "entry-1",
            planId = validPlan.id,
            dayIndex = 0,
            reflection = "Reflexão",
            experience = "Experiência",
            recordedAt = 1_100L
        )
        expectIllegalArgument { entry.copy(id = " ") }
        expectIllegalArgument { entry.copy(planId = " ") }
        expectIllegalArgument { entry.copy(dayIndex = -1) }
        expectIllegalArgument { entry.copy(recordedAt = 0L) }
    }

    private fun expectIllegalArgument(block: () -> Unit) {
        var rejected = false
        try {
            block()
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertTrue("O dado inválido deveria ser rejeitado.", rejected)
    }
}
