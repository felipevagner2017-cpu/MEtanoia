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

    private fun navigateAndAssert(tab: String, expected: String) {
        composeRule.onNodeWithText(tab, substring = false).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(expected, substring = false)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(expected, substring = false).assertIsDisplayed()
    }

    @Test
    fun fourDimensionsAndDailyAreasAreReachable() {
        navigateAndAssert("Devoção", "Devocional / Espiritual")
        navigateAndAssert("Matrimônio", "Matrimônio / Ingrid")
        navigateAndAssert("Profissional", "DIMENSÃO 03")
        navigateAndAssert("Cotidiano", "Minha agenda")
        navigateAndAssert("Diário", "Diário de hoje")
        navigateAndAssert("Revisões", "Revisões históricas")
        navigateAndAssert("Alertas", "Notificações e alarmes")
    }
}
