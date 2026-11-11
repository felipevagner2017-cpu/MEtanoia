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
    @Test
    fun contextualSignalsAreReadAcrossTheFourLifeDimensions() {
        val spiritual = engine.recommend(LifeDimension.SPIRITUAL, listOf("Tenho me distraído no celular"), emptyList())
        val matrimonial = engine.recommend(LifeDimension.MATRIMONIAL, listOf("Tivemos um conflito e precisamos conversar"), emptyList())
        val professional = engine.recommend(LifeDimension.PROFESSIONAL, listOf("Estou com sobrecarga e dificuldade de foco"), emptyList())
        val routine = engine.recommend(LifeDimension.ROUTINE, listOf("Estou tentando organizar meu sono e hábitos"), emptyList())

        assertTrue(requireNotNull(spiritual).recommendationReason.contains("Distrações"))
        assertTrue(requireNotNull(matrimonial).recommendationReason.contains("perspectiva da outra pessoa"))
        assertTrue(requireNotNull(professional).recommendationReason.contains("foco"))
        assertTrue(requireNotNull(routine).recommendationReason.contains("hábitos"))
    }

    @Test
    fun recentPlanOfAnyTypePreventsImmediateRepeatInSameDimension() {
        val previous = FastingPlan(
            id = "previous", dimension = LifeDimension.SPIRITUAL, type = "FOOD",
            identifiedNeed = "Atenção", recommendationReason = "Contexto",
            purpose = "Propósito", scriptureReferences = "Mateus 6:16–18",
            scriptureContext = "Contexto", preparation = "Preparação",
            dailyGuidance = "Orientação", durationDays = 1,
            startAt = System.currentTimeMillis(), endAt = System.currentTimeMillis() + 86400000,
            foodAbstinence = true, createdAt = System.currentTimeMillis()
        )
        assertNull(engine.recommend(LifeDimension.SPIRITUAL, listOf("distração"), listOf(previous)))
    }


    @Test
    fun blankOrUnrecognizedRecordsDoNotCreateFalsePersonalization() {
        val result = engine.recommend(
            LifeDimension.PROFESSIONAL,
            listOf("", "   ", "registro sem sinal reconhecível"),
            emptyList()
        )
        requireNotNull(result)
        assertTrue(result.recommendationReason.contains("sugestão é geral"))
    }
}
