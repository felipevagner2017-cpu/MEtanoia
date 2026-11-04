package br.com.metanoia

import android.Manifest
import androidx.test.rule.GrantPermissionRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Rule
import org.junit.Test

class MainActivityNavigationTest {
    @get:Rule
    val permissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun fourDimensionsAndDailyAreasAreReachable() {
        composeRule.onNodeWithText("Devoção", substring = false).performClick()
        composeRule.onNodeWithText("Devocional / Espiritual", substring = false).assertIsDisplayed()

        composeRule.onNodeWithText("Matrimônio", substring = false).performClick()
        composeRule.onNodeWithText("Matrimônio / Ingrid", substring = false).assertIsDisplayed()

        composeRule.onNodeWithText("Profissional", substring = false).performClick()
        composeRule.onNodeWithText("Meta, projeto ou desafio profissional").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithText("Cotidiano", substring = false).performClick()
        composeRule.onNodeWithText("Minha agenda").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithText("Diário", substring = false).performClick()
        composeRule.onNodeWithText("Diário de hoje").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithText("Revisões", substring = false).performClick()
        composeRule.onNodeWithText("Revisões históricas").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithText("Alertas", substring = false).performClick()
        composeRule.onNodeWithText("LEMBRETES DIÁRIOS").performScrollTo().assertIsDisplayed()
    }
}
