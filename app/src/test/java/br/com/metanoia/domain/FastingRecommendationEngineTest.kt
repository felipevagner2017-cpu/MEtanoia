package br.com.metanoia.domain

import br.com.metanoia.data.FastingPlan
import br.com.metanoia.data.LifeDimension
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FastingRecommendationEngineTest {
    private val engine = FastingRecommendationEngine()

    @Test
    fun recommendationsDefaultToNonFoodAndShortDuration() {
        val result = engine.recommend(LifeDimension.SPIRITUAL, listOf("Tenho me distraído nas redes sociais"), emptyList())
        requireNotNull(result)
        assertEquals("NON_FOOD", result.type)
        assertEquals(1, result.suggestedDurationDays)
        assertTrue(result.scriptureReferences.isNotEmpty())
        assertFalse(result.preparation.contains("suspender medicamentos", ignoreCase = true))
    }

    @Test
    fun doesNotInferSpecificProblemFromEmptyRecords() {
        val result = engine.recommend(LifeDimension.MATRIMONIAL, emptyList(), emptyList())
        requireNotNull(result)
        assertTrue(result.recommendationReason.contains("Não há registros recentes"))
    }

    @Test
    fun avoidsRepeatingRecentSuggestionForSameDimension() {
        val previous = FastingPlan(
            id = "previous", dimension = LifeDimension.SPIRITUAL, type = "NON_FOOD",
            identifiedNeed = "Atenção", recommendationReason = "Contexto",
            purpose = "Propósito", scriptureReferences = "Mateus 6:16–18",
            scriptureContext = "Contexto", preparation = "Preparação",
            dailyGuidance = "Orientação", durationDays = 1,
            startAt = System.currentTimeMillis(), endAt = System.currentTimeMillis() + 86400000,
            createdAt = System.currentTimeMillis()
        )
        assertNull(engine.recommend(LifeDimension.SPIRITUAL, listOf("distração"), listOf(previous)))
    }

    @Test
    fun previousSuggestionInAnotherDimensionDoesNotBlockCurrentNeed() {
        val previous = FastingPlan(
            id = "previous", dimension = LifeDimension.SPIRITUAL, type = "NON_FOOD",
            identifiedNeed = "Atenção", recommendationReason = "Contexto",
            purpose = "Propósito", scriptureReferences = "Mateus 6:16–18",
            scriptureContext = "Contexto", preparation = "Preparação",
            dailyGuidance = "Orientação", durationDays = 1,
            startAt = System.currentTimeMillis(), endAt = System.currentTimeMillis() + 86400000,
            createdAt = System.currentTimeMillis()
        )
        assertEquals(LifeDimension.MATRIMONIAL,
            engine.recommend(LifeDimension.MATRIMONIAL, emptyList(), listOf(previous))?.dimension)
    }
}
