package br.com.metanoia.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class DailyContextAnalyzerTest {
    @Test fun sadnessIsNotClassifiedAsSin() {
        val result = DailyContextAnalyzer.analyze("Estou triste e desanimado.", "")
        assertTrue(result.categories.contains("Expressão emocional"))
        assertFalse(result.categories.contains("Reconhecimento de erro"))
        assertFalse(result.categories.contains("Culpa ou arrependimento"))
        assertEquals("", result.scriptureReference.let { if (it == "1 João 1:9") it else "" })
        assertTrue(result.direction.contains("Salmo 34:18"))
    }

    @Test fun financialConcernGetsPracticalAndNonCondemningResponse() {
        val result = DailyContextAnalyzer.analyze("Minha situação financeira está ruim e não encontro soluções.", "")
        assertTrue(result.categories.contains("Dificuldade financeira"))
        assertFalse(result.categories.contains("Reconhecimento de erro"))
        assertTrue(result.direction.contains("Provérbios 21:5"))
        assertTrue(result.nextStep.contains("preocupação financeira"))
    }

    @Test fun explicitWrongdoingIsRecognizedWithoutAutomaticVerdict() {
        val result = DailyContextAnalyzer.analyze("Fiz algo errado e estou me sentindo culpado.", "")
        assertTrue(result.categories.contains("Reconhecimento de erro"))
        assertTrue(result.categories.contains("Culpa ou arrependimento"))
        assertTrue(result.scriptureReference == "1 João 1:9")
        assertTrue(result.nextStep.contains("reparar"))
    }

    @Test fun spiritualDistanceIsClassifiedAsSpiritual() {
        val result = DailyContextAnalyzer.analyze("Estou sentindo que me afastei de Deus e não tenho conseguido orar.", "")
        assertTrue(result.dimensions.contains("Espiritual"))
        assertTrue(result.scriptureReference == "Lucas 11:9-13")
        assertFalse(result.categories.contains("Reconhecimento de erro"))
    }

    @Test fun maritalConflictDoesNotChooseTheResponsiblePerson() {
        val result = DailyContextAnalyzer.analyze("Tive uma discussão com minha esposa e fiquei muito magoado.", "")
        assertTrue(result.dimensions.contains("Matrimonial"))
        assertTrue(result.categories.contains("Expressão emocional"))
        assertTrue(result.attention.contains("sem atribuir culpa"))
    }

    @Test fun integratedReportIdentifiesMultipleDimensions() {
        val result = DailyContextAnalyzer.analyze(
            "Hoje trabalhei muito, tive uma discussão em casa, fiquei preocupado com dinheiro e não consegui separar um tempo para oração.",
            ""
        )
        assertTrue(result.dimensions.contains("Profissional"))
        assertTrue(result.dimensions.contains("Matrimonial"))
        assertTrue(result.dimensions.contains("Individual"))
        assertTrue(result.dimensions.contains("Espiritual"))
        assertFalse(result.categories.contains("Reconhecimento de erro"))
    }

    @Test fun diaryAndConfessionAreInterpretedTogether() {
        val result = DailyContextAnalyzer.analyze(
            "Hoje tive dificuldades no trabalho e fiquei muito preocupado com dinheiro.",
            "Estou triste, sinto que não estou conseguindo resolver as coisas."
        )
        assertTrue(result.dimensions.contains("Profissional"))
        assertTrue(result.dimensions.contains("Individual"))
        assertTrue(result.categories.contains("Dificuldade financeira"))
        assertTrue(result.categories.contains("Expressão emocional"))
        assertTrue(result.summary.contains("dificuldades no trabalho"))
        assertTrue(result.summary.contains("não estou conseguindo resolver"))
    }

    @Test fun wordConfessoAloneDoesNotMeanSin() {
        val result = DailyContextAnalyzer.analyze("Confesso que estou com medo do futuro.", "")
        assertFalse(result.categories.contains("Reconhecimento de erro"))
        assertFalse(result.categories.contains("Culpa ou arrependimento"))
        assertTrue(result.categories.contains("Medos e preocupações"))
    }
}
