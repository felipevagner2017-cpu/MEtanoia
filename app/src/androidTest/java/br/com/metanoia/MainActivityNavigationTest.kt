package br.com.metanoia

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.printToLog
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
        composeRule.onNodeWithText(tab, substring = false).assertIsDisplayed().performClick()
        try {
            composeRule.waitUntil(timeoutMillis = 15_000) {
                runCatching {
                    composeRule.onNodeWithText(expected, substring = false).assertIsDisplayed()
                    true
                }.getOrDefault(false)
            }
            composeRule.onNodeWithText(expected, substring = false).assertIsDisplayed()
        } catch (failure: Throwable) {
            // Keep the real UI hierarchy in CI logs to distinguish a navigation defect
            // from an incorrect expected label or an emulator timing issue.
            composeRule.onRoot().printToLog("METoNOIA_NAV_FAILURE_$tab")
            throw AssertionError("A aba '$tab' não exibiu o conteúdo esperado '$expected'.", failure)
        }
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
