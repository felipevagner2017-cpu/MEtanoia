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
    fun allFiveHorizontalSectionsAndEveryDailyAreaAreReachable() {
        composeRule.onNodeWithText("Devocional", substring = false).performClick()
        composeRule.onNodeWithText("Passagem ou tema estudado").assertExists()

        composeRule.onNodeWithText("Matrimônio", substring = false).performClick()
        composeRule.onNodeWithText("DESAFIO MATRIMONIAL").assertExists()

        composeRule.onNodeWithText("Profissional", substring = false).performClick()
        composeRule.onNodeWithText("Meta ou projeto").assertExists()

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
