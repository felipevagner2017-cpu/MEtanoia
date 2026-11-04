package br.com.metanoia

import android.Manifest
import androidx.test.rule.GrantPermissionRule
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
        composeRule.onNodeWithText("Texto bíblico ou tema pastoral").assertExists()

        composeRule.onNodeWithText("Matrimônio", substring = false).performClick()
        composeRule.onNodeWithText("DESAFIO MATRIMONIAL").assertExists()

        composeRule.onNodeWithText("Profissional", substring = false).performClick()
        composeRule.onNodeWithText("Meta, projeto ou desafio profissional").assertExists()

        composeRule.onNodeWithText("Cotidiano", substring = false).performClick()
        composeRule.onNodeWithText("Minha agenda").assertExists()

        composeRule.onNodeWithText("Diário", substring = false).performClick()
        composeRule.onNodeWithText("Diário de hoje").assertExists()

        composeRule.onNodeWithText("Revisões", substring = false).performClick()
        composeRule.onNodeWithText("Revisões históricas").assertExists()

        composeRule.onNodeWithText("Alertas", substring = false).performClick()
        composeRule.onNodeWithText("Notificações e alarmes").assertExists()
    }
}
