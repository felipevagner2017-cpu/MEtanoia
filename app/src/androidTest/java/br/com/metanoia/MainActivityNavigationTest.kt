package br.com.metanoia

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.rule.GrantPermissionRule
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

        composeRule.onAllNodesWithText("Profissional", substring = false)[0].performClick()
        composeRule.onNodeWithText("DIMENSÃO 03", substring = false).assertIsDisplayed()

        composeRule.onNodeWithText("Cotidiano", substring = false).performClick()
        composeRule.onNodeWithText("Minha agenda", substring = false).assertIsDisplayed()

        composeRule.onNodeWithText("Diário", substring = false).performClick()
        composeRule.onNodeWithText("Diário de hoje", substring = false).assertIsDisplayed()

        composeRule.onNodeWithText("Revisões", substring = false).performClick()
        composeRule.onNodeWithText("Revisões históricas", substring = false).assertIsDisplayed()

        composeRule.onNodeWithText("Alertas", substring = false).performClick()
        composeRule.onNodeWithText("Notificações e alarmes", substring = false).assertIsDisplayed()
    }
}
